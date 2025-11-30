package co.kr.qgen.feature.loading

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.kr.qgen.core.ui.components.QGenScaffold
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
            },
            containerColor = ExamColors.ExamCardBackground
        )
    }

    QGenScaffold(
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
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                if (uiState.isLoading) {
                    // Circular progress indicator (always shown)
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = ExamColors.ExamTextPrimary
                    )

                    Text(
                        text = "AI가 문제를 생성하고 있습니다",
                        style = ExamTypography.examBodyTextStyle,
                        color = ExamColors.ExamTextPrimary
                    )

                    // Progress bar and text (only show when totalCount > 0)
                    if (uiState.totalCount > 0) {
                        val targetProgress = if (uiState.totalCount > 0) {
                            uiState.currentProgress.toFloat() / uiState.totalCount
                        } else {
                            0f
                        }

                        // Animated progress
                        val animatedProgress by animateFloatAsState(
                            targetValue = targetProgress,
                            animationSpec = tween(durationMillis = 800),
                            label = "progress"
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Linear progress bar with gradient effect
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = androidx.compose.ui.graphics.Color(0xFF2196F3), // 짙은 파란색
                            trackColor = androidx.compose.ui.graphics.Color(0xFFBBDEFB), // 옅은 파란색
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${uiState.currentProgress}/${uiState.totalCount} 문제 완료",
                            style = ExamTypography.examSmallTextStyle,
                            color = ExamColors.ExamTextSecondary
                        )
                    }

                    if (uiState.topic.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
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
