package io.ssttkkl.mahjongdetector

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import io.ssttkkl.mahjongdetector.ImagePreprocessor.preprocessImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.Image
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.io.path.outputStream
import kotlin.math.max
import kotlin.math.min


actual object MahjongDetector {
    private const val MODEL_FILENAME = "best.onnx"
    private val CLASS_NAME = listOf(
        "1m", "1p", "1s",
        "2m", "2p", "2s",
        "3m", "3p", "3s",
        "4m", "4p", "4s",
        "5m", "5p", "5s",
        "6m", "6p", "6s",
        "7m", "7p", "7s",
        "8m", "8p", "8s",
        "9m", "9p", "9s",
        "chun", "haku", "hatsu", "nan", "pe", "sha", "tou"
    )

    private val modelFile = Files.createTempFile("model", ".onnx")

    init {
        modelFile.outputStream().use { output ->
            checkNotNull(this::class.java.classLoader?.getResourceAsStream(MODEL_FILENAME))
                .copyTo(output)
        }
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession =
        env.createSession(modelFile.absolutePathString(), OrtSession.SessionOptions())

    private val inputType: OnnxJavaType
        get() {
            val inputMetaMap = session.inputInfo
            val inputName = session.inputNames.iterator().next()
            val inputMeta = checkNotNull(inputMetaMap[inputName])
            return (inputMeta.info as TensorInfo).type
        }

    fun close() {
        session.close()
        env.close()
    }

    actual suspend fun predict(image: ImageBitmap): List<String> =
        withContext(Dispatchers.Default) {
            // 预处理图像
            val (preprocessedImage, paddingInfo) = preprocessImage(image)

            // 将预处理后的图像数据转换为 ONNX 张量
            val tensor = createTensor(preprocessedImage.toAwtImage())

            // 执行推理
            val results = session.run(mapOf(session.inputNames.first() to tensor))

            // 获取输出张量 (你需要根据你的模型输出名称调整)
            val output = results.get(0).value as Array<Array<FloatArray>>
            val detections =
                YoloV8PostProcessor.postprocess(output[0], paddingInfo, CLASS_NAME.size)

            // 释放资源
            tensor.close()
            results.close()

            detections.sortedBy { it.x1 }.map { CLASS_NAME[it.classId] }
        }

    // 创建ONNX输入Tensor
    private fun createTensor(image: BufferedImage): OnnxTensor {
        val width = image.width
        val height = image.height

        // 1. 获取ARGB像素数据
        val pixels = IntArray(width * height)
        image.getRGB(0, 0, width, height, pixels, 0, width)

        // if model is quantized
        if (inputType == OnnxJavaType.UINT8) {
            // 2. 转换为BGR float数组（OpenCV兼容格式）
            val bgrData = ByteArray(3 * width * height)
            for (i in pixels.indices) {
                val pixel = pixels[i]

                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                bgrData[i] = r.toByte()
                bgrData[pixels.size + i] = g.toByte()
                bgrData[pixels.size * 2 + i] = b.toByte()
            }

            return OnnxTensor.createTensor(
                env,
                ByteBuffer.wrap(bgrData),
                longArrayOf(1, 3, height.toLong(), width.toLong()) // NCHW格式
            )
        } else {
            // 2. 转换为BGR float数组（OpenCV兼容格式）
            val bgrData = FloatArray(3 * width * height)
            for (i in pixels.indices) {
                val pixel = pixels[i]

                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                bgrData[i] = r / 255.0f
                bgrData[pixels.size + i] = g / 255.0f
                bgrData[pixels.size * 2 + i] = b / 255.0f
            }

            return OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(bgrData),
                longArrayOf(1, 3, height.toLong(), width.toLong()) // NCHW格式
            )
        }

    }
}