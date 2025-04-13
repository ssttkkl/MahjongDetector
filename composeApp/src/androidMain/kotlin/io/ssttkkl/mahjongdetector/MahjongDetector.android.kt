package io.ssttkkl.mahjongdetector

import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import java.io.File
import java.nio.ByteBuffer


actual object MahjongDetector {
    private lateinit var interpreter: InterpreterApi
    private var modelLoaded: Boolean = false
    private val modelLoadMutex = Mutex()

    private suspend fun prepareModel() {
        if (!modelLoaded) {
            modelLoadMutex.withLock {
                if (!modelLoaded) {
                    val interpreterOption = InterpreterApi.Options()
                        .setRuntime(
                            InterpreterApi.Options.TfLiteRuntime.PREFER_SYSTEM_OVER_APPLICATION
                        )

                    val bytes = MyApp.current.assets.open("best_float16.tflite")
                        .use { stream ->
                            stream.readBytes()
                        }

                    interpreter = InterpreterApi.create(
                        ByteBuffer.wrap(bytes),
                        interpreterOption
                    )
                    Log.i("MahjongDetector", "interpreter create")
                }

                modelLoaded = true
            }
        }
    }

    actual suspend fun predict(image: ImageBitmap): List<String> =
        withContext(Dispatchers.Default) {
            prepareModel()

            val (preprocessed, paddingInfo) = ImagePreprocessor.preprocessImage(image)
            val inputTensor = createInputTensor(preprocessed)

            // 输出格式：YOLOv8 的输出形状为 [1, numClasses+4, 8400]（分类+框坐标）
            val output = Array(1) { Array(CLASS_NAME.size + 4) { FloatArray(8400) } }  // 根据实际模型调整
            interpreter.run(inputTensor.buffer, output)

            val detections =
                YoloV8PostProcessor.postprocess(output[0], paddingInfo, CLASS_NAME.size)

            detections.sortedBy { it.x1 }.map { CLASS_NAME[it.classId] }
        }

    private fun createInputTensor(image: ImageBitmap): TensorImage {
        // 图像预处理
        val processor: ImageProcessor = ImageProcessor.Builder()
            .add(NormalizeOp(0f, 255f)) // 归一化到 [0, 1]
            .build()

        val inputImage = TensorImage(DataType.FLOAT32)
        inputImage.load(image.asAndroidBitmap()) // 加载 Bitmap
        return processor.process(inputImage)
    }
}