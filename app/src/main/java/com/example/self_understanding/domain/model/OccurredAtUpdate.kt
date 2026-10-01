package com.example.self_understanding.domain.model

import java.time.Instant

/** Nodeの発生時刻を維持する操作と、NULLを含む値への変更を区別する。 */
sealed interface OccurredAtUpdate {
    data object Keep : OccurredAtUpdate
    data class Set(val value: Instant?) : OccurredAtUpdate
}
