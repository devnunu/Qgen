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
    val topic: String = ""
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
            _uiState.update { it.copy(topic = request.topic) }

            questionRepository.generateQuestions(request, useMockApi = false)
                .collect { result ->
                    when (result) {
                        is ResultWrapper.Loading -> {
                            // Already in loading state
                        }
                        is ResultWrapper.Success -> {
                            val questions = result.value.first
                            val metadata = result.value.second

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
    }

    fun cancelGeneration() {
        generationJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
    }
}
