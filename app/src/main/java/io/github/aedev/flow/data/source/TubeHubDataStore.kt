/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.data.source

import android.content.Context
import io.github.aedev.flow.data.local.safePreferencesDataStore

/**
 * The fork's own preference store, holding PeerTube instances and Fediverse accounts.
 *
 * Declared exactly once and shared by every reader: DataStore throws at runtime if two delegates
 * are created for the same file, so this must not be duplicated per feature. It is kept out of
 * `PlayerPreferences` on purpose — that file is 2600 lines and the most upstream-churned in the
 * app, so appending to it would cost a merge conflict on every sync with Flow.
 *
 * Excluded from Android Auto Backup (see `res/xml/backup_rules.xml`) because it holds a Fediverse
 * write token.
 */
internal val Context.tubeHubPreferencesDataStore by safePreferencesDataStore(
    name = "tubehub_preferences"
)
