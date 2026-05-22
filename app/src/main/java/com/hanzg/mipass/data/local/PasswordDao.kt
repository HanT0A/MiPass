package com.hanzg.mipass.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordDao {

    @Query("SELECT * FROM password_entries ORDER BY updated_at DESC")
    fun getAllPasswords(): Flow<List<PasswordEntity>>

    @Query("SELECT * FROM password_entries WHERE id = :id")
    suspend fun getPasswordById(id: String): PasswordEntity?

    @Query("SELECT * FROM password_entries WHERE entry_type = :type ORDER BY updated_at DESC")
    fun getPasswordsByType(type: String): Flow<List<PasswordEntity>>

    @Query("""
        SELECT * FROM password_entries
        WHERE name LIKE '%' || :query || '%' OR account LIKE '%' || :query || '%'
        ORDER BY updated_at DESC
    """)
    fun searchPasswords(query: String): Flow<List<PasswordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPassword(entity: PasswordEntity)

    @Update
    suspend fun updatePassword(entity: PasswordEntity)

    @Delete
    suspend fun deletePassword(entity: PasswordEntity)

    @Query("DELETE FROM password_entries")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM password_entries")
    suspend fun getCount(): Int

    @Query("SELECT * FROM password_entries WHERE name = :name AND account = :account LIMIT 1")
    suspend fun findByNameAndAccount(name: String, account: String): PasswordEntity?

    @Query("SELECT * FROM password_entries WHERE url = :url AND account = :account LIMIT 1")
    suspend fun findByUrlAndAccount(url: String, account: String): PasswordEntity?

    @Query("SELECT DISTINCT category FROM password_entries ORDER BY category")
    suspend fun getAllCategories(): List<String>
}
