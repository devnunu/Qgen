import OpenAI from 'openai';
import { v4 as uuidv4 } from 'uuid';
import { env } from './env';
import {
    GenerateQuestionsRequest,
    GenerateQuestionsResponse,
    Question,
    AiQuestion,
    AiChoice,
    QuestionDirective,
    StructuralValidationResult
} from './types';
import { pickTemplatesForRequest, getTemplateGuidelines } from './templates';

/**
 * 난이도별 가이드라인 제공 (축약 버전)
 */
export function getDifficultyGuidelines(
    difficulty: GenerateQuestionsRequest["difficulty"]
): string {
    switch (difficulty) {
        case "easy":
            return `EASY: Single concept, 1-2 sentences, <10sec answer, obvious distractors`;

        case "medium":
            return `MEDIUM: 1+ reasoning step, 2 related concepts, 2-4 sentences/5-10 lines code, include common mistake`;

        case "hard":
            return `HARD: 2+ concepts+conditions, 3+ reasoning steps, ㄱㄴㄷㄹ format or 10+ lines code, all distractors plausible`;

        case "mixed":
            return `MIXED: 30% easy, 40% medium, 30% hard. Set "difficulty" field correctly per question`;

        default:
            return "";
    }
}

const MODEL_NAME = "gpt-4o-mini";

let openai: OpenAI | null = null;

function getOpenAIClient() {
    if (!openai) {
        openai = new OpenAI({
            apiKey: env.OPENAI_API_KEY,
        });
    }
    return openai;
}

// ============================================================================
// 1차 검증: 로컬 규칙 기반 구조적 검증
// ============================================================================

/**
 * 한국어 문제 지문에서 directive(문제 형식)를 분석
 *
 * @param stem 문제 지문
 * @returns QuestionDirective 타입
 */
export function analyzeQuestionDirective(stem: string): QuestionDirective {
    const normalizedStem = stem.trim();

    // multi_correct: "옳은 것을 모두", "모두 고르시오" 등
    if (
        /옳은\s*(것|설명|진술|문장).*모두/i.test(normalizedStem) ||
        /올바른\s*(것|설명|진술|문장).*모두/i.test(normalizedStem) ||
        /맞는\s*(것|설명|진술|문장).*모두/i.test(normalizedStem) ||
        /모두\s*고르시오/i.test(normalizedStem) ||
        /모두\s*선택하시오/i.test(normalizedStem)
    ) {
        return "multi_correct";
    }

    // multi_incorrect: "옳지 않은 것을 모두", "틀린 것을 모두" 등
    if (
        /옳지\s*않은\s*(것|설명|진술|문장).*모두/i.test(normalizedStem) ||
        /틀린\s*(것|설명|진술|문장).*모두/i.test(normalizedStem) ||
        /잘못된\s*(것|설명|진술|문장).*모두/i.test(normalizedStem) ||
        /그르.*모두/i.test(normalizedStem)
    ) {
        return "multi_incorrect";
    }

    // single_incorrect: "옳지 않은 것은", "틀린 것은" 등
    if (
        /옳지\s*않은\s*(것|설명|진술|문장)/i.test(normalizedStem) ||
        /틀린\s*(것|설명|진술|문장)/i.test(normalizedStem) ||
        /잘못된\s*(것|설명|진술|문장)/i.test(normalizedStem) ||
        /그른\s*(것|설명|진술|문장)/i.test(normalizedStem)
    ) {
        return "single_incorrect";
    }

    // single_correct: "옳은 것은", "올바른 것은", "~고르시오" (단, '모두'가 없을 때)
    if (
        /옳은\s*(것|설명|진술|문장)/i.test(normalizedStem) ||
        /올바른\s*(것|설명|진술|문장)/i.test(normalizedStem) ||
        /맞는\s*(것|설명|진술|문장)/i.test(normalizedStem) ||
        /고르시오/i.test(normalizedStem) ||
        /선택하시오/i.test(normalizedStem)
    ) {
        return "single_correct";
    }

    // 위 패턴에 해당하지 않으면 unknown
    return "unknown";
}

