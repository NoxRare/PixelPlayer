package com.theveloper.pixelplay.data.network.plex

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Plex authentication and server discovery.
 * Handles OAuth sign-in, token storage, and server selection.
 * Uses EncryptedSharedPreferences for secure token storage.
 */
@Singleton
class PlexAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val plexAuthApiService: PlexAuthApiService
) {
    private val TAG = "PlexAuthManager"

    // Encrypted SharedPreferences for secure token storage
    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            EncryptedSharedPreferences.create(
                context,
                PLEX_SECURE_PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to regular SharedPreferences if encryption fails
            Timber.tag(TAG).w(e, "Failed to create encrypted prefs, falling back to regular prefs")
            context.getSharedPreferences(PLEX_PREFS, Context.MODE_PRIVATE)
        }
    }

    // Client identifier persisted across sessions for consistent device recognition
    private val clientIdentifier: String by lazy {
        encryptedPrefs.getString(KEY_CLIENT_ID, null) ?: UUID.randomUUID().toString().also {
            encryptedPrefs.edit().putString(KEY_CLIENT_ID, it).apply()
        }
    }

    private val _authState = MutableStateFlow<PlexAuthState>(PlexAuthState.NotAuthenticated)
    val authState: StateFlow<PlexAuthState> = _authState.asStateFlow()

    private val _servers = MutableStateFlow<List<PlexServer>>(emptyList())
    val servers: StateFlow<List<PlexServer>> = _servers.asStateFlow()

    private val _selectedServer = MutableStateFlow<PlexServer?>(null)
    val selectedServer: StateFlow<PlexServer?> = _selectedServer.asStateFlow()

    private val _selectedMusicSection = MutableStateFlow<PlexLibrarySection?>(null)
    val selectedMusicSection: StateFlow<PlexLibrarySection?> = _selectedMusicSection.asStateFlow()

    init {
        // Restore auth state from preferences
        restoreAuthState()
    }

    /**
     * Start OAuth flow by requesting a PIN.
     * Returns the PIN code and ID for the user to authenticate.
     * @return Result containing OAuth PIN response
     */
    suspend fun startOAuth(): Result<PlexOAuthPinResponse> = withContext(Dispatchers.IO) {
        try {
            _authState.value = PlexAuthState.Authenticating
            
            val pinResponse = plexAuthApiService.requestOAuthPin(
                clientIdentifier = clientIdentifier
            )
            
            Timber.tag(TAG).d("OAuth PIN requested: ${pinResponse.code}")
            Result.success(pinResponse)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to request OAuth PIN")
            _authState.value = PlexAuthState.Error(e.message ?: "Failed to start OAuth")
            Result.failure(e)
        }
    }

    /**
     * Build the OAuth URL for user authentication.
     * @param pinId The PIN ID from startOAuth
     * @param pinCode The PIN code from startOAuth
     * @return OAuth URL to open in browser
     */
    fun buildOAuthUrl(pinId: Int, pinCode: String): String {
        return "https://app.plex.tv/auth#?clientID=$clientIdentifier&code=$pinCode&context[device][product]=PixelPlayer&context[device][platform]=Android"
    }

    /**
     * Poll the OAuth PIN to check if user has authenticated.
     * Call this repeatedly after user opens the OAuth URL.
     * @param pinId The PIN ID from startOAuth
     * @param maxAttempts Maximum number of polling attempts
     * @param delayMs Delay between polling attempts in milliseconds
     * @return Result indicating success or failure with user info
     */
    suspend fun pollOAuthPin(
        pinId: Int,
        maxAttempts: Int = 60,
        delayMs: Long = 1000
    ): Result<PlexUser> = withContext(Dispatchers.IO) {
        try {
            repeat(maxAttempts) { attempt ->
                delay(delayMs)
                
                val checkResponse = plexAuthApiService.checkOAuthPin(
                    pinId = pinId,
                    clientIdentifier = clientIdentifier
                )
                
                if (checkResponse.authToken != null) {
                    // User has authenticated, get user info
                    val user = plexAuthApiService.getUserInfo(
                        authToken = checkResponse.authToken,
                        clientIdentifier = clientIdentifier
                    )
                    
                    saveAuthToken(user.authToken, user.username, user.email)
                    _authState.value = PlexAuthState.Authenticated(user)
                    
                    Timber.tag(TAG).d("OAuth authentication successful for ${user.username}")
                    return@withContext Result.success(user)
                }
                
                Timber.tag(TAG).d("OAuth polling attempt ${attempt + 1}/$maxAttempts")
            }
            
            // Timeout
            _authState.value = PlexAuthState.Error("Authentication timeout")
            Result.failure(Exception("Authentication timeout - user did not complete OAuth"))
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to poll OAuth PIN")
            _authState.value = PlexAuthState.Error(e.message ?: "Authentication failed")
            Result.failure(e)
        }
    }

    /**
     * Sign in to Plex.tv with email/username and password.
     * DEPRECATED: Use OAuth flow instead (startOAuth + pollOAuthPin).
     * @param login Email or username
     * @param password User's password
     * @return Result indicating success or failure with error message
     */
    @Deprecated("Use OAuth flow instead")
    suspend fun signIn(login: String, password: String): Result<PlexUser> = withContext(Dispatchers.IO) {
        try {
            _authState.value = PlexAuthState.Authenticating
            
            val credentials = PlexCredentials(
                user = PlexCredentialsUser(login = login, password = password)
            )
            
            val response = plexAuthApiService.signIn(
                credentials = credentials,
                clientIdentifier = clientIdentifier
            )
            
            val user = response.user
            if (user == null) {
                Timber.tag(TAG).e("Sign in failed: user data is missing")
                _authState.value = PlexAuthState.Error("Authentication failed: user data is missing")
                return@withContext Result.failure(Exception("Authentication failed: user data is missing"))
            }
            
            saveAuthToken(user.authToken, user.username, user.email)
            _authState.value = PlexAuthState.Authenticated(user)
            
            Timber.tag(TAG).d("Successfully signed in as ${user.username}")
            Result.success(user)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to sign in")
            _authState.value = PlexAuthState.Error(e.message ?: "Authentication failed")
            Result.failure(e)
        }
    }

    /**
     * Discover available Plex servers for the authenticated user.
     * Must be called after successful authentication.
     * @return Result containing list of available servers
     */
    suspend fun discoverServers(): Result<List<PlexServer>> = withContext(Dispatchers.IO) {
        try {
            val authToken = getAuthToken() ?: return@withContext Result.failure(
                IllegalStateException("Not authenticated")
            )

            val resources = plexAuthApiService.getResources(
                authToken = authToken,
                clientIdentifier = clientIdentifier
            )

            val servers = resources
                .filter { it.product == "Plex Media Server" }
                .mapNotNull { resource ->
                    // Prefer HTTPS, non-local connections first, then local
                    val connection = resource.connections
                        .sortedWith(compareBy({ it.local }, { it.protocol != "https" }))
                        .firstOrNull() ?: return@mapNotNull null

                    PlexServer(
                        id = resource.clientIdentifier,
                        name = resource.name,
                        uri = connection.uri,
                        accessToken = resource.accessToken ?: authToken,
                        owned = resource.owned,
                        isLocal = connection.local
                    )
                }

            _servers.value = servers
            Timber.tag(TAG).d("Discovered ${servers.size} Plex servers")
            Result.success(servers)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to discover servers")
            Result.failure(e)
        }
    }

    /**
     * Select a Plex server to use for music streaming.
     * @param server The server to select
     */
    fun selectServer(server: PlexServer) {
        _selectedServer.value = server
        saveSelectedServer(server)
        Timber.tag(TAG).d("Selected server: ${server.name}")
    }

    /**
     * Select a music library section on the current server.
     * @param section The music library section to select
     */
    fun selectMusicSection(section: PlexLibrarySection) {
        _selectedMusicSection.value = section
        saveSelectedMusicSection(section)
        Timber.tag(TAG).d("Selected music section: ${section.title}")
    }

    /**
     * Sign out and clear all Plex credentials.
     */
    fun signOut() {
        clearAuthData()
        _authState.value = PlexAuthState.NotAuthenticated
        _servers.value = emptyList()
        _selectedServer.value = null
        _selectedMusicSection.value = null
        Timber.tag(TAG).d("Signed out from Plex")
    }

    /**
     * Check if user is currently authenticated.
     */
    fun isAuthenticated(): Boolean {
        return _authState.value is PlexAuthState.Authenticated
    }

    /**
     * Get the current auth token if available.
     */
    fun getAuthToken(): String? {
        return encryptedPrefs.getString(KEY_AUTH_TOKEN, null)
    }

    /**
     * Build a streaming URL for a Plex track.
     * @param server The Plex server
     * @param partKey The media part key from the track
     * @return Complete streaming URL with authentication
     */
    fun buildStreamUrl(server: PlexServer, partKey: String): String {
        val baseUri = server.uri.trimEnd('/')
        val normalizedPath = if (partKey.startsWith('/')) partKey else "/$partKey"
        return "$baseUri$normalizedPath?X-Plex-Token=${server.accessToken}"
    }

    /**
     * Build a thumbnail URL for Plex artwork.
     * @param server The Plex server
     * @param thumbPath The thumbnail path from the track/album/artist
     * @return Complete thumbnail URL with authentication
     */
    fun buildThumbnailUrl(server: PlexServer, thumbPath: String?): String? {
        if (thumbPath.isNullOrBlank()) return null
        val baseUri = server.uri.trimEnd('/')
        val normalizedPath = if (thumbPath.startsWith('/')) thumbPath else "/$thumbPath"
        return "$baseUri$normalizedPath?X-Plex-Token=${server.accessToken}"
    }

    private fun saveAuthToken(token: String, username: String, email: String) {
        encryptedPrefs.edit()
            .putString(KEY_AUTH_TOKEN, token)
            .putString(KEY_USERNAME, username)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    private fun saveSelectedServer(server: PlexServer) {
        encryptedPrefs.edit()
            .putString(KEY_SERVER_ID, server.id)
            .putString(KEY_SERVER_NAME, server.name)
            .putString(KEY_SERVER_URI, server.uri)
            .putString(KEY_SERVER_TOKEN, server.accessToken)
            .putBoolean(KEY_SERVER_OWNED, server.owned)
            .putBoolean(KEY_SERVER_LOCAL, server.isLocal)
            .apply()
    }

    private fun saveSelectedMusicSection(section: PlexLibrarySection) {
        encryptedPrefs.edit()
            .putString(KEY_MUSIC_SECTION_KEY, section.key)
            .putString(KEY_MUSIC_SECTION_TITLE, section.title)
            .apply()
    }

    private fun restoreAuthState() {
        val token = encryptedPrefs.getString(KEY_AUTH_TOKEN, null)
        val username = encryptedPrefs.getString(KEY_USERNAME, null)
        val email = encryptedPrefs.getString(KEY_EMAIL, null)

        if (token != null && username != null && email != null) {
            // Create a minimal PlexUser for restored state
            val user = PlexUser(
                id = 0,
                uuid = "",
                username = username,
                title = username,
                email = email,
                authToken = token,
                thumb = null
            )
            _authState.value = PlexAuthState.Authenticated(user)

            // Restore selected server if available
            val serverId = encryptedPrefs.getString(KEY_SERVER_ID, null)
            val serverName = encryptedPrefs.getString(KEY_SERVER_NAME, null)
            val serverUri = encryptedPrefs.getString(KEY_SERVER_URI, null)
            val serverToken = encryptedPrefs.getString(KEY_SERVER_TOKEN, null)

            if (serverId != null && serverName != null && serverUri != null && serverToken != null) {
                _selectedServer.value = PlexServer(
                    id = serverId,
                    name = serverName,
                    uri = serverUri,
                    accessToken = serverToken,
                    owned = encryptedPrefs.getBoolean(KEY_SERVER_OWNED, false),
                    isLocal = encryptedPrefs.getBoolean(KEY_SERVER_LOCAL, false)
                )
            }

            // Restore selected music section if available
            val sectionKey = encryptedPrefs.getString(KEY_MUSIC_SECTION_KEY, null)
            val sectionTitle = encryptedPrefs.getString(KEY_MUSIC_SECTION_TITLE, null)
            if (sectionKey != null && sectionTitle != null) {
                _selectedMusicSection.value = PlexLibrarySection(
                    key = sectionKey,
                    title = sectionTitle,
                    type = "artist"
                )
            }
        }
    }

    private fun clearAuthData() {
        encryptedPrefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_USERNAME)
            .remove(KEY_EMAIL)
            .remove(KEY_SERVER_ID)
            .remove(KEY_SERVER_NAME)
            .remove(KEY_SERVER_URI)
            .remove(KEY_SERVER_TOKEN)
            .remove(KEY_SERVER_OWNED)
            .remove(KEY_SERVER_LOCAL)
            .remove(KEY_MUSIC_SECTION_KEY)
            .remove(KEY_MUSIC_SECTION_TITLE)
            .apply()
    }

    companion object {
        private const val PLEX_PREFS = "plex_preferences"
        private const val PLEX_SECURE_PREFS = "plex_secure_preferences"
        private const val KEY_CLIENT_ID = "plex_client_id"
        private const val KEY_AUTH_TOKEN = "plex_auth_token"
        private const val KEY_USERNAME = "plex_username"
        private const val KEY_EMAIL = "plex_email"
        private const val KEY_SERVER_ID = "plex_server_id"
        private const val KEY_SERVER_NAME = "plex_server_name"
        private const val KEY_SERVER_URI = "plex_server_uri"
        private const val KEY_SERVER_TOKEN = "plex_server_token"
        private const val KEY_SERVER_OWNED = "plex_server_owned"
        private const val KEY_SERVER_LOCAL = "plex_server_local"
        private const val KEY_MUSIC_SECTION_KEY = "plex_music_section_key"
        private const val KEY_MUSIC_SECTION_TITLE = "plex_music_section_title"
    }
}

/**
 * Represents the current Plex authentication state.
 */
sealed class PlexAuthState {
    data object NotAuthenticated : PlexAuthState()
    data object Authenticating : PlexAuthState()
    data class Authenticated(val user: PlexUser) : PlexAuthState()
    data class Error(val message: String) : PlexAuthState()
}
