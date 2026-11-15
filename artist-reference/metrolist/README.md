# Metrolist Artist Feature — Source Reference

This folder is a source snapshot copied from MetrolistGroup/Metrolist (branch: main) for BlinkPlay's separate artist-data research.

## Important
- These are reference copies only. They are stored as `.kt.txt` so the Android/Gradle build does not compile them.
- They are NOT integrated into BlinkPlay and are NOT a standalone working module.
- Metrolist uses a different InnerTube client, response models, shared repository, settings, database and Compose architecture. The copied files have imports and dependencies on those original modules.
- Later, adapt only the audio artist-data logic to BlinkPlay's existing Innertube models and existing BlinkPlay player/UI. Do not copy Metrolist's player.
- Do not add video shelves or video playback. The actual BlinkPlay artist feature must remain audio-only.
- Keep BlinkPlay's existing UI design when the data layer is eventually adapted.

## Copied files
- `artist-reference/metrolist/shared/api/YouTubeArtistParser.kt.txt` ← `MetrolistGroup/Metrolist/shared/src/commonMain/kotlin/com/metrolist/shared/api/YouTubeArtistParser.kt`
- `artist-reference/metrolist/shared/api/ArtistSongs.kt.txt` ← `MetrolistGroup/Metrolist/shared/src/commonMain/kotlin/com/metrolist/shared/api/ArtistSongs.kt`
- `artist-reference/metrolist/shared/viewmodels/ArtistTracksLoader.kt.txt` ← `MetrolistGroup/Metrolist/shared/src/commonMain/kotlin/com/metrolist/shared/viewmodels/ArtistTracksLoader.kt`
- `artist-reference/metrolist/shared/viewmodels/ArtistViewModel.kt.txt` ← `MetrolistGroup/Metrolist/shared/src/commonMain/kotlin/com/metrolist/shared/viewmodels/ArtistViewModel.kt`
- `artist-reference/metrolist/shared/ui/screens/ArtistScreen.kt.txt` ← `MetrolistGroup/Metrolist/shared/src/commonMain/kotlin/com/metrolist/shared/ui/screens/ArtistScreen.kt`
- `artist-reference/metrolist/shared/tests/ArtistSongsTest.kt.txt` ← `MetrolistGroup/Metrolist/shared/src/commonTest/kotlin/com/metrolist/shared/api/ArtistSongsTest.kt`
- `artist-reference/metrolist/shared/tests/ArtistLoadFailureTest.kt.txt` ← `MetrolistGroup/Metrolist/shared/src/commonTest/kotlin/com/metrolist/shared/api/ArtistLoadFailureTest.kt`
- `artist-reference/metrolist/shared/tests/ArtistTracksLoaderTest.kt.txt` ← `MetrolistGroup/Metrolist/shared/src/commonTest/kotlin/com/metrolist/shared/viewmodels/ArtistTracksLoaderTest.kt`
- `artist-reference/metrolist/LICENSE.txt` ← `MetrolistGroup/Metrolist/LICENSE`

## License and attribution
The source project is licensed under GNU GPL v3. The original license is included as `LICENSE.txt`. Preserve applicable copyright and license notices when adapting or redistributing source.
