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

package me.proton.core.test.android.uitests.tests.medium.auth.login

import me.proton.core.account.domain.entity.AccountState
import me.proton.core.test.android.robots.auth.AddAccountRobot
import me.proton.core.test.android.robots.auth.login.LoginRobot
import me.proton.core.test.android.robots.auth.login.TwoFaRobot
import me.proton.core.test.android.uitests.robot.CoreexampleRobot
import me.proton.core.test.android.uitests.tests.BaseTest
import org.junit.Before
import org.junit.Test

class TwoFaTests : BaseTest() {

    private val invalidCode = "123456"
    private val twoFaRobot = TwoFaRobot()
    private val incorrectCredMessage = "Incorrect login credentials. Please try again"
    private val user = users.getUser(false) { it.twoFa.isNotEmpty() }

    @Before
    fun goToTwoFa() {
        AddAccountRobot()
            .signIn()
            .loginUser<TwoFaRobot>(user)
        quark.jailUnban()
    }

    @Test
    fun invalidTwoFaCode() {
        twoFaRobot
            .setSecondFactorInput(invalidCode)
            .authenticate<LoginRobot>()
            .verify { errorSnackbarDisplayed(incorrectCredMessage) }
    }

    @Test
    fun invalidVerificationCode() {
        twoFaRobot
            .switchTwoFactorMode()
            .setSecondFactorInput(invalidCode)
            .authenticate<TwoFaRobot>()
            .verify { errorSnackbarDisplayed(incorrectCredMessage) }
    }

    @Test
    fun backToLogin() {
        twoFaRobot
            .apply { verify { formElementsDisplayed() } }
            .back<LoginRobot>()
            .verify { loginElementsDisplayed() }
    }

    @Test
    fun revokeSession() {
        twoFaRobot
            .setSecondFactorInput(invalidCode)
            .apply { quark.expireSession(username = user.name, expireRefreshToken = true) }
            .authenticate<CoreexampleRobot>()
            .verify {
                errorSnackbarDisplayed(incorrectCredMessage)
                userStateIs(user, AccountState.Disabled, null)
            }
    }
}
