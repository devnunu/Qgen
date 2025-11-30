package co.kr.qgen.feature.generation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.kr.qgen.core.data.repository.QuestionRepository
import co.kr.qgen.core.model.Difficulty
import co.kr.qgen.core.model.Language
import co.kr.qgen.core.model.QGenSessionViewModel
import co.kr.qgen.core.model.TopicPreset
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
    val recentTopics: List<String> = emptyList(),
    val tags: String = "",
    val difficulty: Difficulty = Difficulty.MIXED,
    val count: Int = 10,
    val choiceCount: Int = 4,
    val language: Language = Language.KO,
    val useMockApi: Boolean = false,
    val errorMessage: String? = null
)

sealed class GenerationSideEffect {
    data class NavigateToLoading(
        val topic: String,
        val difficulty: String,
        val count: Int,
        val choiceCount: Int,
        val tags: String?
    ) : GenerationSideEffect()
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
    
    fun onTagsChanged(tags: String) {
        _uiState.update { it.copy(tags = tags) }
    }
    
    fun onRecentTopicSelected(topic: String) {
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
        // 5~20개로 제한
        _uiState.update { it.copy(count = count.coerceIn(5, 20)) }
    }

    fun onChoiceCountChanged(choiceCount: Int) {
        _uiState.update { it.copy(choiceCount = choiceCount) }
    }

    fun onMockApiToggled(useMock: Boolean) {
        _uiState.update { it.copy(useMockApi = useMock) }
    }
    
    private fun addRecentTopic(topic: String) {
        if (topic.isBlank()) return
        
        val current = _uiState.value.recentTopics.toMutableList()
        current.remove(topic) // 중복 제거
        current.add(0, topic) // 최신순 추가
        
        // 최대 3개 유지
        if (current.size > 3) {
            current.removeAt(current.lastIndex)
        }
        
        _uiState.update { it.copy(recentTopics = current) }
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

        if (state.count !in 1..20) {
            viewModelScope.launch {
                _sideEffects.send(GenerationSideEffect.ShowError("문항 수는 1~20 사이여야 합니다"))
            }
            return
        }
        
        // 최근 검색어 추가
        addRecentTopic(state.topic)

        // 로딩 화면으로 이동 (파라미터 전달)
        viewModelScope.launch {
            _sideEffects.send(
                GenerationSideEffect.NavigateToLoading(
                    topic = state.topic,
                    difficulty = state.difficulty.value,
                    count = state.count,
                    choiceCount = state.choiceCount,
                    tags = state.tags.ifBlank { null }
                )
            )
        }
    }
}
