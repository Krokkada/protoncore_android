/*
 * Copyright (c) 2020 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.core.auth.presentation.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import me.proton.core.auth.domain.AccountWorkflowHandler
import me.proton.core.auth.domain.crypto.CryptoProvider
import me.proton.core.auth.domain.entity.User
import me.proton.core.auth.domain.repository.AuthRepository
import me.proton.core.auth.domain.usecase.PerformUserSetup
import me.proton.core.network.domain.session.SessionId
import me.proton.core.test.android.ArchTest
import me.proton.core.test.kotlin.CoroutinesTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * @author Dino Kadrikj.
 */
class MailboxLoginViewModelTest : ArchTest, CoroutinesTest {

    // region mocks
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val cryptoProvider = mockk<CryptoProvider>(relaxed = true)
    private val accountManager = mockk<AccountWorkflowHandler>(relaxed = true)
    private val useCase = mockk<PerformUserSetup>()
    private val testUser = mockk<User>(relaxed = true)
    // endregion

    // region test data
    private val testSessionId = "test-session-id"
    private val testPassword = "test-password"
    private val testName = "test-name"
    private val testEmail = "test-email"

    // endregion
    private lateinit var viewModel: MailboxLoginViewModel

    @Before
    fun beforeEveryTest() {
        viewModel = MailboxLoginViewModel(accountManager, useCase)
    }

    @Test
    fun `mailbox login happy path`() = coroutinesTest {
        // GIVEN
        every { testUser.name } returns testName
        every { testUser.email } returns testEmail
        coEvery { useCase.invoke(SessionId(testSessionId), testPassword.toByteArray()) } returns flowOf(
            PerformUserSetup.State.Processing,
            PerformUserSetup.State.Success(testUser)
        )
        val observer = mockk<(PerformUserSetup.State) -> Unit>(relaxed = true)
        viewModel.mailboxLoginState.observeDataForever(observer)
        // WHEN
        viewModel.startUserSetup(SessionId(testSessionId), testPassword.toByteArray())
        // THEN
        val arguments = mutableListOf<PerformUserSetup.State>()
        verify(exactly = 2) { observer(capture(arguments)) }
        val processingState = arguments[0]
        val successState = arguments[1]
        assertTrue(processingState is PerformUserSetup.State.Processing)
        assertTrue(successState is PerformUserSetup.State.Success)
        assertEquals(testUser, successState.user)
        assertEquals(testName, successState.user.name)
        assertEquals(testEmail, successState.user.email)
    }

    @Test
    fun `mailbox login empty password returns correct state of events`() = coroutinesTest {
        // GIVEN
        viewModel =
            MailboxLoginViewModel(accountManager, PerformUserSetup(authRepository, cryptoProvider))
        val observer = mockk<(PerformUserSetup.State) -> Unit>(relaxed = true)
        viewModel.mailboxLoginState.observeDataForever(observer)
        // WHEN
        viewModel.startUserSetup(SessionId(testSessionId), "".toByteArray())
        // THEN
        val arguments = slot<PerformUserSetup.State>()
        verify { observer(capture(arguments)) }
        val argument = arguments.captured
        assertTrue(argument is PerformUserSetup.State.Error.EmptyCredentials)
    }

    @Test
    fun `success mailbox login invokes success on account manager`() = coroutinesTest {
        // GIVEN
        coEvery { useCase.invoke(SessionId(testSessionId), testPassword.toByteArray()) } returns flowOf(
            PerformUserSetup.State.Processing,
            PerformUserSetup.State.Success(testUser)
        )
        // WHEN
        viewModel.startUserSetup(SessionId(testSessionId), testPassword.toByteArray())
        // THEN
        val arguments = slot<SessionId>()
        coVerify(exactly = 1) { accountManager.handleTwoPassModeSuccess(capture(arguments)) }
        coVerify(exactly = 0) { accountManager.handleTwoPassModeFailed(any()) }
        assertEquals(testSessionId, arguments.captured.id)
    }

    @Test
    fun `stop mailbox login invokes failed on account manager`() = coroutinesTest {
        // WHEN
        viewModel.stopMailboxLoginFlow(SessionId(testSessionId))
        // THEN
        val arguments = slot<SessionId>()
        coVerify(exactly = 1) { accountManager.handleTwoPassModeFailed(capture(arguments)) }
        coVerify(exactly = 0) { accountManager.handleTwoPassModeSuccess(any()) }
    }
}
