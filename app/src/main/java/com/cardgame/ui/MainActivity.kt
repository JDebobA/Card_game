package com.cardgame.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CardGameApp() }
    }
}

@Composable
fun CardGameApp() {
    val vm: GameViewModel = viewModel()
    Box(Modifier.fillMaxSize().background(Pal.bg0)) {
        when (vm.screen) {
            Screen.MENU -> MenuScreen(vm)
            Screen.BATTLE -> BattleScreen(vm)
        }
    }
}
