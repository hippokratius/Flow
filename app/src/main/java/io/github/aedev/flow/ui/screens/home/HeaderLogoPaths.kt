/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.ui.screens.home

/*
 * Header logo, drawn on a Canvas in a 24x24 space rather than loaded from a drawable — which is why
 * replacing the launcher artwork did not reach it.
 *
 * The two shapes below deliberately match res/drawable/ic_launcher_foreground.xml, so the header and
 * the launcher icon carry the same mark. Neither is upstream Flow's: its plate was the YouTube
 * play-button silhouette and its glyph a stylised "F". Forking GPL code grants no rights to either
 * mark, and a plate shaped like the platform this app exists to move people away from was the more
 * awkward of the two.
 *
 * The colours stay theme-driven (primary / onPrimary) — they follow Material You, not a brand.
 */
internal const val HEADER_LOGO_PLATE_PATH =
    "M6,3 L18,3 A3,3 0 0 1 21,6 L21,18 A3,3 0 0 1 18,21 L6,21 A3,3 0 0 1 3,18 L3,6 A3,3 0 0 1 6,3 Z"
internal const val HEADER_LOGO_GLYPH_PATH = "M10,8 L16.5,12 L10,16 Z"

/** Replaces the glyph while Deep Flow is on. A generic Material mark, so it is kept as it was. */
internal const val HEADER_INCOGNITO_GLYPH_PATH = "M17.06 13C15.2 13 13.64 14.33 13.24 16.1C12.29 15.69 11.42 15.8 10.76 16.09C10.35 14.31 8.79 13 6.94 13C4.77 13 3 14.79 3 17C3 19.21 4.77 21 6.94 21C9 21 10.68 19.38 10.84 17.32C11.18 17.08 12.07 16.63 13.16 17.34C13.34 19.39 15 21 17.06 21C19.23 21 21 19.21 21 17C21 14.79 19.23 13 17.06 13M6.94 19.86C5.38 19.86 4.13 18.58 4.13 17S5.39 14.14 6.94 14.14C8.5 14.14 9.75 15.42 9.75 17S8.5 19.86 6.94 19.86M17.06 19.86C15.5 19.86 14.25 18.58 14.25 17S15.5 14.14 17.06 14.14C18.62 14.14 19.88 15.42 19.88 17S18.61 19.86 17.06 19.86M22 10.5H2V12H22V10.5M15.53 2.63C15.31 2.14 14.75 1.88 14.22 2.05L12 2.79L9.77 2.05L9.72 2.04C9.19 1.89 8.63 2.17 8.43 2.68L6 9H18L15.56 2.68L15.53 2.63Z"
