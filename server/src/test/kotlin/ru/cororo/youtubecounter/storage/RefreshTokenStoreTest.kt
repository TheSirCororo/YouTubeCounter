package ru.cororo.youtubecounter.storage

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RefreshTokenStoreTest {
    private lateinit var databasePath: Path

    @BeforeTest
    fun createTempDatabase() {
        databasePath = Files.createTempDirectory("token-store").resolve("tokens.db")
    }

    @AfterTest
    fun deleteTempDatabase() {
        databasePath.deleteIfExists()
        Path.of("$databasePath-wal").deleteIfExists()
        Path.of("$databasePath-shm").deleteIfExists()
    }

    private fun open(retention: Duration = Duration.ofDays(90)) =
        RefreshTokenStore(databasePath.toString(), retention)

    @Test
    fun `stores and reads back a refresh token`() = open().use { store ->
        store.put("access", "refresh")
        assertEquals("refresh", store.find("access"))
    }

    @Test
    fun `unknown access token has no refresh token`() = open().use { store ->
        assertNull(store.find("never-seen"))
    }

    @Test
    fun `survives a restart`() {
        open().use { it.put("access", "refresh") }
        // What the ConcurrentHashMap could not do: a new process finds the token again.
        open().use { assertEquals("refresh", it.find("access")) }
    }

    @Test
    fun `rekey moves the refresh token onto the new access token`() = open().use { store ->
        store.put("old-access", "refresh")
        store.rekey("old-access", "new-access", "refresh")

        assertNull(store.find("old-access"))
        assertEquals("refresh", store.find("new-access"))
    }

    @Test
    fun `put replaces the refresh token for the same access token`() = open().use { store ->
        store.put("access", "first")
        store.put("access", "second")
        assertEquals("second", store.find("access"))
    }

    @Test
    fun `remove deletes the token`() = open().use { store ->
        store.put("access", "refresh")
        store.remove("access")
        assertNull(store.find("access"))
    }

    @Test
    fun `prune drops entries older than the retention window`() {
        open().use { it.put("access", "refresh") }
        // Timestamps have second granularity, so a zero window would not reliably put a
        // row just written behind the cutoff. A negative one puts the cutoff in the
        // future, which makes anything already stored stale without sleeping in a test.
        open(retention = Duration.ofSeconds(-1)).use { store ->
            assertEquals(1, store.pruneStale())
            assertNull(store.find("access"))
        }
    }

    @Test
    fun `prune keeps entries inside the retention window`() = open().use { store ->
        store.put("access", "refresh")
        assertEquals(0, store.pruneStale())
        assertEquals("refresh", store.find("access"))
    }

    @Test
    fun `access tokens are not stored in the clear`() {
        open().use { it.put("super-secret-access-token", "refresh") }

        val raw = Files.readAllBytes(databasePath).toString(Charsets.ISO_8859_1)
        assertTrue("super-secret-access-token" !in raw, "the access token must only be stored hashed")
        assertTrue("refresh" in raw, "sanity check: the row was actually written")
    }
}
