package com.hanzg.mipass.domain.model

import java.util.UUID

data class Password(
    val id: String = UUID.randomUUID().toString(),
    val type: EntryType,
    val name: String,
    val url: String?,
    val account: String,
    val password: String,
    val category: String,
    val notes: String,
    val iconUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
