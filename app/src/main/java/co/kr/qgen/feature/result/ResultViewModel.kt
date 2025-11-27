package co.kr.qgen.feature.result

import androidx.lifecycle.ViewModel
import co.kr.qgen.feature.quiz.QuizViewModel
import kotlinx.coroutines.flow.StateFlow

class ResultViewModel(
    private val quizViewModel: QuizViewModel
) : ViewModel() {
    
    val quizResult = quizViewModel.quizResult
}
