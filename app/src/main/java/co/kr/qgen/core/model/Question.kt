package co.kr.qgen.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Question(
    val id: String,
    val stem: String,
    val choices: List<QuestionChoice>,
    val correctChoiceId: String,
    val explanation: String,
    val metadata: QuestionMetadata
)

@Serializable
data class QuestionChoice(
    val id: String,
    val text: String
)

@Serializable
data class QuestionMetadata(
    val topic: String,
    val difficulty: String // "easy" | "medium" | "hard"
)
