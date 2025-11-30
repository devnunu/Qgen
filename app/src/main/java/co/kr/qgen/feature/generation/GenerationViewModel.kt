package co.kr.qgen.feature.generation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.kr.qgen.core.data.repository.QuestionRepository
import co.kr.qgen.core.model.*
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
    private val questionRepository: QuestionRepository,
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

            val request = GenerateQuestionsRequest(
                topic = state.topic,
                difficulty = state.difficulty.value,
                count = state.count,
                choiceCount = state.choiceCount,
                language = state.language.value
            )

            questionRepository.generateQuestions(request, state.useMockApi)
                .collect { result ->
                    when (result) {
                        is ResultWrapper.Loading -> {
                            // Already in loading state
                        }
                        is ResultWrapper.Success -> {
                            val questions = result.value.first
                            val metadata = result.value.second
                            sessionViewModel.setCurrentQuestionSet(questions, metadata)
                            _uiState.update { it.copy(isLoading = false) }
                            _sideEffects.send(GenerationSideEffect.NavigateToQuiz)
                        }
                        is ResultWrapper.Error -> {
                            _uiState.update { it.copy(isLoading = false) }
                            _sideEffects.send(
                                GenerationSideEffect.ShowError(
                                    result.message ?: "문제 생성에 실패했습니다"
                                )
                            )
                        }
                    }
                }
        }
    }
}
