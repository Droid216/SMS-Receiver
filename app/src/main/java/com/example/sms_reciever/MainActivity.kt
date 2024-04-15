package com.example.sms_reciever
import okhttp3.*

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsMessage
import android.widget.EditText
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var smsInterceptionSwitch: Switch
    private lateinit var botTokenEditText: EditText
    private lateinit var chatIdEditText: EditText

    companion object {
        const val SMS_PERMISSION_REQUEST_CODE = 123
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme)
        setContentView(R.layout.activity_main)

        // Find UI elements
        botTokenEditText = findViewById(R.id.editTextBotToken)
        chatIdEditText = findViewById(R.id.editTextChatId)

        // Restore previously saved data
        val (savedBotToken, savedChatId) = loadDataFromSharedPreferences()
        botTokenEditText.setText(savedBotToken)
        chatIdEditText.setText(savedChatId)

        // Configure the switch for SMS interception
        smsInterceptionSwitch = findViewById(R.id.smsInterceptionSwitch)
        smsInterceptionSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveSmsInterceptionStateToPrefs(isChecked)
            if (isChecked) {
                requestSmsPermission()
            }
        }

        // Check if SMS interception was previously enabled
        val isSmsInterceptionEnabled = readSmsInterceptionStateFromPrefs()
        smsInterceptionSwitch.isChecked = isSmsInterceptionEnabled

        if (isSmsInterceptionEnabled) {
            // Check for SMS receive permission
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECEIVE_SMS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // If permission is not granted, turn off interception
                smsInterceptionSwitch.isChecked = false
            } else {
                // Register SMS receiver
                registerSmsReceiver()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Save data before closing the app
        val botToken = botTokenEditText.text.toString()
        val chatId = chatIdEditText.text.toString()
        saveDataToSharedPreferences(botToken, chatId)
    }

    private fun requestSmsPermission() {
        // Request permission for receiving SMS
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECEIVE_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECEIVE_SMS),
                SMS_PERMISSION_REQUEST_CODE
            )
        } else {
            // Register SMS receiver
            registerSmsReceiver()
        }
    }

    private var isReceiverRegistered = false

    private fun registerSmsReceiver() {
        // Register SMS receiver if not already registered
        if (!isReceiverRegistered) {
            val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
            registerReceiver(SmsReceiver(), filter)
            isReceiverRegistered = true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // If permission is granted, register SMS receiver
                registerSmsReceiver()
            } else {
                // If permission is not granted, turn off interception
                smsInterceptionSwitch.isChecked = false
            }
        }
    }

    private fun readSmsInterceptionStateFromPrefs(): Boolean {
        // Read SMS interception state from preferences
        val sharedPreferences =
            getSharedPreferences("sms_interception_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("is_interception_enabled", false)
    }

    private fun saveSmsInterceptionStateToPrefs(isEnabled: Boolean) {
        // Save SMS interception state to preferences
        val sharedPreferences =
            getSharedPreferences("sms_interception_prefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putBoolean("is_interception_enabled", isEnabled)
            apply()
        }
    }

    private fun saveDataToSharedPreferences(botToken: String, chatId: String) {
        // Save data (bot token and chat ID) to preferences
        val sharedPreferences = getSharedPreferences("my_shared_preferences", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("bot_token", botToken)
            putString("chat_id", chatId)
            apply()
        }
    }

    private fun loadDataFromSharedPreferences(): Pair<String, String> {
        // Load saved data (bot token and chat ID) from preferences
        val sharedPreferences = getSharedPreferences("my_shared_preferences", Context.MODE_PRIVATE)
        val botToken = sharedPreferences.getString("bot_token", "") ?: ""
        val chatId = sharedPreferences.getString("chat_id", "") ?: ""
        return Pair(botToken, chatId)
    }


    class SmsReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Get the main activity
            val mainActivity = context as MainActivity
            // Check if SMS interception is enabled
            val isInterceptionEnabled = mainActivity.smsInterceptionSwitch.isChecked
            if (isInterceptionEnabled && intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                val bundle = intent.extras
                if (bundle != null) {
                    val pdus = bundle.get("pdus") as Array<*>
                    for (pdu in pdus) {
                        val smsMessage = SmsMessage.createFromPdu(
                            pdu as ByteArray,
                            bundle.getString("format")
                        )
                        val sender = smsMessage.originatingAddress
                        val body = smsMessage.messageBody
                        val formattedMessage = "*Сообщение от:*\n$sender\n\n*Текст:*\n$body"
                        val botToken = mainActivity.botTokenEditText.text.toString()
                        val chatId = mainActivity.chatIdEditText.text.toString()
                        sendTelegramMessage(botToken, chatId, formattedMessage)
                    }
                }
            }
        }

        private fun sendTelegramMessage(botToken: String, chatTelegramId: String, message: String) {
            // Send message to Telegram
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val url = "https://api.telegram.org/bot$botToken/sendMessage"
                    val requestBody = FormBody.Builder().add("chat_id", chatTelegramId).add("text", message).add("parse_mode", "markdown").build()
                    val request = Request.Builder().url(url).post(requestBody).build()
                    val response = OkHttpClient().newCall(request).execute()
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected code $response")
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }
}