package com.hanzg.mipass.ui.screens

import app.cash.turbine.test
import com.hanzg.mipass.data.local.PasswordEntity
import com.hanzg.mipass.domain.model.EntryType
import com.hanzg.mipass.domain.repository.PasswordRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val testEntries = listOf(
        PasswordEntity(
            id = "1", type = EntryType.APP, name = "微信", account = "user1",
            password = "pwd1", category = "社交", notes = "", url = null,
            createdAt = 0, updatedAt = 0
        ),
        PasswordEntity(
            id = "2", type = EntryType.WEB, name = "淘宝", account = "user2",
            password = "pwd2", category = "购物", url = "taobao.com",
            notes = "", createdAt = 0, updatedAt = 0
        )
    )

    @Test
    fun `filter by type APP shows only app entries`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<PasswordRepository> {
            every { getAllPasswordsFlow() } returns flowOf(testEntries)
        }
        val viewModel = VaultViewModel(repo)
        viewModel.setFilterType(EntryType.APP)
        viewModel.uiState.test {
            // Skip the initial loading state emitted by stateIn's initialValue
            skipItems(1)
            val state = awaitItem()
            assertEquals(1, state.flatList.size)
            assertEquals("微信", state.flatList[0].name)
        }
    }

    @Test
    fun `search query filters by name`() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<PasswordRepository> {
            every { getAllPasswordsFlow() } returns flowOf(testEntries)
        }
        val viewModel = VaultViewModel(repo)
        viewModel.setFilterType(null)
        viewModel.onSearchQueryChanged("淘宝")
        viewModel.uiState.test {
            // Skip the initial loading state emitted by stateIn's initialValue
            skipItems(1)
            val state = awaitItem()
            assertEquals(1, state.flatList.size)
            assertEquals("淘宝", state.flatList[0].name)
        }
    }
}
