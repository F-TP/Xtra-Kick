package com.xtrakick.app.ui.download

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xtrakick.app.R
import com.xtrakick.app.repository.KickRepository
import com.xtrakick.app.repository.PlayerRepository
import com.xtrakick.app.util.AppConstants
import com.xtrakick.app.util.KickApiHelper
import com.xtrakick.app.util.m3u8.PlaylistUtils
import com.xtrakick.app.util.prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import javax.inject.Inject
import okhttp3.OkHttpClient
import okhttp3.Request

@HiltViewModel
class DownloadViewModel @Inject constructor(
    @param:ApplicationContext private val applicationContext: Context,
    private val playerRepository: PlayerRepository,
    private val kickRepository: KickRepository,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    val integrity = MutableStateFlow<String?>(null)
    val errorMessage = MutableStateFlow<Int?>(null)

    private val _qualities = MutableStateFlow<Map<String, Pair<String, String>>?>(null)
    val qualities: StateFlow<Map<String, Pair<String, String>>?> = _qualities
    val dismiss = MutableStateFlow(false)
    var backupQualities: List<String>? = null
    var selectedQuality: String? = null

    private val bitrateCache = java.util.concurrent.ConcurrentHashMap<String, Long>()

    // Size for a clip: measure what will actually download. When the
    // authenticated endpoint resolves a prepared MP4, HEAD it for the exact
    // size; a null HEAD hides the row rather than showing the HLS estimate,
    // which describes a different (much larger) asset. Logged out, or when
    // the endpoint has no MP4, fall back to estimating the selected HLS URL.
    suspend fun estimateClipSize(clipId: String?, fallbackUrl: String?, rangeSeconds: Long?): Long? = withContext(Dispatchers.IO) {
        val mp4 = resolveClipMp4(clipId)
        if (mp4 != null) {
            return@withContext headContentLength(mp4)
        }
        fallbackUrl?.let { estimateDownloadSize(it, rangeSeconds) }
    }

    suspend fun resolveClipMp4(clipId: String?): String? = withContext(Dispatchers.IO) {
        clipId.takeIf { !it.isNullOrBlank() }?.let {
            runCatching { kickRepository.getClipDownloadUrl(it) }.getOrNull()
        }
    }

    private suspend fun headContentLength(url: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            okHttpClient.newCall(Request.Builder().url(url).head().build()).execute().use { response ->
                response.header("Content-Length")?.toLongOrNull()
            }
        }.getOrNull()
    }

    // Size estimate for a quality: measure one representative segment (HEAD,
    // falling back to a GET's headers) and scale its bitrate over the duration.
    suspend fun estimateDownloadSize(playlistUrl: String, rangeSeconds: Long?): Long? = withContext(Dispatchers.IO) {
        runCatching {
            if (!playlistUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
                // Progressive file (e.g. a prepared clip MP4): exact size via
                // HEAD instead of segment sampling. Never GET it here — that
                // would download the whole file just to measure it.
                return@runCatching headContentLength(playlistUrl)
            }
            val playlistText = playerRepository.loadTextFromUrl(
                applicationContext.prefs().getString(AppConstants.NETWORK_LIBRARY, "OkHttp"),
                playlistUrl
            ) ?: return@runCatching null
            val playlist = PlaylistUtils.parseMediaPlaylist(playlistText.byteInputStream())
            if (playlist.segments.isEmpty()) return@runCatching null
            val playlistDurationSeconds = playlist.segments.sumOf { it.duration.toDouble() }
            if (playlistDurationSeconds <= 0.0) return@runCatching null

            val totalRangeBytes = playlist.segments.sumOf { it.byteRangeLength ?: 0L }
            if (totalRangeBytes > 0L) {
                // Byte-range playlist: every segment is a slice of a shared
                // container, so sampling one segment's full Content-Length
                // would inflate the result ~60x. The range sizes ARE the total
                // size of the asset; scale down proportionally when a sub-range
                // (e.g. a 2-minute clip from a multi-hour VOD) is requested.
                val seconds = rangeSeconds?.toDouble()?.coerceAtMost(playlistDurationSeconds) ?: playlistDurationSeconds
                return@runCatching ((totalRangeBytes.toDouble() / playlistDurationSeconds) * seconds).toLong()
            }

            val bitrate = bitrateCache.getOrPut(playlistUrl) {
                val segment = playlist.segments[playlist.segments.size / 2]
                val segmentUrl = if (segment.uri.startsWith("http")) {
                    segment.uri
                } else {
                    playlistUrl.substringBeforeLast('/') + "/" + segment.uri
                }
                val segmentDurationSeconds = segment.duration.toDouble().coerceAtLeast(0.5)
                val contentLength = okHttpClient.newCall(Request.Builder().url(segmentUrl).head().build()).execute().use { response ->
                    response.header("Content-Length")?.toLongOrNull()
                } ?: okHttpClient.newCall(Request.Builder().url(segmentUrl).build()).execute().use { response ->
                    response.header("Content-Length")?.toLongOrNull()
                } ?: return@runCatching null
                (contentLength * 8 / segmentDurationSeconds).toLong()
            }
            val seconds = rangeSeconds?.toDouble()?.coerceAtMost(playlistDurationSeconds) ?: playlistDurationSeconds
            ((bitrate * seconds) / 8).toLong()
        }.getOrNull()
    }

    fun setStream(
        networkLibrary: String?,
        kickWebHeaders: Map<String, String>,
        channelLogin: String?,
        qualities: Map<String, Pair<String, String>>?,
        randomDeviceId: Boolean?,
        xDeviceId: String?,
        playerType: String?,
        supportedCodecs: String?,
        enableIntegrity: Boolean
    ) {
        if (_qualities.value != null) return
        if (!qualities.isNullOrEmpty()) {
            _qualities.value = qualities
            return
        }
        viewModelScope.launch {
            try {
                val playlist = channelLogin
                    ?.takeIf { it.isNotBlank() }
                    ?.let { kickRepository.getChannel(it) }
                    ?.let { kickRepository.getPlayableUrl(it) }
                    ?.let { playerRepository.loadTextFromUrl(networkLibrary, it) }
                if (playlist.isNullOrBlank()) {
                    errorMessage.value = R.string.download_unavailable
                    return@launch
                }
                _qualities.value = parsePlaylistQualities(playlist)
            } catch (e: Exception) {
                if (e.message == "failed integrity check" && integrity.value == null) {
                    integrity.value = "refresh"
                } else {
                    errorMessage.value = R.string.download_unavailable
                }
            }
        }
    }

    fun setVideo(
        networkLibrary: String?,
        kickWebHeaders: Map<String, String>,
        videoId: String?,
        animatedPreviewUrl: String?,
        videoType: String?,
        qualities: Map<String, Pair<String, String>>?,
        channelLogin: String?,
        channelId: String?,
        playerType: String?,
        supportedCodecs: String?,
        skipAccessToken: Int,
        enableIntegrity: Boolean
    ) {
        if (_qualities.value != null) return
        if (!qualities.isNullOrEmpty()) {
            _qualities.value = qualities
            return
        }
        viewModelScope.launch {
            try {
                if (skipAccessToken <= 1 && !animatedPreviewUrl.isNullOrBlank()) {
                    _qualities.value = buildPreviewQualities(animatedPreviewUrl, videoType)
                    return@launch
                }
                val url = if (!channelLogin.isNullOrBlank()) {
                    kickRepository.getChannelVideos(channelLogin, channelId, 100)
                        .firstOrNull { it.id == videoId }
                        ?.url
                } else {
                    null
                }
                if (!url.isNullOrBlank()) {
                    val playlist = runCatching {
                        playerRepository.loadTextFromUrl(networkLibrary, url)
                    }.getOrNull()
                    val parsedQualities = playlist?.takeIf { it.isNotBlank() }?.let {
                        parsePlaylistQualities(it, url)
                    }.orEmpty()
                    _qualities.value = parsedQualities.ifEmpty {
                        linkedMapOf(
                            "source" to (ContextCompat.getString(applicationContext, R.string.source) to url)
                        )
                    }
                } else if (skipAccessToken == 2 && !animatedPreviewUrl.isNullOrBlank()) {
                    _qualities.value = buildPreviewQualities(animatedPreviewUrl, videoType)
                } else {
                    errorMessage.value = R.string.download_unavailable
                }
            } catch (e: Exception) {
                if (e.message == "failed integrity check" && integrity.value == null) {
                    integrity.value = "refresh"
                } else {
                    errorMessage.value = R.string.download_unavailable
                }
            }
        }
    }

    fun setClip(networkLibrary: String?, kickWebHeaders: Map<String, String>, clipId: String?, qualities: Map<String, Pair<String, String>>?, enableIntegrity: Boolean) {
        if (_qualities.value == null) {
            if (!qualities.isNullOrEmpty()) {
                _qualities.value = qualities
            } else {
                viewModelScope.launch {
                    try {
                        val urls = playerRepository.loadClipUrls(networkLibrary, kickWebHeaders, clipId, enableIntegrity)
                        val hideCodecs = urls?.all {
                            it.key.second?.substringBefore('.').let { codec ->
                                codec == "avc1" || codec == "mp4a" || codec.isNullOrBlank()
                            }
                        } == true
                        val map = mutableMapOf<String, Pair<String, String>>()
                        urls?.entries?.forEach {
                            val quality = it.key.first.let { quality ->
                                if (quality == "audio_only") {
                                    ContextCompat.getString(applicationContext, R.string.audio_only)
                                } else {
                                    val label = if (quality == "source") {
                                        ContextCompat.getString(applicationContext, R.string.source)
                                    } else {
                                        quality
                                    }
                                    if (hideCodecs) {
                                        label
                                    } else {
                                        val codec = it.key.second?.substringBefore('.').let { codec ->
                                            when {
                                                codec == "av01" -> "AV1"
                                                codec == "hev1" || codec == "hvc1" -> "H.265"
                                                codec == "avc1" || codec.isNullOrBlank() -> "H.264"
                                                else -> it
                                            }
                                        }
                                        "$label $codec"
                                    }
                                }
                            }
                            map[it.key.first] = Pair(quality, it.value)
                        }
                        _qualities.value = map.toList()
                            .sortedByDescending {
                                it.first.substringAfter("p", "").takeWhile { it.isDigit() }.toIntOrNull()
                            }
                            .sortedByDescending {
                                it.first.substringBefore("p", "").takeWhile { it.isDigit() }.toIntOrNull()
                            }
                            .sortedByDescending {
                                it.first == "source"
                            }
                            .toMap()
                    } catch (e: Exception) {
                        if (e.message == "failed integrity check" && integrity.value == null) {
                            integrity.value = "refresh"
                        }
                    }
                }
            }
        }
    }

    private fun buildPreviewQualities(animatedPreviewUrl: String, videoType: String?): Map<String, Pair<String, String>> {
        val urls = KickApiHelper.getVideoUrlMapFromPreview(animatedPreviewUrl, videoType, backupQualities)
        val map = mutableMapOf<String, Pair<String, String>>()
        urls.entries.forEach {
            when (it.key) {
                "source" -> map[it.key] = Pair(ContextCompat.getString(applicationContext, R.string.source), it.value)
                "audio_only" -> map[it.key] = Pair(ContextCompat.getString(applicationContext, R.string.audio_only), it.value)
                else -> map[it.key] = Pair(it.key, it.value)
            }
        }
        map.remove("audio_only")?.let { map.put("audio_only", it) }
        return map.toList()
            .sortedByDescending {
                it.first.substringAfter("p", "").takeWhile { it.isDigit() }.toIntOrNull()
            }
            .sortedByDescending {
                it.first.substringBefore("p", "").takeWhile { it.isDigit() }.toIntOrNull()
            }
            .sortedByDescending {
                it.first == "source"
            }
            .toMap()
    }

    private fun parsePlaylistQualities(playlist: String, playlistUrl: String? = null): Map<String, Pair<String, String>> {
        val names = Regex("(?:IVS-NAME|NAME)=\"(.+?)\"").findAll(playlist).mapNotNull { it.groups[1]?.value }.toMutableList()
        val codecs = Regex("CODECS=\"(.+?)\"").findAll(playlist).mapNotNull { it.groups[1]?.value }.toMutableList()
        val urls = playlist.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && it.substringBefore('?').endsWith(".m3u8") }
            .map { url -> playlistUrl?.let { resolvePlaylistUrl(it, url) } ?: url }
            .toMutableList()
        val codecList = codecs.map { codec ->
            codec.substringBefore('.').let {
                when (it) {
                    "av01" -> "AV1"
                    "hev1" -> "H.265"
                    "avc1" -> "H.264"
                    else -> it
                }
            }
        }.takeUnless { it.all { it == "H.264" || it == "mp4a" } }
        val map = mutableMapOf<String, Pair<String, String>>()
        names.forEachIndexed { index, quality ->
            urls.getOrNull(index)?.let { url ->
                when {
                    quality.equals("source", true) -> {
                        val label = ContextCompat.getString(applicationContext, R.string.source)
                        map["source"] = Pair(codecList?.getOrNull(index)?.let { "$label $it" } ?: label, url)
                    }
                    quality.startsWith("audio", true) -> {
                        map["audio_only"] = Pair(ContextCompat.getString(applicationContext, R.string.audio_only), url)
                    }
                    else -> {
                        map[quality] = Pair(codecList?.getOrNull(index)?.let { "$quality $it" } ?: quality, url)
                    }
                }
            }
        }
        return map.toList()
            .sortedByDescending {
                it.first.substringAfter("p", "").takeWhile { it.isDigit() }.toIntOrNull()
            }
            .sortedByDescending {
                it.first.substringBefore("p", "").takeWhile { it.isDigit() }.toIntOrNull()
            }
            .sortedByDescending {
                it.first == "source"
            }
            .toMap()
    }

    private fun resolvePlaylistUrl(baseUrl: String, url: String): String {
        return runCatching { URI(baseUrl).resolve(url).toString() }.getOrElse { url }
    }
}
