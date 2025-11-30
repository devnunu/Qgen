package co.kr.qgen.core.model

import kotlinx.serialization.Serializable

@Serializable
data class GenerateQuestionsRequest(
    val topic: String,
    val description: String? = null, // 주제에 대한 상세 설명 (최대 300자)
    val subtopics: List<String>? = null,
    val difficulty: String, // "easy" | "medium" | "hard" | "mixed"
    val count: Int,
    val choiceCount: Int? = 4,
    val language: String? = "ko"
)
