/*
 * Copyright (c) 2021 Proton Technologies AG
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

package me.proton.core.accountmanager.data.job

import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import me.proton.core.account.domain.entity.AccountState
import me.proton.core.accountmanager.data.AccountStateHandler
import me.proton.core.accountmanager.domain.getAccounts
import me.proton.core.domain.arch.mapSuccessValueOrNull
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.extension.areAllInactive
import me.proton.core.user.domain.extension.hasMigratedKey

fun AccountStateHandler.onInvalidUserKey(
    block: suspend (UserId) -> Unit
) = accountManager.getAccounts(AccountState.Ready)
    .flatMapLatest { it.map { account -> userManager.getUserFlow(account.userId) }.merge() }
    .mapSuccessValueOrNull().filterNotNull()
    .onEach { user ->
        if (user.keys.areAllInactive()) return@onEach
        val addresses = userManager.getAddresses(user.userId)
        val hasMigratedKey = addresses.hasMigratedKey()
        val hasInvalidKeys = user.keys.any { key -> key.active == true && !key.privateKey.isActive }
        if (hasMigratedKey && hasInvalidKeys) {
            block(user.userId)
        }
    }
    .launchIn(scope)

fun AccountStateHandler.onInvalidUserAddressKey(
    block: suspend (UserId) -> Unit
) = accountManager.getAccounts(AccountState.Ready)
    .flatMapLatest { it.map { account -> userManager.getAddressesFlow(account.userId) }.merge() }
    .mapSuccessValueOrNull().filterNotNull()
    .onEach { addresses ->
        val hasMigratedKey = addresses.hasMigratedKey()
        val userId = addresses.firstOrNull()?.userId
        val keys = addresses.flatMap { address -> address.keys }
        if (keys.areAllInactive()) return@onEach
        val hasInvalidKeys = keys.any { key -> key.active && !key.privateKey.isActive }
        if (hasMigratedKey && hasInvalidKeys) {
            block(requireNotNull(userId))
        }
    }
    .launchIn(scope)
