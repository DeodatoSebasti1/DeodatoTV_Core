package tv.own.owntv.core.sync

import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.backup.PENDING_KEY
import tv.own.owntv.core.backup.UserDataResolver
import tv.own.owntv.core.backup.pendingStore
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.BulkInsertHelper
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.PlaybackProgressEntity
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.i18n.LocaleStore
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.network.HttpClient
import tv.own.owntv.core.parser.M3uCategoryOverrideStore
import tv.own.owntv.core.parser.M3uParser
import tv.own.owntv.core.settings.SettingsRepository

@RunWith(AndroidJUnit4::class)
class M3uReclassificationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: OwnTVDatabase
    private lateinit var playlist: File
    private lateinit var source: SourceEntity
    private lateinit var syncer: M3uSyncer
    private lateinit var resolver: UserDataResolver
    private var profileId = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, OwnTVDatabase::class.java).allowMainThreadQueries().build()
        playlist = File(context.cacheDir, "m3u-reclassification-${System.nanoTime()}.m3u")
        val sourceId = db.sourceDao().insert(SourceEntity(name = "M3U", type = SourceType.M3U, url = playlist.absolutePath))
        source = db.sourceDao().getById(sourceId)!!
        profileId = db.profileDao().insert(ProfileEntity(name = "Primary", avatarColor = 0x123456))
        val support = SyncSupport(
            db.categoryDao(), db.channelDao(), db.movieDao(), db.seriesDao(), db.sourceDao(),
            CustomizationStore(context), SettingsRepository(context, LocaleStore.from(context)),
        )
        syncer = M3uSyncer(
            context, db.sourceDao(), db.categoryDao(), db.channelDao(), db.movieDao(), db.seriesDao(),
            M3uParser(), HttpClient(OkHttpClient()), BulkInsertHelper(db), M3uCategoryOverrideStore(context), support,
        )
        resolver = UserDataResolver(
            context, db.channelDao(), db.movieDao(), db.seriesDao(), db.profileDao(), db.favoriteDao(),
            db.historyDao(), db.progressDao(), db.contentOrderDao(), db.customCategoryDao(),
            db.seriesSortOrderDao(), db.tombstoneDao(), db,
        )
        context.pendingStore.edit { it.remove(PENDING_KEY) }
    }

    @After
    fun tearDown() = runBlocking {
        context.pendingStore.edit { it.remove(PENDING_KEY) }
        playlist.delete()
        db.close()
    }

    private suspend fun sync(text: String): List<M3uReclassification> {
        playlist.writeText(text)
        return syncer.sync(source, SyncCounters(SyncContentTypes()) {}, SyncStatsCollector(source.id))
    }

    private fun playlist(url: String) = """#EXTM3U
#EXTINF:-1 group-title="Catalog",Show S01E01
$url
"""

    private suspend fun addUserData(type: MediaType, id: Long) {
        db.favoriteDao().add(FavoriteEntity(profileId = profileId, mediaType = type, itemId = id, addedAt = 11))
        db.historyDao().record(WatchHistoryEntity(profileId = profileId, mediaType = type, itemId = id, watchedAt = 22))
        db.progressDao().save(
            PlaybackProgressEntity(profileId = profileId, mediaType = type, itemId = id, positionMs = 33, durationMs = 44, updatedAt = 55),
        )
    }

    @Test
    fun liveToMovieIsTargetedPreservesUserDataAndSecondSyncIsIdempotent() = runBlocking {
        sync(playlist("https://host/content.m3u8"))
        val key = db.channelDao().remoteIdsForSource(source.id).single()
        val old = db.channelDao().findByRemote(source.id, key)!!
        addUserData(MediaType.LIVE, old.id)
        val snapshot = resolver.exportForSource(source.id)

        val changes = sync(playlist("https://host/movie/content.mp4"))
        resolver.relinkAfterSync(snapshot, purge = true, m3uReclassifications = changes)

        assertTrue(db.channelDao().remoteIdsForSource(source.id).isEmpty())
        val movie = db.movieDao().findByRemote(source.id, key)!!
        assertTrue(db.favoriteDao().exists(profileId, MediaType.MOVIE, movie.id))
        assertTrue(db.historyDao().exists(profileId, MediaType.MOVIE, movie.id))
        assertEquals(33, db.progressDao().get(profileId, MediaType.MOVIE, movie.id)?.positionMs)

        val stableId = movie.id
        val secondChanges = sync(playlist("https://host/movie/content.mp4"))
        assertTrue(secondChanges.isEmpty())
        assertEquals(listOf(key), db.movieDao().remoteIdsForSource(source.id))
        assertEquals(stableId, db.movieDao().findByRemote(source.id, key)?.id)
    }

    @Test
    fun liveToSeriesMapsFavoriteToShowAndPlaybackDataToEpisodeIdempotently() = runBlocking {
        sync(playlist("https://host/content.m3u8"))
        val key = db.channelDao().remoteIdsForSource(source.id).single()
        val old = db.channelDao().findByRemote(source.id, key)!!
        addUserData(MediaType.LIVE, old.id)
        val snapshot = resolver.exportForSource(source.id)

        val changes = sync(playlist("https://host/series/content.mp4"))
        resolver.relinkAfterSync(snapshot, purge = true, m3uReclassifications = changes)

        assertTrue(db.channelDao().remoteIdsForSource(source.id).isEmpty())
        val seriesKey = db.seriesDao().remoteIdsForSource(source.id).single()
        val show = db.seriesDao().findSeriesByRemote(source.id, seriesKey)!!
        val episode = db.seriesDao().episodesBySeries(show.id).first().single()
        assertEquals(key, episode.remoteId)
        assertTrue(db.favoriteDao().exists(profileId, MediaType.SERIES, show.id))
        assertTrue(db.historyDao().exists(profileId, MediaType.EPISODE, episode.id))
        assertEquals(33, db.progressDao().get(profileId, MediaType.EPISODE, episode.id)?.positionMs)

        val showId = show.id
        val episodeId = episode.id
        val secondChanges = sync(playlist("https://host/series/content.mp4"))
        assertTrue(secondChanges.isEmpty())
        val sameShow = db.seriesDao().findSeriesByRemote(source.id, seriesKey)!!
        assertEquals(showId, sameShow.id)
        assertEquals(episodeId, db.seriesDao().episodesBySeries(sameShow.id).first().single().id)
    }

    @Test
    fun conflictingRelinkHintsDoNotAttachUserDataToEitherCandidate() = runBlocking {
        sync(playlist("https://host/content.m3u8"))
        val key = db.channelDao().remoteIdsForSource(source.id).single()
        val old = db.channelDao().findByRemote(source.id, key)!!
        addUserData(MediaType.LIVE, old.id)
        val snapshot = resolver.exportForSource(source.id)
        val real = sync(playlist("https://host/movie/content.mp4")).single()
        val conflicting = real.copy(targetType = MediaType.LIVE, targetRemoteId = "another-item")

        resolver.relinkAfterSync(snapshot, purge = true, m3uReclassifications = listOf(real, conflicting))

        val movie = db.movieDao().findByRemote(source.id, key)!!
        assertTrue(!db.favoriteDao().exists(profileId, MediaType.MOVIE, movie.id))
        assertTrue(!db.historyDao().exists(profileId, MediaType.MOVIE, movie.id))
        assertEquals(null, db.progressDao().get(profileId, MediaType.MOVIE, movie.id))
    }
}
