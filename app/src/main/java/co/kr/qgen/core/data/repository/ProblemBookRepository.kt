package co.kr.qgen.core.data.repository

import co.kr.qgen.core.data.source.local.dao.ProblemBookDao
import co.kr.qgen.core.data.source.local.dao.ProblemDao
import co.kr.qgen.core.data.source.local.dao.ProblemSetDao
import co.kr.qgen.core.data.source.local.entity.ChoiceEntity
import co.kr.qgen.core.data.source.local.entity.ProblemBookEntity
import co.kr.qgen.core.data.source.local.entity.ProblemSetEntity
import co.kr.qgen.core.model.Question
import co.kr.qgen.core.model.QuestionChoice
import co.kr.qgen.core.model.QuestionMetadata
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository for managing ProblemBooks
 */
interface ProblemBookRepository {
    fun getAllBooks(): Flow<List<ProblemBookEntity>>
    suspend fun getBookById(bookId: String): ProblemBookEntity?
    suspend fun createBook(title: String): String
    suspend fun updateBookTitle(bookId: String, title: String)
    suspend fun updateLastPlayedAt(bookId: String, timestamp: Long)
    suspend fun deleteBook(bookId: String)
    suspend fun getBookStats(bookId: String): BookStats
    fun getProblemSetsByBookId(bookId: String): Flow<List<ProblemSetEntity>>

    // Ad-hoc quiz sessions
    suspend fun getRandomProblems(bookId: String, count: Int): List<Question>
    suspend fun getWrongProblems(bookId: String, count: Int): List<Question>

    // Statistics updates
    suspend fun updateProblemStatistics(problemId: String, isCorrect: Boolean)
}

data class BookStats(
    val totalSets: Int,
    val totalProblems: Int
)

class ProblemBookRepositoryImpl(
    private val bookDao: ProblemBookDao,
    private val problemSetDao: ProblemSetDao,
    private val problemDao: ProblemDao
) : ProblemBookRepository {

    override fun getAllBooks(): Flow<List<ProblemBookEntity>> {
        return bookDao.getAllBooks()
    }

    override suspend fun getBookById(bookId: String): ProblemBookEntity? {
        return bookDao.getBookById(bookId)
    }

    override suspend fun createBook(title: String): String {
        val bookId = UUID.randomUUID().toString()
        val book = ProblemBookEntity(
            id = bookId,
            title = title,
            createdAt = System.currentTimeMillis(),
            lastPlayedAt = null
        )
        bookDao.insertBook(book)
        return bookId
    }

    override suspend fun updateBookTitle(bookId: String, title: String) {
        bookDao.updateTitle(bookId, title)
    }

    override suspend fun updateLastPlayedAt(bookId: String, timestamp: Long) {
        bookDao.updateLastPlayedAt(bookId, timestamp)
    }

    override suspend fun deleteBook(bookId: String) {
        bookDao.deleteBook(bookId)
    }

    override suspend fun getBookStats(bookId: String): BookStats {
        val setCount = bookDao.getSetCountByBookId(bookId)
        val problemCount = bookDao.getTotalProblemCountByBookId(bookId) ?: 0
        return BookStats(
            totalSets = setCount,
            totalProblems = problemCount
        )
    }

    override fun getProblemSetsByBookId(bookId: String): Flow<List<ProblemSetEntity>> {
        return problemSetDao.getSetsByBookId(bookId)
    }

    override suspend fun getRandomProblems(bookId: String, count: Int): List<Question> {
        // Get all problems for this book
        val problemEntities = problemDao.getProblemsByBookId(bookId)

        // Randomly shuffle and take the requested count
        val selectedProblems = problemEntities.shuffled().take(count)

        // Convert to Question domain models
        return selectedProblems.map { p ->
            val choices = problemDao.getChoicesByProblemId(p.id).map { c: ChoiceEntity ->
                QuestionChoice(
                    id = c.choiceId,
                    text = c.text
                )
            }

            // Get problem set for metadata
            val problemSet = problemSetDao.getSetById(p.problemSetId)

            Question(
                id = p.id,
                stem = p.stem,
                choices = choices,
                correctChoiceId = p.correctChoiceId,
                explanation = p.explanation,
                metadata = QuestionMetadata(
                    topic = problemSet?.topic ?: "",
                    difficulty = p.difficulty
                )
            )
        }
    }

    override suspend fun getWrongProblems(bookId: String, count: Int): List<Question> {
        // Get wrong problems (solvedCount > correctCount)
        val wrongProblemEntities = problemDao.getWrongProblemsByBookId(bookId)

        // Randomly shuffle and take the requested count
        val selectedProblems = wrongProblemEntities.shuffled().take(count)

        // Convert to Question domain models
        return selectedProblems.map { p ->
            val choices = problemDao.getChoicesByProblemId(p.id).map { c: ChoiceEntity ->
                QuestionChoice(
                    id = c.choiceId,
                    text = c.text
                )
            }

            // Get problem set for metadata
            val problemSet = problemSetDao.getSetById(p.problemSetId)

            Question(
                id = p.id,
                stem = p.stem,
                choices = choices,
                correctChoiceId = p.correctChoiceId,
                explanation = p.explanation,
                metadata = QuestionMetadata(
                    topic = problemSet?.topic ?: "",
                    difficulty = p.difficulty
                )
            )
        }
    }

    override suspend fun updateProblemStatistics(problemId: String, isCorrect: Boolean) {
        val increment = if (isCorrect) 1 else 0
        val timestamp = System.currentTimeMillis()
        problemDao.updateProblemStats(problemId, increment, timestamp)
    }
}
