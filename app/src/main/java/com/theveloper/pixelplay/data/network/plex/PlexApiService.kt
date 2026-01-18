package com.theveloper.pixelplay.data.network.plex

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Retrofit interface for Plex API.
 * Used for fetching music library data from Plex servers.
 * 
 * Note: Authentication is handled separately via PlexAuthManager.
 * This interface is for accessing Plex media servers after authentication.
 */
interface PlexApiService {

    /**
     * Get all library sections from a Plex server.
     * @param serverUrl Base URL of the Plex server
     * @param token Plex access token for the server
     * @return Response containing all library sections (Music, Movies, TV Shows, etc.)
     */
    @GET
    suspend fun getLibrarySections(
        @Url serverUrl: String,
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json"
    ): PlexLibrarySectionsResponse

    /**
     * Get all tracks (songs) from a music library section.
     * @param serverUrl Base URL of the Plex server with section path
     * @param token Plex access token for the server
     * @return Response containing all tracks in the music library
     */
    @GET
    suspend fun getAllTracks(
        @Url serverUrl: String,
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json"
    ): PlexMusicLibraryResponse

    /**
     * Get all albums from a music library section.
     * @param serverUrl Base URL of the Plex server with section path
     * @param token Plex access token for the server
     * @return Response containing all albums in the music library
     */
    @GET
    suspend fun getAllAlbums(
        @Url serverUrl: String,
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json"
    ): PlexAlbumsResponse

    /**
     * Get all artists from a music library section.
     * @param serverUrl Base URL of the Plex server with section path
     * @param token Plex access token for the server
     * @return Response containing all artists in the music library
     */
    @GET
    suspend fun getAllArtists(
        @Url serverUrl: String,
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json"
    ): PlexArtistsResponse

    /**
     * Get tracks for a specific album.
     * @param serverUrl Base URL of the Plex server with album path
     * @param token Plex access token for the server
     * @return Response containing all tracks in the album
     */
    @GET
    suspend fun getAlbumTracks(
        @Url serverUrl: String,
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json"
    ): PlexMusicLibraryResponse

    /**
     * Get albums for a specific artist.
     * @param serverUrl Base URL of the Plex server with artist path
     * @param token Plex access token for the server
     * @return Response containing all albums by the artist
     */
    @GET
    suspend fun getArtistAlbums(
        @Url serverUrl: String,
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json"
    ): PlexAlbumsResponse

    /**
     * Search for tracks, albums, or artists in the music library.
     * @param serverUrl Base URL of the Plex server with search path
     * @param token Plex access token for the server
     * @return Response containing search results
     */
    @GET
    suspend fun searchMusic(
        @Url serverUrl: String,
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json"
    ): PlexMusicLibraryResponse
}
