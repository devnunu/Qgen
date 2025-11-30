package co.kr.qgen.feature.bookdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.kr.qgen.core.data.repository.ProblemBookRepository
import co.kr.qgen.core.data.repository.QuestionRepository
import co.kr.qgen.core.data.source.local.InMemoryDataSource
import co.kr.qgen.core.data.source.local.entity.ProblemBookEntity
import co.kr.qgen.core.data.source.local.entity.ProblemSetEntity
import co.kr.qgen.core.model.GenerateQuestionsRequest
import co.kr.qgen.core.model.QGenSessionViewModel
import co.kr.qgen.core.model.QuestionSetMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookDetailUiState(
    val book: ProblemBookEntity? = null,
    val problemSets: List<ProblemSetEntity> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val totalProblems: Int = 0,
    val wrongProblemsCount: Int = 0
)

class BookDetailViewModel(
    private val bookRepository: ProblemBookRepository,
    private val questionRepository: QuestionRepository,
    private val sessionViewModel: QGenSessionViewModel,
    private val inMemoryDataSource: InMemoryDataSource,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val bookId: String = savedStateHandle["bookId"] ?: ""

    private val _book = MutableStateFlow<ProblemBookEntity?>(null)
    private val _isLoading = MutableStateFlow(true)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _totalProblems = MutableStateFlow(0)
    private val _wrongProblemsCount = MutableStateFlow(0)

    val uiState: StateFlow<BookDetailUiState> = combine(
        _book,
        bookRepository.getProblemSetsByBookId(bookId),
        _isLoading,
        _errorMessage,
        _totalProblems,
        _wrongProblemsCount
    ) { values ->
        val book = values[0] as? ProblemBookEntity
        val sets = values[1] as? List<*> ?: emptyList<ProblemSetEntity>()
        val loading = values[2] as? Boolean ?: true
        val error = values[3] as? String?
        val total = values[4] as? Int ?: 0
        val wrong = values[5] as? Int ?: 0

        BookDetailUiState(
            book = book,
            problemSets = sets.filterIsInstance<ProblemSetEntity>(),
            isLoading = loading,
            errorMessage = error,
            totalProblems = total,
            wrongProblemsCount = wrong
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BookDetailUiState()
    )

    init {
        loadBookDetails()
    }

    private fun loadBookDetails() {
        viewModelScope.launch {
            try {
                val book = bookRepository.getBookById(bookId)
                _book.value = book

                // Load stats
                val stats = bookRepository.getBookStats(bookId)
                _totalProblems.value = stats.totalProblems

                // Count wrong problems
                val wrongProblems = bookRepository.getWrongProblems(bookId, Int.MAX_VALUE)
                _wrongProblemsCount.value = wrongProblems.size

                _isLoading.value = false
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "문제집 로딩에 실패했습니다"
                _isLoading.value = false
            }
        }
    }

    // Quick Action: Random Quiz (ad-hoc session - 서버 호출 없이 로컬 문제만 조합)
    fun startRandomQuiz(count: Int, onReady: () -> Unit) {
        viewModelScope.launch {
            try {
                val questions = bookRepository.getRandomProblems(bookId, count)
                if (questions.isEmpty()) {
                    _errorMessage.value = "선택할 문제가 없습니다"
                    return@launch
                }

                // Create metadata for the ad-hoc session
                val metadata = QuestionSetMetadata(
                    topic = "랜덤 문제",
                    difficulty = "mixed",
                    totalCount = questions.size,
                    language = "ko"
                )

                // Set the session (ad-hoc, not saved to DB)
                sessionViewModel.setCurrentQuestionSet(questions, metadata)

                // Update last played time
                bookRepository.updateLastPlayedAt(bookId, System.currentTimeMillis())

                onReady()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "랜덤 문제 생성에 실패했습니다"
            }
        }
    }

    // Quick Action: Wrong Problems Quiz (ad-hoc session)
    fun startWrongProblemsQuiz(count: Int, onReady: () -> Unit) {
        viewModelScope.launch {
            try {
                val wrongProblems = bookRepository.getWrongProblems(bookId, count)
                if (wrongProblems.isEmpty()) {
                    _errorMessage.value = "틀린 문제가 없습니다"
                    return@launch
                }

                // Create metadata for the ad-hoc session
                val metadata = QuestionSetMetadata(
                    topic = "틀린 문제 복습",
                    difficulty = "mixed",
                    totalCount = wrongProblems.size,
                    language = "ko"
                )

                // Set the session (ad-hoc, not saved to DB)
                sessionViewModel.setCurrentQuestionSet(wrongProblems, metadata)

                // Update last played time
                bookRepository.updateLastPlayedAt(bookId, System.currentTimeMillis())

                onReady()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "틀린 문제 불러오기에 실패했습니다"
            }
        }
    }

    fun toggleFavorite(setId: String) {
        viewModelScope.launch {
            val currentSet = uiState.value.problemSets.find { it.id == setId } ?: return@launch
            questionRepository.toggleFavorite(setId, !currentSet.isFavorite)
        }
    }

    fun renameProblemSet(setId: String, newTitle: String) {
        viewModelScope.launch {
            questionRepository.updateTitle(setId, newTitle)
        }
    }

    fun regenerateProblemSet(setId: String, onNavigateToLoading: () -> Unit) {
        viewModelScope.launch {
            _errorMessage.value = null

            try {
                // Get problem set from DB
                val problemSet = questionRepository.getProblemSetById(setId)
                if (problemSet == null) {
                    _errorMessage.value = "문제 세트를 찾을 수 없습니다"
                    return@launch
                }

                // Create request from existing set
                val request = GenerateQuestionsRequest(
                    topic = problemSet.topic,
                    difficulty = problemSet.difficulty,
                    count = problemSet.count,
                    language = problemSet.language
                )

                // Save to InMemoryDataSource for regeneration
                inMemoryDataSource.savePendingRegeneration(
                    request = request,
                    bookId = bookId,
                    setId = setId
                )

                // Navigate to loading screen
                onNavigateToLoading()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "재생성 준비 중 오류가 발생했습니다"
            }
        }
    }

    fun deleteProblemSet(setId: String) {
        viewModelScope.launch {
            questionRepository.deleteProblemSet(setId)
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}
