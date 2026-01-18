package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.plex.MusicSource
import com.theveloper.pixelplay.data.network.plex.PlexAlbum
import com.theveloper.pixelplay.data.network.plex.PlexApiService
import com.theveloper.pixelplay.data.network.plex.PlexArtist
import com.theveloper.pixelplay.data.network.plex.PlexAuthManager
import com.theveloper.pixelplay.data.network.plex.PlexAuthState
import com.theveloper.pixelplay.data.network.plex.PlexLibrarySection
import com.theveloper.pixelplay.data.network.plex.PlexServer
import com.theveloper.pixelplay.data.network.plex.PlexTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of PlexMusicRepository.
 * Handles communication with Plex servers and converts Plex data to app models.
 */
@Singleton
class PlexMusicRepositoryImpl @Inject constructor(
    private val plexAuthManager: PlexAuthManager,
    private val plexApiService: PlexApiService
) : PlexMusicRepository {

    private val TAG = "PlexMusicRepository"

    private val _musicSections = MutableStateFlow<List<PlexLibrarySection>>(emptyList())
    override val musicSections: StateFlow<List<PlexLibrarySection>> = _musicSections.asStateFlow()

    // Cached data
    private val _cachedTracks = MutableStateFlow<List<PlexTrack>>(emptyList())
    private val _cachedAlbums = MutableStateFlow<List<PlexAlbum>>(emptyList())
    private val _cachedArtists = MutableStateFlow<List<PlexArtist>>(emptyList())

    override val isAuthenticated: StateFlow<Boolean>
        get() = MutableStateFlow(plexAuthManager.authState.value is PlexAuthState.Authenticated)
            .also { flow ->
                // Update based on auth state changes
            }

    override val availableServers: StateFlow<List<PlexServer>> = plexAuthManager.servers

    override val selectedServer: StateFlow<PlexServer?> = plexAuthManager.selectedServer

    override val selectedMusicSection: StateFlow<PlexLibrarySection?> = plexAuthManager.selectedMusicSection

    override suspend fun signIn(login: String, password: String): Result<Unit> {
        return plexAuthManager.signIn(login, password).map { }
    }

    override suspend fun signOut() {
        plexAuthManager.signOut()
        _musicSections.value = emptyList()
        _cachedTracks.value = emptyList()
        _cachedAlbums.value = emptyList()
        _cachedArtists.value = emptyList()
    }

    override suspend fun discoverServers(): Result<List<PlexServer>> {
        return plexAuthManager.discoverServers()
    }

    override suspend fun selectServer(server: PlexServer) {
        plexAuthManager.selectServer(server)
        // Clear cached data when server changes
        _musicSections.value = emptyList()
        _cachedTracks.value = emptyList()
        _cachedAlbums.value = emptyList()
        _cachedArtists.value = emptyList()
    }

    override suspend fun fetchMusicSections(): Result<List<PlexLibrarySection>> = withContext(Dispatchers.IO) {
        try {
            val server = plexAuthManager.selectedServer.value
                ?: return@withContext Result.failure(IllegalStateException("No server selected"))

            val sectionsUrl = "${server.uri}/library/sections"
            val response = plexApiService.getLibrarySections(sectionsUrl, server.accessToken)

            val musicSections = response.mediaContainer.directories
                ?.filter { it.type == "artist" }
                ?: emptyList()

            _musicSections.value = musicSections
            Timber.tag(TAG).d("Found ${musicSections.size} music sections")
            Result.success(musicSections)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to fetch music sections")
            Result.failure(e)
        }
    }

    override suspend fun selectMusicSection(section: PlexLibrarySection) {
        plexAuthManager.selectMusicSection(section)
        // Refresh library when section changes
        refreshLibrary()
    }

    override fun getPlexSongs(): Flow<List<Song>> = _cachedTracks.map { tracks ->
        tracks.map { track -> track.toSong() }
    }

    override fun getPlexAlbums(): Flow<List<Album>> = _cachedAlbums.map { albums ->
        albums.map { album -> album.toAlbum() }
    }

    override fun getPlexArtists(): Flow<List<Artist>> = _cachedArtists.map { artists ->
        artists.map { artist -> artist.toArtist() }
    }

    override fun getSongsForPlexAlbum(albumId: String): Flow<List<Song>> = flow {
        try {
            val server = plexAuthManager.selectedServer.value
            if (server == null) {
                emit(emptyList())
                return@flow
            }

            val albumUrl = "${server.uri}/library/metadata/$albumId/children"
            val response = plexApiService.getAlbumTracks(albumUrl, server.accessToken)

            val songs = response.mediaContainer.metadata?.map { it.toSong() } ?: emptyList()
            emit(songs)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to fetch album tracks")
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getAlbumsForPlexArtist(artistId: String): Flow<List<Album>> = flow {
        try {
            val server = plexAuthManager.selectedServer.value
            if (server == null) {
                emit(emptyList())
                return@flow
            }

            val artistUrl = "${server.uri}/library/metadata/$artistId/children"
            val response = plexApiService.getArtistAlbums(artistUrl, server.accessToken)

            val albums = response.mediaContainer.metadata?.map { it.toAlbum() } ?: emptyList()
            emit(albums)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to fetch artist albums")
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    override fun searchPlexSongs(query: String): Flow<List<Song>> = flow {
        try {
            val server = plexAuthManager.selectedServer.value
            val section = plexAuthManager.selectedMusicSection.value
            if (server == null || section == null) {
                emit(emptyList())
                return@flow
            }

            val searchUrl = "${server.uri}/library/sections/${section.key}/search?type=10&query=$query"
            val response = plexApiService.searchMusic(searchUrl, server.accessToken)

            val songs = response.mediaContainer.metadata?.map { it.toSong() } ?: emptyList()
            emit(songs)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to search Plex songs")
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun refreshLibrary() = withContext(Dispatchers.IO) {
        try {
            val server = plexAuthManager.selectedServer.value
            val section = plexAuthManager.selectedMusicSection.value
            if (server == null || section == null) {
                Timber.tag(TAG).w("Cannot refresh library: server or section not selected")
                return@withContext
            }

            // Fetch all tracks
            val tracksUrl = "${server.uri}/library/sections/${section.key}/all?type=10"
            val tracksResponse = plexApiService.getAllTracks(tracksUrl, server.accessToken)
            _cachedTracks.value = tracksResponse.mediaContainer.metadata ?: emptyList()

            // Fetch all albums
            val albumsUrl = "${server.uri}/library/sections/${section.key}/all?type=9"
            val albumsResponse = plexApiService.getAllAlbums(albumsUrl, server.accessToken)
            _cachedAlbums.value = albumsResponse.mediaContainer.metadata ?: emptyList()

            // Fetch all artists
            val artistsUrl = "${server.uri}/library/sections/${section.key}/all?type=8"
            val artistsResponse = plexApiService.getAllArtists(artistsUrl, server.accessToken)
            _cachedArtists.value = artistsResponse.mediaContainer.metadata ?: emptyList()

            Timber.tag(TAG).d("Refreshed Plex library: ${_cachedTracks.value.size} tracks, ${_cachedAlbums.value.size} albums, ${_cachedArtists.value.size} artists")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to refresh Plex library")
        }
    }

    override suspend fun getStreamingUrl(songId: String): String? {
        val server = plexAuthManager.selectedServer.value ?: return null
        val track = _cachedTracks.value.find { it.ratingKey == songId }
        val partKey = track?.media?.firstOrNull()?.parts?.firstOrNull()?.key ?: return null
        return plexAuthManager.buildStreamUrl(server, partKey)
    }

    /**
     * Convert a PlexTrack to a Song model.
     */
    private fun PlexTrack.toSong(): Song {
        val server = plexAuthManager.selectedServer.value
        val albumArtUrl = server?.let { 
            plexAuthManager.buildThumbnailUrl(it, albumThumb ?: thumb)
        }

        return Song(
            id = "plex_$ratingKey",
            title = title,
            artist = artistTitle ?: "Unknown Artist",
            artistId = grandparentRatingKey?.hashCode()?.toLong() ?: 0L,
            artists = emptyList(),
            album = albumTitle ?: "Unknown Album",
            albumId = parentRatingKey?.hashCode()?.toLong() ?: 0L,
            albumArtist = artistTitle,
            path = "", // Plex tracks don't have local paths
            contentUriString = server?.let { 
                media?.firstOrNull()?.parts?.firstOrNull()?.key?.let { partKey ->
                    plexAuthManager.buildStreamUrl(it, partKey)
                }
            } ?: "",
            albumArtUriString = albumArtUrl,
            duration = duration,
            genre = null,
            lyrics = null,
            isFavorite = false,
            trackNumber = trackNumber,
            year = year,
            dateAdded = addedAt * 1000, // Convert to milliseconds
            mimeType = media?.firstOrNull()?.container?.let { "audio/$it" },
            bitrate = media?.firstOrNull()?.bitrate,
            sampleRate = null
        )
    }

    /**
     * Convert a PlexAlbum to an Album model.
     */
    private fun PlexAlbum.toAlbum(): Album {
        val server = plexAuthManager.selectedServer.value
        val albumArtUrl = server?.let { 
            plexAuthManager.buildThumbnailUrl(it, thumb)
        }

        return Album(
            id = "plex_$ratingKey".hashCode().toLong(),
            title = title,
            artist = artistTitle ?: "Unknown Artist",
            year = year,
            albumArtUriString = albumArtUrl,
            songCount = trackCount
        )
    }

    /**
     * Convert a PlexArtist to an Artist model.
     */
    private fun PlexArtist.toArtist(): Artist {
        val server = plexAuthManager.selectedServer.value
        val artistImageUrl = server?.let { 
            plexAuthManager.buildThumbnailUrl(it, thumb)
        }

        return Artist(
            id = "plex_$ratingKey".hashCode().toLong(),
            name = title,
            songCount = 0, // Would need additional API call to get accurate count
            imageUrl = artistImageUrl
        )
    }
}
