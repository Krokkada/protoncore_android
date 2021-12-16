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

package me.proton.core.reports.data.repository

import me.proton.core.domain.entity.Product
import me.proton.core.domain.entity.UserId
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.isSuccess
import me.proton.core.network.domain.onSuccess
import me.proton.core.reports.data.api.ReportsApi
import me.proton.core.reports.data.api.request.BugReportRequest
import me.proton.core.reports.domain.entity.BugReport
import me.proton.core.reports.domain.entity.BugReportExtra
import me.proton.core.reports.domain.entity.BugReportMeta
import me.proton.core.reports.domain.repository.ReportsRepository
import javax.inject.Inject

public class ReportsRepositoryImpl @Inject constructor(private val apiProvider: ApiProvider) : ReportsRepository {
    override suspend fun sendReport(
        userId: UserId,
        bugReport: BugReport,
        meta: BugReportMeta,
        extra: BugReportExtra?
    ) {
        val clientType = when (meta.product) {
            Product.Mail -> 1
            Product.Vpn -> 2
            Product.Calendar -> 3
            Product.Drive -> 4
        }

        val request = BugReportRequest(
            osName = meta.osName,
            osVersion = meta.osVersion,
            client = meta.clientName,
            clientType = clientType,
            appVersionName = meta.appVersionName,
            title = bugReport.title,
            description = bugReport.description,
            username = bugReport.username,
            email = bugReport.email,
            country = extra?.country,
            isp = extra?.isp
        )

        apiProvider.get<ReportsApi>(userId)
            .invoke { sendBugReport(request) }
            .onSuccess { check(it.isSuccess()) }
            .valueOrThrow
    }
}