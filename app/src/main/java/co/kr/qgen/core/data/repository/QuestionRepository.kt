package co.kr.qgen.core.data.repository

import co.kr.qgen.core.data.source.local.dao.ProblemDao
import co.kr.qgen.core.data.source.local.dao.ProblemSetDao
import co.kr.qgen.core.data.source.local.entity.ChoiceEntity
import co.kr.qgen.core.data.source.local.entity.ProblemEntity
import co.kr.qgen.core.data.source.local.entity.ProblemSetEntity
import co.kr.qgen.core.data.source.remote.QuestionRemoteDataSource
import co.kr.qgen.core.model.GenerateQuestionsRequest
import co.kr.qgen.core.model.Question
import co.kr.qgen.core.model.QuestionChoice
import co.kr.qgen.core.model.QuestionSetMetadata
import co.kr.qgen.core.model.ResultWrapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * Repository for question generation
 * Single source of truth for question data
 */
interface QuestionRepository {
    fun generateQuestions(
        request: GenerateQuestionsRequest,
        useMockApi: Boolean = false
    ): Flow<ResultWrapper<Pair<List<Question>, QuestionSetMetadata>>>
    
    suspend fun checkHealth(): ResultWrapper<Boolean>

    // Local DB methods
    suspend fun saveQuestionSet(questions: List<Question>, metadata: QuestionSetMetadata, tags: String?)
    fun getAllProblemSets(): Flow<List<ProblemSetEntity>>
    suspend fun toggleFavorite(setId: String, isFavorite: Boolean)
    suspend fun updateTitle(setId: String, title: String)
    suspend fun getProblemSetById(setId: String): ProblemSetEntity?
    suspend fun regenerateProblemSet(setId: String): ResultWrapper<Unit>
    suspend fun getQuestionsBySetId(setId: String): List<Question>
}

class QuestionRepositoryImpl(
    private val remoteDataSource: QuestionRemoteDataSource,
    private val problemSetDao: ProblemSetDao,
    private val problemDao: ProblemDao
) : QuestionRepository {
    
    override fun generateQuestions(
        request: GenerateQuestionsRequest,
        useMockApi: Boolean
    ): Flow<ResultWrapper<Pair<List<Question>, QuestionSetMetadata>>> = flow {
        emit(ResultWrapper.Loading)
        
        try {
            val response = if (useMockApi) {
                remoteDataSource.generateMockQuestions(request)
            } else {
                remoteDataSource.generateQuestions(request)
            }
            
            if (response.success && response.data != null) {
                val questions = response.data.questions
                val metadata = QuestionSetMetadata(
                    topic = request.topic,
                    difficulty = request.difficulty,
                    totalCount = questions.size,
                    language = request.language ?: "ko"
                )
                emit(ResultWrapper.Success(Pair(questions, metadata)))
            } else {
                emit(ResultWrapper.Error(message = response.error ?: "Unknown error occurred"))
            }
        } catch (e: Exception) {
            emit(ResultWrapper.Error(message = e.message ?: "Network error occurred", throwable = e))
        }
    }
    
    override suspend fun checkHealth(): ResultWrapper<Boolean> {
        return try {
            val response = remoteDataSource.checkHealth()
            if (response.status == "ok") {
                ResultWrapper.Success(true)
            } else {
                ResultWrapper.Error(message = "Health check failed")
            }
        } catch (e: Exception) {
            ResultWrapper.Error(message = e.message ?: "Health check failed", throwable = e)
        }
    }

    override suspend fun saveQuestionSet(
        questions: List<Question>,
        metadata: QuestionSetMetadata,
        tags: String?
    ) {
        val setId = UUID.randomUUID().toString()
        val problemSet = ProblemSetEntity(
            id = setId,
            topic = metadata.topic,
            difficulty = metadata.difficulty,
            language = metadata.language,
            count = metadata.totalCount,
            createdAt = System.currentTimeMillis(),
            lastPlayedAt = null,
            score = null,
            tags = tags,
            title = metadata.topic // 기본값은 주제
        )

        problemSetDao.insertSet(problemSet)

        val problemEntities = questions.map { q ->
            ProblemEntity(
                id = q.id,
                problemSetId = setId,
                stem = q.stem,
                correctChoiceId = q.correctChoiceId,
                explanation = q.explanation,
                difficulty = q.metadata.difficulty
            )
        }

        val choiceEntities = questions.flatMap { q ->
            q.choices.map { c ->
                ChoiceEntity(
                    problemId = q.id,
                    choiceId = c.id,
                    text = c.text
                )
            }
        }

        problemDao.insertProblems(problemEntities)
        problemDao.insertChoices(choiceEntities)
    }

    override fun getAllProblemSets(): Flow<List<ProblemSetEntity>> {
        return problemSetDao.getAllSets()
    }

    override suspend fun toggleFavorite(setId: String, isFavorite: Boolean) {
        problemSetDao.updateFavorite(setId, isFavorite)
    }

    override suspend fun updateTitle(setId: String, title: String) {
        problemSetDao.updateTitle(setId, title)
    }

    override suspend fun getProblemSetById(setId: String): ProblemSetEntity? {
        return problemSetDao.getSetById(setId)
    }

    override suspend fun regenerateProblemSet(setId: String): ResultWrapper<Unit> {
        // ... (existing implementation)
        val problemSet = problemSetDao.getSetById(setId) ?: return ResultWrapper.Error(message = "Problem set not found")

        val request = GenerateQuestionsRequest(
            topic = problemSet.topic,
            difficulty = problemSet.difficulty,
            count = problemSet.count,
            language = problemSet.language
        )

        return try {
            val response = remoteDataSource.generateQuestions(request)
            if (response.success && response.data != null) {
                val questions = response.data.questions
                
                val problemEntities = questions.map { q ->
                    ProblemEntity(
                        id = q.id,
                        problemSetId = setId,
                        stem = q.stem,
                        correctChoiceId = q.correctChoiceId,
                        explanation = q.explanation,
                        difficulty = q.metadata.difficulty
                    )
                }

                val choiceEntities = questions.flatMap { q ->
                    q.choices.map { c ->
                        ChoiceEntity(
                            problemId = q.id,
                            choiceId = c.id,
                            text = c.text
                        )
                    }
                }

                problemDao.replaceProblems(setId, problemEntities, choiceEntities)
                ResultWrapper.Success(Unit)
            } else {
                ResultWrapper.Error(message = response.error ?: "Failed to regenerate questions")
            }
        } catch (e: Exception) {
            ResultWrapper.Error(message = e.message ?: "Network error during regeneration", throwable = e)
        }
    }

    override suspend fun getQuestionsBySetId(setId: String): List<Question> {
        val problemEntities = problemDao.getProblemsBySetId(setId)
        val problemSet = problemSetDao.getSetById(setId) ?: return emptyList()
        
        return problemEntities.map { p ->
            val choices = problemDao.getChoicesByProblemId(p.id).map { c: ChoiceEntity ->
                QuestionChoice(
                    id = c.choiceId,
                    text = c.text
                )
            }
            
            Question(
                id = p.id,
                stem = p.stem,
                choices = choices,
                correctChoiceId = p.correctChoiceId,
                explanation = p.explanation,
                metadata = co.kr.qgen.core.model.QuestionMetadata(
                    topic = problemSet.topic,
                    difficulty = p.difficulty
                )
            )
        }
    }
}
