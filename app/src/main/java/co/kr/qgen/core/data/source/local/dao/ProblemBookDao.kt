package co.kr.qgen.core.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import co.kr.qgen.core.data.source.local.entity.ProblemBookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProblemBookDao {
    @Query("SELECT * FROM problem_books ORDER BY createdAt DESC")
    fun getAllBooks(): Flow<List<ProblemBookEntity>>

    @Query("SELECT * FROM problem_books WHERE id = :bookId")
    suspend fun getBookById(bookId: String): ProblemBookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: ProblemBookEntity)

    @Query("UPDATE problem_books SET title = :title WHERE id = :bookId")
    suspend fun updateTitle(bookId: String, title: String)

    @Query("UPDATE problem_books SET lastPlayedAt = :lastPlayedAt WHERE id = :bookId")
    suspend fun updateLastPlayedAt(bookId: String, lastPlayedAt: Long)

    @Query("DELETE FROM problem_books WHERE id = :bookId")
    suspend fun deleteBook(bookId: String)

    @Query("SELECT COUNT(*) FROM problem_sets WHERE bookId = :bookId")
    suspend fun getSetCountByBookId(bookId: String): Int

    @Query("SELECT SUM(count) FROM problem_sets WHERE bookId = :bookId")
    suspend fun getTotalProblemCountByBookId(bookId: String): Int?
}
