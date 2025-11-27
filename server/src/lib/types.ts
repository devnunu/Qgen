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
