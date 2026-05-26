package com.hanzg.mipass.domain.repository

import com.hanzg.mipass.domain.model.Password
import kotlinx.coroutines.flow.Flow

interface PasswordRepository {

    fun getAllPasswordsFlow(): Flow<List<Password>>

    suspend fun getPasswordById(id: String): Password?

    suspend fun insertPassword(entity: Password)

    suspend fun updatePassword(entity: Password)

    suspend fun deletePassword(entity: Password)

    suspend fun deleteAll()

    suspend fun findDuplicate(name: String, account: String, url: String?): Password?

    suspend fun getCount(): Int

    suspend fun getAllCategories(): List<String>

    suspend fun getCategoriesByType(type: String): List<String>

    fun searchPasswords(query: String, category: String, type: String?): Flow<List<Password>>
}
