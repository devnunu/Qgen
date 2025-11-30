package co.kr.qgen.core.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import co.kr.qgen.core.data.source.local.entity.ChoiceEntity
import co.kr.qgen.core.data.source.local.entity.ProblemEntity

@Dao
interface ProblemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProblems(problems: List<ProblemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChoices(choices: List<ChoiceEntity>)

    @Query("DELETE FROM problems WHERE problemSetId = :setId")
    suspend fun deleteProblemsBySetId(setId: String)

    @Transaction
    suspend fun replaceProblems(setId: String, problems: List<ProblemEntity>, choices: List<ChoiceEntity>) {
        deleteProblemsBySetId(setId)
        insertProblems(problems)
        insertChoices(choices)
    }
    
    @Query("SELECT * FROM problems WHERE problemSetId = :setId")
    suspend fun getProblemsBySetId(setId: String): List<ProblemEntity>
    
    @Query("SELECT * FROM choices WHERE problemId = :problemId")
    suspend fun getChoicesByProblemId(problemId: String): List<ChoiceEntity>
}
