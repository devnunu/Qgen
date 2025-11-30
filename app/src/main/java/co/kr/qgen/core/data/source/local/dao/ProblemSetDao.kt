package co.kr.qgen.core.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import co.kr.qgen.core.data.source.local.entity.ProblemSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProblemSetDao {
    @Query("SELECT * FROM problem_sets ORDER BY createdAt DESC")
    fun getAllSets(): Flow<List<ProblemSetEntity>>

    @Query("SELECT * FROM problem_sets WHERE id = :setId")
    suspend fun getSetById(setId: String): ProblemSetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(problemSet: ProblemSetEntity)

    @Query("UPDATE problem_sets SET isFavorite = :isFavorite WHERE id = :setId")
    suspend fun updateFavorite(setId: String, isFavorite: Boolean)

    @Query("UPDATE problem_sets SET title = :title WHERE id = :setId")
    suspend fun updateTitle(setId: String, title: String)

    @Query("UPDATE problem_sets SET score = :score, lastPlayedAt = :lastPlayedAt WHERE id = :setId")
    suspend fun updateScore(setId: String, score: Int, lastPlayedAt: Long)

    @Query("DELETE FROM problem_sets WHERE id = :setId")
    suspend fun deleteSet(setId: String)
}
