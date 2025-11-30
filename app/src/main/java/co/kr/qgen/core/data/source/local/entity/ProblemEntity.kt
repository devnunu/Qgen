package co.kr.qgen.core.data.source.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "problems",
    foreignKeys = [
        ForeignKey(
            entity = ProblemSetEntity::class,
            parentColumns = ["id"],
            childColumns = ["problemSetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("problemSetId")]
)
data class ProblemEntity(
    @PrimaryKey val id: String,
    val problemSetId: String,
    val stem: String,
    val correctChoiceId: String,
    val explanation: String,
    val difficulty: String
)
