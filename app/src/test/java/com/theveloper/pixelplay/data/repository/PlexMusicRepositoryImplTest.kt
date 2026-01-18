package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.network.plex.MusicSource
import com.theveloper.pixelplay.data.network.plex.PlexAlbum
import com.theveloper.pixelplay.data.network.plex.PlexAlbumsContainer
import com.theveloper.pixelplay.data.network.plex.PlexAlbumsResponse
import com.theveloper.pixelplay.data.network.plex.PlexApiService
import com.theveloper.pixelplay.data.network.plex.PlexArtist
import com.theveloper.pixelplay.data.network.plex.PlexArtistsContainer
import com.theveloper.pixelplay.data.network.plex.PlexArtistsResponse
import com.theveloper.pixelplay.data.network.plex.PlexAuthManager
import com.theveloper.pixelplay.data.network.plex.PlexAuthState
import com.theveloper.pixelplay.data.network.plex.PlexLibrarySection
import com.theveloper.pixelplay.data.network.plex.PlexLibrarySectionsContainer
import com.theveloper.pixelplay.data.network.plex.PlexLibrarySectionsResponse
import com.theveloper.pixelplay.data.network.plex.PlexMedia
import com.theveloper.pixelplay.data.network.plex.PlexMediaPart
import com.theveloper.pixelplay.data.network.plex.PlexMusicContainer
import com.theveloper.pixelplay.data.network.plex.PlexMusicLibraryResponse
import com.theveloper.pixelplay.data.network.plex.PlexServer
import com.theveloper.pixelplay.data.network.plex.PlexTrack
import com.theveloper.pixelplay.data.network.plex.PlexUser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import com.google.common.truth.Truth.assertThat

@ExperimentalCoroutinesApi
class PlexMusicRepositoryImplTest {

    private lateinit var repository: PlexMusicRepositoryImpl
    private val mockPlexAuthManager: PlexAuthManager = mockk(relaxed = true)
    private val mockPlexApiService: PlexApiService = mockk()

    private val testDispatcher = StandardTestDispatcher()

    private val testServer = PlexServer(
        id = "test-server-id",
        name = "Test Plex Server",
        uri = "https://test.plex.direct:32400",
        accessToken = "test-token-123",
        owned = true,
        isLocal = false
    )

    private val testMusicSection = PlexLibrarySection(
        key = "1",
        title = "Music",
        type = "artist"
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Setup default mock behaviors
        every { mockPlexAuthManager.authState } returns MutableStateFlow(PlexAuthState.NotAuthenticated)
        every { mockPlexAuthManager.servers } returns MutableStateFlow(emptyList())
        every { mockPlexAuthManager.selectedServer } returns MutableStateFlow(null)
        every { mockPlexAuthManager.selectedMusicSection } returns MutableStateFlow(null)

        repository = PlexMusicRepositoryImpl(
            plexAuthManager = mockPlexAuthManager,
            plexApiService = mockPlexApiService
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("Authentication Tests")
    inner class AuthenticationTests {

        @Test
        fun `signIn calls plexAuthManager signIn`() = runTest(testDispatcher) {
            val testUser = PlexUser(
                id = 1,
                uuid = "test-uuid",
                username = "testuser",
                title = "Test User",
                email = "test@example.com",
                authToken = "auth-token"
            )
            coEvery { mockPlexAuthManager.signIn(any(), any()) } returns Result.success(testUser)

            val result = repository.signIn("test@example.com", "password123")

            assertThat(result.isSuccess).isTrue()
        }

        @Test
        fun `signIn returns failure on error`() = runTest(testDispatcher) {
            coEvery { mockPlexAuthManager.signIn(any(), any()) } returns 
                Result.failure(Exception("Auth failed"))

            val result = repository.signIn("test@example.com", "wrongpassword")

            assertThat(result.isFailure).isTrue()
        }

        @Test
        fun `signOut clears cached data`() = runTest(testDispatcher) {
            every { mockPlexAuthManager.signOut() } returns Unit

            repository.signOut()

            verify { mockPlexAuthManager.signOut() }
        }
    }

    @Nested
    @DisplayName("Server Discovery Tests")
    inner class ServerDiscoveryTests {

        @Test
        fun `discoverServers returns servers from auth manager`() = runTest(testDispatcher) {
            val servers = listOf(testServer)
            coEvery { mockPlexAuthManager.discoverServers() } returns Result.success(servers)

            val result = repository.discoverServers()

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).hasSize(1)
            assertThat(result.getOrNull()?.first()?.name).isEqualTo("Test Plex Server")
        }

        @Test
        fun `selectServer delegates to auth manager`() = runTest(testDispatcher) {
            every { mockPlexAuthManager.selectServer(any()) } returns Unit

            repository.selectServer(testServer)

            verify { mockPlexAuthManager.selectServer(testServer) }
        }
    }

    @Nested
    @DisplayName("Music Library Tests")
    inner class MusicLibraryTests {

        @Test
        fun `fetchMusicSections returns music type sections only`() = runTest(testDispatcher) {
            every { mockPlexAuthManager.selectedServer } returns MutableStateFlow(testServer)
            coEvery { 
                mockPlexApiService.getLibrarySections(any(), any(), any()) 
            } returns PlexLibrarySectionsResponse(
                mediaContainer = PlexLibrarySectionsContainer(
                    directories = listOf(
                        PlexLibrarySection(key = "1", title = "Music", type = "artist"),
                        PlexLibrarySection(key = "2", title = "Movies", type = "movie"),
                        PlexLibrarySection(key = "3", title = "More Music", type = "artist")
                    )
                )
            )

            val result = repository.fetchMusicSections()

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).hasSize(2)
            assertThat(result.getOrNull()?.all { it.type == "artist" }).isTrue()
        }

        @Test
        fun `fetchMusicSections fails when no server selected`() = runTest(testDispatcher) {
            every { mockPlexAuthManager.selectedServer } returns MutableStateFlow(null)

            val result = repository.fetchMusicSections()

            assertThat(result.isFailure).isTrue()
        }
    }

