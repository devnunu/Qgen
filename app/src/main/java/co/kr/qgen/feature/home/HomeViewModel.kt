package co.kr.qgen.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.kr.qgen.core.data.repository.QuestionRepository
import co.kr.qgen.core.model.ResultWrapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProblemSetSummary(
    val id: String,
    val title: String,
    val topic: String,
    val difficulty: String,
    val language: String,
    val count: Int,
    val createdAt: Long,
    val lastPlayedAt: Long?,
    val score: Int?,
    val isFavorite: Boolean,
    val tags: List<String>
)

data class HomeUiState(
    val sets: List<ProblemSetSummary> = emptyList(),
    val isEditing: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val showFavoritesOnly: Boolean = false,
    val searchQuery: String = "",
    val tagFilter: String? = null,
    val isRegenerating: Boolean = false,
    val errorMessage: String? = null
)

class HomeViewModel(
    private val questionRepository: QuestionRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _tagFilter = MutableStateFlow<String?>(null)
    private val _showFavoritesOnly = MutableStateFlow(false)
    private val _isRegenerating = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        questionRepository.getAllProblemSets(),
        combine(_searchQuery, _tagFilter, _showFavoritesOnly) { query, tag, fav -> Triple(query, tag, fav) },
        _isRegenerating,
        _errorMessage
    ) { sets, (query, tag, favoritesOnly), regenerating, error ->
        val summaries = sets.map { entity ->
            ProblemSetSummary(
                id = entity.id,
                title = entity.title ?: entity.topic,
                topic = entity.topic,
                difficulty = entity.difficulty,
                language = entity.language,
                count = entity.count,
                createdAt = entity.createdAt,
                lastPlayedAt = entity.lastPlayedAt,
                score = entity.score,
                isFavorite = entity.isFavorite,
                tags = entity.tags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            )
        }

        val filteredSets = summaries.filter { summary ->
            val matchesQuery = if (query.isBlank()) true else {
                summary.title.contains(query, ignoreCase = true) ||
                summary.topic.contains(query, ignoreCase = true) ||
                summary.tags.any { it.contains(query, ignoreCase = true) }
            }
            val matchesTag = if (tag == null) true else summary.tags.contains(tag)
            val matchesFavorite = if (favoritesOnly) summary.isFavorite else true

            matchesQuery && matchesTag && matchesFavorite
        }

        HomeUiState(
            sets = filteredSets,
            searchQuery = query,
            tagFilter = tag,
            showFavoritesOnly = favoritesOnly,
            isRegenerating = regenerating,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onTagFilterChanged(tag: String?) {
        _tagFilter.value = if (_tagFilter.value == tag) null else tag
    }

    fun toggleShowFavoritesOnly() {
        _showFavoritesOnly.value = !_showFavoritesOnly.value
    }

    fun toggleFavorite(setId: String) {
        viewModelScope.launch {
            val currentSet = uiState.value.sets.find { it.id == setId } ?: return@launch
            questionRepository.toggleFavorite(setId, !currentSet.isFavorite)
        }
    }

    fun renameProblemSet(setId: String, newTitle: String) {
        viewModelScope.launch {
            questionRepository.updateTitle(setId, newTitle)
        }
    }

    fun regenerateProblemSet(setId: String) {
        viewModelScope.launch {
            _isRegenerating.value = true
            _errorMessage.value = null
            
            val result = questionRepository.regenerateProblemSet(setId)
            
            _isRegenerating.value = false
            if (result is ResultWrapper.Error) {
                _errorMessage.value = result.message ?: "재생성에 실패했습니다"
            }
        }
    }
    
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}