/**
 * 문제의 구조적 유효성을 검증 (도메인 지식 없이 형식만 체크)
 *
 * @param stem 문제 지문
 * @param choices 선택지 배열
 * @returns StructuralValidationResult
 */
export function validateQuestionStructure(
    stem: string,
    choices: AiChoice[]
): StructuralValidationResult {
    const directive = analyzeQuestionDirective(stem);
    const actualCorrectCount = choices.filter(c => c.isCorrect).length;
    const issues: string[] = [];

    let expectedCorrectCount: number | null = null;
    let isStructurallyValid = true;

    switch (directive) {
        case "single_correct":
        case "single_incorrect":
            expectedCorrectCount = 1;
            if (actualCorrectCount !== 1) {
                isStructurallyValid = false;
                issues.push(
                    `Directive requires exactly 1 correct answer, but found ${actualCorrectCount}. ` +
                    `Stem: "${stem.substring(0, 100)}..."`
                );
            }
            break;

        case "multi_correct":
        case "multi_incorrect":
            expectedCorrectCount = null; // 제한 없음
            if (actualCorrectCount === 0) {
                isStructurallyValid = false;
                issues.push(
                    `Multi-select question must have at least 1 correct answer, but found 0. ` +
                    `Stem: "${stem.substring(0, 100)}..."`
                );
            }
            break;

        case "unknown":
            expectedCorrectCount = null;
            // unknown의 경우 구조적으로 강하게 판단하지 않음
            // 다만 정보성 이슈는 남김
            issues.push(
                `Unable to determine question directive from stem. ` +
                `Stem: "${stem.substring(0, 100)}..."`
            );
            // isStructurallyValid는 true로 유지 (unknown은 통과시킴)
            break;
    }

    return {
        directive,
        expectedCorrectCount,
        actualCorrectCount,
        isStructurallyValid,
        issues
    };
}


/**
 * AI 문제를 내부 Question 타입으로 변환하는 헬퍼 함수
 *
 * @param aiQuestion AI가 반환한 문제 (isCorrect 기반)
 * @param request 원본 요청 (topic, difficulty 등)
 * @returns 클라이언트에게 반환할 Question 객체
 */
function mapAiQuestionToInternalQuestion(
    aiQuestion: AiQuestion,
    request: GenerateQuestionsRequest
): Question {
    const { topic, difficulty, choiceCount = 4 } = request;

    // 1. stem 검증
    if (!aiQuestion.stem || aiQuestion.stem.trim().length === 0) {
        throw new Error("Question stem is empty");
    }

    // 2. choices 검증
    if (!Array.isArray(aiQuestion.choices)) {
        throw new Error("Choices is not an array");
    }

    if (aiQuestion.choices.length !== choiceCount) {
        throw new Error(`Expected ${choiceCount} choices, got ${aiQuestion.choices.length}`);
    }

    // 3. 각 choice 검증
    aiQuestion.choices.forEach((choice, idx) => {
        if (!choice.text || choice.text.trim().length === 0) {
            throw new Error(`Choice ${idx} text is empty`);
        }
        if (typeof choice.isCorrect !== 'boolean') {
            throw new Error(`Choice ${idx} isCorrect is not boolean`);
        }
    });

    // 4. isCorrect === true인 choice가 정확히 1개인지 확인
    const correctChoices = aiQuestion.choices.filter(c => c.isCorrect);
    if (correctChoices.length !== 1) {
        throw new Error(`Expected exactly 1 correct choice, found ${correctChoices.length}`);
    }

    // 5. explanation 검증
    if (!aiQuestion.explanation || aiQuestion.explanation.trim().length === 0) {
        throw new Error("Explanation is empty");
    }

    // 6. choices에 id 부여 ("A", "B", "C", "D", ...)
    const choicesWithId = aiQuestion.choices.map((choice, idx) => ({
        id: String.fromCharCode(65 + idx), // 65 = 'A'
        text: choice.text,
        // NOTE: reason 필드는 현재 클라이언트에 전달하지 않지만,
        // 향후 보기별 피드백 기능을 추가할 때 활용 가능
        // reason: choice.reason
    }));

    // 7. correctChoiceId 찾기
    const correctIndex = aiQuestion.choices.findIndex(c => c.isCorrect);
    const correctChoiceId = String.fromCharCode(65 + correctIndex);

    // 8. Question 객체 생성
    return {
        id: uuidv4(),
        stem: aiQuestion.stem,
        choices: choicesWithId,
        correctChoiceId: correctChoiceId,
        explanation: aiQuestion.explanation,
        metadata: {
            topic: topic,
            difficulty: aiQuestion.difficulty || (difficulty === 'mixed' ? 'medium' : difficulty as "easy" | "medium" | "hard"),
        },
    };
}

