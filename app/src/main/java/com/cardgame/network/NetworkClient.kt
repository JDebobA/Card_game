package com.cardgame.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class NetworkClient(private val listener: (JSONObject) -> Unit) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    fun connect(ip: String, port: Int = 8765) {
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
                val err = JSONObject().apply {
                    put("type", "CONNECTION_ERROR")
                    put("message", t.message ?: "Ошибка подключения к серверу")
                }
                listener(err)
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

    fun close() {
        webSocket?.close(1000, "Left game")
    }
}