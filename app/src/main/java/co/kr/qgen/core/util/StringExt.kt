package co.kr.qgen.core.util

/**
 * JSON에서 받은 문자열의 이스케이프 시퀀스를 실제 문자로 변환
 *
 * 예: "Hello\nWorld" -> "Hello
 * World"
 */
fun String.unescapeString(): String {
    return this
        .replace("\\n", "\n")      // 줄바꿈
        .replace("\\t", "\t")      // 탭
        .replace("\\r", "\r")      // 캐리지 리턴
        .replace("\\\"", "\"")     // 큰따옴표
        .replace("\\'", "'")       // 작은따옴표
        .replace("\\\\", "\\")     // 백슬래시 (마지막에 처리)
}
