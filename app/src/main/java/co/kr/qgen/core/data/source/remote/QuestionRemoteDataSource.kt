package co.kr.qgen.core.data.source.remote

import co.kr.qgen.core.model.GenerateQuestionsRequest
import co.kr.qgen.core.model.GenerateQuestionsResponse
import co.kr.qgen.core.model.HealthResponse
import co.kr.qgen.core.network.QGenApi

/**
 * Remote data source for question generation API
 */
interface QuestionRemoteDataSource {
    suspend fun checkHealth(): HealthResponse
    suspend fun generateQuestions(request: GenerateQuestionsRequest): GenerateQuestionsResponse
    suspend fun generateMockQuestions(request: GenerateQuestionsRequest): GenerateQuestionsResponse
}

class QuestionRemoteDataSourceImpl(
    private val api: QGenApi
) : QuestionRemoteDataSource {
    
    override suspend fun checkHealth(): HealthResponse {
        return api.health()
    }
    
    override suspend fun generateQuestions(request: GenerateQuestionsRequest): GenerateQuestionsResponse {
        return api.generateQuestions(request)
    }
    
    override suspend fun generateMockQuestions(request: GenerateQuestionsRequest): GenerateQuestionsResponse {
        return api.mockQuestions(request)
    }
}
