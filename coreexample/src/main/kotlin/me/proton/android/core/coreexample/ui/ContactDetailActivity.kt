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

package me.proton.android.core.coreexample.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.proton.android.core.coreexample.R
import me.proton.android.core.coreexample.databinding.ActivityContactDetailsBinding
import me.proton.android.core.coreexample.viewmodel.ContactDetailViewModel
import me.proton.android.core.coreexample.viewmodel.ContactDetailViewModel.Companion.ARG_CONTACT_ID
import me.proton.core.contact.domain.entity.ContactId
import me.proton.core.presentation.ui.ProtonActivity
import me.proton.core.presentation.utils.showToast
import me.proton.core.util.kotlin.exhaustive

@AndroidEntryPoint
class ContactDetailActivity : ProtonActivity<ActivityContactDetailsBinding>() {
    override fun layoutId(): Int = R.layout.activity_contact_details

    private val viewModel: ContactDetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.updateButton.setOnClickListener {
            viewModel.updateContact()
        }
        binding.deleteButton.setOnClickListener {
            viewModel.deleteContact()
        }
        viewModel.viewState.flowWithLifecycle(lifecycle, minActiveState = Lifecycle.State.STARTED).onEach { viewState ->
            binding.rawContact.text = viewState.rawContact
            binding.contactVCards.text = viewState.vCardContact
        }.launchIn(lifecycleScope)
        viewModel.loadingState
            .flowWithLifecycle(lifecycle, minActiveState = Lifecycle.State.STARTED)
            .onEach { loadingState ->
                binding.progress.isVisible = loadingState
                binding.deleteButton.isClickable = !loadingState
        }.launchIn(lifecycleScope)
        viewModel.viewEvent.flowWithLifecycle(lifecycle, minActiveState = Lifecycle.State.STARTED).onEach { viewEvent ->
            when (viewEvent) {
                is ContactDetailViewModel.ViewEvent.Error -> showToast(viewEvent.reason)
                ContactDetailViewModel.ViewEvent.Success -> {
                    showToast("success")
                    finish()
                }
            }.exhaustive
        }.launchIn(lifecycleScope)
    }

    companion object {
        fun createIntent(context: Context, contactId: ContactId) =
            Intent(context, ContactDetailActivity::class.java).apply {
                putExtra(ARG_CONTACT_ID, contactId.id)
            }
    }
}
