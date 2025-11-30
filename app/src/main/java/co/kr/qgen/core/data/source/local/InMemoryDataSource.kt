package co.kr.qgen.core.data.source.local

import co.kr.qgen.core.model.GenerateQuestionsRequest

/**
 * In-memory data source for temporary data storage between screens
 */
class InMemoryDataSource {
    private var pendingGenerationRequest: GenerateQuestionsRequest? = null
    private var pendingTags: String? = null
    private var pendingBookId: String? = null

    fun savePendingRequest(request: GenerateQuestionsRequest, bookId: String, tags: String?) {
        pendingGenerationRequest = request
        pendingBookId = bookId
        pendingTags = tags
    }

    fun getPendingRequest(): Triple<GenerateQuestionsRequest, String, String?>? {
        val request = pendingGenerationRequest ?: return null
        val bookId = pendingBookId ?: return null
        val tags = pendingTags
        return Triple(request, bookId, tags)
    }

    fun clearPendingRequest() {
        pendingGenerationRequest = null
        pendingBookId = null
        pendingTags = null
    }
}
