package com.tablet.hid.model

data class HidHost(
    val address: String,
    val btName: String,
    val alias: String? = null,
    val lastSeenMs: Long = 0L
) {
    /** Alias if the user set one, otherwise the Bluetooth name, otherwise the MAC address. */
    val displayName: String
        get() = alias?.takeIf { it.isNotBlank() } ?: btName.ifBlank { address }
}
