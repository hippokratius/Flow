/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.di

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.data.source.ContentSource
import io.github.aedev.flow.data.source.ContentSourceRegistry
import io.github.aedev.flow.data.source.youtube.YouTubeContentSource

/**
 * Content sources, bound into the map [ContentSourceRegistry] consumes.
 *
 * A separate module rather than an addition to [RepositoryModule], so that adding a source never
 * touches a file upstream also edits.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ContentSourceModule {

    @Binds
    @IntoMap
    @StringKey(ContentSourceRegistry.KEY_YOUTUBE)
    abstract fun bindYouTubeSource(impl: YouTubeContentSource): ContentSource
}
