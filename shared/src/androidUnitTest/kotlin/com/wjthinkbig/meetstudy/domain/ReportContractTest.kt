package com.wjthinkbig.meetstudy.domain

import com.wjthinkbig.meetstudy.data.SessionRecord
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Same cases as teacherWeb/src/domain/report.contract.test.ts: both platforms must agree. */
class ReportContractTest {
    private val root = Json.parseToJsonElement(File("../contracts/report-cases.json").readText()).jsonObject

    private val code = mapOf(
        FocusState.STUDYING to 'S', FocusState.AWAY to 'A', FocusState.LOOKING_AWAY to 'L', FocusState.CAMERA_GAZE to 'C',
        FocusState.DROWSY to 'D', FocusState.PHONE to 'P', FocusState.APP_LEFT to 'X', FocusState.BREAK to 'B',
    )

    private fun segments(o: JsonObject) = o["segments"]!!.jsonArray.map {
        val s = it.jsonObject
        Segment(FocusState.valueOf(s["state"]!!.jsonPrimitive.content), s["startMs"]!!.jsonPrimitive.long, s["endMs"]!!.jsonPrimitive.long)
    }

    @Test fun sessionCases() {
        for (c in root["cases"]!!.jsonArray.map { it.jsonObject }) {
            val name = c["name"]!!.jsonPrimitive.content
            val segs = segments(c)
            val e = c["expect"]!!.jsonObject
            val r = buildReport(segs)
            assertEquals(e["totalMs"]!!.jsonPrimitive.long, r.totalMs, name)
            assertEquals(e["focusedMs"]!!.jsonPrimitive.long, r.focusedMs, name)
            assertEquals(e["focusRatePct"]!!.jsonPrimitive.int, focusRatePct(segs), name)
            assertEquals(e["lostMs"]!!.jsonPrimitive.long, lostMs(segs), name)
            val want = e["planner"]!!.jsonArray.map { it.jsonObject["hour"]!!.jsonPrimitive.int to it.jsonObject["row"]!!.jsonPrimitive.content }
            val got = plannerRows(segs, SEOUL).map { row -> row.hour to row.minutes.joinToString("") { (it?.let(code::getValue) ?: '.').toString() } }
            assertEquals(want, got, name)
        }
    }

    @Test fun dayCases() {
        for (c in root["days"]!!.jsonArray.map { it.jsonObject }) {
            val name = c["name"]!!.jsonPrimitive.content
            val today = LocalDate.parse(c["today"]!!.jsonPrimitive.content)
            val days = c["days"]!!.jsonPrimitive.int
            val records = c["records"]!!.jsonArray.map {
                val o = it.jsonObject
                val segs = segments(o)
                SessionRecord(
                    id = o["id"]!!.jsonPrimitive.content, startMs = o["startMs"]!!.jsonPrimitive.long, endMs = segs.maxOf { s -> s.endMs },
                    goalMs = 0, segments = segs, goalStar = o["goalStar"]!!.jsonPrimitive.boolean,
                    proofStar = o["proofStar"]!!.jsonPrimitive.boolean, proofPhotoPath = null,
                )
            }
            val e = c["expect"]!!.jsonObject
            val want = e["daily"]!!.jsonArray.map {
                val d = it.jsonObject
                Triple(d["date"]!!.jsonPrimitive.content, d["focusedMs"]!!.jsonPrimitive.long, d["stars"]!!.jsonPrimitive.int)
            }
            val from = today.minus(days - 1, kotlinx.datetime.DateTimeUnit.DAY)
            val got = dailyTotals(records, SEOUL, from, today).map { Triple(it.date.toString(), it.focusedMs, it.stars) }
            assertEquals(want, got, name)
            assertEquals(e["streak"]!!.jsonPrimitive.int, streakDays(records, SEOUL, today), name)
        }
    }

    @Test fun previousSessionIsTheLatestEarlierOne() {
        fun r(id: String, start: Long) = SessionRecord(id, start, start + 1, 0, emptyList(), false, false, null)
        val all = listOf(r("a", 100), r("b", 300), r("c", 200))
        assertEquals("c", previousSession(all, all[1])?.id)
        assertEquals(null, previousSession(all, all[0]))
    }
}
