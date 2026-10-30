package com.cardgame.network

import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * Прямая игра по одной сети без сервера: один телефон «создаёт игру» (TCP-сервер + ответы на
 * UDP-поиск), второй находит его и подключается. Работает и в общей Wi-Fi сети, и когда одно
 * из устройств раздаёт точку доступа.
 *
 * Строки по сети — JSON, по одной на строку:
 *   клиент → хозяину: {"type":"HELLO","faction":"HUMANS"}
 *   хозяин → клиенту: {"type":"MATCH_FOUND",...}   (тот же формат, что у server.py)
 *   далее в обе стороны: {"type":"EVENT","payload":{...}} и {"type":"PING"}
 */

// ============================================================ поиск устройств (UDP)

object LanDiscovery {

    /** Адреса всех широковещательных рассылок активных сетей этого устройства. */
    fun broadcastAddresses(): List<InetAddress> {
        val result = LinkedHashSet<InetAddress>()
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) ia.broadcast?.let { result.add(it) }
            }
        } catch (_: Exception) {
        }
        return result.toList()
    }

    /** Собственные IPv4-адреса устройства в локальных сетях — чтобы показать их игроку. */
    fun localAddresses(): List<String> {
        val result = ArrayList<String>()
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) {
                    val a = ia.address
                    if (a is java.net.Inet4Address && !a.isLoopbackAddress) a.hostAddress?.let { result.add(it) }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    /**
     * Рассылает запрос и собирает ответы timeoutMs миллисекунд.
     * extraTargets — адреса, на которые запрос шлётся напрямую (шлюз Wi-Fi, IP из поля ввода):
     * помогает, когда роутер не пропускает широковещание.
     */
    fun scan(
        extraTargets: Collection<String> = emptyList(),
        timeoutMs: Int = 1200,
        port: Int = NetConfig.DISCOVERY_PORT
    ): List<DiscoveredHost> {
        val found = LinkedHashMap<String, DiscoveredHost>()
        val socket = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(0))
            }
        } catch (_: Exception) {
            return emptyList()
        }
        try {
            val data = NetConfig.DISCOVER_REQUEST.toByteArray(Charsets.UTF_8)
            val targets = LinkedHashSet<InetAddress>()
            targets.addAll(broadcastAddresses())
            try {
                targets.add(InetAddress.getByName("255.255.255.255"))
            } catch (_: Exception) {
            }
            for (t in extraTargets) {
                val s = t.trim()
                if (s.isEmpty()) continue
                try {
                    targets.add(InetAddress.getByName(s))
                } catch (_: Exception) {
                }
            }
            for (t in targets) {
                try {
                    socket.send(DatagramPacket(data, data.size, t, port))
                } catch (_: Exception) {
                }
            }
            val deadline = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(512)
            while (true) {
                val left = (deadline - System.currentTimeMillis()).toInt()
                if (left <= 0) break
                socket.soTimeout = left
                val p = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(p)
                } catch (_: SocketTimeoutException) {
                    break
                } catch (_: Exception) {
                    break
                }
                val host = parseReply(String(p.data, 0, p.length, Charsets.UTF_8), p.address.hostAddress ?: continue)
                if (host != null) found["${host.kind}@${host.ip}"] = host
            }
        } finally {
            socket.close()
        }
        return found.values.toList()
    }

    internal fun parseReply(text: String, ip: String): DiscoveredHost? {
        val parts = text.trim().split("|")
        return when (parts.firstOrNull()) {
            NetConfig.SERVER_REPLY -> DiscoveredHost(
                ip, parts.getOrNull(1)?.toIntOrNull() ?: NetConfig.SERVER_PORT,
                parts.getOrNull(2) ?: "Сервер", HostKind.SERVER
            )
            NetConfig.HOST_REPLY -> DiscoveredHost(
                ip, parts.getOrNull(1)?.toIntOrNull() ?: NetConfig.LAN_PORT,
                parts.getOrNull(2) ?: "Игрок", HostKind.LAN_HOST, parts.getOrNull(3)
            )
            else -> null
        }
    }

    /** Проверка, что на ip:port кто-то слушает (сервер запущен). */
    fun isPortOpen(ip: String, port: Int, timeoutMs: Int = 800): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs) }
        true
    } catch (_: Exception) {
        false
    }
}

/** Отвечает на UDP-запросы поиска, пока создатель игры ждёт соперника. */
class DiscoveryResponder(
    private val replyProvider: () -> String,
    private val port: Int = NetConfig.DISCOVERY_PORT
) {
    @Volatile
    private var running = false
    private var socket: DatagramSocket? = null

    fun start() {
        running = true
        val bindPort = port // внутри apply{} слово «port» означало бы порт сокета (-1)
        thread(isDaemon = true, name = "cardgame-discovery") {
            val s = try {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(bindPort))
                }
            } catch (_: Exception) {
                return@thread // порт занят — игру всё равно можно найти по IP вручную
            }
            socket = s
            val buf = ByteArray(256)
            while (running) {
                try {
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    val text = String(p.data, 0, p.length, Charsets.UTF_8).trim()
                    if (text == NetConfig.DISCOVER_REQUEST) {
                        val reply = replyProvider().toByteArray(Charsets.UTF_8)
                        s.send(DatagramPacket(reply, reply.size, p.address, p.port))
                    }
                } catch (_: Exception) {
                    if (!running) break
                }
            }
            s.close()
        }
    }

    fun stop() {
        running = false
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }
}

// ============================================================ TCP-связь

private const val READ_TIMEOUT_MS = 15_000
private const val PING_EVERY_MS = 4_000L

