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

package me.proton.core.accountmanager.domain.entity

import me.proton.core.accountmanager.domain.AccountManager

enum class AccountState {
    /**
     * First state emitted after adding a new [Account], it is not yet [Ready] to use.
     *
     * Note: Usually followed by [Initializing] and/or [Ready].
     */
    Added,

    /**
     * Intermediate state emitted just after [Added] if this [Account] need more step(s) to be [Ready] to use.
     */
    Initializing,

    /**
     * A two pass mode is needed.
     *
     * Note: Another [Account] could be progressing in a workflow.
     *
     * @see [TwoPassModeStarting]
     * @see [TwoPassModeInProgress].
     */
    TwoPassModeNeeded,

    /**
     * The two pass mode is starting.
     *
     * Client should call [startTwoPassModeWorkflow] asap.
     */
    TwoPassModeStarting,

    /**
     * The two pass mode is in progress.
     *
     * Note: Always followed by either [TwoPassModeSuccess] or [TwoPassModeFailed].
     */
    TwoPassModeInProgress,

    /**
     * The two pass mode has been successful.
     *
     * Note: Usually followed by [Ready].
     */
    TwoPassModeSuccess,

    /**
     * The two pass mode has failed.
     *
     * Client should consider calling [startTwoPassModeWorkflow] in conjunction with [hasWorkflowProgressing].
     */
    TwoPassModeFailed,

    /**
     * The [Account] is ready to use and contains a valid [Session].
     */
    Ready,

    /**
     * The [Account] has been disabled and do not contains valid [Session].
     */
    Disabled,

    /**
     * The [Account] has been removed from [AccountManager].
     *
     * Note: Usually used by Client to clean up [Account] related resources.
     */
    Removed
}