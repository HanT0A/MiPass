package com.hanzg.mipass.data.local

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hanzg.mipass.domain.model.EntryType
import java.util.UUID

@Entity(tableName = "password_entries")
@Immutable
data class PasswordEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "entry_type")
    val type: EntryType,
    val name: String,
    val url: String?,
    val account: String,
    val password: String,
    val category: String,
    val notes: String,
    @ColumnInfo(name = "icon_uri")
    val iconUri: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