/**
 * Skeletal Question Generation (isCorrect 기반)
 * 템플릿 기반으로 문제를 생성하여 토큰 사용량과 응답 시간을 줄임
 */
/**
 * AI로부터 문제를 생성 (단일 호출용)
 * NOTE: 대량 생성(10개 이상)은 generateQuestionsWithBatching 사용 권장
 */
export async function generateQuestionsFromAI(
    request: GenerateQuestionsRequest
): Promise<GenerateQuestionsResponse> {
    const client = getOpenAIClient();

    const {
        topic,
        description,
        subtopics,
        difficulty,
        count,
        choiceCount = 4,
        language = "ko",
        validationLevel = "light" // 기본값: light
    } = request;

    // 1. 요청에 맞는 템플릿 선택
    const selectedTemplates = pickTemplatesForRequest(request);
    const templateGuidelines = getTemplateGuidelines(selectedTemplates);

    console.log(`[OpenAI] Selected ${selectedTemplates.length} templates for ${count} questions`);

    // 2. 난이도 가이드라인 가져오기
    const difficultyGuidelines = getDifficultyGuidelines(difficulty);

    // 3. Hard 문제 구조 힌트 (축약 버전)
    const hardHint = (difficulty === "hard" || difficulty === "mixed")
        ? `\nHARD format: Use ㄱㄴㄷㄹ multi-statement or 10+ line code with timing/flow analysis. Don't copy, create new.`
        : "";

    // 4. System 메시지 (강화된 버전 - 한국 시험 출제위원 페르소나 + self-check)
    const systemPrompt = `
당신은 대한민국 국가고시 출제위원으로 20년 경력의 전문가입니다.

[페르소나]
- 엄격한 출제 기준 준수
- 명확하고 모호하지 않은 문제만 출제
- 학습 목표와 정답 간 완벽한 정합성 보장

[출제 프로세스]
문제를 생성할 때 반드시 다음 단계를 거쳐야 합니다:

1단계: STEM 분석
- stem을 읽고 directive를 명확히 판단합니다
- "옳은 것" → single_correct (정답 1개)
- "옳지 않은 것" → single_incorrect (오답 1개를 정답으로)
- "옳은 것을 모두" → multi_correct (하지만 우리는 단일정답만 사용)
- "틀린 것" → single_incorrect

2단계: CHOICES 진위 판단
- 각 선택지의 내용이 사실인지 거짓인지 명확히 판단
- 애매하거나 논란의 여지가 있는 선택지는 사용하지 않음
- 모든 선택지는 독립적으로 참/거짓 판단 가능해야 함

3단계: 단일 정답 보정
- directive와 각 선택지의 진위를 종합하여 isCorrect를 할당
- **반드시 정확히 1개의 선택지만 isCorrect: true**
- 나머지는 모두 isCorrect: false

4단계: SELF-CHECK
출력 직전에 다음을 재확인:
- [ ] 정답이 정확히 1개인가?
- [ ] stem의 directive와 정답이 일치하는가?
- [ ] 모든 선택지가 명확하고 중복이 없는가?
- [ ] explanation이 충분히 상세한가? (3-6문장)

[TEMPLATES 활용]
아래 템플릿 가이드라인을 참고하되, 반드시 새로운 문제를 창작하세요:
${templateGuidelines}

[난이도 규칙]
난이도: "${difficulty}"
${difficultyGuidelines}${hardHint}

[ㄱㄴㄷㄹ 형식 특수 규칙]
- 문제 stem에 "ㄱ. ...\\nㄴ. ...\\nㄷ. ..."와 같이 여러 진술이 나열된 경우
- choices는 반드시 조합만 가능: "① ㄱ,ㄴ", "② ㄴ,ㄷ", "③ ㄱ,ㄷ", "④ ㄱ,ㄴ,ㄷ"
- 각 진술(ㄱ,ㄴ,ㄷ,ㄹ)의 참/거짓을 먼저 판단한 후, directive에 맞는 조합을 정답으로 선택

[중복 금지]
- stem과 choices 간 텍스트 중복 최소화
- stem에 이미 포함된 정보를 choices에서 반복하지 않음

[JSON 출력 형식]
{
  "questions": [{
    "stem": "문제 지문 (코드 포함 가능, \\n 사용)",
    "choices": [
      {"text":"선택지 1", "isCorrect":false, "reason":"1-2문장 이유"},
      {"text":"선택지 2", "isCorrect":true, "reason":"1-2문장 이유"},
      {"text":"선택지 3", "isCorrect":false, "reason":"1-2문장 이유"},
      {"text":"선택지 4", "isCorrect":false, "reason":"1-2문장 이유"}
    ],
    "explanation": "핵심 개념 + 풀이 과정 + 오답 이유 (3-6문장)",
    "difficulty": "easy|medium|hard"
  }]
}

[EXPLANATION 작성 기준]
3~6문장으로 작성하되 다음을 포함:
1. 핵심 개념/원리
2. 정답 도출 논리 (1~3단계)
3. 오답들이 틀린 이유
4. ㄱㄴㄷㄹ 형식의 경우: "ㄱ: 참이다. 이유는 ~" / "ㄴ: 거짓이다. 이유는 ~" 식으로 각 진술별 판단 근거 명시

[중요 제약]
- 선택지는 정확히 ${choiceCount}개
- 언어: ${language === "ko" ? "한국어" : "영어"}
- 코드 포함 시 JSON escape 처리 (\\n, \\", 등)
- Markdown 코드 블록 사용 금지, 순수 JSON만 반환
- 정답은 반드시 1개

위 모든 규칙을 준수하여 문제를 생성하세요.
`.trim();

    // 5. User 메시지: 구체적인 요청 사항
    const userPrompt = `
Generate ${count} multiple-choice questions.

Topic: ${topic}
${description && description.trim().length > 0
            ? `\nAdditional Context:\n${description}\n\nIMPORTANT: Use this additional context to create more precise and relevant questions. The description provides specific details about what aspects of the topic to focus on, expected question styles, or particular concepts to emphasize.\n`
            : ''}
${subtopics && subtopics.length > 0 ? `Subtopics: ${subtopics.join(', ')}` : ''}
Requested overall difficulty: ${difficulty}
Choices per Question: ${choiceCount}
Language: ${language}

${difficulty === "mixed"
            ? `IMPORTANT: For mixed difficulty, distribute questions as ~30% easy, ~40% medium, ~30% hard.
Set each question's "difficulty" field correctly according to its actual difficulty level.`
            : `IMPORTANT: All questions must match the "${difficulty}" difficulty level as defined in DIFFICULTY RULES.
Set each question's "difficulty" field to "${difficulty}".`}

