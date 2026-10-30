package com.cardgame.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardgame.network.AppEnv

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppEnv.context = applicationContext // до создания ViewModel: оттуда читаются настройки
        // Во время сетевой партии экран не должен гаснуть, иначе связь с соперником оборвётся.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent { CardGameApp() }
    }
}

@Composable
fun CardGameApp() {
    val vm: GameViewModel = viewModel()
    MaterialTheme(colorScheme = darkColorScheme(primary = Pal.gold, background = Pal.bg0, surface = Pal.panel)) {
        Surface(Modifier.fillMaxSize(), color = Pal.bg0) {
            Crossfade(targetState = vm.screen, animationSpec = tween(350), label = "screen") { screen ->
                when (screen) {
                    Screen.MENU -> MenuScreen(vm)
                    Screen.BATTLE -> BattleScreen(vm)
                }
            }
        }
    }
}
