package co.kr.qgen.core.data.repository

import co.kr.qgen.core.data.source.remote.QuestionRemoteDataSource
import co.kr.qgen.core.model.GenerateQuestionsRequest
import co.kr.qgen.core.model.Question
import co.kr.qgen.core.model.QuestionSetMetadata
import co.kr.qgen.core.model.ResultWrapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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
}

class QuestionRepositoryImpl(
    private val remoteDataSource: QuestionRemoteDataSource
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
}
