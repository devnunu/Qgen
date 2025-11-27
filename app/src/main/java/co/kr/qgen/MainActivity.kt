package co.kr.qgen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import co.kr.qgen.feature.generation.GenerationScreen
import co.kr.qgen.feature.home.HomeScreen
import co.kr.qgen.feature.quiz.QuizScreen
import co.kr.qgen.feature.result.ResultScreen
import co.kr.qgen.ui.theme.QgenTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QgenTheme {
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "generation",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("generation") {
                GenerationScreen(
                    onNavigateToQuiz = { navController.navigate("quiz") },
                    onShowMessage = { message ->
                        scope.launch {
                            snackbarHostState.showSnackbar(message)
                        }
                    }
                )
            }
            composable("quiz") {
                QuizScreen(
                    onNavigateToResult = { navController.navigate("result") }
                )
            }
            composable("result") {
                ResultScreen(
                    onNavigateHome = {
                        navController.navigate("generation") {
                            popUpTo("generation") { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}