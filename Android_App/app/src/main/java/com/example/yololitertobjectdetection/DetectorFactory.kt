package com.example.yololitertobjectdetection

import android.content.Context

object DetectorFactory {

    fun createDetector(
        context: Context,
        modelType: String,
        detectorListener: Detector.DetectorListener,
        message: (String) -> Unit
    ): Any { // Return Any since we have different detector types
        return when (modelType) {
            Constants.MODEL_TYPE_YOLO -> {
                Detector(
                    context,
                    Constants.MODEL_PATH,
                    Constants.LABELS_PATH,
                    detectorListener,
                    message
                )
            }
            Constants.MODEL_TYPE_CURRENCY -> {
                CurrencyDetector(
                    context,
                    Constants.CURRENCY_MODEL_PATH,
                    Constants.CURRENCY_LABELS_PATH,
                    object : CurrencyDetector.DetectorListener {
                        override fun onEmptyDetect() {
                            detectorListener.onEmptyDetect()
                        }
                        override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
                            detectorListener.onDetect(boundingBoxes, inferenceTime)
                        }
                    },
                    message
                )
            }
            else -> throw IllegalArgumentException("Unknown model type: $modelType")
        }
    }
}