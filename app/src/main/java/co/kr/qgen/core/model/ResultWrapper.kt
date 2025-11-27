package co.kr.qgen.core.model

sealed class ResultWrapper<out T> {
    data class Success<out T>(val value: T) : ResultWrapper<T>()
    data class Error(val code: Int? = null, val message: String? = null, val throwable: Throwable? = null) : ResultWrapper<Nothing>()
    object Loading : ResultWrapper<Nothing>()
}
