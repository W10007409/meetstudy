package com.wjthinkbig.meetstudy.data

import com.wjthinkbig.meetstudy.domain.localDate
import com.wjthinkbig.meetstudy.domain.stars
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

// ponytail: one JSON file rewritten per save; move to SQLDelight when records reach the thousands.
class SessionStore(private val dir: String) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SessionRecord.serializer())
    private val path get() = "$dir/sessions.json"

    /** Empty if missing or corrupt; check [load] for the distinction. */
    fun all(): List<SessionRecord> = load() ?: emptyList()

    fun save(r: SessionRecord) = write { it + r }

    /** Replaces the record with the same id; throws if none exists. */
    fun update(r: SessionRecord) = write { cur ->
        check(cur.any { it.id == r.id }) { "no record with id ${r.id}" }
        cur.map { if (it.id == r.id) r else it }
    }

    private fun write(change: (List<SessionRecord>) -> List<SessionRecord>) {
        val current = load() ?: run {
            // Corrupt file: quarantine it rather than overwrite user data.
            renameFile(path, "$dir/sessions.corrupt-${nowMs()}.json")
            emptyList()
        }
        writeTextFile(path, json.encodeToString(serializer, change(current)))
    }

    fun starsOn(date: LocalDate, tz: TimeZone): Int =
        all().filter { it.localDate(tz) == date }.sumOf { it.stars() }

    /** null = unreadable/corrupt; empty list if no file yet. */
    private fun load(): List<SessionRecord>? {
        val text = readTextFile(path) ?: return emptyList()
        return try { json.decodeFromString(serializer, text) } catch (e: Exception) { null }
    }

    @OptIn(kotlin.time.ExperimentalTime::class)
    private fun nowMs() = kotlin.time.Clock.System.now().toEpochMilliseconds()
}
