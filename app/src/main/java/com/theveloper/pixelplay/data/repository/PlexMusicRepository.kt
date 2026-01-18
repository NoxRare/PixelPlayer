package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.plex.PlexLibrarySection
import com.theveloper.pixelplay.data.network.plex.PlexServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for accessing music from Plex servers.
 * Provides methods for authentication, server management, and music library access.
 */
interface PlexMusicRepository {

    /**
     * Current authentication status.
     */
    val isAuthenticated: StateFlow<Boolean>

    /**
     * List of available Plex servers.
     */
    val availableServers: StateFlow<List<PlexServer>>

    /**
     * Currently selected Plex server.
     */
    val selectedServer: StateFlow<PlexServer?>

    /**
     * Available music library sections on the selected server.
     */
    val musicSections: StateFlow<List<PlexLibrarySection>>

    /**
     * Currently selected music library section.
     */
    val selectedMusicSection: StateFlow<PlexLibrarySection?>

    /**
     * Sign in to Plex with username/email and password.
     * @param login Email or username
     * @param password User's password
     * @return Result indicating success or failure
     */
    suspend fun signIn(login: String, password: String): Result<Unit>

    /**
     * Sign out from Plex and clear all credentials.
     */
    suspend fun signOut()

    /**
     * Discover available Plex servers.
     * @return Result containing list of discovered servers
     */
    suspend fun discoverServers(): Result<List<PlexServer>>

    /**
     * Select a Plex server to use.
     * @param server The server to select
     */
    suspend fun selectServer(server: PlexServer)

    /**
     * Fetch music library sections from the selected server.
     * @return Result containing list of music sections
     */
    suspend fun fetchMusicSections(): Result<List<PlexLibrarySection>>

    /**
     * Select a music library section.
     * @param section The section to select
     */
    suspend fun selectMusicSection(section: PlexLibrarySection)

    /**
     * Get all songs from the selected Plex music library.
     * @return Flow emitting list of songs
     */
    fun getPlexSongs(): Flow<List<Song>>

    /**
     * Get all albums from the selected Plex music library.
     * @return Flow emitting list of albums
     */
    fun getPlexAlbums(): Flow<List<Album>>

    /**
     * Get all artists from the selected Plex music library.
     * @return Flow emitting list of artists
     */
    fun getPlexArtists(): Flow<List<Artist>>

    /**
     * Get songs for a specific Plex album.
     * @param albumId The Plex album rating key
     * @return Flow emitting list of songs
     */
    fun getSongsForPlexAlbum(albumId: String): Flow<List<Song>>

    /**
     * Get albums for a specific Plex artist.
     * @param artistId The Plex artist rating key
     * @return Flow emitting list of albums
     */
    fun getAlbumsForPlexArtist(artistId: String): Flow<List<Album>>

    /**
     * Search for songs in the Plex music library.
     * @param query Search query
     * @return Flow emitting list of matching songs
     */
    fun searchPlexSongs(query: String): Flow<List<Song>>

    /**
     * Refresh the Plex music library cache.
     */
    suspend fun refreshLibrary()

    /**
     * Get the streaming URL for a Plex song.
     * @param songId The Plex track rating key
     * @return The streaming URL or null if not found
     */
    suspend fun getStreamingUrl(songId: String): String?
}
