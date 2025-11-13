@file:Suppress("DEPRECATION")

package com.iie.st10320489.stylu.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import java.util.*
import java.util.Locale.setDefault

object LanguageManager {

    private const val PREF_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "selected_language"

    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_AFRIKAANS = "af"
    const val LANGUAGE_XHOSA = "xh"

    const val LANGUAGE_VENDA = "ve"

    const val LANGUAGE_ZULU = "zu"

    const val LANGUAGE_TSWANA = "tw"

    const val LANGUAGE_NDEBELE = "nd"

    const val LANGUAGE_FRENCH = "fr"

    const val LANGUAGE_ITALIAN = "it"

    const val LANGUAGE_SPANISH = "sp"



    /**
     * Get the currently selected language
     */
    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, LANGUAGE_ENGLISH) ?: LANGUAGE_ENGLISH
    }

    /**
     * Set and apply a new language
     */
    @SuppressLint("UseKtx")
    fun setLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()

        updateResources(context, languageCode)
    }

    /**
     * Apply the saved language to the context
     */
    fun applyLanguage(context: Context): Context {
        val languageCode = getLanguage(context)
        return updateResources(context, languageCode)
    }

    /**
     * Update the app's resources with the new language
     */
    private fun updateResources(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        setDefault(locale)

        val config = Configuration(context.resources.configuration)

        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    /**
     * Get the display name for a language code
     */
    fun getLanguageDisplayName(context: Context, languageCode: String): String {
        return when (languageCode) {
            LANGUAGE_ENGLISH -> context.getString(com.iie.st10320489.stylu.R.string.language_english)
            LANGUAGE_AFRIKAANS -> context.getString(com.iie.st10320489.stylu.R.string.language_afrikaans)
            LANGUAGE_XHOSA -> context.getString(com.iie.st10320489.stylu.R.string.language_xhosa)
            LANGUAGE_FRENCH -> context.getString(com.iie.st10320489.stylu.R.string.language_french)
            LANGUAGE_ITALIAN -> context.getString(com.iie.st10320489.stylu.R.string.language_italian)
            LANGUAGE_SPANISH -> context.getString(com.iie.st10320489.stylu.R.string.language_spanish)
            LANGUAGE_VENDA -> context.getString(com.iie.st10320489.stylu.R.string.language_venda)
            LANGUAGE_ZULU -> context.getString(com.iie.st10320489.stylu.R.string.language_zulu)
            LANGUAGE_TSWANA -> context.getString(com.iie.st10320489.stylu.R.string.language_tswana)
            LANGUAGE_NDEBELE -> context.getString(com.iie.st10320489.stylu.R.string.language_ndebele)

            else -> context.getString(com.iie.st10320489.stylu.R.string.language_english)
        }
    }
}