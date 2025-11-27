package co.kr.qgen.core.model

import kotlinx.serialization.Serializable

@Serializable
data class GenerateQuestionsRequest(
    val topic: String,
    val subtopics: List<String>? = null,
    val difficulty: String, // "easy" | "medium" | "hard" | "mixed"
    val count: Int,
    val choiceCount: Int? = 4,
    val language: String? = "ko"
)
