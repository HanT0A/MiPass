package com.hanzg.mipass.domain.repository

import com.hanzg.mipass.data.local.PasswordEntity
import kotlinx.coroutines.flow.Flow

interface PasswordRepository {

    fun getAllPasswordsFlow(): Flow<List<PasswordEntity>>

    suspend fun getPasswordById(id: String): PasswordEntity?

    suspend fun insertPassword(entity: PasswordEntity)

    suspend fun updatePassword(entity: PasswordEntity)

    suspend fun deletePassword(entity: PasswordEntity)

    suspend fun deleteAll()

    suspend fun findDuplicate(name: String, account: String, url: String?): PasswordEntity?

    suspend fun getCount(): Int

    suspend fun getAllCategories(): List<String>
}
