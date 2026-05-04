package com.tablet.hid.model

/**
 * Identifies a configuration profile. Built-in profiles have stable keys;
 * custom profiles use timestamp-based keys generated at creation time.
 */
data class Profile(val name: String, val key: String) {
    companion object {
        val DEFAULT       = Profile("Default",          "default")
        val ACCESS_BASIC  = Profile("Access – Basic",    "access_basic")
        val ACCESS_ADVANCED = Profile("Access – Advanced", "access_advanced")
        val BUILT_INS     = listOf(DEFAULT, ACCESS_BASIC, ACCESS_ADVANCED)

        fun fromKey(key: String, customProfiles: List<Profile>): Profile =
            BUILT_INS.find { it.key == key }
                ?: customProfiles.find { it.key == key }
                ?: DEFAULT
    }
}
