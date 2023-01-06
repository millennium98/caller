package com.example.callertheme.data.preference

import android.content.Context
import com.example.callertheme.PREFERENCE_NAME

class AppPreference(context: Context) {

    private val sharedPreferences = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
    private val editor = sharedPreferences.edit()

    companion object {
        private const val IS_USING_CUSTOM_RINGTONE = "IS_USING_CUSTOM_RINGTONE"
    }

    fun isUsingCustomRingtone() = sharedPreferences.getBoolean(IS_USING_CUSTOM_RINGTONE, false)
    fun setIsUsingCustomRingtone(value: Boolean) = editor.putBoolean(IS_USING_CUSTOM_RINGTONE, value).commit()
}