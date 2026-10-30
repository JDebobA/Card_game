package com.cardgame.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Клиент сервера подбора игроков (server.py). */
class NetworkClient(private val listener: (JSONObject) -> Unit) : GameLink {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS) // быстро заметить, что сервер или сеть пропали
        .build()
    private var webSocket: WebSocket? = null

    @Volatile
    private var closedByUs = false

    /** Номер партии, который выдал сервер (подставляется в игровые события). */
    @Volatile
    var gameId: String? = null

    fun connect(ip: String, port: Int = NetConfig.SERVER_PORT) {
        val request = Request.Builder().url("ws://$ip:$port").build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    listener(JSONObject(text))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (closedByUs) return
                listener(errorMessage(t.message ?: "Ошибка подключения к серверу"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closedByUs) listener(errorMessage("Соединение с сервером закрыто"))
            }
        })
    }

    fun findMatch(factionName: String) {
        val json = JSONObject().apply {
            put("action", "find_match")
            put("faction", factionName)
        }
        webSocket?.send(json.toString())
    }

    fun cancelSearch() {
        val json = JSONObject().apply {
            put("action", "cancel_search")
        }
        webSocket?.send(json.toString())
    }

    fun sendAction(gameId: String, actionType: String, data: JSONObject = JSONObject()) {
        val payload = JSONObject().apply {
            put("type", actionType)
            put("data", data)
        }
        val msg = JSONObject().apply {
            put("action", "game_event")
            put("game_id", gameId)
            put("payload", payload)
        }
        webSocket?.send(msg.toString())
    }

    override fun send(type: String, data: JSONObject) {
        val id = gameId ?: return
        sendAction(id, type, data)
    }

    override fun close() {
        closedByUs = true
        webSocket?.close(1000, "Left game")
    }
}
