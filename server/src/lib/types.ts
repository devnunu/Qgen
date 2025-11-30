export interface ApiResponse<T = any> {
    success: boolean;
    data?: T;
    error?: string;
}

export interface QuestionChoice {
    id: string; // "A", "B", "C", "D"
    text: string;
}

export interface Question {
    id: string; // Server-generated unique ID
    stem: string; // Question text
    choices: QuestionChoice[];
    correctChoiceId: string; // "A", "B", etc.
    explanation: string;
    metadata: {
        topic: string;
        difficulty: "easy" | "medium" | "hard";
    };
}

export interface GenerateQuestionsRequest {
    topic: string;
    subtopics?: string[];
    difficulty: "easy" | "medium" | "hard" | "mixed";
    count: number; // 1 ~ 50
    choiceCount?: 4 | 5; // Default 4
    language?: "ko" | "en"; // Default "ko"
}

export interface GenerateQuestionsResponse {
    questions: Question[];
}

// ============================================================================
// Internal Types for AI Response (not exposed to client)
// ============================================================================

/**
 * AI가 반환하는 선택지 형식 (isCorrect 기반)
 * reason 필드는 향후 보기별 피드백에 활용 가능
 */
export interface AiChoice {
    text: string;
    isCorrect: boolean;
    reason?: string; // 짧은 설명 (1~2문장) - 왜 맞거나 틀린지
}

/**
 * AI가 반환하는 문제 형식 (내부 검증용)
 */
export interface AiQuestion {
    stem: string;
    choices: AiChoice[];
    explanation: string;
    difficulty?: "easy" | "medium" | "hard";
}

// ============================================================================
// Question Validation Types
// ============================================================================

/**
 * 문제 지문에서 파악한 directive 타입
 */
export type QuestionDirective =
    | "single_correct"          // '옳은 것', '올바른 설명' 등 하나만 고르는 문제
    | "single_incorrect"        // '옳지 않은 것', '틀린 것' 등 하나만 고르는 문제
    | "multi_correct"           // '옳은 것을 모두', '모두 고르시오' 등 여러 개 정답 가능
    | "multi_incorrect"         // '옳지 않은 것을 모두', '틀린 것을 모두' 등 여러 개 오답 가능
    | "unknown";                // 패턴을 파악하지 못한 경우

/**
 * 구조적 검증 결과
 */
export interface StructuralValidationResult {
    directive: QuestionDirective;
    expectedCorrectCount: number | null; // single인 경우 1, multi면 null(제한 없음)
    actualCorrectCount: number;         // isCorrect === true 개수
    isStructurallyValid: boolean;       // 규칙 기반으로 봤을 때 구조적으로 문제 없는지
    issues: string[];                   // 발견한 문제점 설명
}

/**
 * AI 2차 검증 결과
 */
export interface AiVerificationResult {
    index: number;                 // 원본 질문 인덱스
    isValid: boolean;             // 최종적으로 사용해도 되는지
    fixedIsCorrect?: boolean[];   // 필요하다면 수정된 isCorrect 배열
    issueSummary?: string;        // 문제 발견 시 요약 설명 (로그용)
}
