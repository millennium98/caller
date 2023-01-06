package com.example.callertheme.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.telecom.TelecomManager
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.callertheme.*
import com.example.callertheme.databinding.CallerOngoingThemeLayoutBinding
import com.example.callertheme.databinding.CallerThemeLayoutBinding
import com.example.callertheme.model.Contact
import java.util.*

var NOTIFICATION_CHANNEL_ID = "caller.theme"
var channelName = "Caller Theme"

class ThemeDrawerService : Service() {

    private val layoutInflater: LayoutInflater by lazy {
        getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    }
    private val windowManager: WindowManager by lazy {
        getSystemService(WINDOW_SERVICE) as WindowManager
    }
    private val onGoingCallBinding: CallerOngoingThemeLayoutBinding by lazy {
        CallerOngoingThemeLayoutBinding.bind(layoutInflater.inflate(R.layout.caller_ongoing_theme_layout, null))
    }
    private val inComingCallBinding: CallerThemeLayoutBinding by lazy {
        CallerThemeLayoutBinding.bind(layoutInflater.inflate(R.layout.caller_theme_layout, null))
    }
    private val telephonyManager: TelecomManager by lazy {
        getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    }
    private val timer: Timer by lazy { Timer() }
    private var contact: Contact? = null
    private var isCallAnswered = false

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        contact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(CONTACT_FLG, Contact::class.java)
        } else {
            intent?.getParcelableExtra(CONTACT_FLG)
        }
        isCallAnswered = intent?.getBooleanExtra(CALL_ANSWERED_STATE, false) ?: false

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        inComingCallBinding.contactName.text = contact?.contactName ?: contact?.contactNumber
        inComingCallBinding.callStart.setOnClickListener(::onIncomingClick)
        inComingCallBinding.callEnd.setOnClickListener(::onIncomingClick)

        onGoingCallBinding.callEnd.setOnClickListener(::callEnd)
        onGoingCallBinding.contactName.text = contact?.contactName ?: contact?.contactNumber

        if (contact?.contactAvatar != null) {
            inComingCallBinding.ivAvatar.setImageBitmap(contact!!.contactAvatar)
            onGoingCallBinding.ivAvatar.setImageBitmap(contact!!.contactAvatar)
        }

        try {
            if (isCallAnswered) {
                if (onGoingCallBinding.root.windowToken == null && onGoingCallBinding.root.parent == null) {
                    windowManager.addView(onGoingCallBinding.root, params)

                    timer.scheduleAtFixedRate(object : TimerTask() {
                        val start = Date(System.currentTimeMillis()).time
                        override fun run() {
                            val elapsedTime = Date(System.currentTimeMillis() - start).time
                            val elapsedTimeText = getElapsedTimeString(elapsedTime)

                            val timerTextView = onGoingCallBinding.root.findViewById<TextView>(R.id.timer)
                            timerTextView.post {
                                timerTextView.text = elapsedTimeText
                            }
                        }
                    }, 0, 1000)
                }
            } else {
                if (inComingCallBinding.root.windowToken == null && inComingCallBinding.root.parent == null) {
                    windowManager.addView(inComingCallBinding.root, params)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        //create notification state foreground service running
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_MIN)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        val notification = notificationBuilder.setOngoing(true)
            .setContentTitle("In call")
            .setContentText("with $contact")
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        startForeground(1, notification)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (inComingCallBinding.root.parent != null) {
            windowManager.removeView(inComingCallBinding.root)
            inComingCallBinding.root.invalidate()
            (inComingCallBinding.root.parent as? ViewGroup)?.removeAllViews()
        }
        if (onGoingCallBinding.root.parent != null) {
            windowManager.removeView(onGoingCallBinding.root)
            onGoingCallBinding.root.invalidate()
            (onGoingCallBinding.root.parent as? ViewGroup)?.removeAllViews()
        }
        timer.cancel()
        super.onDestroy()
    }

    private fun getElapsedTimeString(time: Long): String {
        val hour = (time / (1000 * 60 * 60)) % 24
        val minute = (time / (1000 * 60)) % 60
        val second = (time / 1000) % 60
        return String.format("%02d:%02d:%02d", hour, minute, second)
    }

    private fun onIncomingClick(v: View) {
        val id = v.id
        if (id == R.id.call_start) {
            if (ContextCompat.checkSelfPermission(this, PERMISSION_ANSWER_PHONE_CALLS) != 0) {
                return
            }
            telephonyManager.acceptRingingCall()
            val btnDownIntent = Intent(ACTION_MEDIA_BUTTON).apply {
                putExtra(EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK))
            }
            val btnUpIntent = Intent(ACTION_MEDIA_BUTTON).apply {
                putExtra(EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK))
            }
            sendOrderedBroadcast(btnDownIntent, PERMISSION_CALL_PRIVILEGED)
            sendOrderedBroadcast(btnUpIntent, PERMISSION_CALL_PRIVILEGED)

        } else if (id == R.id.call_end) {
            if (ContextCompat.checkSelfPermission(this, PERMISSION_ANSWER_PHONE_CALLS) != 0) {
                return
            }
            telephonyManager.endCall()
            stopSelf()
        }
        windowManager.removeView(inComingCallBinding.root)
        inComingCallBinding.root.invalidate()
        (inComingCallBinding.root.parent as? ViewGroup)?.removeAllViews()
    }

    private fun callEnd(v: View) {
        if (ContextCompat.checkSelfPermission(this, PERMISSION_ANSWER_PHONE_CALLS) != 0) {
            return
        }
        telephonyManager.endCall()
        stopSelf()
        windowManager.removeView(onGoingCallBinding.root)
        onGoingCallBinding.root.invalidate()
        (onGoingCallBinding.root.parent as? ViewGroup)?.removeAllViews()
    }
}