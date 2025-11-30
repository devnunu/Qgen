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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.kr.qgen.core.ui.components.QGenScaffold
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
    onNavigateToBookDetail: (String) -> Unit,
    onNavigateToCreateBook: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

    // Create Book Dialog
    if (showCreateDialog) {
        var title by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("새 문제집 만들기") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("문제집 이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createBook(title) { bookId ->
                            showCreateDialog = false
                            onNavigateToBookDetail(bookId)
                        }
                    },
                    enabled = title.isNotBlank()
                ) {
                    Text("만들기")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("취소")
                }
            },
            containerColor = ExamColors.ExamCardBackground
        )
    }

    // Rename Dialog
    if (showRenameDialog != null) {
        val bookId = showRenameDialog!!
        val currentTitle = uiState.books.find { it.id == bookId }?.title ?: ""
        var newTitle by remember { mutableStateOf(currentTitle) }

        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("문제집 이름 변경") },
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
                        viewModel.renameBook(bookId, newTitle)
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
            },
            containerColor = ExamColors.ExamCardBackground
        )
    }

    // Delete Dialog
    if (showDeleteDialog != null) {
        val bookId = showDeleteDialog!!
        val book = uiState.books.find { it.id == bookId }

        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("문제집 삭제") },
            text = {
                Column {
                    Text("이 문제집을 삭제하시겠습니까?")
                    if (book != null && book.totalSets > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "주의: 이 문제집에 포함된 ${book.totalSets}개의 문제 세트와 ${book.totalProblems}개의 문제가 함께 삭제됩니다.",
                            color = ExamColors.ErrorColor
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBook(bookId)
                        showDeleteDialog = null
                    }
                ) {
                    Text("삭제", color = ExamColors.ErrorColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("취소")
                }
            },
            containerColor = ExamColors.ExamCardBackground
        )
    }

    QGenScaffold(
        topBar = {
            Column(modifier = Modifier.background(ExamColors.ExamBackground)) {
                CenterAlignedTopAppBar(
                    title = { Text("내 문제집", style = ExamTypography.examTitleTextStyle) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = ExamColors.ExamBackground
                    )
                )

                // Search Bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("문제집 이름 검색") },
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
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = ExamColors.ExamAccent,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "문제집 만들기")
            }
        },
        containerColor = ExamColors.ExamBackground
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uiState.books) { book ->
                    ProblemBookCard(
                        summary = book,
                        onClick = { onNavigateToBookDetail(book.id) },
                        onRenameClick = { showRenameDialog = book.id },
                        onDeleteClick = { showDeleteDialog = book.id }
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
fun ProblemBookCard(
    summary: ProblemBookSummary,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
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
                        text = "${summary.totalSets}개 세트 • ${summary.totalProblems}문항 • ${
                            SimpleDateFormat(
                                "yyyy.MM.dd",
                                Locale.getDefault()
                            ).format(Date(summary.createdAt))
                        }",
                        style = ExamTypography.examSmallTextStyle,
                        color = ExamColors.ExamTextSecondary
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
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(ExamColors.ExamCardBackground)
                    ) {
                        DropdownMenuItem(
                            text = { Text("이름 변경하기") },
                            onClick = {
                                showMenu = false
                                onRenameClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("삭제하기", color = ExamColors.ErrorColor) },
                            onClick = {
                                showMenu = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }
        }
    }
}
