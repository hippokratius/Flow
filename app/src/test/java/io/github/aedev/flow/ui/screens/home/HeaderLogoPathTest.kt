package io.github.aedev.flow.ui.screens.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Regression guard for a defect that shipped: the home screen header kept showing upstream Flow's
 * mark — a red YouTube-play-button-shaped plate with a white "F" — long after the launcher artwork
 * had been replaced. It was missed because the header logo is not a drawable at all; it is drawn on a
 * Compose Canvas from the path strings in HeaderLogoPaths.kt, so a sweep of `res/drawable/` could
 * never have found it.
 *
 * Two things are pinned here. First, the upstream geometry is gone. Second, the header and the
 * launcher icon are held to the *same* shapes, which is what stops them drifting apart again — the
 * launcher is the obvious place to look, and it was the one that got fixed.
 */
class HeaderLogoPathTest {

    /** Start of upstream's plate path. It is the YouTube play-button silhouette. */
    private val upstreamPlateSignature = "21.58 7.16"

    /** Start of upstream's glyph path, a stylised "F". */
    private val upstreamGlyphSignature = "M10 7L18 7"

    @Test
    fun `the upstream plate and glyph are gone`() {
        assertThat(HEADER_LOGO_PLATE_PATH).doesNotContain(upstreamPlateSignature)
        assertThat(HEADER_LOGO_PLATE_PATH).doesNotContain(upstreamGlyphSignature)
        assertThat(HEADER_LOGO_GLYPH_PATH).doesNotContain(upstreamPlateSignature)
        assertThat(HEADER_LOGO_GLYPH_PATH).doesNotContain(upstreamGlyphSignature)
    }

    @Test
    fun `the paths are closed shapes`() {
        listOf(HEADER_LOGO_PLATE_PATH, HEADER_LOGO_GLYPH_PATH, HEADER_INCOGNITO_GLYPH_PATH)
            .forEach { path ->
                assertThat(path).isNotEmpty()
                assertThat(path.trimStart()).startsWith("M")
                assertThat(path.trimEnd().uppercase()).endsWith("Z")
            }
    }

    @Test
    fun `header and launcher icon carry the same mark`() {
        val launcher = readResource("drawable/ic_launcher_foreground.xml")

        assertThat(launcher).contains(HEADER_LOGO_PLATE_PATH)
        assertThat(launcher).contains(HEADER_LOGO_GLYPH_PATH)
    }

    /** The TV banner is the third surface that renders the mark, and the easiest one to forget. */
    @Test
    fun `the tv banner carries the same mark`() {
        val banner = readResource("drawable-xhdpi/tv_banner.xml")

        assertThat(banner).contains(HEADER_LOGO_PLATE_PATH)
        assertThat(banner).contains(HEADER_LOGO_GLYPH_PATH)
    }

    /** Unit tests run from the module directory, but tolerate being run from the repository root. */
    private fun readResource(relativePath: String): String {
        val candidates = listOf(
            File("src/main/res/$relativePath"),
            File("app/src/main/res/$relativePath"),
        )
        val file = candidates.firstOrNull(File::exists)
        assertThat(file).isNotNull()
        return file!!.readText()
    }
}
