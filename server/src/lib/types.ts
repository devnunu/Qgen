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
