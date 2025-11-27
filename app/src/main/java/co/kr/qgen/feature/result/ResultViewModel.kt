package co.kr.qgen.feature.result

import androidx.lifecycle.ViewModel
import co.kr.qgen.core.model.QGenSessionViewModel

class ResultViewModel(
    private val sessionViewModel: QGenSessionViewModel
) : ViewModel() {
    
    val quizResult = sessionViewModel.quizResult
}
