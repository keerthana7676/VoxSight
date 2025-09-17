package com.example.yololitertobjectdetection

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.util.*

class CurrencyDetector(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String,
    private val detectorListener: DetectorListener,
    private val message: (String) -> Unit
) {
    private var interpreter: Interpreter
    private var labels = mutableListOf<String>()
    private var isDetectionEnabled = false // Control detection with this flag

    private var inputWidth = 640
    private var inputHeight = 640
    private val numChannels = 11 // Based on your output shape (1, 11, 8400)
    private val numPredictions = 8400 // Based on your output shape (1, 11, 8400)

    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(0f, 255f)) // Normalize from 0-255 to 0-1
        .add(CastOp(DataType.FLOAT32))
        .build()

    init {
        // Load model
        val model = FileUtil.loadMappedFile(context, modelPath)
        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }
        interpreter = Interpreter(model, options)

        // Load labels
        val extractedLabels = MetaData.extractNamesFromLabelFile(context, labelPath)
        labels.addAll(extractedLabels)
        if (labels.isEmpty()) {
            message("Failed to load currency labels")
            // Add some default Indian currency labels
            labels.addAll(listOf("10_rupee", "20_rupee", "50_rupee", "100_rupee", "200_rupee", "500_rupee", "2000_rupee"))
        }

        message("Currency detector initialized. Say 'detect currency' to start.")
        message("Labels loaded: ${labels.size} classes")
    }

    // Control detection state
    fun enableDetection() {
        isDetectionEnabled = true
        message("Currency detection enabled")
    }

    fun disableDetection() {
        isDetectionEnabled = false
        message("Currency detection disabled")
    }

    fun isDetectionEnabled(): Boolean {
        return isDetectionEnabled
    }

    fun detect(frame: Bitmap) {
        if (!isDetectionEnabled) {
            return // Skip detection if not enabled
        }

        val startTime = SystemClock.uptimeMillis()

        try {
            // Preprocess image - resize to 640x640 as required by the model
            val resizedBitmap = Bitmap.createScaledBitmap(frame, inputWidth, inputHeight, false)
            val tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(resizedBitmap)
            val processedImage = imageProcessor.process(tensorImage)
            val inputBuffer = processedImage.buffer

            // Prepare output buffer - shape should be (1, 11, 8400)
            val outputShape = intArrayOf(1, numChannels, numPredictions)
            val outputBuffer = TensorBuffer.createFixedSize(outputShape, DataType.FLOAT32)

            // Run inference
            interpreter.run(inputBuffer, outputBuffer.buffer)

            val inferenceTime = SystemClock.uptimeMillis() - startTime
            processOutput(outputBuffer.floatArray, inferenceTime)

        } catch (e: Exception) {
            message("Detection error: ${e.message}")
            detectorListener.onEmptyDetect()
        }
    }

    private fun processOutput(outputArray: FloatArray, inferenceTime: Long) {
        val boundingBoxes = mutableListOf<BoundingBox>()

        // Process each of the 8400 predictions
        for (i in 0 until numPredictions) {
            val objectnessScore = outputArray[i * numChannels + 4] // 5th element is objectness score

            // Use a higher threshold for objectness to reduce false positives
            if (objectnessScore > OBJECTNESS_THRESHOLD) {
                // Extract bounding box coordinates (normalized)
                val xCenter = outputArray[i * numChannels]
                val yCenter = outputArray[i * numChannels + 1]
                val width = outputArray[i * numChannels + 2]
                val height = outputArray[i * numChannels + 3]

                // Convert center coordinates to corner coordinates
                val x1 = (xCenter - width / 2).coerceIn(0f, 1f)
                val y1 = (yCenter - height / 2).coerceIn(0f, 1f)
                val x2 = (xCenter + width / 2).coerceIn(0f, 1f)
                val y2 = (yCenter + height / 2).coerceIn(0f, 1f)

                // Calculate box area to filter out very small boxes (noise)
                val boxArea = (x2 - x1) * (y2 - y1)
                if (boxArea < MIN_BOX_AREA) {
                    continue // Skip very small boxes
                }

                // Find the class with highest probability
                var maxClassScore = 0f
                var classId = -1

                for (c in 0 until labels.size) {
                    val classScore = outputArray[i * numChannels + 5 + c] // Classes start at index 5
                    if (classScore > maxClassScore) {
                        maxClassScore = classScore
                        classId = c
                    }
                }

                // Calculate final confidence (objectness * class probability)
                val confidence = objectnessScore * maxClassScore

                if (confidence > CONFIDENCE_THRESHOLD && classId != -1 && classId < labels.size) {
                    boundingBoxes.add(
                        BoundingBox(
                            x1 = x1,
                            y1 = y1,
                            x2 = x2,
                            y2 = y2,
                            cnf = confidence,
                            cls = classId,
                            clsName = labels[classId]
                        )
                    )
                }
            }
        }

        // Apply Non-Maximum Suppression to remove duplicate detections
        val filteredBoxes = applyNMS(boundingBoxes)

        // Additional filtering: only keep boxes with reasonable aspect ratios
        val finalBoxes = filteredBoxes.filter { box ->
            val aspectRatio = (box.x2 - box.x1) / (box.y2 - box.y1)
            aspectRatio in 0.3f..3.0f // Currency notes typically have aspect ratio around 2:1
        }

        if (finalBoxes.isEmpty()) {
            detectorListener.onEmptyDetect()
        } else {
            detectorListener.onDetect(finalBoxes, inferenceTime)
        }
    }

    private fun applyNMS(boxes: List<BoundingBox>, iouThreshold: Float = 0.4f): List<BoundingBox> {
        if (boxes.isEmpty()) return emptyList()

        val sortedBoxes = boxes.sortedByDescending { it.cnf }.toMutableList()
        val selectedBoxes = mutableListOf<BoundingBox>()

        while (sortedBoxes.isNotEmpty()) {
            val currentBox = sortedBoxes.first()
            selectedBoxes.add(currentBox)
            sortedBoxes.removeAt(0)

            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                val box = iterator.next()
                val iou = calculateIOU(currentBox, box)
                if (iou >= iouThreshold) {
                    iterator.remove()
                }
            }
        }

        return selectedBoxes
    }

    private fun calculateIOU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)

        val intersectionArea = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val area1 = (box1.x2 - box1.x1) * (box1.y2 - box1.y1)
        val area2 = (box2.x2 - box2.x1) * (box2.y2 - box2.y1)
        val unionArea = area1 + area2 - intersectionArea

        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }

    fun close() {
        interpreter.close()
    }

    interface DetectorListener {
        fun onEmptyDetect()
        fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long)
    }

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.6f // Increased from 0.5 to reduce false positives
        private const val OBJECTNESS_THRESHOLD = 0.7f // Higher threshold for objectness
        private const val MIN_BOX_AREA = 0.01f // Minimum area to consider (1% of image)
    }
}