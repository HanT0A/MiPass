package com.hanzg.mipass.data.repository

import com.hanzg.mipass.data.local.PasswordDao
import com.hanzg.mipass.data.local.PasswordEntity
import com.hanzg.mipass.domain.repository.PasswordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasswordRepositoryImpl @Inject constructor(
    private val passwordDao: PasswordDao
) : PasswordRepository {

    override fun getAllPasswordsFlow(): Flow<List<PasswordEntity>> {
        return passwordDao.getAllPasswords()
    }

    override suspend fun getPasswordById(id: String): PasswordEntity? {
        return passwordDao.getPasswordById(id)
    }

    override suspend fun insertPassword(entity: PasswordEntity) {
        passwordDao.insertPassword(entity)
    }

    override suspend fun updatePassword(entity: PasswordEntity) {
        passwordDao.updatePassword(entity)
    }

    override suspend fun deletePassword(entity: PasswordEntity) {
        passwordDao.deletePassword(entity)
    }

    override suspend fun deleteAll() {
        passwordDao.deleteAll()
    }

    override suspend fun findDuplicate(name: String, account: String, url: String?): PasswordEntity? {
        val byName = passwordDao.findByNameAndAccount(name, account)
        if (byName != null) return byName
        if (!url.isNullOrBlank()) {
            return passwordDao.findByUrlAndAccount(url, account)
        }
        return null
    }

    override suspend fun getCount(): Int {
        return passwordDao.getCount()
    }

    override suspend fun getAllCategories(): List<String> {
        return passwordDao.getAllCategories()
    }
}
