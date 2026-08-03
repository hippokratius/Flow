package io.github.aedev.flow.data.source.peertube

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.schabi.newpipe.extractor.stream.DeliveryMethod

/**
 * The single most load-bearing mapping in the PeerTube path.
 *
 * `VideoPlaybackResolver` dispatches on `VideoStream.deliveryMethod`; only `HLS` reaches
 * `createHlsSource`. If an `.m3u8` were ever tagged `PROGRESSIVE_HTTP` it would be handed to the
 * progressive source builder, which produces a black screen and no error message at all — a
 * failure mode that is very hard to trace back here. Hence a test on the exact contract.
 */
class PeerTubeStreamMappingTest {

    @Test
    fun `an HLS playlist is tagged as HLS`() {
        val stream = buildPeerTubeVideoStream(
            streamingPlaylists = listOf(
                PTStreamingPlaylistDto(playlistUrl = "https://tilvids.com/static/streaming-playlists/hls/x/master.m3u8")
            ),
            files = emptyList()
        )

        assertThat(stream).isNotNull()
        assertThat(stream!!.deliveryMethod).isEqualTo(DeliveryMethod.HLS)
        assertThat(stream.content)
            .isEqualTo("https://tilvids.com/static/streaming-playlists/hls/x/master.m3u8")
    }

    @Test
    fun `HLS wins even when progressive files are also offered`() {
        val stream = buildPeerTubeVideoStream(
            streamingPlaylists = listOf(PTStreamingPlaylistDto(playlistUrl = "https://h/master.m3u8")),
            files = listOf(PTFileDto(fileUrl = "https://h/720.mp4", resolution = PTResolutionDto(720)))
        )

        assertThat(stream!!.deliveryMethod).isEqualTo(DeliveryMethod.HLS)
    }

    /**
     * The resolver's preferMuxed path selects on this when there is no separate audio stream,
     * which is always the case for PeerTube. Getting it wrong yields silent video.
     */
    @Test
    fun `streams are marked muxed rather than video-only`() {
        val hls = buildPeerTubeVideoStream(
            listOf(PTStreamingPlaylistDto("https://h/master.m3u8")),
            emptyList()
        )
        val mp4 = buildPeerTubeVideoStream(
            emptyList(),
            listOf(PTFileDto("https://h/720.mp4", PTResolutionDto(720)))
        )

        assertThat(hls!!.isVideoOnly).isFalse()
        assertThat(mp4!!.isVideoOnly).isFalse()
    }

    @Test
    fun `without a playlist the 720p file is preferred`() {
        val stream = buildPeerTubeVideoStream(
            streamingPlaylists = emptyList(),
            files = listOf(
                PTFileDto(fileUrl = "https://h/360.mp4", resolution = PTResolutionDto(360)),
                PTFileDto(fileUrl = "https://h/720.mp4", resolution = PTResolutionDto(720)),
                PTFileDto(fileUrl = "https://h/1080.mp4", resolution = PTResolutionDto(1080)),
            )
        )

        assertThat(stream!!.deliveryMethod).isEqualTo(DeliveryMethod.PROGRESSIVE_HTTP)
        assertThat(stream.content).isEqualTo("https://h/720.mp4")
        assertThat(stream.getResolution()).isEqualTo("720p")
    }

    @Test
    fun `without 720p the first file is used`() {
        val stream = buildPeerTubeVideoStream(
            streamingPlaylists = emptyList(),
            files = listOf(
                PTFileDto(fileUrl = "https://h/480.mp4", resolution = PTResolutionDto(480)),
                PTFileDto(fileUrl = "https://h/1080.mp4", resolution = PTResolutionDto(1080)),
            )
        )

        assertThat(stream!!.content).isEqualTo("https://h/480.mp4")
    }

    @Test
    fun `nothing playable yields null instead of a broken stream`() {
        assertThat(buildPeerTubeVideoStream(emptyList(), emptyList())).isNull()
    }
}