/** Общая часть хозяина и клиента: строки JSON поверх сокета, пинг, уведомление об обрыве. */
abstract class SocketLink(protected val listener: (JSONObject) -> Unit) : GameLink {
    @Volatile
    protected var closed = false
    private var socket: Socket? = null
    private var out: BufferedWriter? = null
    private val writeLock = Any()

    protected fun attach(s: Socket): BufferedReader {
        s.tcpNoDelay = true
        s.soTimeout = READ_TIMEOUT_MS
        socket = s
        out = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
        thread(isDaemon = true, name = "cardgame-ping") {
            while (!closed) {
                try {
                    Thread.sleep(PING_EVERY_MS)
                } catch (_: InterruptedException) {
                    break
                }
                if (!closed) writeLine(JSONObject().put("type", "PING"))
            }
        }
        return BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
    }

    protected fun writeLine(obj: JSONObject) {
        synchronized(writeLock) {
            try {
                out?.write(obj.toString())
                out?.write("\n")
                out?.flush()
            } catch (_: IOException) {
                // обрыв заметит читающий поток
            }
        }
    }

    override fun send(type: String, data: JSONObject) {
        val payload = JSONObject().put("type", type).put("data", data)
        writeLine(JSONObject().put("type", "EVENT").put("payload", payload))
    }

    /** Читает сообщения до обрыва. Служебные (не EVENT/PING) отдаёт в onOther. */
    protected fun readLoop(reader: BufferedReader, onOther: (JSONObject) -> Unit = {}) {
        try {
            while (!closed) {
                val line = reader.readLine() ?: break
                val obj = try {
                    JSONObject(line)
                } catch (_: Exception) {
                    continue
                }
                when (obj.optString("type")) {
                    "EVENT" -> listener(
                        JSONObject().put("type", "OPPONENT_EVENT").put("payload", obj.getJSONObject("payload"))
                    )
                    "PING" -> Unit
                    else -> onOther(obj)
                }
            }
        } catch (_: IOException) {
            // обрыв или таймаут (соперник пропал из сети)
        } finally {
            if (!closed) {
                closed = true
                listener(JSONObject().put("type", "OPPONENT_DISCONNECTED"))
            }
            closeSocket()
        }
    }

    protected fun closeSocket() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }

    override fun close() {
        closed = true
        closeSocket()
    }
}

/** Создатель игры: ждёт одного подключения, назначает сид и роли, играет как PLAYER_1. */
class LanHost(
    listener: (JSONObject) -> Unit,
    private val port: Int = NetConfig.LAN_PORT,
    private val discoveryPort: Int = NetConfig.DISCOVERY_PORT
) : SocketLink(listener) {

    private var server: ServerSocket? = null
    private var responder: DiscoveryResponder? = null

    fun start(hostName: String, faction: String) {
        val safeName = hostName.replace("|", " ").take(32)
        val bindPort = port
        try {
            server = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(bindPort))
            }
        } catch (e: Exception) {
            listener(errorMessage("Не удалось открыть порт $port: ${e.message}"))
            return
        }
        responder = DiscoveryResponder({ "${NetConfig.HOST_REPLY}|$port|$safeName|$faction" }, discoveryPort)
            .also { it.start() }
        thread(isDaemon = true, name = "cardgame-host") { acceptLoop(faction) }
    }

    private fun acceptLoop(hostFaction: String) {
        val srv = server ?: return
        while (!closed) {
            val s = try {
                srv.accept()
            } catch (_: Exception) {
                return // закрыли (отмена)
            }
            val reader = try {
                s.soTimeout = 5000
                BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            } catch (_: Exception) {
                s.close(); continue
            }
            val hello = try {
                JSONObject(reader.readLine() ?: "")
            } catch (_: Exception) {
                s.close(); continue
            }
            if (hello.optString("type") != "HELLO") {
                s.close(); continue
            }
            val clientFaction = hello.optString("faction", "MONSTERS")

            // Соперник найден: больше никого не принимаем.
            try { srv.close() } catch (_: Exception) {}
            responder?.stop()

            val gameId = "lan" + Random.nextInt(1000, 9999)
            val seed = Random.nextLong(1000, 999_999)
            val liveReader = attach(s)
            writeLine(matchFound(gameId, "PLAYER_2", clientFaction, hostFaction, seed))
            listener(matchFound(gameId, "PLAYER_1", hostFaction, clientFaction, seed))
            readLoop(liveReader)
            return
        }
    }

    private fun matchFound(id: String, role: String, mine: String, enemy: String, seed: Long) =
        JSONObject().put("type", "MATCH_FOUND").put("game_id", id).put("role", role)
            .put("my_faction", mine).put("enemy_faction", enemy).put("seed", seed)

    override fun close() {
        super.close()
        responder?.stop()
        try { server?.close() } catch (_: Exception) {}
    }
}

/** Подключающийся игрок. Получает MATCH_FOUND от хозяина и играет как PLAYER_2. */
class LanClient(listener: (JSONObject) -> Unit) : SocketLink(listener) {

    fun join(ip: String, faction: String, port: Int = NetConfig.LAN_PORT) {
        thread(isDaemon = true, name = "cardgame-join") {
            val s = Socket()
            try {
                s.connect(InetSocketAddress(ip.trim(), port), 4000)
            } catch (e: Exception) {
                try { s.close() } catch (_: Exception) {}
                if (!closed) listener(errorMessage("Не удалось подключиться к $ip: ${e.message ?: "нет ответа"}"))
                return@thread
            }
            if (closed) {
                s.close(); return@thread
            }
            val reader = attach(s)
            writeLine(JSONObject().put("type", "HELLO").put("faction", faction))
            readLoop(reader) { obj ->
                if (obj.optString("type") == "MATCH_FOUND") listener(obj)
            }
        }
    }
}
