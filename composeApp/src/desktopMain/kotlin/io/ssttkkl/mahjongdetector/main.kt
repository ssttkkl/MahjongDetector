package io.ssttkkl.mahjongdetector

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.vinceglb.filekit.FileKit

fun main() = application {
    // Initialize FileKit
    FileKit.init(appId = "MyApplication")

    Window(
        onCloseRequest = ::exitApplication,
        title = "MahjongDetector",
    ) {
        App()
    }
}