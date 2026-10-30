package com.cardgame.network

import android.content.Context
import android.net.wifi.WifiManager

/** Контекст приложения для мелких настроек (задаётся в MainActivity). */
object AppEnv {
    @Volatile
    var context: Context? = null
}

object Prefs {
    private const val FILE = "cardgame"
    private const val KEY_SERVER_IP = "server_ip"

    fun serverIp(): String? = try {
        AppEnv.context?.getSharedPreferences(FILE, Context.MODE_PRIVATE)?.getString(KEY_SERVER_IP, null)
    } catch (_: Exception) {
        null
    }

    fun saveServerIp(value: String) {
        try {
            AppEnv.context?.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                ?.edit()?.putString(KEY_SERVER_IP, value)?.apply()
        } catch (_: Exception) {
        }
    }
}

object AndroidNet {
    /**
     * Адрес шлюза текущей Wi-Fi сети. Если соперник раздаёт точку доступа, шлюз — это и есть
     * его телефон; также помогает, когда роутер не пропускает широковещательные запросы.
     */
    @Suppress("DEPRECATION")
    fun gatewayCandidates(): List<String> {
        val ctx = AppEnv.context ?: return emptyList()
        return try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val gw = wm?.dhcpInfo?.gateway ?: 0
            if (gw == 0) emptyList()
            else listOf("${gw and 0xff}.${(gw shr 8) and 0xff}.${(gw shr 16) and 0xff}.${(gw shr 24) and 0xff}")
        } catch (_: Exception) {
            emptyList()
        }
    }
}
