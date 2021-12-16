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

package me.proton.core.reports.data

import androidx.lifecycle.asFlow
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import me.proton.core.domain.entity.UserId
import me.proton.core.reports.data.work.BugReportWorker
import me.proton.core.reports.domain.entity.BugReport
import me.proton.core.reports.domain.entity.BugReportExtra
import me.proton.core.reports.domain.entity.BugReportMeta
import me.proton.core.reports.domain.usecase.SendBugReport
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider

public class SendBugReportImpl @Inject constructor(
    private val bugReportMetaProvider: Provider<BugReportMeta>,
    private val workManager: WorkManager
) : SendBugReport {
    override suspend fun cancel(requestId: String) {
        workManager.cancelWorkById(UUID.fromString(requestId)).await()
    }

    override fun invoke(
        userId: UserId,
        bugReport: BugReport,
        extra: BugReportExtra?
    ): Flow<SendBugReport.Result> {
        val request = BugReportWorker.makeWorkerRequest(userId, bugReport, bugReportMetaProvider.get(), extra)
        workManager.enqueue(request)
        return flow {
            emit(SendBugReport.Result.Initialized(request.id.toString()))
            emitAll(makeWorkInfoFlow(request))
        }
    }

    private fun makeWorkInfoFlow(request: WorkRequest): Flow<SendBugReport.Result> {
        val requestId = request.id.toString()
        return workManager.getWorkInfoByIdLiveData(request.id)
            .asFlow()
            .mapNotNull { workInfo ->
                when (workInfo.state) {
                    WorkInfo.State.ENQUEUED -> SendBugReport.Result.Enqueued(requestId)
                    WorkInfo.State.BLOCKED -> SendBugReport.Result.Blocked(requestId)
                    WorkInfo.State.CANCELLED -> SendBugReport.Result.Cancelled(requestId)
                    WorkInfo.State.SUCCEEDED -> SendBugReport.Result.Sent(requestId)
                    WorkInfo.State.FAILED -> {
                        val errorMessage = workInfo.outputData.getString(BugReportWorker.OUTPUT_ERROR_MESSAGE)
                        SendBugReport.Result.Failed(requestId, errorMessage)
                    }
                    else -> null
                }
            }
    }
}