package com.example.self_understanding

import android.app.Application
import androidx.room.Room
import com.example.self_understanding.data.local.AppDatabase
import com.example.self_understanding.data.repository.TraceRepositoryImpl
import com.example.self_understanding.domain.repository.TraceRepository

class SelfUnderstandingApplication : Application() {
    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "self_understanding.db"
        ).build()
    }

    val traceRepository: TraceRepository by lazy {
        TraceRepositoryImpl(database.traceDao(), database.traceNodeDao())
    }
}
