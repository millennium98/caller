package com.example.callertheme.model

import android.graphics.Bitmap
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
class Contact(
    var contactId: Long? = null,
    var contactName: String? = null,
    var contactAvatar: Bitmap? = null,
    var contactNumber: String? = null
) : Parcelable