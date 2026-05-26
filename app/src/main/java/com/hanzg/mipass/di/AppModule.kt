package com.hanzg.mipass.di

import android.content.Context
import com.hanzg.mipass.data.local.AppPreferences
import com.hanzg.mipass.data.local.MiPassDatabase
import com.hanzg.mipass.data.local.PasswordDao
import com.hanzg.mipass.data.repository.PasswordRepositoryImpl
import com.hanzg.mipass.domain.repository.PasswordRepository
import com.hanzg.mipass.utils.BiometricPromptManager
import com.hanzg.mipass.utils.ClipboardUtils
import com.hanzg.mipass.utils.KeyStoreManager
import com.hanzg.mipass.utils.LocaleHelper
import com.hanzg.mipass.utils.MasterPasswordManager

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideKeyStoreManager(
        @ApplicationContext context: Context
    ): KeyStoreManager {
        return KeyStoreManager(context)
    }

    @Provides
    @Singleton
    fun provideMiPassDatabase(
        @ApplicationContext context: Context,
        keyStoreManager: KeyStoreManager
    ): MiPassDatabase {
        return MiPassDatabase.getInstance(context, keyStoreManager)
    }

    @Provides
    @Singleton
    fun providePasswordDao(database: MiPassDatabase): PasswordDao {
        return database.passwordDao()
    }

    @Provides
    @Singleton
    fun providePasswordRepository(impl: PasswordRepositoryImpl): PasswordRepository {
        return impl
    }

    @Provides
    @Singleton
    fun provideBiometricPromptManager(
        @ApplicationContext context: Context
    ): BiometricPromptManager {
        return BiometricPromptManager(context)
    }

    @Provides
    @Singleton
    fun provideClipboardUtils(
        @ApplicationContext context: Context
    ): ClipboardUtils {
        return ClipboardUtils(context)
    }

    @Provides
    @Singleton
    fun provideAppPreferences(
        @ApplicationContext context: Context
    ): AppPreferences {
        return AppPreferences(context)
    }

    @Provides
    @Singleton
    fun provideMasterPasswordManager(
        @ApplicationContext context: Context,
        keyStoreManager: KeyStoreManager
    ): MasterPasswordManager {
        return MasterPasswordManager(context, keyStoreManager)
    }

    @Provides
    @Singleton
    fun provideLocaleHelper(
        @ApplicationContext context: Context,
        appPreferences: AppPreferences
    ): LocaleHelper {
        return LocaleHelper(context, appPreferences)
    }
}
