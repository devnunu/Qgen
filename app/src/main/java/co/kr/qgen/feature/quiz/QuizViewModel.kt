package co.kr.qgen.feature.quiz

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.kr.qgen.core.data.repository.QuestionRepository
import co.kr.qgen.core.model.QGenSessionViewModel
import co.kr.qgen.core.model.Question
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuizUiState(
    val questions: List<Question> = emptyList(),
    val currentPage: Int = 0,
    val userAnswers: Map<String, String> = emptyMap(), // questionId -> choiceId
    val isLoading: Boolean = true,
    val error: String? = null
)

data class QuizResult(
    val totalQuestions: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val score: Int, // percentage
    val resultItems: List<ResultItem>
)

data class ResultItem(
    val question: Question,
    val userAnswer: String?, // choiceId
    val isCorrect: Boolean
)

class QuizViewModel(
    private val sessionViewModel: QGenSessionViewModel,
    private val questionRepository: QuestionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private val _shouldNavigateToResult = MutableStateFlow(false)
    val shouldNavigateToResult: StateFlow<Boolean> = _shouldNavigateToResult.asStateFlow()
    
    private val setId: String? = savedStateHandle["setId"]

    init {
        if (!setId.isNullOrBlank() && setId != "new") {
            loadQuestionsFromDb(setId)
        } else {
            loadQuestions()
        }
    }

    private fun loadQuestionsFromDb(setId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val questions = questionRepository.getQuestionsBySetId(setId)
            if (questions.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        questions = questions,
                        isLoading = false
                    )
                }
                
                val problemSet = questionRepository.getProblemSetById(setId)
                if (problemSet != null) {
                    val metadata = co.kr.qgen.core.model.QuestionSetMetadata(
                        topic = problemSet.topic,
                        difficulty = problemSet.difficulty,
                        totalCount = problemSet.count,
                        language = problemSet.language
                    )
                    sessionViewModel.setCurrentQuestionSet(questions, metadata)
                }
            } else {
                 _uiState.update { it.copy(isLoading = false, error = "문제를 불러올 수 없습니다.") }
            }
        }
    }

    private fun loadQuestions() {
        viewModelScope.launch {
            sessionViewModel.currentQuestions.collect { questions ->
                if (questions.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            questions = questions,
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    fun onAnswerSelected(questionId: String, choiceId: String) {
        _uiState.update { state ->
            state.copy(
                userAnswers = state.userAnswers + (questionId to choiceId)
            )
        }
    }

    fun onPageChanged(page: Int) {
        _uiState.update { it.copy(currentPage = page) }
    }

    fun submitQuiz() {
        val state = _uiState.value
        val questions = state.questions
        val userAnswers = state.userAnswers

        val resultItems = questions.map { question ->
            val userAnswer = userAnswers[question.id]
            val isCorrect = userAnswer == question.correctChoiceId
            ResultItem(
                question = question,
                userAnswer = userAnswer,
                isCorrect = isCorrect
            )
        }

        val correctCount = resultItems.count { it.isCorrect }
        val wrongCount = resultItems.count { !it.isCorrect }
        val score = if (questions.isNotEmpty()) {
            (correctCount * 100) / questions.size
        } else {
            0
        }

        val result = QuizResult(
            totalQuestions = questions.size,
            correctCount = correctCount,
            wrongCount = wrongCount,
            score = score,
            resultItems = resultItems
        )
        
        // Save to session view model
        sessionViewModel.setQuizResult(result)
        
        // Trigger navigation
        _shouldNavigateToResult.value = true
    }
    
    fun onNavigatedToResult() {
        _shouldNavigateToResult.value = false
    }

    fun getAnsweredCount(): Int {
        return _uiState.value.userAnswers.size
    }

    fun isQuestionAnswered(questionId: String): Boolean {
        return _uiState.value.userAnswers.containsKey(questionId)
    }
}
