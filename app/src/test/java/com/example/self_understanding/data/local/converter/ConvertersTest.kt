package com.example.self_understanding.data.local.converter

import com.example.self_understanding.domain.model.TraceNodeType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun allSevenNodeTypesRoundTrip() {
        val expectedNames = listOf(
            "SITUATION", "THOUGHT", "EMOTION", "BODY", "ACTION", "RESULT", "UNKNOWN"
        )
        assertEquals(expectedNames, TraceNodeType.entries.map { it.name })
        expectedNames.forEach { storedName ->
            val type = converters.fromTraceNodeType(storedName)
            assertEquals(storedName, converters.traceNodeTypeToString(type))
            assertEquals(type, converters.fromTraceNodeType(converters.traceNodeTypeToString(type)))
        }
    }

    @Test
    fun existingStoredTypeNamesStillRestoreToTheirOriginalTypes() {
        val existingTypes = mapOf(
            "SITUATION" to TraceNodeType.SITUATION,
            "THOUGHT" to TraceNodeType.THOUGHT,
            "EMOTION" to TraceNodeType.EMOTION,
            "BODY" to TraceNodeType.BODY,
            "ACTION" to TraceNodeType.ACTION,
            "RESULT" to TraceNodeType.RESULT
        )
        existingTypes.forEach { (storedName, originalType) ->
            assertEquals(originalType, converters.fromTraceNodeType(storedName))
            assertEquals(storedName, converters.traceNodeTypeToString(originalType))
        }
    }

    @Test
    fun nullInstantAndTimestampRemainNull() {
        assertNull(converters.dateToTimestamp(null))
        assertNull(converters.fromTimestamp(null))
    }

    @Test
    fun instantsRoundTripAtMillisecondPrecision() {
        listOf(-1234567890123L, -1L, 0L, 1L, 1790812800123L).forEach { millis ->
            val instant = Instant.ofEpochMilli(millis)
            assertEquals(millis, converters.dateToTimestamp(instant))
            assertEquals(instant, converters.fromTimestamp(millis))
        }
    }
}
