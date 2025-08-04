package com.ammakkutti.sa

import ConvertViewModel
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.ammakkutti.sa.ui.screens.ConvertScreen

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Media Converter",
        state = WindowState(width = 800.dp, height = 600.dp)
    ) {
        val viewModel = remember { ConvertViewModel() }
        ConvertScreen(viewModel)
    }
}