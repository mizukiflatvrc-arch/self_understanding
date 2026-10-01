package com.example.self_understanding.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.self_understanding.data.local.converter.Converters
import com.example.self_understanding.data.local.dao.TraceDao
import com.example.self_understanding.data.local.dao.TraceNodeDao
import com.example.self_understanding.data.local.entity.TraceEntity
import com.example.self_understanding.data.local.entity.TraceNodeEntity

@Database(
    entities = [TraceEntity::class, TraceNodeEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun traceDao(): TraceDao
    abstract fun traceNodeDao(): TraceNodeDao
}
