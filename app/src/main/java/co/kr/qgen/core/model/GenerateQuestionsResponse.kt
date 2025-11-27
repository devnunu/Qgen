package co.kr.qgen.core.model

import kotlinx.serialization.Serializable

@Serializable
data class GenerateQuestionsResponse(
    val success: Boolean,
    val data: QuestionData? = null,
    val error: String? = null
)

@Serializable
data class QuestionData(
    val questions: List<Question>
)
