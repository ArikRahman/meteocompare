package com.meteocompare.app.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class OsmCacheDiagnosticsInterceptorTest {
    @Test
    fun `fresh or still valid OSM response is served from disk`() {
        assertEquals("DISK_HIT", osmCacheSource(hasCacheResponse = true, networkStatus = null))
    }

    @Test
    fun `expired ETag validated by 304 is labelled revalidated`() {
        assertEquals("REVALIDATED_304", osmCacheSource(hasCacheResponse = true, networkStatus = 304))
    }

    @Test
    fun `uncached tile is served from the network`() {
        assertEquals("NETWORK", osmCacheSource(hasCacheResponse = false, networkStatus = 200))
    }

    @Test
    fun `radar frames classify HTTP cache hits separately from network responses`() {
        assertEquals("DISK_HIT", osmCacheSource(hasCacheResponse = true, networkStatus = null))
        assertEquals("NETWORK", osmCacheSource(hasCacheResponse = false, networkStatus = 200))
        assertEquals("REVALIDATED_304", osmCacheSource(hasCacheResponse = true, networkStatus = 304))
    }

    @Test
    fun `radar logging exposes frame ID but never the geographic coordinates`() {
        assertEquals("abcdef123", radarFrameIdForDiagnostics(
            "tilecache.rainviewer.com",
            listOf("v2", "radar", "abcdef123", "512", "7", "48.85341", "2.34880", "2", "0_1.png")
        ))
        assertEquals(null, radarFrameIdForDiagnostics("tile.evil.example", listOf("v2", "radar", "abcdef123", "0_1.png")))
        assertEquals(null, radarFrameIdForDiagnostics("tilecache.rainviewer.com", listOf("other", "radar", "abcdef123", "0_1.png")))
    }

    @Test
    fun `non 304 cache and network responses are not incorrectly called disk hits`() {
        assertEquals("REVALIDATED", osmCacheSource(hasCacheResponse = true, networkStatus = 200))
    }
}