Use the provided templates to create diverse questions.
Distribute questions across different template types for variety.

REMEMBER: Each question must have EXACTLY ONE choice with "isCorrect": true.

Return ONLY the JSON object. No other text.
`.trim();

    try {
        console.log(`[OpenAI] Starting generation for ${count} questions on topic: "${topic}"`);
        const startTime = Date.now();

        // 6. OpenAI API 호출
        const completion = await client.chat.completions.create({
            model: MODEL_NAME,
            messages: [
                { role: "system", content: systemPrompt },
                { role: "user", content: userPrompt },
            ],
            temperature: 0.7,
            response_format: { type: "json_object" },
        });

        const apiDuration = Date.now() - startTime;
        console.log(`[OpenAI] API call completed in ${apiDuration}ms (${(apiDuration / 1000).toFixed(2)}s)`);

        const content = completion.choices[0]?.message?.content;
        if (!content) {
            throw new Error("No content received from OpenAI");
        }

        // 7. JSON 파싱 (강화된 정제 로직)
        let jsonString = content.trim();

        // 마크다운 코드 블록 제거 (다양한 형태)
        jsonString = jsonString.replace(/^```json\s*/i, '').replace(/^```\s*/, '').replace(/\s*```$/g, '');

        // 앞뒤 공백 및 개행 제거
        jsonString = jsonString.trim();

        // JSON 시작/끝 찾기 (추가 텍스트가 있을 경우 대비)
        const jsonStart = jsonString.indexOf('{');
        const jsonEnd = jsonString.lastIndexOf('}');

        if (jsonStart !== -1 && jsonEnd !== -1 && jsonEnd > jsonStart) {
            jsonString = jsonString.substring(jsonStart, jsonEnd + 1);
        }

        let parsed: any;
        try {
            parsed = JSON.parse(jsonString);
        } catch (e) {
            console.error("[OpenAI] Failed to parse JSON");
            console.error("[OpenAI] Raw content length:", content.length);
            console.error("[OpenAI] First 500 chars:", content.substring(0, 500));
            console.error("[OpenAI] Last 500 chars:", content.substring(Math.max(0, content.length - 500)));
            console.error("[OpenAI] Parse error:", e);
            throw new Error("Failed to parse JSON response from AI");
        }

        // 8. 응답 검증
        if (!Array.isArray(parsed.questions)) {
            throw new Error("Invalid response format: 'questions' array is missing");
        }

        // 9. 단순화된 검증 및 Question 타입으로 변환
        // 9-1. AiQuestion 배열로 변환
        const aiQuestions: AiQuestion[] = parsed.questions;

        // 9-2. 구조적 검증 및 변환
        console.log(`[Validation] Starting validation for ${aiQuestions.length} questions`);
        const validQuestions: Question[] = [];
        const errors: Array<{ index: number; error: string }> = [];

        aiQuestions.forEach((aiQuestion, index) => {
            try {
                // 구조적 검증: choices 개수, 정답 1개, text 비어있지 않음
                if (!Array.isArray(aiQuestion.choices) || aiQuestion.choices.length !== choiceCount) {
                    throw new Error(`Expected ${choiceCount} choices, got ${aiQuestion.choices.length}`);
                }

                // 각 choice 검증
                aiQuestion.choices.forEach((choice, idx) => {
                    if (!choice.text || choice.text.trim().length === 0) {
                        throw new Error(`Choice ${idx} text is empty`);
                    }
                    if (typeof choice.isCorrect !== 'boolean') {
                        throw new Error(`Choice ${idx} isCorrect is not boolean`);
                    }
                });

                // 정답이 정확히 1개인지 확인
                const correctCount = aiQuestion.choices.filter(c => c.isCorrect).length;
                if (correctCount !== 1) {
                    throw new Error(`Expected exactly 1 correct choice, found ${correctCount}`);
                }

                // stem, explanation 검증
                if (!aiQuestion.stem || aiQuestion.stem.trim().length === 0) {
                    throw new Error("Question stem is empty");
                }
                if (!aiQuestion.explanation || aiQuestion.explanation.trim().length === 0) {
                    throw new Error("Explanation is empty");
                }

                // Question 타입으로 변환
                const question = mapAiQuestionToInternalQuestion(aiQuestion, request);
                validQuestions.push(question);

            } catch (error) {
                const errorMessage = error instanceof Error ? error.message : String(error);
                console.warn(`[Validation] Question ${index} failed: ${errorMessage}`);
                console.warn(`[Validation] Stem: "${aiQuestion.stem?.substring(0, 100)}..."`);
                errors.push({ index, error: errorMessage });
            }
        });

        console.log(
            `[Validation] Validation complete: ` +
            `${validQuestions.length} valid, ${errors.length} invalid`
        );

        // 10. 최종 검증: 유효한 문제 수 확인
        if (validQuestions.length === 0) {
            throw new Error(`No valid questions generated. Total errors: ${errors.length}`);
        }

        if (validQuestions.length < count) {
            console.warn(
                `[OpenAI] Warning: Generated ${validQuestions.length}/${count} valid questions. ` +
                `${errors.length} failed validation.`
            );
        }

        const totalDuration = Date.now() - startTime;
        console.log(`[OpenAI] Total processing completed in ${totalDuration}ms (${(totalDuration / 1000).toFixed(2)}s)`);
        console.log(`[OpenAI] Successfully generated ${validQuestions.length} questions (${errors.length} failed validation)`);

        return { questions: validQuestions };

    } catch (error) {
        console.error("[OpenAI] Error generating questions:", error);
        throw error;
    }
}

