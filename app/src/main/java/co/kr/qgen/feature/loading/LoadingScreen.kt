package co.kr.qgen.feature.loading

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.kr.qgen.core.ui.theme.ExamColors
import co.kr.qgen.core.ui.theme.ExamTypography
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadingScreen(
    viewModel: LoadingViewModel = koinViewModel(),
    onNavigateToQuiz: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showBackDialog by remember { mutableStateOf(false) }

    // 생성 완료 시 퀴즈 화면으로 이동
    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) {
            onNavigateToQuiz()
        }
    }

    // 뒤로가기 처리
    BackHandler {
        showBackDialog = true
    }

    // 뒤로가기 확인 다이얼로그
    if (showBackDialog) {
        AlertDialog(
            onDismissRequest = { showBackDialog = false },
            title = { Text("문제 생성 취소") },
            text = { Text("문제 생성을 취소하고 돌아가시겠습니까?\n진행 중인 작업이 취소됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackDialog = false
                        viewModel.cancelGeneration()
                        onNavigateBack()
                    }
                ) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("문제 생성 중", style = ExamTypography.examTitleTextStyle) },
                navigationIcon = {
                    IconButton(onClick = { showBackDialog = true }) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = ExamColors.ExamBackground
                )
            )
        },
        containerColor = ExamColors.ExamBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = ExamColors.ExamTextPrimary
                    )
                    
                    Text(
                        text = "AI가 문제를 생성하고 있습니다...",
                        style = ExamTypography.examBodyTextStyle,
                        color = ExamColors.ExamTextPrimary
                    )
                    
                    if (uiState.topic.isNotEmpty()) {
                        Text(
                            text = "주제: ${uiState.topic}",
                            style = ExamTypography.examSmallTextStyle,
                            color = ExamColors.ExamTextSecondary
                        )
                    }
                }

                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage!!,
                        style = ExamTypography.examBodyTextStyle,
                        color = ExamColors.ErrorColor
                    )
                    
                    Button(
                        onClick = onNavigateBack,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ExamColors.ExamTextPrimary
                        )
                    ) {
                        Text("돌아가기")
                    }
                }
            }
        }
    }
}
