/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.R
import io.github.aedev.flow.data.source.peertube.PeerTubeInstance
import io.github.aedev.flow.data.source.peertube.PeerTubeInstances
import io.github.aedev.flow.data.source.peertube.PeerTubePreferences
import io.github.aedev.flow.fediverse.FediverseAccount
import io.github.aedev.flow.fediverse.FediverseAccountStore
import io.github.aedev.flow.fediverse.MiAuth
import io.github.aedev.flow.fediverse.MiAuthEventBus
import io.github.aedev.flow.fediverse.MiAuthResult
import io.github.aedev.flow.fediverse.PendingMiAuth
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PeerTubeInstancesViewModel @Inject constructor(
    private val preferences: PeerTubePreferences,
    private val accountStore: FediverseAccountStore,
    private val miAuthEvents: MiAuthEventBus,
) : ViewModel() {

    val instances: StateFlow<List<PeerTubeInstance>> = preferences.instances
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val accounts: StateFlow<List<FediverseAccount>> = accountStore.accounts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val miAuthResults = miAuthEvents.events

    fun add(name: String, url: String) {
        viewModelScope.launch { preferences.addInstance(name, url) }
    }

    fun remove(id: String) {
        viewModelScope.launch { preferences.removeInstance(id) }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { preferences.setInstanceEnabled(id, enabled) }
    }

    /**
     * Starts a MiAuth sign-in and returns the URL to open in a browser.
     *
     * The pending session is written to disk *before* the browser is opened, because the user
     * leaves the app entirely at that point and the process may not survive until they return.
     */
    fun startMiAuth(rawInstanceUrl: String, onReady: (String) -> Unit) {
        val url = PeerTubeInstances.normalizeUrl(rawInstanceUrl) ?: return
        viewModelScope.launch {
            val uuid = UUID.randomUUID().toString()
            accountStore.setPendingMiAuth(PendingMiAuth(uuid = uuid, instanceUrl = url))
            onReady(MiAuth.authorizationUrl(url, uuid))
        }
    }

    fun disconnect(id: String) {
        viewModelScope.launch { accountStore.removeAccount(id) }
    }
}

@Composable
fun PeerTubeInstancesScreen(
    onNavigateBack: () -> Unit,
    viewModel: PeerTubeInstancesViewModel = hiltViewModel(),
) {
    val instances by viewModel.instances.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val context = LocalContext.current

    var newUrl by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var misskeyUrl by remember { mutableStateOf("") }
    var feedbackRes by remember { mutableStateOf<Int?>(null) }

    // The sign-in result arrives from MainActivity via a shared flow, because this screen may have
    // been destroyed and recreated while the user was away in the browser.
    LaunchedEffect(Unit) {
        viewModel.miAuthResults.collect { result ->
            feedbackRes = when (result) {
                is MiAuthResult.Success -> null
                is MiAuthResult.Failure -> result.messageRes
            }
            if (result is MiAuthResult.Success) misskeyUrl = ""
        }
    }

    fun submit() {
        if (newUrl.isBlank()) return
        viewModel.add(newName, newUrl)
        newUrl = ""
        newName = ""
    }

    fun connect() {
        if (misskeyUrl.isBlank()) return
        viewModel.startMiAuth(misskeyUrl) { authUrl ->
            val opened = runCatching {
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(authUrl))
            }.isSuccess
            if (!opened) feedbackRes = R.string.fediverse_error_no_browser
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
                    }
                    Text(
                        text = stringResource(R.string.peertube_instances_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.peertube_instances_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = newUrl,
                            onValueChange = { newUrl = it },
                            label = { Text(stringResource(R.string.peertube_instance_url_label)) },
                            placeholder = { Text(stringResource(R.string.peertube_instance_url_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next
                            )
                        )
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text(stringResource(R.string.peertube_instance_name_label)) },
                            supportingText = {
                                Text(stringResource(R.string.peertube_instance_name_hint))
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { submit() },
                                    enabled = newUrl.isNotBlank()
                                ) {
                                    Icon(
                                        Icons.Default.AddCircle,
                                        contentDescription = stringResource(R.string.peertube_instance_add)
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() })
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.fediverse_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                Text(
                    text = stringResource(R.string.fediverse_section_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (accounts.isEmpty()) {
                            OutlinedTextField(
                                value = misskeyUrl,
                                onValueChange = { misskeyUrl = it },
                                label = { Text(stringResource(R.string.fediverse_instance_label)) },
                                placeholder = {
                                    Text(stringResource(R.string.fediverse_instance_placeholder))
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { connect() })
                            )
                            Button(
                                onClick = { connect() },
                                enabled = misskeyUrl.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.fediverse_connect))
                            }
                        } else {
                            accounts.forEach { account ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.fediverse_connected_as,
                                            account.handle
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { viewModel.disconnect(account.id) }) {
                                        Text(stringResource(R.string.fediverse_disconnect))
                                    }
                                }
                            }
                        }

                        feedbackRes?.let { messageRes ->
                            Text(
                                text = stringResource(messageRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            if (instances.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.peertube_instances_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    SettingsGroup {
                        Column {
                            instances.forEachIndexed { index, instance ->
                                PeerTubeInstanceRow(
                                    instance = instance,
                                    onToggle = { viewModel.setEnabled(instance.id, it) },
                                    onRemove = { viewModel.remove(instance.id) }
                                )
                                if (index != instances.lastIndex) {
                                    HorizontalDivider(
                                        Modifier.padding(start = 16.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerTubeInstanceRow(
    instance: PeerTubeInstance,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = instance.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = instance.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = instance.enabled, onCheckedChange = onToggle)
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.peertube_instance_remove),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
