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

package me.proton.core.accountmanager.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.proton.core.account.domain.entity.Account
import me.proton.core.account.domain.entity.AccountState
import me.proton.core.account.domain.entity.SessionState
import me.proton.core.account.domain.entity.isReady
import me.proton.core.account.domain.entity.isSecondFactorNeeded
import me.proton.core.account.domain.repository.AccountRepository
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.auth.domain.AccountWorkflowHandler
import me.proton.core.auth.domain.repository.AuthRepository
import me.proton.core.domain.entity.Product
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.session.Session
import me.proton.core.network.domain.session.SessionId
import me.proton.core.user.domain.UserManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountManagerImpl @Inject constructor(
    product: Product,
    private val accountRepository: AccountRepository,
    private val authRepository: AuthRepository,
    private val userManager: UserManager
) : AccountManager(product), AccountWorkflowHandler {

    private val removeSessionLock = Mutex()

    private suspend fun removeSession(sessionId: SessionId) {
        removeSessionLock.withLock {
            accountRepository.getAccountOrNull(sessionId)?.let { account ->
                if (account.sessionState == SessionState.Authenticated) {
                    authRepository.revokeSession(sessionId)
                }
            }
            accountRepository.deleteSession(sessionId)
        }
    }

    private suspend fun disableAccount(account: Account) {
        accountRepository.updateAccountState(account.userId, AccountState.Disabled)
        account.sessionId?.let { removeSession(it) }
        userManager.lock(account.userId)
    }

    private suspend fun disableAccount(sessionId: SessionId) {
        accountRepository.getAccountOrNull(sessionId)?.let { disableAccount(it) }
    }

    private suspend fun clearSessionDetails(userId: UserId) {
        accountRepository.getSessionIdOrNull(userId)?.let { accountRepository.clearSessionDetails(it) }
    }

    override suspend fun addAccount(account: Account, session: Session) {
        handleSession(account.copy(state = AccountState.Ready), session)
    }

    override suspend fun removeAccount(userId: UserId) {
        accountRepository.getAccountOrNull(userId)?.let { account ->
            accountRepository.updateAccountState(account.userId, AccountState.Removed)
            account.sessionId?.let { removeSession(it) }
            accountRepository.deleteAccount(account.userId)
        }
    }

    override suspend fun disableAccount(userId: UserId) {
        accountRepository.getAccountOrNull(userId)?.let { disableAccount(it) }
    }

    override fun getAccount(userId: UserId): Flow<Account?> =
        accountRepository.getAccount(userId)

    override fun getAccounts(): Flow<List<Account>> =
        accountRepository.getAccounts()

    override fun getSessions(): Flow<List<Session>> =
        accountRepository.getSessions()

    override fun onAccountStateChanged(initialState: Boolean): Flow<Account> =
        accountRepository.onAccountStateChanged(initialState)

    override fun onSessionStateChanged(initialState: Boolean): Flow<Account> =
        accountRepository.onSessionStateChanged(initialState)

    override fun getPrimaryUserId(): Flow<UserId?> =
        accountRepository.getPrimaryUserId()

    override suspend fun getPreviousPrimaryUserId(): UserId? =
        accountRepository.getPreviousPrimaryUserId()

    override suspend fun setAsPrimary(userId: UserId) =
        accountRepository.setAsPrimary(userId)

    // region AccountWorkflowHandler

    override suspend fun handleSession(account: Account, session: Session) {
        // Remove any existing Session.
        accountRepository.getSessionIdOrNull(account.userId)?.let { removeSession(it) }
        // Account state must be != Ready if SecondFactorNeeded.
        val state = if (account.isReady() && account.isSecondFactorNeeded()) AccountState.NotReady else account.state
        accountRepository.createOrUpdateAccountSession(account.copy(state = state), session)
    }

    override suspend fun handleTwoPassModeNeeded(userId: UserId) {
        accountRepository.updateAccountState(userId, AccountState.TwoPassModeNeeded)
    }

    override suspend fun handleTwoPassModeSuccess(userId: UserId) {
        accountRepository.updateAccountState(userId, AccountState.TwoPassModeSuccess)
    }

    override suspend fun handleTwoPassModeFailed(userId: UserId) {
        accountRepository.updateAccountState(userId, AccountState.TwoPassModeFailed)
    }

    override suspend fun handleSecondFactorSuccess(sessionId: SessionId, updatedScopes: List<String>) {
        accountRepository.updateSessionScopes(sessionId, updatedScopes)
        accountRepository.updateSessionState(sessionId, SessionState.SecondFactorSuccess)
        accountRepository.updateSessionState(sessionId, SessionState.Authenticated)
    }

    override suspend fun handleSecondFactorFailed(sessionId: SessionId) {
        accountRepository.updateSessionState(sessionId, SessionState.SecondFactorFailed)
        disableAccount(sessionId)
    }

    override suspend fun handleCreateAddressNeeded(userId: UserId) {
        accountRepository.updateAccountState(userId, AccountState.CreateAddressNeeded)
    }

    override suspend fun handleCreateAddressSuccess(userId: UserId) {
        accountRepository.updateAccountState(userId, AccountState.CreateAddressSuccess)
    }

    override suspend fun handleCreateAddressFailed(userId: UserId) {
        accountRepository.updateAccountState(userId, AccountState.CreateAddressFailed)
    }

    override suspend fun handleUnlockFailed(userId: UserId) {
        accountRepository.updateAccountState(userId, AccountState.UnlockFailed)
        disableAccount(userId)
    }

    override suspend fun handleAccountReady(userId: UserId) {
        accountRepository.updateAccountState(userId, AccountState.Ready)
        clearSessionDetails(userId)
    }

    override suspend fun handleAccountNotReady(userId: UserId) {
        accountRepository.updateAccountState(userId, AccountState.NotReady)
    }

    override suspend fun handleAccountDisabled(userId: UserId) {
        disableAccount(userId)
    }

    // endregion
}
