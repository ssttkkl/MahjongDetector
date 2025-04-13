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
import network.chaintech.cmpimagepickncrop.imagecropper.ImageCropResult
import network.chaintech.cmpimagepickncrop.imagecropper.ImageCropper
import network.chaintech.cmpimagepickncrop.imagecropper.cropImage
import network.chaintech.cmpimagepickncrop.imagecropper.rememberImageCropper
import network.chaintech.cmpimagepickncrop.ui.ImageCropperDialogContainer

@Composable
fun PickImageButton(
    cropper: ImageCropper = rememberImageCropper(),
    onPick: (ImageBitmap) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    val picker = rememberFilePickerLauncher(
        type = FileKitType.Image
    ) { file ->
        coroutineScope.launch {
            runCatching { checkNotNull(file?.loadAsImage()) }
                .onFailure { e -> e.printStackTrace() }
                .map { originImg ->
                    val cropResult = cropper.cropImage(bmp = originImg)
                    if (cropResult is ImageCropResult.Success) {
                        onPick(cropResult.bitmap)
                    }
                }
        }
    }

    Button(
        onClick = {
            picker.launch()
        },
    ) { Text("Choose Image") }
}

@Composable
fun App() = MaterialTheme {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        backgroundColor = Color.White
    ) {
        val coroutineScope = rememberCoroutineScope()
        val cropper = rememberImageCropper()

        var selectedImage by remember { mutableStateOf<ImageBitmap?>(null) }
        var result by remember { mutableStateOf("") }

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

            PickImageButton(cropper, onPick = {
                selectedImage = it
                coroutineScope.launch {
                    runCatching {
                        result = MahjongDetector.predict(it).joinToString()
                    }
                }
            })
        }

        cropper.imageCropState?.let { cropState ->
            ImageCropperDialogContainer(cropState)
        }
    }
}