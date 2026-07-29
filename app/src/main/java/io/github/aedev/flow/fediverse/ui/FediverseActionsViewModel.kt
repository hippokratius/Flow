/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.fediverse.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.R
import io.github.aedev.flow.fediverse.FediverseAccount
import io.github.aedev.flow.fediverse.FediverseAccountStore
import io.github.aedev.flow.fediverse.FediverseException
import io.github.aedev.flow.fediverse.FediverseRepository
import io.github.aedev.flow.fediverse.VideoStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ActionState { IDLE, LOADING, DONE, ERROR }

data class FediverseActionsUiState(
    val account: FediverseAccount? = null,
    val likeState: ActionState = ActionState.IDLE,
    val boostState: ActionState = ActionState.IDLE,
    val commentState: ActionState = ActionState.IDLE,
    val stats: VideoStats? = null,
    val errorRes: Int? = null,
) {
    /** Nothing to offer without a connected account. */
    val isConnected: Boolean get() = account != null
}

@HiltViewModel
class FediverseActionsViewModel @Inject constructor(
    private val repository: FediverseRepository,
    private val store: FediverseAccountStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FediverseActionsUiState())
    val uiState: StateFlow<FediverseActionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            store.accounts.collect { accounts ->
                _uiState.update { it.copy(account = accounts.firstOrNull()) }
            }
        }
    }

    /** Loads reaction and reply counts. Failures stay silent — stats are decoration. */
    fun loadStats(apUrl: String) {
        val account = _uiState.value.account ?: return
        viewModelScope.launch {
            runCatching { repository.stats(account, apUrl) }
                .onSuccess { stats -> _uiState.update { it.copy(stats = stats) } }
        }
    }

    fun like(apUrl: String) = perform(apUrl, { copy(likeState = it) }) { account ->
        repository.like(account, apUrl)
    }

    fun boost(apUrl: String) = perform(apUrl, { copy(boostState = it) }) { account ->
        repository.boost(account, apUrl)
    }

    fun comment(apUrl: String, text: String) {
        if (text.isBlank()) return
        perform(apUrl, { copy(commentState = it) }) { account ->
            repository.comment(account, apUrl, text)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorRes = null) }
    }

    private fun perform(
        apUrl: String,
        setState: FediverseActionsUiState.(ActionState) -> FediverseActionsUiState,
        block: suspend (FediverseAccount) -> Unit,
    ) {
        val account = _uiState.value.account ?: return
        viewModelScope.launch {
            _uiState.update { it.setState(ActionState.LOADING) }
            runCatching { block(account) }
                .onSuccess {
                    _uiState.update { it.setState(ActionState.DONE) }
                    // Reflect the new count without the user having to reopen the video.
                    runCatching { repository.stats(account, apUrl) }
                        .onSuccess { stats -> _uiState.update { it.copy(stats = stats) } }
                }
                .onFailure { failure ->
                    val messageRes = (failure as? FediverseException)?.messageRes
                        ?: R.string.fediverse_error_generic
                    _uiState.update { it.setState(ActionState.ERROR).copy(errorRes = messageRes) }
                }
        }
    }
}
