package co.kr.qgen.feature.bookdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.kr.qgen.core.data.source.local.entity.ProblemSetEntity
import co.kr.qgen.core.ui.components.QGenScaffold
import co.kr.qgen.core.ui.icons.BoltIcon
import co.kr.qgen.core.ui.theme.ExamColors
import co.kr.qgen.core.ui.theme.ExamShapes
import co.kr.qgen.core.ui.theme.ExamTypography
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    viewModel: BookDetailViewModel = koinViewModel(),
    onNavigateToGeneration: (String) -> Unit,
    onNavigateToQuiz: (String) -> Unit,
    onNavigateToAdHocQuiz: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showQuickActionsSheet by remember { mutableStateOf(false) }
    var showRandomQuizDialog by remember { mutableStateOf(false) }
    var showWrongQuizDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<String?>(null) }
    var showRegenerateDialog by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState()

    // Random Quiz Dialog
    if (showRandomQuizDialog) {
        var count by remember { mutableIntStateOf(10) }

        AlertDialog(
            onDismissRequest = { showRandomQuizDialog = false },
            title = { Text("랜덤 문제 풀기") },
            text = {
                Column {
                    Text("이 문제집의 모든 문제 중 랜덤으로 선택해서 풀이합니다.")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = count.toString(),
                        onValueChange = {
                            count = it.toIntOrNull()?.coerceIn(1, uiState.totalProblems) ?: count
                        },
                        label = { Text("문제 수") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "총 ${uiState.totalProblems}문항 중 선택",
                        style = ExamTypography.examSmallTextStyle,
                        color = ExamColors.ExamTextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.startRandomQuiz(count) {
                            showRandomQuizDialog = false
                            onNavigateToAdHocQuiz()
                        }
                    }
                ) {
                    Text("시작")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRandomQuizDialog = false }) {
                    Text("취소")
                }
            },
            containerColor = ExamColors.ExamCardBackground
        )
    }

    // Wrong Problems Quiz Dialog
    if (showWrongQuizDialog) {
        var count by remember { mutableIntStateOf(10.coerceAtMost(uiState.wrongProblemsCount)) }

        AlertDialog(
            onDismissRequest = { showWrongQuizDialog = false },
            title = { Text("틀린 문제만 풀기") },
            text = {
                Column {
                    Text("이전에 틀렸던 문제들을 복습합니다.")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = count.toString(),
                        onValueChange = {
                            count = it.toIntOrNull()?.coerceIn(1, uiState.wrongProblemsCount) ?: count
                        },
                        label = { Text("문제 수") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "총 ${uiState.wrongProblemsCount}개의 틀린 문제",
                        style = ExamTypography.examSmallTextStyle,
                        color = ExamColors.ErrorColor
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.startWrongProblemsQuiz(count) {
                            showWrongQuizDialog = false
                            onNavigateToAdHocQuiz()
                        }
                    },
                    enabled = uiState.wrongProblemsCount > 0
                ) {
                    Text("시작")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWrongQuizDialog = false }) {
                    Text("취소")
                }
            },
            containerColor = ExamColors.ExamCardBackground
        )
    }

    // Rename Dialog
    if (showRenameDialog != null) {
        val setId = showRenameDialog!!
        val currentTitle = uiState.problemSets.find { it.id == setId }?.title
            ?: uiState.problemSets.find { it.id == setId }?.topic
            ?: ""
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
            },
            containerColor = ExamColors.ExamCardBackground
        )
    }

    // Regenerate Dialog
    if (showRegenerateDialog != null) {
        val setId = showRegenerateDialog!!

        AlertDialog(
            onDismissRequest = {
                if (!uiState.isRegenerating) {
                    showRegenerateDialog = null
                }
            },
            title = { Text("문제 다시 생성하기") },
            text = {
                if (uiState.isRegenerating) {
                    Text("문제를 재생성하고 있습니다. 잠시만 기다려주세요...")
                } else {
                    Text("이 세트의 문제들을 모두 새로 생성합니다. 기존 문제들은 삭제됩니다. 계속하시겠습니까?")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.regenerateProblemSet(setId) { success ->
                            showRegenerateDialog = null
                        }
                    },
                    enabled = !uiState.isRegenerating
                ) {
                    Text(if (uiState.isRegenerating) "재생성 중..." else "확인")
                }
            },
            dismissButton = {
                if (!uiState.isRegenerating) {
                    TextButton(onClick = { showRegenerateDialog = null }) {
                        Text("취소")
                    }
                }
            },
            containerColor = ExamColors.ExamCardBackground
        )
    }

    // Delete Dialog
    if (showDeleteDialog != null) {
        val setId = showDeleteDialog!!

        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("세트 삭제") },
            text = { Text("이 세트를 삭제하시겠습니까? 삭제된 세트는 복구할 수 없습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProblemSet(setId)
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

    // Quick Actions Bottom Sheet
    if (showQuickActionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQuickActionsSheet = false },
            sheetState = sheetState,
            containerColor = ExamColors.ExamCardBackground
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = "빠른 실행",
                    style = ExamTypography.examTitleTextStyle,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                HorizontalDivider(color = ExamColors.ExamBorderGray)

                // 문제 세트 생성하기
                ListItem(
                    headlineContent = { Text("문제 세트 생성하기") },
                    supportingContent = { Text("AI가 새로운 문제를 생성합니다") },
                    leadingContent = {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = ExamColors.ExamAccent
                        )
                    },
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clickable {
                            showQuickActionsSheet = false
                            uiState.book?.let { onNavigateToGeneration(it.id) }
                        },
                    colors = ListItemDefaults.colors(containerColor = ExamColors.ExamCardBackground)
                )

                // 랜덤 문제 풀기
                ListItem(
                    headlineContent = { Text("랜덤 문제 풀기") },
                    supportingContent = {
                        if (uiState.totalProblems > 0) {
                            Text("${uiState.totalProblems}개 문항 중 랜덤 선택")
                        } else {
                            Text("아직 문제가 없습니다", color = ExamColors.ExamTextSecondary)
                        }
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = if (uiState.totalProblems > 0) ExamColors.ExamTextPrimary else ExamColors.ExamTextSecondary
                        )
                    },
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clickable(enabled = uiState.totalProblems > 0) {
                            showQuickActionsSheet = false
                            showRandomQuizDialog = true
                        },
                    colors = ListItemDefaults.colors(containerColor = ExamColors.ExamCardBackground)
                )

                // 틀린 문제만 풀기
                ListItem(
                    headlineContent = { Text("틀린 문제만 풀기") },
                    supportingContent = {
                        if (uiState.wrongProblemsCount > 0) {
                            Text("${uiState.wrongProblemsCount}개의 틀린 문제")
                        } else {
                            Text("틀린 문제가 없습니다", color = ExamColors.ExamTextSecondary)
                        }
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = if (uiState.wrongProblemsCount > 0) ExamColors.ErrorColor else ExamColors.ExamTextSecondary
                        )
                    },
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clickable(enabled = uiState.wrongProblemsCount > 0) {
                            showQuickActionsSheet = false
                            showWrongQuizDialog = true
                        },
                    colors = ListItemDefaults.colors(containerColor = ExamColors.ExamCardBackground)
                )
            }
        }
    }

    QGenScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        uiState.book?.title ?: "문제집",
                        style = ExamTypography.examTitleTextStyle
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = ExamColors.ExamBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showQuickActionsSheet = true },
                containerColor = androidx.compose.ui.graphics.Color(0xFF2196F3), // Material Blue
                contentColor = androidx.compose.ui.graphics.Color.White
            ) {
                Icon(
                    imageVector = BoltIcon,
                    contentDescription = "빠른 실행",
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        containerColor = ExamColors.ExamBackground
    ) { padding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(padding)
            ) {
                // Problem Sets Section Header
                item {
                    Text(
                        text = "문제 세트 (${uiState.problemSets.size})",
                        style = ExamTypography.examTitleTextStyle,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Problem Sets List
                items(uiState.problemSets) { set ->
                    ProblemSetCard(
                        summary = set,
                        onClick = { onNavigateToQuiz(set.id) },
                        onRenameClick = { showRenameDialog = set.id },
                        onRegenerateClick = { showRegenerateDialog = set.id },
                        onDeleteClick = { showDeleteDialog = set.id }
                    )
                }
            }

            // Error Snackbar
            if (uiState.errorMessage != null) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
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
    summary: ProblemSetEntity,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onRegenerateClick: () -> Unit,
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
                        text = summary.title ?: summary.topic,
                        style = ExamTypography.examBodyTextStyle,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${summary.count}문항 • ${summary.difficulty} • ${
                            SimpleDateFormat(
                                "yyyy.MM.dd",
                                Locale.getDefault()
                            ).format(Date(summary.createdAt))
                        }",
                        style = ExamTypography.examSmallTextStyle,
                        color = ExamColors.ExamTextSecondary
                    )
                }

                androidx.compose.foundation.layout.Box {
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
                        DropdownMenuItem(
                            text = { Text("세트 삭제하기", color = ExamColors.ErrorColor) },
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
