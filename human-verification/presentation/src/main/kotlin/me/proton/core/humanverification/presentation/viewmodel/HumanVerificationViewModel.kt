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

package me.proton.core.humanverification.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import me.proton.core.account.domain.repository.AccountRepository
import me.proton.core.domain.entity.Product
import me.proton.core.humanverification.domain.HumanVerificationWorkflowHandler
import me.proton.core.humanverification.presentation.entity.HumanVerificationToken
import me.proton.core.network.domain.NetworkPrefs
import me.proton.core.network.domain.client.ClientId
import me.proton.core.network.domain.humanverification.HumanVerificationListener
import me.proton.core.presentation.viewmodel.ProtonViewModel
import me.proton.core.usersettings.domain.usecase.GetSettings
import javax.inject.Inject

/**
 * View model class to serve the main Human Verification screen.
 */
@HiltViewModel
class HumanVerificationViewModel @Inject constructor(
    private val humanVerificationWorkflowHandler: HumanVerificationWorkflowHandler,
    private val humanVerificationListener: HumanVerificationListener,
    private val accountRepository: AccountRepository,
    private val getSettings: GetSettings,
    private val networkPrefs: NetworkPrefs,
    private val product: Product,
) : ProtonViewModel() {

    private val backgroundContext = Dispatchers.IO + viewModelScope.coroutineContext

    val activeAltUrlForDoH: String? get() = networkPrefs.activeAltBaseUrl?.let {
        if (!it.endsWith("/")) "$it/" else it
    }

    suspend fun getHumanVerificationExtraParams() = withContext(backgroundContext) {
        val userId = accountRepository.getPrimaryUserId()
            .firstOrNull()

        val settings = userId?.let { getSettings(it) }
        val defaultCountry = settings?.locale?.substringAfter("_")
        HumanVerificationExtraParams(
            settings?.phone?.value,
            settings?.locale,
            defaultCountry,
            product == Product.Vpn
        )
    }

    suspend fun onHumanVerificationResult(
        clientId: ClientId,
        token: HumanVerificationToken?
    ): HumanVerificationListener.HumanVerificationResult = withContext(backgroundContext) {
        if (token != null) {
            humanVerificationWorkflowHandler.handleHumanVerificationSuccess(
                clientId = clientId,
                tokenType = token.type,
                tokenCode = token.code
            )
        } else {
            humanVerificationWorkflowHandler.handleHumanVerificationFailed(clientId = clientId)
        }
        humanVerificationListener.awaitHumanVerificationProcessFinished(clientId)
    }
}

data class HumanVerificationExtraParams(
    val recoveryPhone: String?,
    val locale: String?,
    val defaultCountry: String?,
    val useVPNTheme: Boolean,
)
