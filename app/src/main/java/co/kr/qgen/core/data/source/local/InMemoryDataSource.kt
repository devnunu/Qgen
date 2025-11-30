package co.kr.qgen.core.data.source.local

import co.kr.qgen.core.model.GenerateQuestionsRequest

/**
 * In-memory data source for temporary data storage between screens
 */
class InMemoryDataSource {
    private var pendingGenerationRequest: GenerateQuestionsRequest? = null
    private var pendingTags: String? = null
    private var pendingBookId: String? = null
    private var regenerationSetId: String? = null  // 재생성할 세트 ID (null이면 새로 생성)

    fun savePendingRequest(request: GenerateQuestionsRequest, bookId: String, tags: String?) {
        pendingGenerationRequest = request
        pendingBookId = bookId
        pendingTags = tags
        regenerationSetId = null  // 새로 생성
    }

    fun savePendingRegeneration(request: GenerateQuestionsRequest, bookId: String, setId: String) {
        pendingGenerationRequest = request
        pendingBookId = bookId
        pendingTags = null
        regenerationSetId = setId  // 재생성 모드
    }

    fun getPendingRequest(): Triple<GenerateQuestionsRequest, String, String?>? {
        val request = pendingGenerationRequest ?: return null
        val bookId = pendingBookId ?: return null
        val tags = pendingTags
        return Triple(request, bookId, tags)
    }

    fun getRegenerationSetId(): String? {
        return regenerationSetId
    }

    fun clearPendingRequest() {
        pendingGenerationRequest = null
        pendingBookId = null
        pendingTags = null
        regenerationSetId = null
    }
}