/**
 * 기존 문제를 변형하는 경량 API (isCorrect 기반으로 통일)
 * 정답 의미는 유지하되 표현만 다르게 재작성
 */
export async function regenerateQuestion(
    originalQuestion: Question,
    targetDifficulty?: "easy" | "medium" | "hard",
    targetLanguage?: string
): Promise<Question> {
    const client = getOpenAIClient();

    const language = targetLanguage || "ko";
    const difficulty = targetDifficulty || originalQuestion.metadata.difficulty;

    // 난이도 가이드라인
    const difficultyGuidelines = getDifficultyGuidelines(difficulty);

    const systemPrompt = `
당신은 대한민국 국가고시 출제위원으로 20년 경력의 전문가입니다.
기존 문제를 학습 목표는 유지하되 표현을 완전히 새롭게 재작성하는 임무를 맡았습니다.

[재작성 프로세스]
1단계: 원본 문제의 학습 목표 파악
- 원본 문제가 평가하고자 하는 핵심 개념/원리 파악
- 정답의 논리적 근거 이해

2단계: STEM 재작성
- 동일한 학습 목표를 평가하되, 완전히 다른 표현으로 작성
- directive는 동일하게 유지 ("옳은 것", "틀린 것" 등)
- 원본과 다른 예시, 상황, 코드 사용

3단계: CHOICES 재작성
- 정답의 논리적 근거는 동일하되, 표현 완전히 변경
- 오답들도 그럴듯하게 재작성 (단, 여전히 명확하게 틀려야 함)
- 각 선택지의 진위를 명확히 판단

4단계: 단일 정답 보정
- **반드시 정확히 1개의 선택지만 isCorrect: true**
- 나머지는 모두 isCorrect: false

5단계: SELF-CHECK
출력 직전에 다음을 재확인:
- [ ] 정답이 정확히 1개인가?
- [ ] 원본과 동일한 학습 목표를 평가하는가?
- [ ] 표현이 충분히 달라졌는가?
- [ ] explanation이 충분히 상세한가? (3-6문장)

[난이도 규칙]
목표 난이도: "${difficulty}"
${difficultyGuidelines}

[JSON 출력 형식]
{
  "stem": "재작성된 문제 지문",
  "choices": [
    {"text":"선택지 1", "isCorrect":false, "reason":"1-2문장 이유"},
    {"text":"선택지 2", "isCorrect":true, "reason":"1-2문장 이유"},
    {"text":"선택지 3", "isCorrect":false, "reason":"1-2문장 이유"},
    {"text":"선택지 4", "isCorrect":false, "reason":"1-2문장 이유"}
  ],
  "explanation": "핵심 개념 + 풀이 과정 + 오답 이유 (3-6문장)"
}

[EXPLANATION 작성 기준]
3~6문장으로 작성하되 다음을 포함:
1. 핵심 개념/원리
2. 정답 도출 논리 (1~3단계)
3. 오답들이 틀린 이유
4. ㄱㄴㄷㄹ 형식의 경우: "ㄱ: 참이다. 이유는 ~" / "ㄴ: 거짓이다. 이유는 ~" 식으로 각 진술별 판단 근거 명시

[중요 제약]
- 정답은 반드시 1개
- Markdown 코드 블록 사용 금지, 순수 JSON만 반환
- 코드 포함 시 JSON escape 처리 (\\n, \\", 등)

위 모든 규칙을 준수하여 문제를 재작성하세요.
`.trim();

    const userPrompt = `
Rephrase this question:

Original Question: ${originalQuestion.stem}
Original Choices:
${originalQuestion.choices.map((c, i) => `${i + 1}. ${c.text} ${c.id === originalQuestion.correctChoiceId ? '(정답)' : ''}`).join('\n')}
Original Explanation: ${originalQuestion.explanation}

Target Difficulty: ${difficulty}
Target Language: ${language}
Number of Choices: ${originalQuestion.choices.length}

Maintain the same learning objective but use different wording.
Ensure EXACTLY ONE choice has "isCorrect": true.

Return ONLY the JSON object.
`.trim();

    try {
        console.log(`[OpenAI] Regenerating question: ${originalQuestion.id}`);
        const startTime = Date.now();

        const completion = await client.chat.completions.create({
            model: MODEL_NAME,
            messages: [
                { role: "system", content: systemPrompt },
                { role: "user", content: userPrompt },
            ],
            temperature: 0.8, // 더 높은 temperature로 다양성 확보
            response_format: { type: "json_object" },
        });

        const content = completion.choices[0]?.message?.content;
        if (!content) {
            throw new Error("No content received from OpenAI");
        }

        const jsonString = content.replace(/^```json\s*/, '').replace(/\s*```$/, '').trim();
        const parsed = JSON.parse(jsonString);

        // mapAiQuestionToInternalQuestion 재사용
        const aiQuestion: AiQuestion = {
            stem: parsed.stem,
            choices: parsed.choices,
            explanation: parsed.explanation,
            difficulty: difficulty,
        };

        const regeneratedQuestion = mapAiQuestionToInternalQuestion(aiQuestion, {
            topic: originalQuestion.metadata.topic,
            difficulty: difficulty,
            count: 1,
            choiceCount: originalQuestion.choices.length as 4 | 5,
            language: language as "ko" | "en",
        });

        const duration = Date.now() - startTime;
        console.log(`[OpenAI] Question regenerated in ${duration}ms`);

        return regeneratedQuestion;

    } catch (error) {
        console.error("[OpenAI] Error regenerating question:", error);
        throw error;
    }
}

