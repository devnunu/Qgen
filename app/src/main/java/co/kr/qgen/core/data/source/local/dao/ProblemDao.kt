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

    // 문제집 내 모든 문제 가져오기
    @Query("""
        SELECT p.* FROM problems p
        INNER JOIN problem_sets ps ON p.problemSetId = ps.id
        WHERE ps.bookId = :bookId
    """)
    suspend fun getProblemsByBookId(bookId: String): List<ProblemEntity>

    // 틀린 문제만 가져오기 (solvedCount > correctCount)
    @Query("""
        SELECT p.* FROM problems p
        INNER JOIN problem_sets ps ON p.problemSetId = ps.id
        WHERE ps.bookId = :bookId AND p.solvedCount > p.correctCount
    """)
    suspend fun getWrongProblemsByBookId(bookId: String): List<ProblemEntity>

    // 문제 통계 업데이트
    @Query("""
        UPDATE problems
        SET solvedCount = solvedCount + 1,
            correctCount = correctCount + :increment,
            lastAnsweredAt = :timestamp
        WHERE id = :problemId
    """)
    suspend fun updateProblemStats(problemId: String, increment: Int, timestamp: Long)
}
