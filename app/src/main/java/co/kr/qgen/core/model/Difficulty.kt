package co.kr.qgen.core.model

enum class Difficulty(val value: String, val displayName: String) {
    EASY("easy", "쉬움"),
    MEDIUM("medium", "보통"),
    HARD("hard", "어려움"),
    MIXED("mixed", "혼합")
}

enum class Language(val value: String, val displayName: String) {
    KO("ko", "한국어"),
    EN("en", "English")
}

enum class TopicPreset(val displayName: String, val topicValue: String) {
    ANDROID_COROUTINES("Android Coroutines", "Android Coroutines"),
    ANDROID_FLOW("Android Flow", "Android Flow"),
    KOTLIN_BASICS("Kotlin Basics", "Kotlin Basics"),
    HIGH_SCHOOL_SOCIAL("고3 사회문화", "고3 사회문화"),
    GENERAL_KNOWLEDGE("상식 퀴즈", "상식 퀴즈"),
    CUSTOM("직접 입력", "")
}
