package com.hanzg.mipass.data.repository

import com.hanzg.mipass.data.local.PasswordDao
import com.hanzg.mipass.data.local.PasswordEntity
import com.hanzg.mipass.data.local.SnapshotManager
import com.hanzg.mipass.domain.model.Password
import com.hanzg.mipass.domain.repository.PasswordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasswordRepositoryImpl @Inject constructor(
    private val passwordDao: PasswordDao,
    private val snapshotManager: SnapshotManager
) : PasswordRepository {

    private val snapshotScope = CoroutineScope(Dispatchers.IO)
    private var snapshotJob: Job? = null

    override fun getAllPasswordsFlow(): Flow<List<Password>> {
        return passwordDao.getAllPasswords().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getPasswordById(id: String): Password? {
        return passwordDao.getPasswordById(id)?.toDomain()
    }

    override suspend fun insertPassword(entity: Password) {
        passwordDao.insertPassword(entity.toEntity())
        triggerAutoSnapshot()
    }

    override suspend fun updatePassword(entity: Password) {
        passwordDao.updatePassword(entity.toEntity())
        triggerAutoSnapshot()
    }

    override suspend fun deletePassword(entity: Password) {
        passwordDao.deletePassword(entity.toEntity())
        triggerAutoSnapshot()
    }

    override suspend fun deleteAll() {
        passwordDao.deleteAll()
        triggerAutoSnapshot()
    }

    override suspend fun findDuplicate(name: String, account: String, url: String?): Password? {
        val byName = passwordDao.findByNameAndAccount(name, account)
        if (byName != null) return byName.toDomain()
        if (!url.isNullOrBlank()) {
            return passwordDao.findByUrlAndAccount(url, account)?.toDomain()
        }
        return null
    }

    override suspend fun getCount(): Int {
        return passwordDao.getCount()
    }

    override suspend fun getAllCategories(): List<String> {
        return passwordDao.getAllCategories()
    }

    override suspend fun getCategoriesByType(type: String): List<String> {
        return passwordDao.getCategoriesByType(type)
    }

    override fun searchPasswords(query: String, category: String, type: String?): Flow<List<Password>> {
        return passwordDao.searchPasswordsFiltered(query, category, type)
            .map { list -> list.map { it.toDomain() } }
    }

    private fun triggerAutoSnapshot() {
        snapshotJob?.cancel()
        snapshotJob = snapshotScope.launch {
            delay(10_000L)
            try {
                val allPasswords = passwordDao.getAllPasswords().first()
                snapshotManager.createSnapshot(allPasswords)
            } catch (_: Exception) { }
        }
    }
}

private fun PasswordEntity.toDomain(): Password = Password(
    id = id,
    type = type,
    name = name,
    url = url,
    account = account,
    password = password,
    category = category,
    notes = notes,
    iconUri = iconUri,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun Password.toEntity(): PasswordEntity = PasswordEntity(
    id = id,
    type = type,
    name = name,
    url = url,
    account = account,
    password = password,
    category = category,
    notes = notes,
    iconUri = iconUri,
    createdAt = createdAt,
    updatedAt = updatedAt
)
