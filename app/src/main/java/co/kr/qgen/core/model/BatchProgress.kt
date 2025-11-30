package co.kr.qgen.core.model

data class BatchProgress(
    val currentBatch: Int,      // 1-indexed
    val totalBatches: Int,
    val questionsGenerated: Int,
    val totalQuestions: Int
)

data class BatchGenerationResult(
    val allQuestions: List<Question>,
    val metadata: QuestionSetMetadata,
    val failedBatches: List<Int> = emptyList()
) {
    val isPartialSuccess: Boolean = failedBatches.isNotEmpty()
    val successfulQuestions: Int = allQuestions.size
}
