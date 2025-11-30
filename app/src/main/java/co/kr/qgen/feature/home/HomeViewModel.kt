package co.kr.qgen.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.kr.qgen.core.data.repository.ProblemBookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProblemBookSummary(
    val id: String,
    val title: String,
    val createdAt: Long,
    val lastPlayedAt: Long?,
    val totalSets: Int,
    val totalProblems: Int
)

data class HomeUiState(
    val books: List<ProblemBookSummary> = emptyList(),
    val searchQuery: String = "",
    val errorMessage: String? = null
)

class HomeViewModel(
    private val bookRepository: ProblemBookRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _bookStats = MutableStateFlow<Map<String, Pair<Int, Int>>>(emptyMap())

    val uiState: StateFlow<HomeUiState> = combine(
        bookRepository.getAllBooks(),
        _searchQuery,
        _bookStats,
        _errorMessage
    ) { books, query, stats, error ->
        val summaries = books.map { entity ->
            val (setCount, problemCount) = stats[entity.id] ?: Pair(0, 0)
            ProblemBookSummary(
                id = entity.id,
                title = entity.title,
                createdAt = entity.createdAt,
                lastPlayedAt = entity.lastPlayedAt,
                totalSets = setCount,
                totalProblems = problemCount
            )
        }

        val filteredBooks = summaries.filter { summary ->
            if (query.isBlank()) true else {
                summary.title.contains(query, ignoreCase = true)
            }
        }

        HomeUiState(
            books = filteredBooks,
            searchQuery = query,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    init {
        loadBookStats()
    }

    private fun loadBookStats() {
        viewModelScope.launch {
            bookRepository.getAllBooks().collect { books ->
                val statsMap = books.associate { book ->
                    val stats = bookRepository.getBookStats(book.id)
                    book.id to Pair(stats.totalSets, stats.totalProblems)
                }
                _bookStats.value = statsMap
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun createBook(title: String, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val bookId = bookRepository.createBook(title)
                loadBookStats() // Refresh stats
                onSuccess(bookId)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "문제집 생성에 실패했습니다"
            }
        }
    }

    fun renameBook(bookId: String, newTitle: String) {
        viewModelScope.launch {
            bookRepository.updateBookTitle(bookId, newTitle)
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            bookRepository.deleteBook(bookId)
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}
