package co.kr.qgen.core.network

import co.kr.qgen.core.model.GenerateQuestionsRequest
import co.kr.qgen.core.model.GenerateQuestionsResponse
import co.kr.qgen.core.model.HealthResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface QGenApi {
    @GET("/api/health")
    suspend fun health(): HealthResponse

    @POST("/api/generate-questions")
    suspend fun generateQuestions(
        @Body request: GenerateQuestionsRequest
    ): GenerateQuestionsResponse
    
    @POST("/api/mock-questions")
    suspend fun mockQuestions(
        @Body request: GenerateQuestionsRequest
    ): GenerateQuestionsResponse
}