// ============================================================================
// 배치 생성: 대량 문제 생성 시 병렬 처리
// ============================================================================

/**
 * 한 번에 생성할 최대 문제 수 (배치 크기)
 * 너무 크면 timeout 위험, 너무 작으면 API 호출 횟수 증가
 */
const MAX_QUESTIONS_PER_CALL = 5;

/**
 * 대량 문제 생성 시 병렬 배치 처리
 *
 * count가 MAX_QUESTIONS_PER_CALL보다 클 때 여러 번의 generateQuestionsFromAI 호출을
 * Promise.all로 병렬 처리하여 속도를 높임
 *
 * NOTE: API 핸들러에서는 이 함수를 사용하는 것을 권장
 * (generateQuestionsFromAI는 단일 호출용, 이 함수는 배치용)
 *
 * @param request 문제 생성 요청
 * @returns 생성된 모든 문제
 */
export async function generateQuestionsWithBatching(
    request: GenerateQuestionsRequest
): Promise<GenerateQuestionsResponse> {
    const { count } = request;

    // 소량이면 기존 로직 그대로 사용
    if (count <= MAX_QUESTIONS_PER_CALL) {
        return generateQuestionsFromAI(request);
    }

    // 배치 단위로 분할
    const batchSize = MAX_QUESTIONS_PER_CALL;
    const tasks: Promise<GenerateQuestionsResponse>[] = [];

    console.log(`[Batching] Splitting ${count} questions into batches of ${batchSize}`);

    for (let i = 0; i < count; i += batchSize) {
        const size = Math.min(batchSize, count - i);
        tasks.push(
            generateQuestionsFromAI({
                ...request,
                count: size,
            })
        );
    }

    console.log(`[Batching] Executing ${tasks.length} parallel batches`);
    const batchStartTime = Date.now();

    // 병렬 실행
    const results = await Promise.all(tasks);

    const batchDuration = Date.now() - batchStartTime;
    console.log(`[Batching] All batches completed in ${batchDuration}ms (${(batchDuration / 1000).toFixed(2)}s)`);

    // 결과 병합
    const questions = results.flatMap(r => r.questions);

    console.log(`[Batching] Total questions generated: ${questions.length}/${count}`);

    return { questions };
}
