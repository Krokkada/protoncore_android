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

package me.proton.core.user.domain.usecase

import me.proton.core.network.domain.session.SessionId
import me.proton.core.user.domain.entity.UserVerificationTokenType
import me.proton.core.user.domain.entity.VerificationResult
import me.proton.core.user.domain.exception.EmptyDestinationException
import me.proton.core.user.domain.repository.UserRepository
import me.proton.core.user.domain.repository.UserValidationRepository
import javax.inject.Inject

/**
 * Use case for sending the verification code to any destination chosen by the user, based on the
 * verification type (method), which can be captcha, email, sms etc..
 *
 * Depends on the [UserRepository] for contacting the API.
 *
 * @param userValidationRepository the remote repository interface implementation.
 *
 * @author Dino Kadrikj.
 */
class SendVerificationCodeToEmailDestination @Inject constructor(
    private val userValidationRepository: UserValidationRepository
) {

    /**
     * Sends the verification code (token) to the API.
     *
     * @param emailAddress email destination, if the token type (verification type/method) is EMAIL,
     * this value should be populated.
     *
     * @throws EmptyDestinationException if the destination email address is empty
     */
    suspend operator fun invoke(sessionId: SessionId? = null, emailAddress: String): VerificationResult {
        if (emailAddress.isEmpty()) {
            throw EmptyDestinationException("Provide valid email destination.")
        }
        return userValidationRepository.sendVerificationCodeEmailAddress(
            sessionId = sessionId,
            emailAddress = emailAddress,
            type = UserVerificationTokenType.EMAIL
        )
    }
}