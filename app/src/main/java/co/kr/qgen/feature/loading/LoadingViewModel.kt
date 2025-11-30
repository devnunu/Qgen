package co.kr.qgen.feature.loading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.kr.qgen.core.data.repository.QuestionRepository
import co.kr.qgen.core.data.source.local.InMemoryDataSource
import co.kr.qgen.core.model.QGenSessionViewModel
import co.kr.qgen.core.model.ResultWrapper
import kotlinx.coroutines.Job
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
    val currentProgress: Int = 0,  // 현재까지 생성된 문제 수
    val totalCount: Int = 0         // 총 요청한 문제 수
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
                    totalCount = request.count
                )
            }

            // Use parallel batching for requests > 5 questions
            if (request.count > 5) {
                handleParallelBatchGeneration(request, bookId, tags)
            } else {
                handleSingleGeneration(request, bookId, tags)
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

    private suspend fun handleParallelBatchGeneration(
        request: co.kr.qgen.core.model.GenerateQuestionsRequest,
        bookId: String,
        tags: String?
    ) {
        questionRepository.generateQuestionsWithParallelBatching(
            request = request,
            useMockApi = false,
            batchSize = 5
        ).collect { result ->
            when (result) {
                is ResultWrapper.Loading -> {
                    // Already in loading state
                }
                is ResultWrapper.Progress -> {
                    // Update progress
                    _uiState.update {
                        it.copy(
                            currentProgress = result.progress.questionsGenerated
                        )
                    }
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
        // Check if this is a regeneration or new generation
        val regenerationSetId = inMemoryDataSource.getRegenerationSetId()

        if (regenerationSetId != null) {
            // Regeneration mode: Replace problems in existing set
            questionRepository.replaceProblemsInSet(regenerationSetId, questions)
        } else {
            // New generation mode: Create new problem set
            questionRepository.saveQuestionSet(questions, metadata, bookId, tags)
        }

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

    fun cancelGeneration() {
        generationJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
    }
}