    @Nested
    @DisplayName("Song Conversion Tests")
    inner class SongConversionTests {

        @Test
        fun `PlexTrack converts to Song correctly`() = runTest(testDispatcher) {
            every { mockPlexAuthManager.selectedServer } returns MutableStateFlow(testServer)
            every { mockPlexAuthManager.selectedMusicSection } returns MutableStateFlow(testMusicSection)
            every { mockPlexAuthManager.buildThumbnailUrl(any(), any()) } returns "https://test.plex.direct/thumb"
            every { mockPlexAuthManager.buildStreamUrl(any(), any()) } returns "https://test.plex.direct/stream"

            val plexTrack = PlexTrack(
                ratingKey = "12345",
                key = "/library/metadata/12345",
                parentRatingKey = "album-123",
                grandparentRatingKey = "artist-456",
                title = "Test Song",
                albumTitle = "Test Album",
                artistTitle = "Test Artist",
                duration = 210000, // 3:30
                trackNumber = 5,
                year = 2024,
                thumb = "/library/metadata/12345/thumb",
                addedAt = 1700000000,
                media = listOf(
                    PlexMedia(
                        id = 1,
                        bitrate = 320,
                        audioCodec = "mp3",
                        container = "mp3",
                        parts = listOf(
                            PlexMediaPart(
                                id = 1,
                                key = "/library/parts/12345/file.mp3"
                            )
                        )
                    )
                )
            )

            coEvery {
                mockPlexApiService.getAllTracks(any(), any(), any())
            } returns PlexMusicLibraryResponse(
                mediaContainer = PlexMusicContainer(
                    metadata = listOf(plexTrack)
                )
            )
            coEvery {
                mockPlexApiService.getAllAlbums(any(), any(), any())
            } returns PlexAlbumsResponse(
                mediaContainer = PlexAlbumsContainer(metadata = emptyList())
            )
            coEvery {
                mockPlexApiService.getAllArtists(any(), any(), any())
            } returns PlexArtistsResponse(
                mediaContainer = PlexArtistsContainer(metadata = emptyList())
            )

            repository.refreshLibrary()
            testDispatcher.scheduler.advanceUntilIdle()

            val songs = repository.getPlexSongs().first()
            
            assertThat(songs).hasSize(1)
            val song = songs.first()
            assertThat(song.id).isEqualTo("plex_12345")
            assertThat(song.title).isEqualTo("Test Song")
            assertThat(song.artist).isEqualTo("Test Artist")
            assertThat(song.album).isEqualTo("Test Album")
            assertThat(song.duration).isEqualTo(210000)
            assertThat(song.trackNumber).isEqualTo(5)
            assertThat(song.year).isEqualTo(2024)
        }
    }

    @Nested
    @DisplayName("Album Conversion Tests")
    inner class AlbumConversionTests {

        @Test
        fun `getPlexAlbums returns converted albums`() = runTest(testDispatcher) {
            every { mockPlexAuthManager.selectedServer } returns MutableStateFlow(testServer)
            every { mockPlexAuthManager.selectedMusicSection } returns MutableStateFlow(testMusicSection)
            every { mockPlexAuthManager.buildThumbnailUrl(any(), any()) } returns "https://test.plex.direct/thumb"

            val plexAlbum = PlexAlbum(
                ratingKey = "album-123",
                key = "/library/metadata/album-123",
                title = "Test Album",
                artistTitle = "Test Artist",
                year = 2024,
                thumb = "/library/metadata/album-123/thumb",
                trackCount = 12
            )

            coEvery {
                mockPlexApiService.getAllTracks(any(), any(), any())
            } returns PlexMusicLibraryResponse(
                mediaContainer = PlexMusicContainer(metadata = emptyList())
            )
            coEvery {
                mockPlexApiService.getAllAlbums(any(), any(), any())
            } returns PlexAlbumsResponse(
                mediaContainer = PlexAlbumsContainer(metadata = listOf(plexAlbum))
            )
            coEvery {
                mockPlexApiService.getAllArtists(any(), any(), any())
            } returns PlexArtistsResponse(
                mediaContainer = PlexArtistsContainer(metadata = emptyList())
            )

            repository.refreshLibrary()
            testDispatcher.scheduler.advanceUntilIdle()

            val albums = repository.getPlexAlbums().first()

            assertThat(albums).hasSize(1)
            val album = albums.first()
            assertThat(album.title).isEqualTo("Test Album")
            assertThat(album.artist).isEqualTo("Test Artist")
            assertThat(album.year).isEqualTo(2024)
            assertThat(album.songCount).isEqualTo(12)
        }
    }

