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
import co.kr.qgen.core.util.RetryUtil
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

    fun generateQuestionsWithParallelBatching(
        request: GenerateQuestionsRequest,
        useMockApi: Boolean = false,
        batchSize: Int = 5
    ): Flow<ResultWrapper<Pair<List<Question>, QuestionSetMetadata>>>

    suspend fun checkHealth(): ResultWrapper<Boolean>

    // Local DB methods
    suspend fun saveQuestionSet(questions: List<Question>, metadata: QuestionSetMetadata, bookId: String, tags: String?)
    fun getAllProblemSets(): Flow<List<ProblemSetEntity>>
    suspend fun toggleFavorite(setId: String, isFavorite: Boolean)
    suspend fun updateTitle(setId: String, title: String)
    suspend fun getProblemSetById(setId: String): ProblemSetEntity?
    suspend fun regenerateProblemSet(setId: String): ResultWrapper<Unit>
    suspend fun getQuestionsBySetId(setId: String): List<Question>
    suspend fun deleteProblemSet(setId: String)
    suspend fun updateProblemStatistics(problemId: String, isCorrect: Boolean)
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

    override fun generateQuestionsWithParallelBatching(
        request: GenerateQuestionsRequest,
        useMockApi: Boolean,
        batchSize: Int
    ): Flow<ResultWrapper<Pair<List<Question>, QuestionSetMetadata>>> = flow {
        emit(ResultWrapper.Loading)

        try {
            val totalCount = request.count

            // If count is less than or equal to batch size, use single request
            if (totalCount <= batchSize) {
                val response = RetryUtil.retryWithExponentialBackoff {
                    if (useMockApi) {
                        remoteDataSource.generateMockQuestions(request)
                    } else {
                        remoteDataSource.generateQuestions(request)
                    }
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
                return@flow
            }

            // Split into batches
            val batches = mutableListOf<GenerateQuestionsRequest>()
            var remaining = totalCount
            while (remaining > 0) {
                val currentBatchSize = minOf(batchSize, remaining)
                batches.add(
                    request.copy(count = currentBatchSize)
                )
                remaining -= currentBatchSize
            }

            // Execute batches sequentially to track progress
            val allQuestions = mutableListOf<Question>()

            for ((index, batchRequest) in batches.withIndex()) {
                try {
                    val questions = RetryUtil.retryWithExponentialBackoff {
                        val response = if (useMockApi) {
                            remoteDataSource.generateMockQuestions(batchRequest)
                        } else {
                            remoteDataSource.generateQuestions(batchRequest)
                        }

                        if (response.success && response.data != null) {
                            response.data.questions
                        } else {
                            throw Exception(response.error ?: "Failed to generate questions for batch")
                        }
                    }

                    allQuestions.addAll(questions)

                    // Emit progress after each batch
                    emit(ResultWrapper.Progress(
                        progress = co.kr.qgen.core.model.BatchProgress(
                            currentBatch = index + 1,
                            totalBatches = batches.size,
                            questionsGenerated = allQuestions.size,
                            totalQuestions = totalCount
                        )
                    ))
                } catch (e: Exception) {
                    println("[Repository] Batch ${index + 1} failed: ${e.message}")
                    throw e
                }
            }

            // If we got fewer questions than requested, request the missing ones
            var retryCount = 0
            val maxRetries = 3
            while (allQuestions.size < totalCount && retryCount < maxRetries) {
                val missing = totalCount - allQuestions.size
                println("[Repository] Got ${allQuestions.size}/$totalCount questions. Requesting $missing more (retry ${retryCount + 1}/$maxRetries)")

                try {
                    val additionalQuestions = RetryUtil.retryWithExponentialBackoff {
                        val response = if (useMockApi) {
                            remoteDataSource.generateMockQuestions(request.copy(count = missing))
                        } else {
                            remoteDataSource.generateQuestions(request.copy(count = missing))
                        }

                        if (response.success && response.data != null) {
                            response.data.questions
                        } else {
                            throw Exception(response.error ?: "Failed to generate additional questions")
                        }
                    }

                    allQuestions.addAll(additionalQuestions)

                    if (additionalQuestions.isEmpty()) {
                        // 추가 요청에서 0개가 반환되면 무한 루프 방지
                        break
                    }
                } catch (e: Exception) {
                    println("[Repository] Failed to generate additional questions: ${e.message}")
                    break
                }

                retryCount++
            }

            // Ensure we have exactly the requested count (trim if needed)
            val finalQuestions = allQuestions.take(totalCount)

            if (finalQuestions.isEmpty()) {
                emit(ResultWrapper.Error(message = "No questions were generated"))
            } else {
                val metadata = QuestionSetMetadata(
                    topic = request.topic,
                    difficulty = request.difficulty,
                    totalCount = finalQuestions.size,
                    language = request.language ?: "ko"
                )
                emit(ResultWrapper.Success(Pair(finalQuestions, metadata)))
            }

        } catch (e: Exception) {
            emit(ResultWrapper.Error(message = e.message ?: "Failed to generate questions", throwable = e))
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
        bookId: String,
        tags: String?
    ) {
        val setId = UUID.randomUUID().toString()
        val problemSet = ProblemSetEntity(
            id = setId,
            bookId = bookId,
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
        val problemSet = problemSetDao.getSetById(setId) ?: return ResultWrapper.Error(message = "Problem set not found")

        val request = GenerateQuestionsRequest(
            topic = problemSet.topic,
            difficulty = problemSet.difficulty,
            count = problemSet.count,
            language = problemSet.language
        )

        return try {
            // Use parallel batching with retry logic
            var result: ResultWrapper<Pair<List<Question>, QuestionSetMetadata>>? = null

            if (request.count > 5) {
                // Use parallel batching for larger sets
                generateQuestionsWithParallelBatching(request, useMockApi = false, batchSize = 5)
                    .collect { wrapper ->
                        result = wrapper
                    }
            } else {
                // Use single request for smaller sets
                generateQuestions(request, useMockApi = false)
                    .collect { wrapper ->
                        result = wrapper
                    }
            }

            when (result) {
                is ResultWrapper.Success -> {
                    val questions = (result as ResultWrapper.Success).value.first

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
                }
                is ResultWrapper.Error -> {
                    ResultWrapper.Error(message = (result as ResultWrapper.Error).message ?: "Failed to regenerate questions")
                }
                else -> {
                    ResultWrapper.Error(message = "Failed to regenerate questions")
                }
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

    override suspend fun deleteProblemSet(setId: String) {
        problemDao.deleteProblemsBySetId(setId)
        problemSetDao.deleteSet(setId)
    }

    override suspend fun updateProblemStatistics(problemId: String, isCorrect: Boolean) {
        val increment = if (isCorrect) 1 else 0
        val timestamp = System.currentTimeMillis()
        problemDao.updateProblemStats(problemId, increment, timestamp)
    }
}
