package co.kr.qgen.core.data.source.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "problem_books")
data class ProblemBookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val lastPlayedAt: Long? = null
)
