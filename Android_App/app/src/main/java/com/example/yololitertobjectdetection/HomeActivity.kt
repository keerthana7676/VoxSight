package com.example.yololitertobjectdetection

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.text.format.DateFormat
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.Date
import android.database.Cursor
import android.provider.Telephony
import android.content.ContentValues

class HomeActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var micIV: ImageView
    private lateinit var outputTV: TextView
    private lateinit var tts: TextToSpeech
    private var isTtsInitialized = false
    private lateinit var speechRecognizerHelper: SpeechRecognizerHelper

    // Permission request codes
    private val RECORD_AUDIO_PERMISSION_CODE = 1
    private val CALL_PHONE_PERMISSION_CODE = 2
    private val SMS_PERMISSION_CODE = 3
    private val READ_SMS_PERMISSION_CODE = 4
    private val CAMERA_PERMISSION_CODE = 5

    // Flags for interactive SMS sending
    private var isWaitingForRecipient = false
    private var isWaitingForMessage = false
    private var pendingRecipient: String? = null

    // Flag to track if we should resume listening
    private var shouldResumeListening = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        micIV = findViewById(R.id.mic_speak_iv)
        outputTV = findViewById(R.id.speak_output_tv)

        // Initialize TextToSpeech
        tts = TextToSpeech(this, this)

        // Initialize SpeechRecognizerHelper
        speechRecognizerHelper = SpeechRecognizerHelper(
            context = this,
            onCommandRecognized = { command ->
                runOnUiThread {
                    outputTV.text = command
                    Log.d("SpeechRecognizer", "Recognized: $command")
                    processVoiceCommand(command.lowercase(Locale.getDefault()))
                }
            },
            onListeningStateChanged = { isListening ->
                runOnUiThread {
                    val color = if (isListening) {
                        R.color.mic_enabled_color
                    } else {
                        R.color.mic_disabled_color
                    }
                    micIV.setColorFilter(ContextCompat.getColor(applicationContext, color))

                    if (isListening) {
                        outputTV.text = "Listening..."
                    }
                }
            },
            onError = { error ->
                runOnUiThread {
                    Log.e("SpeechRecognizer", "Error: $error")
                    Toast.makeText(applicationContext, error, Toast.LENGTH_SHORT).show()

                    if (error != "Insufficient permissions" && error != "Recognizer busy") {
                        startListeningAfterDelay()
                    }
                }
            }
        )
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.getDefault())
            isTtsInitialized = if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Language not supported")
                false
            } else {
                true
            }
            if (isTtsInitialized) {
                speakOut("VoxSight Started") {
                    checkAudioPermission()
                }
            } else {
                Toast.makeText(this, "VoxSight Started (TTS not available)", Toast.LENGTH_LONG).show()
                checkAudioPermission()
            }
        } else {
            Log.e("TTS", "Initialization failed")
            isTtsInitialized = false
            Toast.makeText(this, "VoxSight Started (TTS initialization failed)", Toast.LENGTH_LONG).show()
            checkAudioPermission()
        }
    }

    private fun startSpeechToText() {
        if (shouldResumeListening) {
            speechRecognizerHelper.startListening()
        }
    }

    private fun stopListening() {
        speechRecognizerHelper.stopListening()
    }

    private fun processVoiceCommand(command: String) {
        Log.d("VoiceCommand", "Processing command: $command")

        when {
            command.contains("battery") && (command.contains("percentage") || command.contains("level")) -> {
                val batteryLevel = getBatteryPercentage()
                speakOut("Battery level is $batteryLevel percent") {
                    startListeningAfterDelay()
                }
            }
            command.contains("time") -> {
                val time = DateFormat.getTimeFormat(this).format(Date())
                speakOut("Current time is $time") {
                    startListeningAfterDelay()
                }
            }
            command.contains("date") -> {
                val date = DateFormat.getDateFormat(this).format(Date())
                speakOut("Today's date is $date") {
                    startListeningAfterDelay()
                }
            }
            command.contains("read messages") || command.contains("read message") || command.contains("read my messages") -> {
                Log.d("VoiceCommand", "Read messages command detected")
                checkReadSmsPermission()
            }
            command.contains("send message") || command.contains("text") -> {
                startInteractiveSmsSending()
            }
            isWaitingForRecipient -> {
                processRecipientInput(command)
            }
            isWaitingForMessage -> {
                processMessageInput(command)
            }
            command.contains("check messages") || command.contains("new messages") -> {
                checkForNewMessages()
            }
            command.contains("call") -> {
                val number = extractPhoneNumber(command)
                if (number != null) {
                    makePhoneCall(number)
                } else {
                    speakOut("I couldn't find a phone number in your request") {
                        startListeningAfterDelay()
                    }
                }
            }
            command.contains("help") || command.contains("list commands") -> {
                displayHelpCommands()
            }
            command.contains("open camera") || command.contains("camera on") ||
                    command.contains("start camera") || command.contains("enable camera") -> {
                speakOut("Opening camera.") {
                    checkCameraPermissionAndStartCamera()
                }
            }
            command.contains("stop") || command.contains("stop listening") ||
                    command.contains("exit") || command.contains("exit app") -> {
                stopVoxSight()
            }
            else -> {
                speakOut("I didn't understand that command. Say 'help' for available commands.") {
                    startListeningAfterDelay()
                }
            }
        }
    }

    private fun checkCameraPermissionAndStartCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
            speakOut("Camera permission is needed to open the camera.") {
                // Listening will resume after permission handling
            }
        } else {
            startCameraActivity()
        }
    }

    private fun startCameraActivity() {
        try {
            // Stop listening before opening camera
            stopListening()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("Camera", "Failed to start camera activity", e)
            speakOut("Failed to open camera. Please try again.") {
                startListeningAfterDelay()
            }
        }
    }

    private fun startInteractiveSmsSending() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.SEND_SMS),
                SMS_PERMISSION_CODE
            )
            speakOut("Please grant SMS send permission first.") {
                startListeningAfterDelay()
            }
            return
        }
        speakOut("Please say the recipient's phone number.")
        isWaitingForRecipient = true
        isWaitingForMessage = false
        pendingRecipient = null
        startSpeechToText()
    }

    private fun processRecipientInput(input: String) {
        val number = extractPhoneNumber(input)
        if (number != null) {
            pendingRecipient = number
            speakOut("You said $number. Now please say your message.")
            isWaitingForRecipient = false
            isWaitingForMessage = true
            startSpeechToText()
        } else {
            speakOut("I didn't get a valid phone number. Please try again.") {
                startSpeechToText()
            }
        }
    }

    private fun processMessageInput(input: String) {
        pendingRecipient?.let { recipient ->
            sendSms(recipient, input)
            isWaitingForMessage = false
        } ?: run {
            speakOut("Error: No recipient found. Please start over.") {
                startListeningAfterDelay()
            }
            isWaitingForMessage = false
        }
    }

    private fun checkReadSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            Log.d("Permissions", "Requesting READ_SMS permission")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_SMS),
                READ_SMS_PERMISSION_CODE
            )
            speakOut("Please grant SMS read permission first.") {
                startListeningAfterDelay()
            }
        } else {
            Log.d("Permissions", "READ_SMS permission already granted")
            readSmsMessages()
        }
    }

    private fun readSmsMessages() {
        Log.d("SMS", "Attempting to read SMS messages")

        try {
            val uri = Telephony.Sms.Inbox.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )
            val selection = "${Telephony.Sms.READ} = 0"
            val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT 1"
            val cursor = contentResolver.query(
                uri,
                projection,
                selection,
                null,
                sortOrder
            )
            cursor?.use {
                if (it.count == 0) {
                    Log.d("SMS", "No unread messages found")
                    speakOut("You have no new messages.") {
                        startListeningAfterDelay()
                    }
                    return
                }
                if (it.moveToFirst()) {
                    val address = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                    val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY))
                    val messageId = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms._ID))
                    val formattedMessage = "From ${formatPhoneNumber(address)}, Message is '$body'"
                    Log.d("SMS", "Latest unread message: $formattedMessage")
                    speakOut(formattedMessage) {
                        markMessageAsRead(messageId)
                        startListeningAfterDelay()
                    }
                }
            } ?: run {
                Log.e("SMS", "Cursor is null, could not query messages.")
                speakOut("Could not access your messages.") {
                    startListeningAfterDelay()
                }
            }
        } catch (e: SecurityException) {
            Log.e("SMS", "Security Exception: Permission denied to read messages", e)
            speakOut("Permission denied to read messages.") {
                startListeningAfterDelay()
            }
        } catch (e: Exception) {
            Log.e("SMS", "Error reading messages", e)
            speakOut("Error reading messages.") {
                startListeningAfterDelay()
            }
        }
    }

    private fun formatPhoneNumber(number: String): String {
        return if (number.matches(Regex("^\\d{10}$"))) {
            "${number.substring(0, 3)} ${number.substring(3, 6)} ${number.substring(6)}"
        } else {
            number
        }
    }

    private fun markMessageAsRead(messageId: Long) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
            }
            contentResolver.update(
                Uri.withAppendedPath(Telephony.Sms.Inbox.CONTENT_URI, messageId.toString()),
                values,
                null,
                null
            )
            Log.d("SMS", "Marked message $messageId as read.")
        } catch (e: Exception) {
            Log.e("SMS", "Error marking message $messageId as read", e)
        }
    }

    private fun sendSms(number: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(number, null, message, null, null)
            speakOut("Message sent successfully to $number.") {
                pendingRecipient = null
                startListeningAfterDelay()
            }
        } catch (e: Exception) {
            speakOut("Failed to send message. ${e.localizedMessage}") {
                pendingRecipient = null
                startListeningAfterDelay()
            }
            Log.e("SMS", "Failed to send SMS to $number: ${e.localizedMessage}", e)
        }
    }

    private fun extractPhoneNumber(command: String): String? {
        val digitsOnly = command.replace(Regex("[^0-9+]"), "")
        return if (digitsOnly.matches(Regex("^\\+?[0-9]{7,15}$"))) {
            digitsOnly
        } else {
            null
        }
    }

    private fun checkForNewMessages() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            checkReadSmsPermission()
            return
        }

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.READ} = ?",
                arrayOf("0"),
                null
            )
            val unreadCount = cursor?.count ?: 0
            if (unreadCount > 0) {
                speakOut("You have $unreadCount new messages. Say 'read messages' to hear the latest unread message.") {
                    startListeningAfterDelay()
                }
            } else {
                speakOut("You have no new messages.") {
                    startListeningAfterDelay()
                }
            }
        } catch (e: SecurityException) {
            Log.e("SMS", "Security Exception checking messages: ${e.localizedMessage}")
            speakOut("Permission denied to check messages.") {
                startListeningAfterDelay()
            }
        } catch (e: Exception) {
            Log.e("SMS", "Error checking for new messages", e)
            speakOut("Error checking for new messages.") {
                startListeningAfterDelay()
            }
        } finally {
            cursor?.close()
        }
    }

    private fun getBatteryPercentage(): Int {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            registerReceiver(null, ifilter)
        }
        return batteryStatus?.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            (level * 100 / scale.toFloat()).toInt()
        } ?: -1
    }

    private fun makePhoneCall(number: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            // Stop listening before making call
            stopListening()
            val callIntent = Intent(Intent.ACTION_CALL)
            callIntent.data = Uri.parse("tel:$number")
            startActivity(callIntent)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), CALL_PHONE_PERMISSION_CODE)
            speakOut("Please grant phone call permission first.") {
                startListeningAfterDelay()
            }
        }
    }

    private fun speakOut(text: String, onDone: (() -> Unit)? = null) {
        if (isTtsInitialized) {
            val utteranceId = this.hashCode().toString() + System.currentTimeMillis()
            tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("TTS", "Speaking started for utteranceId: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d("TTS", "Speaking done for utteranceId: $utteranceId")
                    runOnUiThread {
                        onDone?.invoke()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        Log.e("TTS", "Error speaking out text: $text for utteranceId: $utteranceId")
                        Toast.makeText(this@HomeActivity, "Error speaking: $text", Toast.LENGTH_LONG).show()
                        onDone?.invoke()
                    }
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    Log.d("TTS", "Speaking stopped for utteranceId: $utteranceId, interrupted: $interrupted")
                    if (interrupted) {
                        runOnUiThread {
                            onDone?.invoke()
                        }
                    }
                }

                override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {}
            })
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            Toast.makeText(this, text, Toast.LENGTH_LONG).show()
            onDone?.invoke()
        }
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
        } else {
            startSpeechToText()
        }
    }

    private fun startListeningAfterDelay() {
        android.os.Handler(mainLooper).postDelayed({
            checkAudioPermission()
        }, 1000)
    }

    private fun displayHelpCommands() {
        val helpText = """
            Here are the commands you can use:
            Ask for battery level: say "battery percentage" or "battery level".
            Ask for current time: say "time".
            Ask for current date: say "date".
            Read your messages: say "read messages" or "read my messages".
            Send a new message: say "send message" or "text".
            Check for new messages: say "check messages" or "new messages".
            Make a phone call: say "call" followed by the phone number.
            Open camera: say "open camera" or "camera on".
            To stop VoxSight: say "stop" or "stop listening" or "exit" or "exit app".
            To hear these commands again: say "help" or "list commands".
            Please say a command.
        """.trimIndent().replace("\n", " ")

        speakOut(helpText) {
            startListeningAfterDelay()
        }
    }

    private fun stopVoxSight() {
        speakOut("Stopping VoxSight. Goodbye!") {
            speechRecognizerHelper.stopListening()
            if (isTtsInitialized) {
                tts.stop()
                tts.shutdown()
            }
            Log.d("HomeActivity", "Stopping VoxSight and finishing activity.")
            finish()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            RECORD_AUDIO_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startSpeechToText()
                } else {
                    Toast.makeText(this, "Microphone permission denied.", Toast.LENGTH_SHORT).show()
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                        Toast.makeText(this, "Please allow Microphone permission from Settings for full functionality.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            CALL_PHONE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    speakOut("Phone call permission granted. Please try your call command again.") {
                        startListeningAfterDelay()
                    }
                } else {
                    speakOut("Phone call permission denied. Cannot make calls.") {
                        startListeningAfterDelay()
                    }
                }
            }
            SMS_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    speakOut("SMS send permission granted. Please repeat your message command.") {
                        startListeningAfterDelay()
                    }
                } else {
                    speakOut("SMS send permission denied. Cannot send messages.") {
                        startListeningAfterDelay()
                    }
                }
            }
            READ_SMS_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Permissions", "READ_SMS permission granted.")
                    speakOut("SMS read permission granted. Reading messages now.") {
                        readSmsMessages()
                    }
                } else {
                    Log.d("Permissions", "READ_SMS permission denied.")
                    speakOut("Cannot read messages without permission. Please grant SMS permission in settings.") {
                        startListeningAfterDelay()
                    }
                }
            }
            CAMERA_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    speakOut("Camera permission granted. Opening camera.") {
                        startCameraActivity()
                    }
                } else {
                    speakOut("Camera permission denied. Cannot open camera.") {
                        startListeningAfterDelay()
                    }
                    Toast.makeText(this, "Camera permission denied.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop listening when activity goes to background
        stopListening()
        shouldResumeListening = false
        Log.d("HomeActivity", "Activity paused, listening stopped")
    }

    override fun onResume() {
        super.onResume()
        shouldResumeListening = true
        // Restart listening when returning to this activity, but only if not in SMS flow
        if (!isWaitingForRecipient && !isWaitingForMessage) {
            startListeningAfterDelay()
        }
        Log.d("HomeActivity", "Activity resumed, listening may restart")
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizerHelper.stopListening()
        if (isTtsInitialized) {
            tts.stop()
            tts.shutdown()
        }
        Log.d("HomeActivity", "Activity destroyed, resources released.")
    }
}