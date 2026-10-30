package com.cardgame.network

import org.json.JSONObject

/** Порты и строки протокола. Те же значения использует server.py. */
object NetConfig {
    /** WebSocket-сервер подбора игроков на ПК (server.py). */
    const val SERVER_PORT = 8765

    /** TCP: одно устройство создаёт игру по сети, второе подключается. */
    const val LAN_PORT = 8766

    /** UDP: автопоиск сервера и созданных игр в локальной сети. */
    const val DISCOVERY_PORT = 8767

    const val DISCOVER_REQUEST = "CARDGAME_DISCOVER?"

    /** Ответ сервера: CARDGAME_SERVER|порт|название */
    const val SERVER_REPLY = "CARDGAME_SERVER"

    /** Ответ телефона-хозяина: CARDGAME_HOST|порт|название|ФРАКЦИЯ */
    const val HOST_REPLY = "CARDGAME_HOST"
}

/**
 * Канал связи с соперником (через сервер или напрямую по сети).
 * Входящие сообщения приходят в listener одного формата:
 *  MATCH_FOUND, OPPONENT_EVENT, OPPONENT_DISCONNECTED, CONNECTION_ERROR, QUEUE_STATUS.
 */
interface GameLink {
    /** Отправить ход соперника. data — параметры события. */
    fun send(type: String, data: JSONObject)

    fun close()
}

enum class HostKind { SERVER, LAN_HOST }

data class DiscoveredHost(
    val ip: String,
    val port: Int,
    val name: String,
    val kind: HostKind,
    val faction: String? = null
)

internal fun errorMessage(text: String): JSONObject =
    JSONObject().put("type", "CONNECTION_ERROR").put("message", text)
