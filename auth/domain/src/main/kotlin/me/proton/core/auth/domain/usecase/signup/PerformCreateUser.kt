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

package me.proton.core.auth.domain.usecase.signup

import me.proton.core.account.domain.entity.CreateUserType
import me.proton.core.auth.domain.repository.AuthRepository
import me.proton.core.crypto.common.keystore.EncryptedString
import me.proton.core.crypto.common.keystore.KeyStoreCrypto
import me.proton.core.crypto.common.keystore.decryptWith
import me.proton.core.crypto.common.keystore.use
import me.proton.core.crypto.common.srp.SrpCrypto
import me.proton.core.user.domain.entity.User
import me.proton.core.user.domain.repository.UserRepository
import javax.inject.Inject

class PerformCreateUser @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val srpCrypto: SrpCrypto,
    private val keyStoreCrypto: KeyStoreCrypto
) {

    suspend operator fun invoke(
        username: String,
        password: EncryptedString,
        recoveryEmail: String?,
        recoveryPhone: String?,
        referrer: String?,
        type: CreateUserType,
    ): User {
        require(
            (recoveryEmail == null && recoveryPhone == null) ||
                (recoveryEmail == null && recoveryPhone != null) ||
                (recoveryEmail != null && recoveryPhone == null)
        ) { "Recovery Email and Phone could not be set together" }
        val modulus = authRepository.randomModulus()

        password.decryptWith(keyStoreCrypto).toByteArray().use { decryptedPassword ->
            val auth = srpCrypto.calculatePasswordVerifier(
                username = username,
                password = decryptedPassword.array,
                modulusId = modulus.modulusId,
                modulus = modulus.modulus
            )
            return userRepository.createUser(
                username = username,
                password = password,
                recoveryEmail = recoveryEmail,
                recoveryPhone = recoveryPhone,
                referrer = referrer,
                type = type,
                auth = auth
            )
        }
    }
}