package co.kr.qgen.feature.loading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.kr.qgen.core.data.repository.QuestionRepository
import co.kr.qgen.core.data.source.local.InMemoryDataSource
import co.kr.qgen.core.model.QGenSessionViewModel
import co.kr.qgen.core.model.ResultWrapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoadingUiState(
    val isLoading: Boolean = true,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null,
    val topic: String = "",

    // Batch progress fields
    val batchProgress: co.kr.qgen.core.model.BatchProgress? = null,
    val showPartialSuccessDialog: Boolean = false,
    val partialResult: co.kr.qgen.core.model.BatchGenerationResult? = null,

    // Store for partial success handling
    val currentBookId: String = "",
    val currentTags: String? = null
)

class LoadingViewModel(
    private val questionRepository: QuestionRepository,
    private val sessionViewModel: QGenSessionViewModel,
    private val inMemoryDataSource: InMemoryDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoadingUiState())
    val uiState: StateFlow<LoadingUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    init {
        startGeneration()
    }

    private fun startGeneration() {
        generationJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // InMemoryDataSource에서 요청 데이터 가져오기
            val pendingData = inMemoryDataSource.getPendingRequest()
            if (pendingData == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "요청 데이터를 찾을 수 없습니다"
                    )
                }
                return@launch
            }

            val (request, bookId, tags) = pendingData
            _uiState.update {
                it.copy(
                    topic = request.topic,
                    currentBookId = bookId,
                    currentTags = tags
                )
            }

            // Use batching for 10+ questions
            if (request.count >= 10) {
                handleBatchedGeneration(request, bookId, tags)
            } else {
                handleSingleGeneration(request, bookId, tags)
            }
        }
    }

    private suspend fun handleBatchedGeneration(
        request: co.kr.qgen.core.model.GenerateQuestionsRequest,
        bookId: String,
        tags: String?
    ) {
        questionRepository.generateQuestionsWithBatching(request, useMockApi = false)
            .collect { result ->
                when (result) {
                    is ResultWrapper.Loading -> {
                        // Already in loading state
                    }

                    is ResultWrapper.Progress -> {
                        _uiState.update { it.copy(batchProgress = result.progress) }
                    }

                    is ResultWrapper.Success -> {
                        val batchResult = result.value

                        if (batchResult.isPartialSuccess) {
                            // Show partial success dialog
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    showPartialSuccessDialog = true,
                                    partialResult = batchResult
                                )
                            }
                        } else {
                            // Complete success - show 100% progress
                            _uiState.update {
                                it.copy(
                                    batchProgress = co.kr.qgen.core.model.BatchProgress(
                                        currentBatch = request.count / 5 + if (request.count % 5 > 0) 1 else 0,
                                        totalBatches = request.count / 5 + if (request.count % 5 > 0) 1 else 0,
                                        questionsGenerated = batchResult.allQuestions.size,
                                        totalQuestions = batchResult.allQuestions.size
                                    )
                                )
                            }

                            // Wait for animation to complete (800ms animation + 300ms buffer)
                            delay(1100)

                            saveAndComplete(batchResult.allQuestions, batchResult.metadata, bookId, tags)
                        }
                    }

                    is ResultWrapper.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = result.message ?: "문제 생성에 실패했습니다"
                            )
                        }
                    }
                }
            }
    }

    private suspend fun handleSingleGeneration(
        request: co.kr.qgen.core.model.GenerateQuestionsRequest,
        bookId: String,
        tags: String?
    ) {
        questionRepository.generateQuestions(request, useMockApi = false)
            .collect { result ->
                when (result) {
                    is ResultWrapper.Loading -> {
                        // Already in loading state
                    }
                    is ResultWrapper.Progress -> {
                        // Not used in single generation
                    }
                    is ResultWrapper.Success -> {
                        val questions = result.value.first
                        val metadata = result.value.second
                        saveAndComplete(questions, metadata, bookId, tags)
                    }
                    is ResultWrapper.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = result.message ?: "문제 생성에 실패했습니다"
                            )
                        }
                    }
                }
            }
    }

    private suspend fun saveAndComplete(
        questions: List<co.kr.qgen.core.model.Question>,
        metadata: co.kr.qgen.core.model.QuestionSetMetadata,
        bookId: String,
        tags: String?
    ) {
        // DB 저장
        questionRepository.saveQuestionSet(questions, metadata, bookId, tags)

        // 세션에 저장
        sessionViewModel.setCurrentQuestionSet(questions, metadata)

        // 요청 데이터 정리
        inMemoryDataSource.clearPendingRequest()

        _uiState.update {
            it.copy(
                isLoading = false,
                isCompleted = true
            )
        }
    }

    // Handle partial success actions
    fun onAcceptPartialResult() {
        val result = _uiState.value.partialResult ?: return
        val bookId = _uiState.value.currentBookId
        val tags = _uiState.value.currentTags

        viewModelScope.launch {
            saveAndComplete(result.allQuestions, result.metadata, bookId, tags)
        }
    }

    fun onDismissPartialSuccess() {
        _uiState.update { it.copy(showPartialSuccessDialog = false) }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
    }
}
