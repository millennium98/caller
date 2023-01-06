package com.example.callertheme.ui

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.callertheme.R
import com.example.callertheme.data.preference.AppPreference
import com.example.callertheme.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream


class MainActivity : AppCompatActivity() {

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }
    private val preference: AppPreference by lazy {
        AppPreference(this)
    }

    companion object {
        private const val REQUEST_CHANGE_RINGTONE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        initView()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK && requestCode == REQUEST_CHANGE_RINGTONE) {
            val ringtoneUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }

            val isWriteSettingAllowed = Settings.System.canWrite(this)
            if (isWriteSettingAllowed) {
                RingtoneManager.setActualDefaultRingtoneUri(
                    this,
                    RingtoneManager.TYPE_RINGTONE,
                    ringtoneUri
                )
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun checkPermissions() {
        val permissions = arrayListOf<String>()

        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CONTACTS)
        }
//        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
//            permissions.add(Manifest.permission.WRITE_CONTACTS)
//        }
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CALL_LOG)
        }
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
        }
    }

    private fun initView() {
        binding.cbDrawOverlay.isChecked = Settings.canDrawOverlays(this)
        binding.cbSetting.isChecked = Settings.System.canWrite(this)
        binding.cbCustomRingtone.isChecked = preference.isUsingCustomRingtone()

        binding.cbDrawOverlay.setOnClickListener {
            val myIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivity(myIntent)
        }
        binding.cbSetting.setOnClickListener {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
        binding.cbCustomRingtone.setOnCheckedChangeListener { _, isChecked ->
            if (!Settings.System.canWrite(this)) return@setOnCheckedChangeListener
            if (isChecked) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    setCustomRingtoneForBelowQ("banana.mp3", R.raw.banana)
                } else {
                    setCustomRingtone("banana.mp3", R.raw.banana)
                }
            } else {
                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Ringtone")
                startActivityForResult(intent, REQUEST_CHANGE_RINGTONE)
            }
            preference.setIsUsingCustomRingtone(isChecked)
        }
    }

    private fun setCustomRingtoneForBelowQ(name: String, fileId: Int) {
        val file = File(Environment.getExternalStorageDirectory(), "/$name")
        val path = Environment.getExternalStorageDirectory().absolutePath + "/$name"

        if (!file.exists()) {
            try {
                file.parentFile?.mkdirs()
                val stream = resources.openRawResource(fileId).readBytes()
                File(path).createNewFile()
                FileOutputStream(path).apply {
                    write(stream)
                    close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val ringtoneFile = File(path)

        val values = ContentValues()
        values.put(MediaStore.MediaColumns.DATA, ringtoneFile.absolutePath)
        values.put(MediaStore.MediaColumns.TITLE, "ringtone")
        values.put(MediaStore.MediaColumns.MIME_TYPE, getMIMEType(ringtoneFile.absolutePath))
        values.put(MediaStore.MediaColumns.SIZE, 26454)
        values.put(MediaStore.Audio.Media.ARTIST, R.string.app_name)
        values.put(MediaStore.Audio.Media.IS_RINGTONE, true)
        values.put(MediaStore.Audio.Media.IS_NOTIFICATION, true)
        values.put(MediaStore.Audio.Media.IS_ALARM, true)
        values.put(MediaStore.Audio.Media.IS_MUSIC, false)

        val baseUri = MediaStore.Audio.Media.getContentUriForPath(ringtoneFile.absolutePath)
        contentResolver.delete(
            baseUri!!,
            MediaStore.MediaColumns.DATA + "=?",
            arrayOf(ringtoneFile.absolutePath)
        )
        val uri = contentResolver.insert(baseUri, values)

        val isWriteSettingAllowed = Settings.System.canWrite(this)
        if (isWriteSettingAllowed) {
            RingtoneManager.setActualDefaultRingtoneUri(
                this,
                RingtoneManager.TYPE_RINGTONE,
                uri
            )
        }
    }

    private fun setCustomRingtone(name: String, fileId: Int) {
        val values = ContentValues()
        values.put(MediaStore.MediaColumns.TITLE, name)
        values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
        values.put(MediaStore.MediaColumns.SIZE, 26454)
        values.put(MediaStore.Audio.Media.ARTIST, R.string.app_name)
        values.put(MediaStore.Audio.Media.IS_RINGTONE, true)
        values.put(MediaStore.Audio.Media.IS_NOTIFICATION, true)
        values.put(MediaStore.Audio.Media.IS_ALARM, true)
        values.put(MediaStore.Audio.Media.IS_MUSIC, false)
        val newUri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        try {
            contentResolver.openOutputStream(newUri!!).use { os ->
                os?.write(resources.openRawResource(fileId).readBytes())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        RingtoneManager.setActualDefaultRingtoneUri(
            this,
            RingtoneManager.TYPE_RINGTONE,
            newUri
        )
    }

    private fun getMIMEType(path: String): String? {
        var mimeType: String? = null
        val extension = MimeTypeMap.getFileExtensionFromUrl(path)
        if (extension != null) {
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
        return mimeType
    }
}