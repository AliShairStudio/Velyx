package com.alert.beacon.data

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class UserChatSettings(
    val customName: String? = null,
    val savedAt: Long? = null
) {
    // Required empty constructor for Firebase
    constructor() : this(null, null)
}
