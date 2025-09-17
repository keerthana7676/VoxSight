package com.example.yololitertobjectdetection

object Constants {
    const val MODEL_PATH = "yolov10n_float16.tflite"
    val LABELS_PATH: String? = null
    const val MODEL_TYPE_YOLO = "yolo"

    const val CURRENCY_MODEL_PATH = "best_float32.tflite" // Add your currency model file
    const val CURRENCY_LABELS_PATH = "labels.txt" // Add your currency labels file
    const val MODEL_TYPE_CURRENCY = "currency"

    const val COMMAND_OBJECT_DETECTION = "object detection"
    const val COMMAND_CURRENCY_DETECTION = "detect currency"
}
