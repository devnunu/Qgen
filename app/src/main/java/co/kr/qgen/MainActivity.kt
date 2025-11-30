package co.kr.qgen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import co.kr.qgen.core.ui.theme.QGenExamTheme
import co.kr.qgen.feature.generation.GenerationScreen
import co.kr.qgen.feature.home.HomeScreen
import co.kr.qgen.feature.quiz.QuizScreen
import co.kr.qgen.feature.result.ResultScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QGenExamTheme {
                QGenApp()
            }
        }
    }
}

@Composable
fun QGenApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    onNavigateToQuiz = { setId -> navController.navigate("quiz/$setId") },
                    onNavigateToGeneration = { navController.navigate("generation") }
                )
            }
            composable("generation") {
                GenerationScreen(
                    onNavigateToLoading = {
                        navController.navigate("loading")
                    },
                    onNavigateBack = { navController.popBackStack() },
                    onShowMessage = { message ->
                        scope.launch {
                            snackbarHostState.showSnackbar(message)
                        }
                    }
                )
            }
            composable("loading") {
                co.kr.qgen.feature.loading.LoadingScreen(
                    onNavigateToQuiz = { 
                        navController.navigate("quiz/new") {
                            popUpTo("home") // 로딩 화면 제거하고 홈까지 스택 정리
                        }
                    },
                    onNavigateBack = { 
                        navController.popBackStack("home", inclusive = false)
                    }
                )
            }
            composable(
                route = "quiz/{setId}",
                arguments = listOf(androidx.navigation.navArgument("setId") { type = androidx.navigation.NavType.StringType })
            ) {
                QuizScreen(
                    onNavigateToResult = { navController.navigate("result") },
                    onNavigateBack = {
                        // 퀴즈 화면에서 뒤로가기 시 홈으로 (로딩 화면 건너뛰기)
                        navController.popBackStack("home", inclusive = false)
                    }
                )
            }
            composable("result") {
                ResultScreen(
                    onNavigateHome = {
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}