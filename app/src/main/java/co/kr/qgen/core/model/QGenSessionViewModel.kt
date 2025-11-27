package co.kr.qgen.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Session-level ViewModel to share question data between screens
 */
class QGenSessionViewModel {
    private val _currentQuestions = MutableStateFlow<List<Question>>(emptyList())
    val currentQuestions: StateFlow<List<Question>> = _currentQuestions.asStateFlow()

    private val _currentMetadata = MutableStateFlow<QuestionSetMetadata?>(null)
    val currentMetadata: StateFlow<QuestionSetMetadata?> = _currentMetadata.asStateFlow()

    fun setCurrentQuestionSet(questions: List<Question>, metadata: QuestionSetMetadata) {
        _currentQuestions.value = questions
        _currentMetadata.value = metadata
    }

    fun clearQuestionSet() {
        _currentQuestions.value = emptyList()
        _currentMetadata.value = null
    }
}

data class QuestionSetMetadata(
    val topic: String,
    val difficulty: String,
    val totalCount: Int,
    val language: String
)
