package co.kr.qgen.feature.loading

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.kr.qgen.core.data.repository.QuestionRepository
import co.kr.qgen.core.model.GenerateQuestionsRequest
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
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoadingUiState())
    val uiState: StateFlow<LoadingUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    private val topic: String = savedStateHandle["topic"] ?: ""
    private val difficulty: String = savedStateHandle["difficulty"] ?: "medium"
    private val count: Int = savedStateHandle["count"] ?: 10
    private val choiceCount: Int = savedStateHandle["choiceCount"] ?: 4
    private val tags: String? = savedStateHandle["tags"]

    init {
        _uiState.update { it.copy(topic = topic) }
        startGeneration()
    }

    private fun startGeneration() {
        generationJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val request = GenerateQuestionsRequest(
                topic = topic,
                difficulty = difficulty,
                count = count,
                choiceCount = choiceCount,
                language = "ko"
            )

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
                            questionRepository.saveQuestionSet(questions, metadata, tags)

                            // 세션에 저장
                            sessionViewModel.setCurrentQuestionSet(questions, metadata)

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
