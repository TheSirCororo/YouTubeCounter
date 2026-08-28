package ru.cororo.youtubecounter.storage

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import java.time.Instant

/**
 * Refresh tokens, keyed by the access token they belong to, in a SQLite file.
 *
 * They used to live in a map, which meant every restart - including every deploy -
 * signed all users out. SQLite keeps the whole thing a single file with no service
 * to run alongside the app.
 *
 * The access token is stored only as a SHA-256 hash: it is a lookup key here, never
 * read back, so a leaked database file gives away no usable access tokens. The
 * refresh tokens themselves have to be stored as-is to be usable, which makes this
 * file as sensitive as the client secret.
 */
class RefreshTokenStore(
    databasePath: String,
    private val retention: Duration = Duration.ofDays(90)
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(javaClass)
    private val connection: Connection
    private val lock = Any()

    init {
        val path = Path.of(databasePath).toAbsolutePath()
        path.parent?.let(Files::createDirectories)

        connection = DriverManager.getConnection("jdbc:sqlite:$path")
        connection.createStatement().use { statement ->
            // WAL plus a busy timeout: writes are rare, but a deploy can overlap a request.
            statement.execute("PRAGMA journal_mode=WAL")
            statement.execute("PRAGMA busy_timeout=5000")
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS refresh_tokens (
                    access_token_hash TEXT PRIMARY KEY,
                    refresh_token     TEXT    NOT NULL,
                    updated_at        INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
        restrictPermissions(path)
        log.info("Refresh tokens stored at {}", path)
    }

    /** The refresh token for [accessToken], or null if this server never issued one. */
    fun find(accessToken: String): String? = synchronized(lock) {
        connection.prepareStatement(
            "SELECT refresh_token FROM refresh_tokens WHERE access_token_hash = ?"
        ).use { statement ->
            statement.setString(1, hash(accessToken))
            statement.executeQuery().use { rows ->
                if (rows.next()) rows.getString(1) else null
            }
        }
    }

    fun put(accessToken: String, refreshToken: String): Unit = synchronized(lock) {
        connection.prepareStatement(
            """
            INSERT INTO refresh_tokens (access_token_hash, refresh_token, updated_at)
            VALUES (?, ?, ?)
            ON CONFLICT(access_token_hash) DO UPDATE SET
                refresh_token = excluded.refresh_token,
                updated_at    = excluded.updated_at
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, hash(accessToken))
            statement.setString(2, refreshToken)
            statement.setLong(3, Instant.now().epochSecond)
            statement.executeUpdate()
        }
    }

    /**
     * Moves a refresh token onto a newly issued access token, in one transaction, so a
     * failure halfway cannot leave the user with neither key working.
     */
    fun rekey(oldAccessToken: String, newAccessToken: String, refreshToken: String): Unit = synchronized(lock) {
        connection.autoCommit = false
        try {
            remove(oldAccessToken)
            put(newAccessToken, refreshToken)
            connection.commit()
        } catch (ex: Exception) {
            connection.rollback()
            throw ex
        } finally {
            connection.autoCommit = true
        }
    }

    fun remove(accessToken: String): Unit = synchronized(lock) {
        connection.prepareStatement("DELETE FROM refresh_tokens WHERE access_token_hash = ?").use { statement ->
            statement.setString(1, hash(accessToken))
            statement.executeUpdate()
        }
    }

    /**
     * Drops tokens untouched for longer than [retention]. Without this the table only ever
     * grows: a row is written per login, and nothing else ever deletes one.
     */
    fun pruneStale(): Int = synchronized(lock) {
        connection.prepareStatement("DELETE FROM refresh_tokens WHERE updated_at < ?").use { statement ->
            statement.setLong(1, Instant.now().minus(retention).epochSecond)
            statement.executeUpdate()
        }.also { removed ->
            if (removed > 0) log.info("Pruned {} refresh tokens unused for over {} days", removed, retention.toDays())
        }
    }

    override fun close() = synchronized(lock) { connection.close() }

    private fun hash(accessToken: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(accessToken.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** Best effort: the file holds refresh tokens, so keep it to the owner where the OS allows it. */
    private fun restrictPermissions(path: Path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
        } catch (ex: Exception) {
            log.debug("Could not restrict permissions on {}: {}", path, ex.message)
        }
    }
}
