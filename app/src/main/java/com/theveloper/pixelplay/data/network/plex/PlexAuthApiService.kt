package com.theveloper.pixelplay.data.network.plex

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit interface for Plex.tv authentication API.
 * Used for user sign-in and server discovery.
 */
interface PlexAuthApiService {

    /**
     * Sign in to Plex.tv with username/email and password.
     * @param credentials User credentials for authentication
     * @param clientIdentifier Unique identifier for this client app
     * @param product Name of this app
     * @param version Version of this app
     * @param platform Platform identifier (Android)
     * @param device Device name
     * @return Authentication response containing user info and auth token
     */
    @POST("users/sign_in.json")
    suspend fun signIn(
        @Body credentials: PlexCredentials,
        @Header("X-Plex-Client-Identifier") clientIdentifier: String,
        @Header("X-Plex-Product") product: String = "PixelPlayer",
        @Header("X-Plex-Version") version: String = "1.0.0",
        @Header("X-Plex-Platform") platform: String = "Android",
        @Header("X-Plex-Device") device: String = "Android Device"
    ): PlexAuthResponse

    /**
     * Get all resources (servers) available to the authenticated user.
     * @param authToken User's Plex auth token
     * @param clientIdentifier Unique identifier for this client app
     * @param includeHttps Include HTTPS connections
     * @param includeRelay Include relay connections
     * @return List of available Plex servers
     */
    @GET("resources")
    suspend fun getResources(
        @Header("X-Plex-Token") authToken: String,
        @Header("X-Plex-Client-Identifier") clientIdentifier: String,
        @Query("includeHttps") includeHttps: Int = 1,
        @Query("includeRelay") includeRelay: Int = 1,
        @Header("Accept") accept: String = "application/json"
    ): List<PlexResource>
}

/**
 * User credentials for Plex authentication.
 */
data class PlexCredentials(
    val user: PlexCredentialsUser
)

data class PlexCredentialsUser(
    val login: String,
    val password: String
)
