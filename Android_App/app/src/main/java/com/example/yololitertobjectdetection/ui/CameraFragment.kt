package com.example.yololitertobjectdetection.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.yololitertobjectdetection.BoundingBox
import com.example.yololitertobjectdetection.Constants.CURRENCY_LABELS_PATH
import com.example.yololitertobjectdetection.Constants.CURRENCY_MODEL_PATH
import com.example.yololitertobjectdetection.Constants.LABELS_PATH
import com.example.yololitertobjectdetection.Constants.MODEL_PATH
import com.example.yololitertobjectdetection.Constants.COMMAND_OBJECT_DETECTION
import com.example.yololitertobjectdetection.Constants.COMMAND_CURRENCY_DETECTION
import com.example.yololitertobjectdetection.Detector
import com.example.yololitertobjectdetection.databinding.FragmentCameraBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment(), Detector.DetectorListener {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private val isFrontCamera = false
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var detector: Detector? = null
    private var currencyDetector: com.example.yololitertobjectdetection.CurrencyDetector? = null
    private lateinit var cameraExecutor: ExecutorService

    // Add TTS variables
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var lastSpokenClass: String? = null

    // Add mode management variables
    private enum class DetectionMode { OBJECT, CURRENCY, NONE }
    private var currentMode = DetectionMode.NONE
    private var isDetectionRunning = false // Track if detection is active

    // Add Speech Recognizer
    private lateinit var speechRecognizer: com.example.yololitertobjectdetection.SpeechRecognizerHelper

    // Permission launchers
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val microphoneGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false

        if (cameraGranted) {
            startCamera()
        }

        if (microphoneGranted && isTtsReady) {
            startSpeechRecognition()
            speakOut("Ready for commands. Say object detection or detect currency")
        }
    }

    private val requestMicrophonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSpeechRecognition()
            if (isTtsReady) {
                speakOut("Microphone permission granted. Say object detection or detect currency")
            }
        } else {
            toast("Microphone permission is required for voice commands")
            Log.e(TAG, "Microphone permission denied")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize TTS
        textToSpeech = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS Language not supported")
                } else {
                    isTtsReady = true
                    // Check and request microphone permission
                    checkMicrophonePermission()
                }
            } else {
                Log.e(TAG, "Failed to initialize TTS")
            }
        }

        // Start with object detection by default
        switchDetectionMode(DetectionMode.OBJECT)
        isDetectionRunning = true

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun checkMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            startSpeechRecognition()
            if (isTtsReady) {
                speakOut("Ready for commands. Say object detection or detect currency")
            }
        } else {
            requestMicrophonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startSpeechRecognition() {
        speechRecognizer = com.example.yololitertobjectdetection.SpeechRecognizerHelper(
            context = requireContext(),
            onCommandRecognized = { command ->
                processCameraVoiceCommand(command.lowercase(Locale.getDefault()))
            },
            onListeningStateChanged = { isListening ->
                // Optional: Update UI based on listening state
                Log.d(TAG, "Speech recognition listening state: $isListening")
            },
            onError = { errorMessage ->
                Log.e(TAG, "Speech recognition error: $errorMessage")
                toast("Speech recognition error: $errorMessage")
            }
        )
        speechRecognizer.startListening()
    }

    private fun processCameraVoiceCommand(command: String) {
        Log.d(TAG, "Processing camera voice command: $command")

        when {
            command.contains("stop detection") || command.contains("detection stop") -> {
                if (isDetectionRunning) {
                    stopDetection()
                    speakOut("Detection stopped")
                } else {
                    speakOut("Detection is already stopped")
                }
            }
            command.contains("start detection") || command.contains("detection start") -> {
                if (!isDetectionRunning) {
                    startDetection()
                    speakOut("Detection started")
                } else {
                    speakOut("Detection is already running")
                }
            }
            command.contains("object detection") -> {
                speakOut("Switching to object detection")
                switchDetectionMode(DetectionMode.OBJECT)
                isDetectionRunning = true
            }
            command.contains("currency detection") || command.contains("detect currency") -> {
                speakOut("Switching to currency detection")
                switchDetectionMode(DetectionMode.CURRENCY)
                isDetectionRunning = true
            }
            command.contains("go back") || command.contains("return home") -> {
                speakOut("Returning to home")
                requireActivity().finish()
            }
            command.contains("help") -> {
                speakOut("Available commands: stop detection, start detection, object detection, currency detection, go back")
            }
            else -> {
                Log.d(TAG, "Unrecognized command: $command")
            }
        }
    }

    private fun stopDetection() {
        isDetectionRunning = false
        // Clear the overlay and reset last spoken class
        requireActivity().runOnUiThread {
            binding.overlay.clear()
            lastSpokenClass = null
        }
        speakOut("Detection stopped")
        Log.d(TAG, "Detection stopped by voice command")
    }

    private fun startDetection() {
        isDetectionRunning = true
        speakOut("Detection started")
        Log.d(TAG, "Detection started by voice command")
    }

    private fun switchDetectionMode(mode: DetectionMode) {
        cameraExecutor.execute {
            // Close current detectors
            detector?.close()
            currencyDetector?.close()

            currentMode = mode

            when (mode) {
                DetectionMode.OBJECT -> {
                    detector = Detector(requireContext(), MODEL_PATH, LABELS_PATH, this@CameraFragment) { message ->
                        requireActivity().runOnUiThread {
                            toast(message)
                        }
                    }
                    currencyDetector = null
                    Log.d(TAG, "Switched to object detection mode")
                }
                DetectionMode.CURRENCY -> {
                    currencyDetector = com.example.yololitertobjectdetection.CurrencyDetector(
                        requireContext(),
                        CURRENCY_MODEL_PATH,
                        CURRENCY_LABELS_PATH,
                        object : com.example.yololitertobjectdetection.CurrencyDetector.DetectorListener {
                            override fun onEmptyDetect() {
                                requireActivity().runOnUiThread {
                                    binding.overlay.clear()
                                }
                            }
                            override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
                                requireActivity().runOnUiThread {
                                    if (isDetectionRunning) {
                                        binding.overlay.apply {
                                            setResults(boundingBoxes)
                                            invalidate()
                                        }
                                        // Speak detection results
                                        if (boundingBoxes.isNotEmpty() && isTtsReady) {
                                            val detectedClass = boundingBoxes[0].clsName
                                            if (detectedClass != lastSpokenClass) {
                                                speakOut(detectedClass)
                                                lastSpokenClass = detectedClass
                                            }
                                        } else {
                                            lastSpokenClass = null
                                        }
                                    }
                                }
                            }
                        },
                        { message ->
                            requireActivity().runOnUiThread {
                                toast(message)
                            }
                        }
                    )
                    detector = null
                    Log.d(TAG, "Switched to currency detection mode")
                }
                DetectionMode.NONE -> {
                    detector = null
                    currencyDetector = null
                    Log.d(TAG, "Detection turned off")
                }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProvider  = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val rotation = binding.viewFinder.display.rotation

        val cameraSelector = CameraSelector
            .Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview =  Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            if (isDetectionRunning) {
                val bitmapBuffer = Bitmap.createBitmap(
                    imageProxy.width,
                    imageProxy.height,
                    Bitmap.Config.ARGB_8888
                )
                imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
                imageProxy.close()

                val matrix = Matrix().apply {
                    postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                    if (isFrontCamera) {
                        postScale(-1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
                    }
                }

                val rotatedBitmap = Bitmap.createBitmap(
                    bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                    matrix, true
                )

                // Perform detection based on current mode
                when (currentMode) {
                    DetectionMode.OBJECT -> detector?.detect(rotatedBitmap)
                    DetectionMode.CURRENCY -> currencyDetector?.detect(rotatedBitmap)
                    DetectionMode.NONE -> { /* No detection */ }
                }
            } else {
                // Detection is stopped, just close the image proxy
                imageProxy.close()

                // Clear overlay if detection is stopped
                requireActivity().runOnUiThread {
                    binding.overlay.clear()
                    lastSpokenClass = null
                }
            }
        }

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            preview?.surfaceProvider = binding.viewFinder.surfaceProvider
        } catch(exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        detector?.close()
        currencyDetector?.close()
        cameraExecutor.shutdown()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        speechRecognizer.stopListening()
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()){
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    override fun onEmptyDetect() {
        if (isDetectionRunning) {
            requireActivity().runOnUiThread {
                binding.overlay.clear()
                lastSpokenClass = null
            }
        }
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        if (isDetectionRunning) {
            requireActivity().runOnUiThread {
                binding.overlay.apply {
                    setResults(boundingBoxes)
                    invalidate()
                }

                // Speak detection results
                if (boundingBoxes.isNotEmpty() && isTtsReady) {
                    val detectedClass = boundingBoxes[0].clsName
                    if (detectedClass != lastSpokenClass) {
                        speakOut(detectedClass)
                        lastSpokenClass = detectedClass
                    }
                } else {
                    lastSpokenClass = null
                }
            }
        }
    }

    private fun toast(message: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    private fun speakOut(text: String) {
        if (isTtsReady) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    companion object {
        private const val TAG = "Camera"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
}