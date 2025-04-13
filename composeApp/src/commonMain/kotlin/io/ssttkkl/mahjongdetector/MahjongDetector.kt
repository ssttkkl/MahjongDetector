package io.ssttkkl.mahjongdetector

import androidx.compose.ui.graphics.ImageBitmap

expect object MahjongDetector {
    suspend fun predict(image: ImageBitmap): List<String>
}