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

package me.proton.core.reports.presentation.viewmodel

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.yield
import me.proton.core.reports.domain.entity.BugReportValidationError
import me.proton.core.reports.domain.usecase.SendBugReport
import me.proton.core.reports.presentation.entity.BugReportFormState
import me.proton.core.reports.presentation.entity.ExitSignal
import me.proton.core.reports.presentation.entity.ReportFormData
import me.proton.core.test.kotlin.CoroutinesTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class BugReportViewModelTest : CoroutinesTest {
    private lateinit var tested: BugReportViewModel
    private lateinit var sendBugReport: SendBugReport

    private val testReportFormData = ReportFormData(
        subject = "Subject",
        description = "Description",
        country = "Switzerland",
        isp = "InternetServiceProvider"
    )
    private val testEmail = "email@test"
    private val testUsername = "testUsername"

    @Before
    fun setUp() {
        sendBugReport = mockk()
        tested = BugReportViewModel(sendBugReport)
    }

    @Test
    fun `send bug report successfully`() = runBlockingTest {
        val requestId = "123"
        val expectedResults = mockBugReportResults(
            SendBugReport.Result.Initialized(requestId),
            SendBugReport.Result.Enqueued(requestId),
            SendBugReport.Result.Blocked(requestId),
            SendBugReport.Result.Enqueued(requestId),
            SendBugReport.Result.Sent(requestId)
        )

        val formStateJob = launch {
            tested.bugReportFormState.test {
                assertIs<BugReportFormState.Idle>(awaitItem())
                assertIs<BugReportFormState.Processing>(awaitItem())
                expectedResults.forEach { expected ->
                    assertEquals(expected, assertIs<BugReportFormState.SendingResult>(awaitItem()).result)
                }
                expectNoEvents()
                cancel()
            }
        }
        tested.trySendingBugReport(testReportFormData, email = testEmail, username = testUsername)
        formStateJob.join()
    }

    @Test
    fun `sending bug report failed`() = runBlockingTest {
        val requestId = "123"
        val expectedResults = mockBugReportResults(
            SendBugReport.Result.Initialized(requestId),
            SendBugReport.Result.Enqueued(requestId),
            SendBugReport.Result.Failed(requestId, "Failed")
        )

        val formStateJob = launch {
            tested.bugReportFormState.test {
                assertIs<BugReportFormState.Idle>(awaitItem())
                assertIs<BugReportFormState.Processing>(awaitItem())
                expectedResults.forEach { expected ->
                    assertEquals(expected, assertIs<BugReportFormState.SendingResult>(awaitItem()).result)
                }
                expectNoEvents()
                cancel()
            }
        }
        tested.trySendingBugReport(
            testReportFormData.copy(country = null, isp = null),
            email = testEmail,
            username = testUsername
        )
        formStateJob.join()
    }

    @Test
    fun `invalid form data`() = runBlockingTest {
        val formStateJob = launch {
            tested.bugReportFormState.test {
                assertIs<BugReportFormState.Idle>(awaitItem())
                assertIs<BugReportFormState.Processing>(awaitItem())
                val formError = assertIs<BugReportFormState.FormError>(awaitItem())
                assertContentEquals(
                    listOf(BugReportValidationError.DescriptionMissing),
                    formError.errors
                )
                expectNoEvents()
                cancel()
            }
        }
        val invalidFormData = testReportFormData.copy(description = "")
        tested.trySendingBugReport(invalidFormData, email = testEmail, username = testUsername)
        formStateJob.join()
    }

    @Test
    fun `exit with form data`() = runBlockingTest {
        val exitSignalJob = launch {
            tested.exitSignal.test {
                assertEquals(ExitSignal.ExitNow, awaitItem())
            }
        }
        tested.tryExit()
        exitSignalJob.join()
    }

    @Test
    fun `exit with empty form data`() = runBlockingTest {
        val exitSignalJob = launch {
            tested.exitSignal.test {
                assertEquals(ExitSignal.ExitNow, awaitItem())
            }
        }
        tested.tryExit(ReportFormData("", ""))
        exitSignalJob.join()
    }

    @Test
    fun `exit with non-empty form data`() = runBlockingTest {
        val exitSignalJob = launch {
            tested.exitSignal.test {
                assertEquals(ExitSignal.Ask, awaitItem())
            }
        }
        tested.tryExit(ReportFormData("Subject", ""))
        exitSignalJob.join()
    }

    @Test
    fun `force exit with non-empty form data`() = runBlockingTest {
        val exitSignalJob = launch {
            tested.exitSignal.test {
                assertEquals(ExitSignal.ExitNow, awaitItem())
            }
        }
        tested.tryExit(ReportFormData("", "Description"), force = true)
        exitSignalJob.join()
    }

    private fun mockBugReportResults(vararg results: SendBugReport.Result): List<SendBugReport.Result> {
        every { sendBugReport.invoke(any(), any()) } returns flow {
            results.forEach { emit(it) }
            yield()
        }
        return results.toList()
    }
}
