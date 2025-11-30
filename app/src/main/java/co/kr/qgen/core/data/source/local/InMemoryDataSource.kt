package co.kr.qgen.core.data.source.local

import co.kr.qgen.core.model.GenerateQuestionsRequest

/**
 * In-memory data source for temporary data storage between screens
 */
class InMemoryDataSource {
    private var pendingGenerationRequest: GenerateQuestionsRequest? = null
    private var pendingTags: String? = null

    fun savePendingRequest(request: GenerateQuestionsRequest, tags: String?) {
        pendingGenerationRequest = request
        pendingTags = tags
    }

    fun getPendingRequest(): Pair<GenerateQuestionsRequest, String?>? {
        val request = pendingGenerationRequest ?: return null
        val tags = pendingTags
        return Pair(request, tags)
    }

    fun clearPendingRequest() {
        pendingGenerationRequest = null
        pendingTags = null
    }
}
