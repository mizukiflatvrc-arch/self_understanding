package com.example.self_understanding.data.local.converter

import androidx.room.TypeConverter
import com.example.self_understanding.domain.model.TraceNodeType
import java.time.Instant

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Instant?): Long? {
        return date?.toEpochMilli()
    }

    @TypeConverter
    fun fromTraceNodeType(value: String): TraceNodeType {
        return TraceNodeType.valueOf(value)
    }

    @TypeConverter
    fun traceNodeTypeToString(type: TraceNodeType): String {
        return type.name
    }
}
