package it.vfsfitvnm.vimusic.ui.screens.home

import android.net.Uri
import android.text.Html
import android.util.Xml
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import it.vfsfitvnm.vimusic.ui.items.AlbumItem
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.semiBold
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

private data class PodcastSearchResult(
    val title: String,
    val author: String,
    val artwork: String,
    val feedUrl: String,
    val episodeCount: Int
)

private data class PodcastEpisode(
    val title: String,
    val description: String,
    val audioUrl: String,
    val published: String,
    val artwork: String
)

@Composable
fun PodcastScreen(homeResetKey: Int) {
    val (_, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    var searchText by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedPodcast by remember { mutableStateOf<PodcastSearchResult?>(null) }
    val results = remember { mutableStateListOf<PodcastSearchResult>() }
    val episodes = remember { mutableStateListOf<PodcastEpisode>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val storefronts = remember { listOf("in", "us", "gb", "ca", "au", "sg", "nz", "ie", "za", "de", "fr", "jp", "br", "mx") }
    var nextStorefrontIndex by remember { mutableStateOf(1) }
    var isLoadingMore by remember { mutableStateOf(false) }
    val loadedPodcastKeys = remember { mutableStateListOf<String>() }

    fun showPodcastHome() {
        selectedPodcast = null
        episodes.clear()
        error = null
        isLoading = false
    }

    BackHandler(enabled = selectedPodcast != null) {
        showPodcastHome()
    }

    LaunchedEffect(homeResetKey) {
        showPodcastHome()
        searchOpen = false
        searchText = ""
    }

    fun loadEpisodes(podcast: PodcastSearchResult) {
        selectedPodcast = podcast
        scope.launch {
            isLoading = true
            error = null
            episodes.clear()
            try {
                val loaded = withContext(Dispatchers.IO) {
                    fetchPodcastEpisodes(podcast.feedUrl, podcast.title)
                }
                episodes.addAll(loaded)
                if (episodes.isEmpty()) error = "या Podcast मध्ये playable episodes सापडले नाहीत."
            } catch (e: Exception) {
                error = e.message ?: "Podcast load करता आला नाही."
                selectedPodcast = null
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        error = null
        try {
            val popular = withContext(Dispatchers.IO) { fetchTopPodcasts("in") }
            results.clear()
            loadedPodcastKeys.clear()
            popular.forEach { podcast ->
                val key = podcast.feedUrl.ifBlank { podcast.title }
                if (loadedPodcastKeys.add(key)) results.add(podcast)
            }
            nextStorefrontIndex = 1
            if (popular.isEmpty()) error = "आत्ता Podcasts मिळाले नाहीत. Search icon वापरून शोधा."
        } catch (e: Exception) {
            error = "Popular Podcasts load झाले नाहीत. Search icon वापरून शोधा."
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(selectedPodcast, searchOpen, results.size) {
        if (selectedPodcast != null || searchOpen) return@LaunchedEffect
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= (results.size - 6).coerceAtLeast(0)
        }.collect { nearEnd ->
            if (nearEnd && !isLoading && !isLoadingMore && nextStorefrontIndex < storefronts.size) {
                isLoadingMore = true
                try {
                    val country = storefronts[nextStorefrontIndex]
                    val more = withContext(Dispatchers.IO) { fetchTopPodcasts(country) }
                    more.forEach { podcast ->
                        val key = podcast.feedUrl.ifBlank { podcast.title }
                        if (loadedPodcastKeys.add(key)) results.add(podcast)
                    }
                    nextStorefrontIndex++
                } catch (_: Exception) {
                    // A later scroll can retry if a storefront is temporarily unavailable.
                } finally {
                    isLoadingMore = false
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Header(
            title = "Podcast",
            titleScale = 1.404f,
            actionsContent = {
                IconButton(onClick = {
                    if (selectedPodcast != null) {
                        showPodcastHome()
                    } else {
                        searchOpen = !searchOpen
                        if (!searchOpen) {
                            searchText = ""
                            error = null
                        }
                    }
                }) {
                    Text(if (selectedPodcast != null) "←" else "⌕")
                }
            }
        )

        if (selectedPodcast == null) {
            if (searchOpen) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search podcasts") },
                    singleLine = true
                )
                Button(
                    onClick = {
                        if (searchText.isBlank()) return@Button
                        scope.launch {
                            isLoading = true
                            error = null
                            results.clear()
                            try {
                                val found = withContext(Dispatchers.IO) { searchPodcasts(searchText) }
                                results.addAll(found)
                                if (found.isEmpty()) error = "Podcast सापडला नाही. दुसरा शब्द वापरून पाहा."
                            } catch (e: Exception) {
                                error = e.message ?: "Search करता आला नाही. Internet तपासा."
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Search") }
            }

            if (isLoading) CircularProgressIndicator()
            error?.let { Text(it) }
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(results) { podcast ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlbumItem(
                            thumbnailUrl = podcast.artwork,
                            title = podcast.title,
                            authors = podcast.author,
                            year = "${podcast.episodeCount} Podcast",
                            thumbnailSizePx = 72.dp.px,
                            thumbnailSizeDp = 72.dp,
                            modifier = Modifier.weight(1f).clickable { loadEpisodes(podcast) }
                        )

                    }
                }
            }
        } else {
            BasicText(
                text = selectedPodcast!!.title,
                style = typography.m.semiBold.copy(
                    fontSize = typography.s.fontSize,
                    textAlign = TextAlign.End
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).offset(y = (-4).dp)
            )
            BasicText(
                text = "All Episodes",
                style = typography.s.semiBold.copy(
                    fontSize = typography.xxl.fontSize * 0.5616f,
                    textAlign = TextAlign.End
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).offset(y = (-4).dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(model = selectedPodcast!!.artwork, contentDescription = selectedPodcast!!.title, contentScale = ContentScale.Crop, modifier = Modifier.size(88.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (selectedPodcast!!.author.isNotBlank()) Text(selectedPodcast!!.author)
                }
            }
            if (isLoading) CircularProgressIndicator()
            error?.let { Text(it) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(episodes) { episode ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AsyncImage(model = episode.artwork.ifBlank { selectedPodcast?.artwork.orEmpty() }, contentDescription = episode.title, contentScale = ContentScale.Crop, modifier = Modifier.size(60.dp))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(
                                    text = episode.title,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                if (episode.published.isNotBlank()) {
                                    Text(
                                        text = episode.published,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (episode.audioUrl.isNotBlank()) {
                                IconButton(onClick = {
                                    val mediaItem = MediaItem.Builder()
                                        .setMediaId(episode.audioUrl)
                                        .setUri(episode.audioUrl)
                                        .setCustomCacheKey("podcast:${episode.audioUrl}")
                                        .setMediaMetadata(MediaMetadata.Builder().setTitle(episode.title).setArtist(selectedPodcast?.title ?: "Podcast").setArtworkUri(selectedPodcast?.artwork?.takeIf { it.isNotBlank() }?.let(Uri::parse)).build())
                                        .build()
                                    binder?.player?.setMediaItem(mediaItem, true)
                                    binder?.player?.playWhenReady = true
                                    binder?.player?.prepare()
                                }, enabled = binder != null, modifier = Modifier.size(36.dp)) { Text("▶") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun httpGet(url: String): String {
    val request = Request.Builder().url(url).header("User-Agent", "BlinkPlayPodcast/1.0").build()
    return OkHttpClient().newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("HTTP ${response.code}")
        response.body?.string() ?: error("Empty response")
    }
}

private fun fetchTopPodcasts(country: String): List<PodcastSearchResult> {
    val chart = JSONObject(httpGet("https://itunes.apple.com/$country/rss/toppodcasts/limit=200/json"))
    val items = chart.optJSONObject("feed")?.optJSONArray("entry") ?: return emptyList()
    val ids = (0 until items.length()).mapNotNull { index ->
        items.optJSONObject(index)?.optJSONObject("id")?.optJSONObject("attributes")?.optString("im:id")?.takeIf(String::isNotBlank)
    }
    if (ids.isEmpty()) return emptyList()
    val lookup = JSONObject(httpGet("https://itunes.apple.com/lookup?id=${ids.joinToString(",")}&entity=podcast"))
    val array = lookup.optJSONArray("results") ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val feed = item.optString("feedUrl")
        if (feed.isBlank()) return@mapNotNull null
        PodcastSearchResult(
            title = item.optString("collectionName", "Untitled podcast"),
            author = item.optString("artistName", ""),
            artwork = item.optString("artworkUrl600", item.optString("artworkUrl100", "")),
            feedUrl = feed,
            episodeCount = item.optInt("trackCount", 0).coerceAtLeast(0)
        )
    }
}

private fun searchPodcasts(term: String): List<PodcastSearchResult> {
    val url = "https://itunes.apple.com/search?media=podcast&entity=podcast&limit=100&term=${Uri.encode(term)}"
    val json = JSONObject(httpGet(url))
    val array = json.optJSONArray("results") ?: return emptyList()
    val words = term.lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val title = item.optString("collectionName", "")
        val author = item.optString("artistName", "")
        if (words.isNotEmpty() && words.none { title.contains(it, true) || author.contains(it, true) }) return@mapNotNull null
        val feed = item.optString("feedUrl")
        if (feed.isBlank()) return@mapNotNull null
        PodcastSearchResult(
            title = title.ifBlank { "Untitled podcast" },
            author = author,
            artwork = item.optString("artworkUrl600", item.optString("artworkUrl100", "")),
            feedUrl = feed,
            episodeCount = item.optInt("trackCount", 0).coerceAtLeast(0)
        )
    }
}

private fun cleanPodcastHtml(raw: String): String {
    if (raw.isBlank()) return ""
    return Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT)
        .toString()
        .replace(Regex("[\\t ]+"), " ")
        .replace(Regex("\\n{3,}"), "\\n\\n")
        .trim()
        .take(500)
}

private fun fetchPodcastEpisodes(feedUrl: String, expectedTitle: String): List<PodcastEpisode> {
    val bytes = httpGet(feedUrl).toByteArray()
    val parser = Xml.newPullParser()
    parser.setInput(bytes.inputStream(), null)
    val found = mutableListOf<PodcastEpisode>()
    var insideItem = false
    var channelTitle = ""
    var title = ""
    var description = ""
    var audio = ""
    var published = ""
    var artwork = ""
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                "channel" -> Unit
                "title" -> {
                    if (insideItem && title.isBlank()) title = parser.nextText().trim()
                    else if (!insideItem && channelTitle.isBlank()) channelTitle = parser.nextText().trim()
                }
                "item" -> {
                    insideItem = true
                    title = ""
                    description = ""
                    audio = ""
                    published = ""
                    artwork = ""
                }
                "description", "summary" -> if (insideItem && description.isBlank()) description = parser.nextText().trim()
                "pubdate", "published" -> if (insideItem && published.isBlank()) published = parser.nextText().trim()
                "enclosure" -> if (insideItem && audio.isBlank()) audio = parser.getAttributeValue(null, "url") ?: ""
                "content" -> if (insideItem && audio.isBlank()) audio = parser.getAttributeValue("http://search.yahoo.com/mrss/", "url") ?: ""
                "image" -> if (insideItem && artwork.isBlank()) artwork = parser.getAttributeValue(null, "href") ?: ""
                "thumbnail" -> if (insideItem && artwork.isBlank()) artwork = parser.getAttributeValue(null, "url") ?: ""
            }
            XmlPullParser.END_TAG -> if (parser.name.equals("item", true) && insideItem) {
                if (title.isNotBlank()) found.add(PodcastEpisode(title, cleanPodcastHtml(description), audio, published, artwork))
                insideItem = false
            }
        }
        event = parser.next()
    }
    if (channelTitle.isNotBlank() && !channelTitle.contains(expectedTitle, true) && !expectedTitle.contains(channelTitle, true)) {
        error("हा RSS Feed '$channelTitle' साठी आहे; निवडलेल्या '$expectedTitle' Podcast शी जुळत नाही. चुकीचा Feed टाळला.")
    }
    return found
}
