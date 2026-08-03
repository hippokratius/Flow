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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Reaches the [ContentSourceRegistry] from a class Hilt does not construct.
 *
 * Most call sites inject the registry directly. `SubscriptionsViewModel` cannot: it is a plain
 * `ViewModel` with an `initialize(context)` entry point that ~10 upstream call sites depend on, and
 * converting it to `@HiltViewModel` would be a far larger edit to an upstream file than reading one
 * singleton out of the graph.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ContentSourceEntryPoint {
    fun contentSourceRegistry(): ContentSourceRegistry
}

fun contentSourceRegistry(context: Context): ContentSourceRegistry =
    EntryPointAccessors
        .fromApplication(context.applicationContext, ContentSourceEntryPoint::class.java)
        .contentSourceRegistry()