    @Nested
    @DisplayName("Artist Conversion Tests")
    inner class ArtistConversionTests {

        @Test
        fun `getPlexArtists returns converted artists`() = runTest(testDispatcher) {
            every { mockPlexAuthManager.selectedServer } returns MutableStateFlow(testServer)
            every { mockPlexAuthManager.selectedMusicSection } returns MutableStateFlow(testMusicSection)
            every { mockPlexAuthManager.buildThumbnailUrl(any(), any()) } returns "https://test.plex.direct/thumb"

            val plexArtist = PlexArtist(
                ratingKey = "artist-456",
                key = "/library/metadata/artist-456",
                title = "Test Artist",
                thumb = "/library/metadata/artist-456/thumb"
            )

            coEvery {
                mockPlexApiService.getAllTracks(any(), any(), any())
            } returns PlexMusicLibraryResponse(
                mediaContainer = PlexMusicContainer(metadata = emptyList())
            )
            coEvery {
                mockPlexApiService.getAllAlbums(any(), any(), any())
            } returns PlexAlbumsResponse(
                mediaContainer = PlexAlbumsContainer(metadata = emptyList())
            )
            coEvery {
                mockPlexApiService.getAllArtists(any(), any(), any())
            } returns PlexArtistsResponse(
                mediaContainer = PlexArtistsContainer(metadata = listOf(plexArtist))
            )

            repository.refreshLibrary()
            testDispatcher.scheduler.advanceUntilIdle()

            val artists = repository.getPlexArtists().first()

            assertThat(artists).hasSize(1)
            val artist = artists.first()
            assertThat(artist.name).isEqualTo("Test Artist")
        }
    }

    @Nested
    @DisplayName("Streaming URL Tests")
    inner class StreamingUrlTests {

        @Test
        fun `getStreamingUrl returns correct URL for cached track`() = runTest(testDispatcher) {
            every { mockPlexAuthManager.selectedServer } returns MutableStateFlow(testServer)
            every { mockPlexAuthManager.selectedMusicSection } returns MutableStateFlow(testMusicSection)
            every { mockPlexAuthManager.buildThumbnailUrl(any(), any()) } returns null
            every { mockPlexAuthManager.buildStreamUrl(testServer, "/library/parts/12345/file.mp3") } returns 
                "https://test.plex.direct:32400/library/parts/12345/file.mp3?X-Plex-Token=test-token-123"

            val plexTrack = PlexTrack(
                ratingKey = "12345",
                key = "/library/metadata/12345",
                title = "Test Song",
                albumTitle = "Test Album",
                artistTitle = "Test Artist",
                duration = 210000,
                media = listOf(
                    PlexMedia(
                        id = 1,
                        parts = listOf(
                            PlexMediaPart(
                                id = 1,
                                key = "/library/parts/12345/file.mp3"
                            )
                        )
                    )
                )
            )

            coEvery {
                mockPlexApiService.getAllTracks(any(), any(), any())
            } returns PlexMusicLibraryResponse(
                mediaContainer = PlexMusicContainer(metadata = listOf(plexTrack))
            )
            coEvery {
                mockPlexApiService.getAllAlbums(any(), any(), any())
            } returns PlexAlbumsResponse(
                mediaContainer = PlexAlbumsContainer(metadata = emptyList())
            )
            coEvery {
                mockPlexApiService.getAllArtists(any(), any(), any())
            } returns PlexArtistsResponse(
                mediaContainer = PlexArtistsContainer(metadata = emptyList())
            )

            repository.refreshLibrary()
            testDispatcher.scheduler.advanceUntilIdle()

            val streamUrl = repository.getStreamingUrl("12345")

            assertThat(streamUrl).contains("test-token-123")
            assertThat(streamUrl).contains("/library/parts/12345/file.mp3")
        }

        @Test
        fun `getStreamingUrl returns null when no server selected`() = runTest(testDispatcher) {
            every { mockPlexAuthManager.selectedServer } returns MutableStateFlow(null)

            val streamUrl = repository.getStreamingUrl("12345")

            assertThat(streamUrl).isNull()
        }
    }
}
