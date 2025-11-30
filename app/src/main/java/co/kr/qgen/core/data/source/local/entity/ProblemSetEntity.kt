package co.kr.qgen.core.data.source.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "problem_sets",
    foreignKeys = [
        ForeignKey(
            entity = ProblemBookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class ProblemSetEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val topic: String,
    val difficulty: String,
    val language: String,
    val count: Int,
    val createdAt: Long,
    val lastPlayedAt: Long?,
    val score: Int?,

    // 새로 추가할 필드들
    val isFavorite: Boolean = false,
    val tags: String? = null,     // 예: "Android,Coroutine,면접"
    val title: String? = null     // 사용자가 변경 가능한 표시용 이름
)
