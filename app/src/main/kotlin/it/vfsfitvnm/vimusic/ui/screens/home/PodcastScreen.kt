package it.vfsfitvnm.vimusic.ui.screens.home

import android.net.Uri
import android.util.Xml
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.URL

private data class PodcastSearchResult(
    val title: String,
    val author: String,
    val artwork: String,
    val feedUrl: String
)

private data class PodcastEpisode(
    val title: String,
    val description: String,
    val audioUrl: String,
    val published: String
)

@Composable
fun PodcastScreen() {
    var searchText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedPodcast by remember { mutableStateOf<PodcastSearchResult?>(null) }
    val results = remember { mutableStateListOf<PodcastSearchResult>() }
    val episodes = remember { mutableStateListOf<PodcastEpisode>() }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Podcasts")
        if (selectedPodcast == null) {
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
                            val found = withContext(Dispatchers.IO) {
                                searchPodcasts(searchText)
                            }
                            results.addAll(found)
                            if (found.isEmpty()) error = "No podcasts found. Try another search."
                        } catch (e: Exception) {
                            error = e.message ?: "Could not fetch podcasts. Check your internet."
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Search")
            }
            if (isLoading) CircularProgressIndicator()
            error?.let { Text(it) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(results) { podcast ->
                    Card(modifier = Modifier.fillMaxWidth().clickable {
                        selectedPodcast = podcast
                        scope.launch {
                            isLoading = true
                            error = null
                            episodes.clear()
                            try {
                                episodes.addAll(withContext(Dispatchers.IO) {
                                    fetchPodcastEpisodes(podcast.feedUrl)
                                })
                                if (episodes.isEmpty()) error = "No episodes found in this feed."
                            } catch (e: Exception) {
                                error = e.message ?: "Could not load this podcast feed."
                                selectedPodcast = null
                            } finally {
                                isLoading = false
                            }
                        }
                    }) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(podcast.title)
                            Text(podcast.author)
                            Text("Tap to fetch episodes")
                        }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = {
                    selectedPodcast = null
                    episodes.clear()
                    error = null
                }) { Text("Back") }
            }
            Text(selectedPodcast!!.title)
            Text(selectedPodcast!!.author)
            if (isLoading) CircularProgressIndicator()
            error?.let { Text(it) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(episodes) { episode ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(episode.title)
                            if (episode.published.isNotBlank()) Text(episode.published)
                            if (episode.description.isNotBlank()) Text(episode.description)
                            if (episode.audioUrl.isNotBlank()) Text("Audio available")
                        }
                    }
                }
            }
        }
    }
}

private fun searchPodcasts(term: String): List<PodcastSearchResult> {
    val url = "https://itunes.apple.com/search?media=podcast&entity=podcast&limit=30&term=${Uri.encode(term)}"
    val request = Request.Builder().url(url).header("User-Agent", "BlinkPlayPodcast/1.0").build()
    val body = OkHttpClient().newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("Podcast search failed: HTTP ${response.code}")
        response.body?.string() ?: error("Empty search response")
    }
    val json = JSONObject(body)
    val array = json.optJSONArray("results") ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val feed = item.optString("feedUrl")
        if (feed.isBlank()) return@mapNotNull null
        PodcastSearchResult(
            title = item.optString("collectionName", "Untitled podcast"),
            author = item.optString("artistName", ""),
            artwork = item.optString("artworkUrl600", item.optString("artworkUrl100", "")),
            feedUrl = feed
        )
    }
}

private fun fetchPodcastEpisodes(feedUrl: String): List<PodcastEpisode> {
    val request = Request.Builder().url(feedUrl).header("User-Agent", "BlinkPlayPodcast/1.0").build()
    val stream = OkHttpClient().newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("RSS feed failed: HTTP ${response.code}")
        response.body?.byteStream()?.readBytes() ?: error("Empty RSS feed")
    }
    val parser = Xml.newPullParser()
    parser.setInput(stream.inputStream(), null)
    val found = mutableListOf<PodcastEpisode>()
    var insideItem = false
    var title = ""
    var description = ""
    var audio = ""
    var published = ""
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                when (parser.name.lowercase()) {
                    "item" -> {
                        insideItem = true
                        title = ""
                        description = ""
                        audio = ""
                        published = ""
                    }
                    "title" -> if (insideItem && title.isBlank()) title = parser.nextText().trim()
                    "description", "summary" -> if (insideItem && description.isBlank()) description = parser.nextText().trim().take(500)
                    "pubdate", "published" -> if (insideItem && published.isBlank()) published = parser.nextText().trim()
                    "enclosure" -> if (insideItem && audio.isBlank()) audio = parser.getAttributeValue(null, "url") ?: ""
                    "content" -> if (insideItem && audio.isBlank()) audio = parser.getAttributeValue("http://search.yahoo.com/mrss/", "url") ?: ""
                }
            }
            XmlPullParser.END_TAG -> if (parser.name.equals("item", true) && insideItem) {
                if (title.isNotBlank()) found.add(PodcastEpisode(title, description, audio, published))
                insideItem = false
            }
        }
        event = parser.next()
    }
    return found
}
