package co.kr.qgen.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.kr.qgen.core.ui.components.ExamFilterButton
import co.kr.qgen.core.ui.theme.ExamColors
import co.kr.qgen.core.ui.theme.ExamShapes
import co.kr.qgen.core.ui.theme.ExamTypography
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
    onNavigateToQuiz: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    var showRenameDialog by remember { mutableStateOf<String?>(null) }
    var showRegenerateDialog by remember { mutableStateOf<String?>(null) }
    
    // Rename Dialog
    if (showRenameDialog != null) {
        val setId = showRenameDialog!!
        val currentTitle = uiState.sets.find { it.id == setId }?.title ?: ""
        var newTitle by remember { mutableStateOf(currentTitle) }
        
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("세트 이름 변경") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("이름") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameProblemSet(setId, newTitle)
                        showRenameDialog = null
                    }
                ) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("취소")
                }
            }
        )
    }
    
    // Regenerate Dialog
    if (showRegenerateDialog != null) {
        val setId = showRegenerateDialog!!
        
        AlertDialog(
            onDismissRequest = { showRegenerateDialog = null },
            title = { Text("문제 다시 생성하기") },
            text = { Text("이 세트의 문제들을 모두 새로 생성합니다. 기존 문제들은 삭제됩니다. 계속하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.regenerateProblemSet(setId)
                        showRegenerateDialog = null
                    }
                ) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateDialog = null }) {
                    Text("취소")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(ExamColors.ExamBackground)) {
                CenterAlignedTopAppBar(
                    title = { Text("내 문제집", style = ExamTypography.examTitleTextStyle) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = ExamColors.ExamBackground
                    ),
                    actions = {
                        IconButton(onClick = { viewModel.toggleShowFavoritesOnly() }) {
                            Icon(
                                imageVector = if (uiState.showFavoritesOnly) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = "즐겨찾기 필터",
                                tint = if (uiState.showFavoritesOnly) ExamColors.ExamAccent else ExamColors.ExamTextSecondary
                            )
                        }
                    }
                )
                
                // Search Bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("세트 이름, 주제, 태그 검색") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ExamColors.ExamCardBackground,
                        unfocusedContainerColor = ExamColors.ExamCardBackground,
                        focusedBorderColor = ExamColors.ExamBorderGray,
                        unfocusedBorderColor = ExamColors.ExamButtonBorder
                    ),
                    shape = ExamShapes.ButtonShape
                )
                
                // Tag Filters
                val allTags = uiState.sets.flatMap { it.tags }.distinct().take(10) // 상위 10개만 표시
                if (allTags.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allTags) { tag ->
                            ExamFilterButton(
                                text = "#$tag",
                                isSelected = uiState.tagFilter == tag,
                                onClick = { viewModel.onTagFilterChanged(tag) }
                            )
                        }
                    }
                }
            }
        },
        containerColor = ExamColors.ExamBackground
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (uiState.isRegenerating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uiState.sets) { set ->
                    ProblemSetCard(
                        summary = set,
                        onClick = { onNavigateToQuiz(set.id) },
                        onFavoriteClick = { viewModel.toggleFavorite(set.id) },
                        onRenameClick = { showRenameDialog = set.id },
                        onRegenerateClick = { showRegenerateDialog = set.id }
                    )
                }
            }
            
            // Error Snackbar
            if (uiState.errorMessage != null) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearErrorMessage() }) {
                            Text("닫기")
                        }
                    }
                ) {
                    Text(uiState.errorMessage!!)
                }
            }
        }
    }
}

@Composable
fun ProblemSetCard(
    summary: ProblemSetSummary,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onRenameClick: () -> Unit,
    onRegenerateClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = ExamColors.ExamCardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = ExamShapes.CardShape
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.title,
                        style = ExamTypography.examTitleTextStyle,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${summary.count}문항 • ${summary.difficulty} • ${SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(summary.createdAt))}",
                        style = ExamTypography.examSmallTextStyle,
                        color = ExamColors.ExamTextSecondary
                    )
                }
                
                Row {
                    IconButton(onClick = onFavoriteClick) {
                        Icon(
                            imageVector = if (summary.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = "즐겨찾기",
                            tint = if (summary.isFavorite) ExamColors.ExamAccent else ExamColors.ExamTextSecondary
                        )
                    }
                    
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "메뉴",
                                tint = ExamColors.ExamTextSecondary
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("문제 다시 생성하기") },
                                onClick = {
                                    showMenu = false
                                    onRegenerateClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("세트 이름 변경하기") },
                                onClick = {
                                    showMenu = false
                                    onRenameClick()
                                }
                            )
                        }
                    }
                }
            }
            
            if (summary.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    summary.tags.forEach { tag ->
                        Surface(
                            color = ExamColors.ExamBackground,
                            shape = ExamShapes.ButtonShape,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ExamColors.ExamButtonBorder)
                        ) {
                            Text(
                                text = "#$tag",
                                style = ExamTypography.examSmallTextStyle,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = ExamColors.ExamTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
