package io.ssttkkl.mahjongdetector

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.launch

@Composable
fun App() = MaterialTheme {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        backgroundColor = Color.White
    ) {
        var selectedImage by remember { mutableStateOf<ImageBitmap?>(null) }
        var result by remember { mutableStateOf("") }

        val coroutineScope = rememberCoroutineScope()

        val launcher = rememberFilePickerLauncher(
            type = FileKitType.Image
        ) { file ->
            println(file)
            coroutineScope.launch {
                try {
                    val image = file?.loadAsImage()
                    if (image != null) {
                        selectedImage = ImagePreprocessor.preprocessImage(image).first
                        result = MahjongDetector.predict(image).joinToString()
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .safeContentPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            selectedImage?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.weight(1f)
                )
                Text(result)
            }
            if (selectedImage == null)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("No image selected !", color = Color.Black)
                }

            Button(
                onClick = {
                    launcher.launch()
                },
            ) { Text("Choose Image") }
        }
    }
}