package com.xtrakick.app.util.m3u8

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class PlaylistUtilsByteRangeTest {

    private fun parse(text: String): MediaPlaylist =
        PlaylistUtils.parseMediaPlaylist(ByteArrayInputStream(text.toByteArray()))

    @Test
    fun `explicit offsets attach to following segments`() {
        val playlist = parse(
            """
            #EXTM3U
            #EXT-X-TARGETDURATION:2
            #EXT-X-BYTERANGE:100@0
            #EXTINF:2.0,
            container.ts
            #EXT-X-BYTERANGE:200@100
            #EXTINF:2.0,
            container.ts
            #EXT-X-ENDLIST
            """.trimIndent()
        )
        assertEquals(2, playlist.segments.size)
        assertEquals(0L, playlist.segments[0].byteRangeStart)
        assertEquals(100L, playlist.segments[0].byteRangeLength)
        assertEquals(100L, playlist.segments[1].byteRangeStart)
        assertEquals(200L, playlist.segments[1].byteRangeLength)
    }

    @Test
    fun `missing offset continues from previous range end`() {
        val playlist = parse(
            """
            #EXTM3U
            #EXT-X-TARGETDURATION:2
            #EXT-X-BYTERANGE:100@50
            #EXTINF:2.0,
            container.ts
            #EXT-X-BYTERANGE:200
            #EXTINF:2.0,
            container.ts
            #EXT-X-ENDLIST
            """.trimIndent()
        )
        assertEquals(150L, playlist.segments[1].byteRangeStart)
        assertEquals(200L, playlist.segments[1].byteRangeLength)
    }

    @Test
    fun `first bare range starts at zero`() {
        val playlist = parse(
            """
            #EXTM3U
            #EXT-X-TARGETDURATION:2
            #EXT-X-BYTERANGE:100
            #EXTINF:2.0,
            container.ts
            #EXT-X-ENDLIST
            """.trimIndent()
        )
        assertEquals(0L, playlist.segments[0].byteRangeStart)
        assertEquals(100L, playlist.segments[0].byteRangeLength)
    }

    @Test
    fun `segments without byterange have null range`() {
        val playlist = parse(
            """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXTINF:5.0,
            segment0.ts
            #EXTINF:5.0,
            segment1.ts
            #EXT-X-ENDLIST
            """.trimIndent()
        )
        assertEquals(2, playlist.segments.size)
        playlist.segments.forEach {
            assertNull(it.byteRangeStart)
            assertNull(it.byteRangeLength)
        }
    }
}
