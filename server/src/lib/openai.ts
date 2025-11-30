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
    StructuralValidationResult,
    AiVerificationResult
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

// ============================================================================
// 2차 검증: OpenAI 자기 검열 (도메인 지식 기반)
// ============================================================================

/**
 * OpenAI를 사용하여 문제들의 정답 정확성을 검증
 *
 * NOTE: 이 함수는 추가 비용이 발생하므로, 향후 feature flag나 환경변수로
 * 켜고 끌 수 있도록 확장 가능
 *
 * @param questions 검증할 문제 배열 (AiQuestion 형식)
 * @returns AiVerificationResult 배열 (각 문제의 검증 결과)
 */
async function verifyQuestionsWithAI(
    questions: AiQuestion[]
): Promise<AiVerificationResult[]> {
    const client = getOpenAIClient();

    // 배치 크기 제한 (토큰 사용량 고려)
    const BATCH_SIZE = 10;
    const allResults: AiVerificationResult[] = [];

    // 배치 단위로 처리
    for (let i = 0; i < questions.length; i += BATCH_SIZE) {
        const batch = questions.slice(i, Math.min(i + BATCH_SIZE, questions.length));

        // 검증용 요청 데이터 생성
        const verificationRequest = {
            questions: batch.map((q, idx) => ({
                index: i + idx, // 원본 배열에서의 인덱스
                stem: q.stem,
                choices: q.choices.map(c => c.text), // text만 전달
                proposedIsCorrect: q.choices.map(c => c.isCorrect)
            }))
        };

        const systemPrompt = `
Exam question auditor. Verify proposed "isCorrect" flags.

KOREAN PATTERNS:
- "옳은 것" → find correct (single unless "모두")
- "옳지 않은 것" → find incorrect (single unless "모두")
- "모두" → multiple ok

TASKS:
1. Check if each choice is factually true
2. Compare proposed isCorrect vs reality
3. Mark INVALID if:
   - Single answer expected but multiple factually correct/incorrect
   - Flags wrong or ambiguous question
4. Provide fixedIsCorrect if salvageable

JSON OUTPUT:
{"results":[{"index":0,"isValid":true,"fixedIsCorrect":[...],"issueSummary":""},...]}

Return ONLY JSON.
`.trim();

        const userPrompt = `
Verify these ${batch.length} questions:

${JSON.stringify(verificationRequest, null, 2)}

For each question:
1. Analyze the directive from the stem
2. Check if each choice is factually correct
3. Verify if the proposedIsCorrect flags match reality
4. Return isValid=true only if the question is unambiguous and correctly marked
5. If fixable, provide fixedIsCorrect array; otherwise mark isValid=false

Return ONLY the JSON response.
`.trim();

        try {
            console.log(`[Verification] Verifying batch ${Math.floor(i / BATCH_SIZE) + 1} (${batch.length} questions)`);
            const startTime = Date.now();

            const completion = await client.chat.completions.create({
                model: MODEL_NAME, // 또는 더 작은 모델 사용 가능
                messages: [
                    { role: "system", content: systemPrompt },
                    { role: "user", content: userPrompt }
                ],
                temperature: 0.3, // 낮은 temperature로 일관성 확보
                response_format: { type: "json_object" }
            });

            const content = completion.choices[0]?.message?.content;
            if (!content) {
                console.error(`[Verification] No content received for batch ${Math.floor(i / BATCH_SIZE) + 1}`);
                // 검증 실패 시 모든 문제를 valid로 처리 (보수적 접근)
                batch.forEach((_, idx) => {
                    allResults.push({
                        index: i + idx,
                        isValid: true, // 검증 자체가 실패했으므로 통과시킴
                        issueSummary: "Verification API returned no content - defaulting to valid"
                    });
                });
                continue;
            }

            // JSON 파싱
            let jsonString = content.trim();
            jsonString = jsonString.replace(/^```json\s*/i, '').replace(/^```\s*/, '').replace(/\s*```$/g, '').trim();

            let parsed: any;
            try {
                parsed = JSON.parse(jsonString);
            } catch (e) {
                console.error(`[Verification] JSON parse error for batch ${Math.floor(i / BATCH_SIZE) + 1}:`, e);
                console.error(`[Verification] Content:`, content.substring(0, 500));
                // 파싱 실패 시 모든 문제를 valid로 처리
                batch.forEach((_, idx) => {
                    allResults.push({
                        index: i + idx,
                        isValid: true,
                        issueSummary: "Verification JSON parse failed - defaulting to valid"
                    });
                });
                continue;
            }

            // 결과 검증 및 저장
            if (!Array.isArray(parsed.results)) {
                console.error(`[Verification] Invalid response format - results array missing`);
                batch.forEach((_, idx) => {
                    allResults.push({
                        index: i + idx,
                        isValid: true,
                        issueSummary: "Verification response format invalid - defaulting to valid"
                    });
                });
                continue;
            }

            // 결과 병합
            parsed.results.forEach((result: any) => {
                allResults.push({
                    index: result.index,
                    isValid: result.isValid ?? true, // 기본값은 true
                    fixedIsCorrect: result.fixedIsCorrect,
                    issueSummary: result.issueSummary || ""
                });
            });

            const duration = Date.now() - startTime;
            console.log(`[Verification] Batch ${Math.floor(i / BATCH_SIZE) + 1} completed in ${duration}ms`);

        } catch (error) {
            console.error(`[Verification] Error verifying batch ${Math.floor(i / BATCH_SIZE) + 1}:`, error);
            // 에러 발생 시 해당 배치의 모든 문제를 valid로 처리 (보수적 접근)
            batch.forEach((_, idx) => {
                allResults.push({
                    index: i + idx,
                    isValid: true,
                    issueSummary: `Verification error: ${error instanceof Error ? error.message : String(error)}`
                });
            });
        }
    }

    return allResults;
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

    // 4. System 메시지 (축약 버전)
    const systemPrompt = `
Expert exam question creator. Use templates & difficulty rules.

TEMPLATES:
${templateGuidelines}

DIFFICULTY: "${difficulty}"
${difficultyGuidelines}${hardHint}

JSON FORMAT:
{
  "questions": [{
    "stem": "Question (code ok)",
    "choices": [{"text":"...", "isCorrect":false, "reason":"..."}, ...],
    "explanation": "Core concept + reasoning + why wrong",
    "difficulty": "easy|medium|hard"
  }]
}

RULES:
- EXACTLY ONE "isCorrect":true per question
- ${choiceCount} choices, language: ${language === "ko" ? "Korean" : "English"}
- Code: use \\n, JSON-escape properly
- Return ONLY JSON (no markdown blocks)

EXPLANATION (3-6 sentences):
- Core concept tested
- Reasoning steps (1-3)
- Why others wrong
- For ㄱㄴㄷㄹ/numbered: "ㄱ: 참/거짓. 이유..." per statement

STEM/CHOICES:
- Stem: context+question. Choices: answers
- ㄱㄴㄷㄹ in stem → choices are label combinations only (① ㄱ,ㄴ)
- Full sentences in choices → stem is question only (no repetition)
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

        // 9. 다단계 검증 및 Question 타입으로 변환
        // 9-1. AiQuestion 배열로 변환
        const aiQuestions: AiQuestion[] = parsed.questions;

        // 9-2. 1차 검증: 구조적 검증 (로컬 규칙 기반)
        console.log(`[Validation] Starting structural validation for ${aiQuestions.length} questions`);
        const structurallyValidQuestions: AiQuestion[] = [];
        const structurallyInvalidIndices: number[] = [];

        aiQuestions.forEach((aiQuestion, index) => {
            const validationResult = validateQuestionStructure(aiQuestion.stem, aiQuestion.choices);

            if (!validationResult.isStructurallyValid) {
                console.warn(
                    `[Validation] Question ${index} failed structural validation:`,
                    validationResult.issues.join('; ')
                );
                console.warn(
                    `[Validation] Stem: "${aiQuestion.stem.substring(0, 100)}..."`,
                    `Choices: ${aiQuestion.choices.length}, Correct count: ${validationResult.actualCorrectCount}`
                );
                structurallyInvalidIndices.push(index);
            } else {
                structurallyValidQuestions.push(aiQuestion);
            }
        });

        console.log(
            `[Validation] Structural validation complete: ` +
            `${structurallyValidQuestions.length} valid, ${structurallyInvalidIndices.length} invalid`
        );

        // 구조적으로 유효한 문제가 없으면 에러
        if (structurallyValidQuestions.length === 0) {
            throw new Error(
                `All ${aiQuestions.length} questions failed structural validation. ` +
                `Check question directive patterns (옳은 것, 틀린 것, etc.)`
            );
        }

        // 9-3. 2차 검증: AI 기반 도메인 지식 검증 (validationLevel에 따라 분기)
        const validQuestions: Question[] = [];
        const errors: Array<{ index: number; error: string }> = [];

        if (validationLevel === "none") {
            // none: 2차 검증 생략, 구조적으로 유효한 문제만 변환
            console.log(`[Validation] Skipping AI verification (validationLevel: none)`);

            structurallyValidQuestions.forEach((aiQuestion, idx) => {
                try {
                    const question = mapAiQuestionToInternalQuestion(aiQuestion, request);
                    validQuestions.push(question);
                } catch (error) {
                    const errorMessage = error instanceof Error ? error.message : String(error);
                    console.warn(`[Validation] Question ${idx} failed mapping: ${errorMessage}`);
                    errors.push({ index: idx, error: errorMessage });
                }
            });
        } else {
            // light | strict: 2차 AI 검증 수행
            console.log(`[Validation] Starting AI verification for ${structurallyValidQuestions.length} questions (level: ${validationLevel})`);
            const verificationStartTime = Date.now();

            const verificationResults = await verifyQuestionsWithAI(structurallyValidQuestions);

            const verificationDuration = Date.now() - verificationStartTime;
            console.log(`[Validation] AI verification completed in ${verificationDuration}ms`);

            // 9-4. 검증 결과 적용 및 최종 문제 생성
            structurallyValidQuestions.forEach((aiQuestion, idx) => {
                const verificationResult = verificationResults[idx];

                // AI 검증에서 invalid로 판정된 경우
                if (!verificationResult.isValid) {
                    console.warn(
                        `[Validation] Question ${idx} marked invalid by AI verification:`,
                        verificationResult.issueSummary
                    );
                    console.warn(
                        `[Validation] Stem: "${aiQuestion.stem.substring(0, 100)}..."`
                    );
                    errors.push({
                        index: idx,
                        error: `AI verification failed: ${verificationResult.issueSummary}`
                    });
                    return; // 이 문제는 제외
                }

                // fixedIsCorrect가 제공된 경우 적용
                if (verificationResult.fixedIsCorrect && verificationResult.fixedIsCorrect.length === aiQuestion.choices.length) {
                    console.log(
                        `[Validation] Question ${idx}: Applying AI-corrected isCorrect flags`
                    );
                    aiQuestion.choices.forEach((choice, choiceIdx) => {
                        choice.isCorrect = verificationResult.fixedIsCorrect![choiceIdx];
                    });
                }

                // Question 타입으로 변환 (기존 검증 로직 포함)
                try {
                    const question = mapAiQuestionToInternalQuestion(aiQuestion, request);
                    validQuestions.push(question);
                } catch (error) {
                    const errorMessage = error instanceof Error ? error.message : String(error);
                    console.warn(`[Validation] Question ${idx} failed final mapping: ${errorMessage}`);
                    console.warn(`[Validation] Skipping question ${idx}:`, JSON.stringify(aiQuestion, null, 2));
                    errors.push({ index: idx, error: errorMessage });
                }
            });
        }

        // 10. 최종 검증: 유효한 문제 수 확인 (validationLevel에 따라 분기)
        if (validQuestions.length === 0) {
            throw new Error(`No valid questions generated. Total errors: ${errors.length}`);
        }

        if (validQuestions.length < count) {
            console.warn(
                `[OpenAI] Warning: Generated ${validQuestions.length}/${count} valid questions. ` +
                `${errors.length} failed validation. Mode: ${validationLevel}`
            );

            if (validationLevel === "strict") {
                // strict: 부족하면 에러
                throw new Error(
                    `Generated only ${validQuestions.length} valid questions out of ${count} requested. ` +
                    `${errors.length} questions failed validation.`
                );
            } else {
                // none | light: 경고만 하고 진행
                console.log(`[OpenAI] Returning ${validQuestions.length} questions in ${validationLevel} mode`);
            }
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
You are an expert at rephrasing exam questions while maintaining their educational value.

Your task is to rewrite a given question with:
- Same correct answer and learning objective
- Different wording and phrasing
- Same difficulty level (unless specified otherwise)
- Plausible alternative distractors

DIFFICULTY RULES:
Target difficulty is "${difficulty}".
${difficultyGuidelines}

OUTPUT FORMAT (JSON ONLY):
{
  "stem": "Rephrased question text",
  "choices": [
    {
      "text": "Choice 1",
      "isCorrect": false,
      "reason": "짧은 이유"
    },
    {
      "text": "Choice 2",
      "isCorrect": true,
      "reason": "짧은 이유"
    },
    ...
  ],
  "explanation": "Updated explanation"
}

CRITICAL CONSTRAINTS:
- EXACTLY ONE choice must have "isCorrect": true
- All other choices must have "isCorrect": false
- The "reason" field should be 1-2 sentences
- NO Markdown, NO extra text - ONLY JSON

EXPLANATION QUALITY REQUIREMENTS:
The "explanation" must be detailed (3-6 sentences):
- Mention the core concept being tested
- Explain the logical reasoning to reach the correct answer
- For multi-statement questions (ㄱ,ㄴ,ㄷ,ㄹ or numbered), break down EACH statement with reason
- Example: "ㄱ: 참이다. 이유는 ~" / "ㄴ: 거짓이다. 이유는 ~"
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
const MAX_QUESTIONS_PER_CALL = 10;

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
