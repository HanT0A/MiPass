package com.hanzg.mipass.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.hanzg.mipass.data.local.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocaleHelper @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val appPreferences: AppPreferences
) {
    private var cachedLocale: Locale? = null

    fun getSavedLocale(): Locale? {
        val settings = runBlocking { appPreferences.settingsFlow.first() }
        return when (settings.language) {
            "en" -> Locale.ENGLISH
            else -> Locale.SIMPLIFIED_CHINESE
        }
    }

    fun wrapContext(context: Context): Context {
        val locale = getSavedLocale() ?: return context
        cachedLocale = locale
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        return context.createConfigurationContext(config)
    }

    fun getCurrentLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appContext.resources.configuration.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            appContext.resources.configuration.locale
        }
    }
}
