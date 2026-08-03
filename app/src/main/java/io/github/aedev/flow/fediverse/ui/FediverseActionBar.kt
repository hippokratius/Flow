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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R

/**
 * Like, boost and comment on a federated video through the user's connected Fediverse account.
 *
 * A sibling of `VideoInfoSection` rather than a set of extra parameters on it: that composable
 * already takes 25 arguments, and adding four more callbacks would both worsen the interface and
 * create permanent merge surface in a file upstream keeps editing.
 *
 * Renders nothing at all when no account is connected — an empty row of disabled buttons would
 * only invite taps that cannot work.
 */
@Composable
fun FediverseActionBar(
    apUrl: String,
    modifier: Modifier = Modifier,
    viewModel: FediverseActionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (!state.isConnected) return

    LaunchedEffect(apUrl, state.account?.id) { viewModel.loadStats(apUrl) }

    var commentText by remember(apUrl) { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionChip(
                label = stringResource(R.string.fediverse_action_like),
                count = state.stats?.reactions?.values?.sum(),
                state = state.likeState,
                onClick = { viewModel.like(apUrl) },
            ) {
                Icon(
                    Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            }
            ActionChip(
                label = stringResource(R.string.fediverse_action_boost),
                count = state.stats?.renoteCount,
                state = state.boostState,
                onClick = { viewModel.boost(apUrl) },
            ) {
                Icon(
                    Icons.Outlined.Repeat,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            }
        }

        OutlinedTextField(
            value = commentText,
            onValueChange = { commentText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.fediverse_comment_label)) },
            singleLine = true,
            enabled = state.commentState != ActionState.LOADING,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                viewModel.comment(apUrl, commentText)
                commentText = ""
            }),
            trailingIcon = {
                IconButton(
                    onClick = {
                        viewModel.comment(apUrl, commentText)
                        commentText = ""
                    },
                    enabled = commentText.isNotBlank() &&
                        state.commentState != ActionState.LOADING,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = stringResource(R.string.fediverse_action_comment),
                    )
                }
            },
        )

        state.errorRes?.let { messageRes ->
            Text(
                text = stringResource(messageRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        state.account?.let { account ->
            Text(
                text = stringResource(R.string.fediverse_acting_as, account.handle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    count: Int?,
    state: ActionState,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        enabled = state != ActionState.LOADING,
        label = {
            Text(
                if (count != null && count > 0) {
                    stringResource(R.string.fediverse_action_with_count, label, count)
                } else {
                    label
                }
            )
        },
        leadingIcon = {
            if (state == ActionState.LOADING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                    strokeWidth = 2.dp,
                )
            } else {
                icon()
            }
        },
    )
}
