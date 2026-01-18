package com.theveloper.pixelplay.data.network.plex

import com.google.gson.annotations.SerializedName

/**
 * Response from Plex authentication API (plex.tv).
 * Contains the user's auth token for accessing Plex servers.
 */
data class PlexAuthResponse(
    @SerializedName("user") val user: PlexUser
)

/**
 * Plex user information including authentication token.
 */
data class PlexUser(
    @SerializedName("id") val id: Long,
    @SerializedName("uuid") val uuid: String,
    @SerializedName("username") val username: String,
    @SerializedName("title") val title: String,
    @SerializedName("email") val email: String,
    @SerializedName("authToken") val authToken: String,
    @SerializedName("thumb") val thumb: String? = null
)

/**
 * Response from Plex resources API containing available servers.
 */
data class PlexResourcesResponse(
    val resources: List<PlexResource>
)

/**
 * Represents a Plex server resource available to the user.
 */
data class PlexResource(
    @SerializedName("name") val name: String,
    @SerializedName("product") val product: String,
    @SerializedName("productVersion") val productVersion: String,
    @SerializedName("platform") val platform: String,
    @SerializedName("clientIdentifier") val clientIdentifier: String,
    @SerializedName("owned") val owned: Boolean,
    @SerializedName("accessToken") val accessToken: String? = null,
    @SerializedName("connections") val connections: List<PlexConnection> = emptyList()
)

/**
 * Represents a connection endpoint for a Plex server.
 */
data class PlexConnection(
    @SerializedName("protocol") val protocol: String,
    @SerializedName("address") val address: String,
    @SerializedName("port") val port: Int,
    @SerializedName("uri") val uri: String,
    @SerializedName("local") val local: Boolean
)

/**
 * Simplified Plex server model for UI display.
 */
data class PlexServer(
    val id: String,
    val name: String,
    val uri: String,
    val accessToken: String,
    val owned: Boolean,
    val isLocal: Boolean
)

/**
 * Response from Plex library sections API.
 */
data class PlexLibrarySectionsResponse(
    @SerializedName("MediaContainer") val mediaContainer: PlexLibrarySectionsContainer
)

data class PlexLibrarySectionsContainer(
    @SerializedName("Directory") val directories: List<PlexLibrarySection>? = emptyList()
)

/**
 * Represents a library section in Plex (e.g., Music, Movies, etc.).
 */
data class PlexLibrarySection(
    @SerializedName("key") val key: String,
    @SerializedName("title") val title: String,
    @SerializedName("type") val type: String,
    @SerializedName("agent") val agent: String? = null,
    @SerializedName("scanner") val scanner: String? = null
)

/**
 * Response from Plex music library API.
 */
data class PlexMusicLibraryResponse(
    @SerializedName("MediaContainer") val mediaContainer: PlexMusicContainer
)

data class PlexMusicContainer(
    @SerializedName("Metadata") val metadata: List<PlexTrack>? = emptyList(),
    @SerializedName("size") val size: Int = 0,
    @SerializedName("totalSize") val totalSize: Int = 0
)

/**
 * Represents a track (song) in Plex.
 */
data class PlexTrack(
    @SerializedName("ratingKey") val ratingKey: String,
    @SerializedName("key") val key: String,
    @SerializedName("parentRatingKey") val parentRatingKey: String? = null,
    @SerializedName("grandparentRatingKey") val grandparentRatingKey: String? = null,
    @SerializedName("title") val title: String,
    @SerializedName("parentTitle") val albumTitle: String? = null,
    @SerializedName("grandparentTitle") val artistTitle: String? = null,
    @SerializedName("duration") val duration: Long = 0,
    @SerializedName("index") val trackNumber: Int = 0,
    @SerializedName("parentIndex") val discNumber: Int = 0,
    @SerializedName("year") val year: Int = 0,
    @SerializedName("thumb") val thumb: String? = null,
    @SerializedName("parentThumb") val albumThumb: String? = null,
    @SerializedName("grandparentThumb") val artistThumb: String? = null,
    @SerializedName("addedAt") val addedAt: Long = 0,
    @SerializedName("Media") val media: List<PlexMedia>? = emptyList()
)

/**
 * Represents media information for a track.
 */
data class PlexMedia(
    @SerializedName("id") val id: Long,
    @SerializedName("duration") val duration: Long = 0,
    @SerializedName("bitrate") val bitrate: Int = 0,
    @SerializedName("audioChannels") val audioChannels: Int = 0,
    @SerializedName("audioCodec") val audioCodec: String? = null,
    @SerializedName("container") val container: String? = null,
    @SerializedName("Part") val parts: List<PlexMediaPart>? = emptyList()
)

/**
 * Represents a media part (file) for a track.
 */
data class PlexMediaPart(
    @SerializedName("id") val id: Long,
    @SerializedName("key") val key: String,
    @SerializedName("duration") val duration: Long = 0,
    @SerializedName("file") val file: String? = null,
    @SerializedName("size") val size: Long = 0,
    @SerializedName("container") val container: String? = null
)

/**
 * Response from Plex albums API.
 */
data class PlexAlbumsResponse(
    @SerializedName("MediaContainer") val mediaContainer: PlexAlbumsContainer
)

data class PlexAlbumsContainer(
    @SerializedName("Metadata") val metadata: List<PlexAlbum>? = emptyList(),
    @SerializedName("size") val size: Int = 0,
    @SerializedName("totalSize") val totalSize: Int = 0
)

/**
 * Represents an album in Plex.
 */
data class PlexAlbum(
    @SerializedName("ratingKey") val ratingKey: String,
    @SerializedName("key") val key: String,
    @SerializedName("parentRatingKey") val parentRatingKey: String? = null,
    @SerializedName("title") val title: String,
    @SerializedName("parentTitle") val artistTitle: String? = null,
    @SerializedName("year") val year: Int = 0,
    @SerializedName("thumb") val thumb: String? = null,
    @SerializedName("parentThumb") val artistThumb: String? = null,
    @SerializedName("addedAt") val addedAt: Long = 0,
    @SerializedName("leafCount") val trackCount: Int = 0
)

/**
 * Response from Plex artists API.
 */
data class PlexArtistsResponse(
    @SerializedName("MediaContainer") val mediaContainer: PlexArtistsContainer
)

data class PlexArtistsContainer(
    @SerializedName("Metadata") val metadata: List<PlexArtist>? = emptyList(),
    @SerializedName("size") val size: Int = 0,
    @SerializedName("totalSize") val totalSize: Int = 0
)

/**
 * Represents an artist in Plex.
 */
data class PlexArtist(
    @SerializedName("ratingKey") val ratingKey: String,
    @SerializedName("key") val key: String,
    @SerializedName("title") val title: String,
    @SerializedName("thumb") val thumb: String? = null,
    @SerializedName("addedAt") val addedAt: Long = 0
)

/**
 * Enum representing the source of music (local device vs Plex server).
 */
enum class MusicSource {
    LOCAL,
    PLEX
}

/**
 * OAuth PIN request for Plex authentication.
 */
data class PlexOAuthPinRequest(
    val strong: Boolean = true
)

/**
 * Response from Plex OAuth PIN endpoint.
 */
data class PlexOAuthPinResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("code") val code: String,
    @SerializedName("product") val product: String? = null,
    @SerializedName("trusted") val trusted: Boolean = false,
    @SerializedName("clientIdentifier") val clientIdentifier: String? = null,
    @SerializedName("authToken") val authToken: String? = null
)

/**
 * Response from checking OAuth PIN status.
 */
data class PlexOAuthCheckResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("code") val code: String,
    @SerializedName("authToken") val authToken: String? = null
)
