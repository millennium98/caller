package com.example.callertheme.service

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.PhoneLookup
import android.provider.Settings
import android.telephony.TelephonyManager
import com.example.callertheme.ACTION_PHONE_STATE
import com.example.callertheme.CALL_ANSWERED_STATE
import com.example.callertheme.CONTACT_FLG
import com.example.callertheme.model.Contact
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.*


class PhoneCallReceiver : BroadcastReceiver() {

    companion object {
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var intent: Intent? = null
        private var callStartTime: Date? = null
        private var isIncoming = false
        private var savedNumber: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        //We listen to two intents.  The new outgoing call only tells us of an outgoing call.  We use it to get the number.
        if (!checkPermission(context)) return
        val action = intent.action
        if (action.equals(ACTION_PHONE_STATE)) {
            val stateStr = intent.extras!!.getString(TelephonyManager.EXTRA_STATE)
            val number = intent.extras!!.getString(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: return
            var state = 0
            if (stateStr == TelephonyManager.EXTRA_STATE_IDLE) {
                state = TelephonyManager.CALL_STATE_IDLE
            } else if (stateStr == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                state = TelephonyManager.CALL_STATE_OFFHOOK
            } else if (stateStr == TelephonyManager.EXTRA_STATE_RINGING) {
                state = TelephonyManager.CALL_STATE_RINGING
            }
            onCallStateChanged(context, state, number)
        }
    }

    private fun getContactInformation(phoneNumber: String?, context: Context): Contact {
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        val projection = arrayOf(PhoneLookup.DISPLAY_NAME, PhoneLookup._ID)

        val contact = Contact(contactNumber = phoneNumber)
        val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                contact.contactName = cursor.getString(0)
                contact.contactId = cursor.getLong(1)
            }
            cursor.close()
        }

        val contactId = contact.contactId
        try {
            if (contactId != null) {
                val inputStream = ContactsContract.Contacts.openContactPhotoInputStream(
                    context.contentResolver,
                    ContentUris.withAppendedId(
                        ContactsContract.Contacts.CONTENT_URI,
                        contactId.toLong()
                    )
                )
                contact.contactAvatar = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return contact
    }

    private fun checkPermission(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
                && context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                && context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
                && Settings.canDrawOverlays(context)
    }

    fun openPhoto(context: Context, contactId: Long): InputStream? {
        val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        val photoUri = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY)
        val cursor: Cursor = context.contentResolver.query(
            photoUri,
            arrayOf(ContactsContract.Contacts.Photo.PHOTO),
            null,
            null,
            null
        ) ?: return null
        cursor.use {
            if (it.moveToFirst()) {
                val data = it.getBlob(0)
                if (data != null) {
                    return ByteArrayInputStream(data)
                }
            }
        }
        return null
    }

    private fun getRawResourceUri(context: Context, fileName: String): Uri = Uri.parse(
        ContentResolver.SCHEME_ANDROID_RESOURCE +
                File.pathSeparator +
                File.separator +
                context.packageName +
                "/raw/" +
                fileName
    )

    private fun onIncomingCallReceived(ctx: Context, number: String?, start: Date?) {
        intent = Intent(ctx, ThemeDrawerService::class.java)
        val contact = getContactInformation(number, ctx)
        intent?.putExtra(CONTACT_FLG, contact)
        ctx.startForegroundService(intent)
    }

    private fun onIncomingCallAnswered(ctx: Context, number: String?, start: Date?) {
        if (intent != null) {
            intent!!.putExtra(CALL_ANSWERED_STATE, true)
            ctx.startForegroundService(intent)
        }
    }

    private fun callEnded(
        ctx: Context?,
        number: String?,
        start: Date?,
        end: Date?
    ) {
        ctx?.stopService(intent ?: return)
    }

    private fun onOutgoingCallStarted(ctx: Context?, number: String?, start: Date?) {

    }

    private fun onOutgoingCallEnded(
        ctx: Context?,
        number: String?,
        start: Date?,
        end: Date?
    ) {

    }

    private fun onMissedCall(ctx: Context?, number: String?, start: Date?) {

    }

    private fun onCallStateChanged(context: Context, state: Int, number: String?) {
        if (lastState == state) {
            return
        }
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                isIncoming = true
                callStartTime = Date()
                savedNumber = number
                onIncomingCallReceived(context, number, callStartTime)
            }
            TelephonyManager.CALL_STATE_OFFHOOK ->
                if (lastState != TelephonyManager.CALL_STATE_RINGING) {
                    isIncoming = false
                    callStartTime = Date()
                    onOutgoingCallStarted(context, savedNumber, callStartTime)
                } else {
                    isIncoming = true
                    callStartTime = Date()
                    onIncomingCallAnswered(context, savedNumber, callStartTime)
                }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    //Ring but no pickup-  a miss
                    onMissedCall(context, savedNumber, callStartTime)
                } else {
                    callEnded(context, savedNumber, callStartTime, Date())
                }
                context.stopService(intent ?: return)
            }
        }
        lastState = state
    }
}