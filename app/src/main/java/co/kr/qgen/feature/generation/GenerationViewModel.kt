package co.kr.qgen.feature.generation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.kr.qgen.core.model.*
import co.kr.qgen.core.network.QGenApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GenerationUiState(
    val topic: String = "",
    val selectedPreset: TopicPreset? = null,
    val difficulty: Difficulty = Difficulty.MIXED,
    val count: Int = 20,
    val choiceCount: Int = 4,
    val language: Language = Language.KO,
    val useMockApi: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed class GenerationSideEffect {
    data object NavigateToQuiz : GenerationSideEffect()
    data class ShowError(val message: String) : GenerationSideEffect()
}

class GenerationViewModel(
    private val api: QGenApi,
    private val sessionViewModel: QGenSessionViewModel
) : ViewModel() {

    private val _uiState = MutableStateFlow(GenerationUiState())
    val uiState: StateFlow<GenerationUiState> = _uiState.asStateFlow()

    private val _sideEffects = Channel<GenerationSideEffect>(Channel.BUFFERED)
    val sideEffects = _sideEffects.receiveAsFlow()

    fun onTopicChanged(topic: String) {
        _uiState.update { it.copy(topic = topic, selectedPreset = null) }
    }

    fun onPresetSelected(preset: TopicPreset) {
        _uiState.update {
            it.copy(
                selectedPreset = preset,
                topic = if (preset != TopicPreset.CUSTOM) preset.topicValue else it.topic
            )
        }
    }

    fun onDifficultyChanged(difficulty: Difficulty) {
        _uiState.update { it.copy(difficulty = difficulty) }
    }

    fun onCountChanged(count: Int) {
        _uiState.update { it.copy(count = count.coerceIn(1, 50)) }
    }

    fun onChoiceCountChanged(choiceCount: Int) {
        _uiState.update { it.copy(choiceCount = choiceCount) }
    }

    fun onLanguageChanged(language: Language) {
        _uiState.update { it.copy(language = language) }
    }

    fun onMockApiToggled(useMock: Boolean) {
        _uiState.update { it.copy(useMockApi = useMock) }
    }

    fun onGenerateClicked() {
        val state = _uiState.value

        // Validation
        if (state.topic.isBlank()) {
            viewModelScope.launch {
                _sideEffects.send(GenerationSideEffect.ShowError("주제를 입력해주세요"))
            }
            return
        }

        if (state.count !in 1..50) {
            viewModelScope.launch {
                _sideEffects.send(GenerationSideEffect.ShowError("문항 수는 1~50 사이여야 합니다"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val request = GenerateQuestionsRequest(
                    topic = state.topic,
                    difficulty = state.difficulty.value,
                    count = state.count,
                    choiceCount = state.choiceCount,
                    language = state.language.value
                )

                val response = if (state.useMockApi) {
                    api.mockQuestions(request)
                } else {
                    api.generateQuestions(request)
                }

                if (response.success && response.data != null) {
                    val questions = response.data.questions
                    val metadata = QuestionSetMetadata(
                        topic = state.topic,
                        difficulty = state.difficulty.value,
                        totalCount = questions.size,
                        language = state.language.value
                    )
                    
                    sessionViewModel.setCurrentQuestionSet(questions, metadata)
                    _sideEffects.send(GenerationSideEffect.NavigateToQuiz)
                } else {
                    _sideEffects.send(
                        GenerationSideEffect.ShowError(
                            response.error ?: "문제 생성에 실패했습니다"
                        )
                    )
                }
            } catch (e: Exception) {
                _sideEffects.send(
                    GenerationSideEffect.ShowError(
                        e.message ?: "네트워크 오류가 발생했습니다"
                    )
                )
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
