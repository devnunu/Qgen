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
 * 난이도별 가이드라인 제공
 * 프로그래밍/상식/학교 과목 등 모든 도메인에 적용 가능한 난이도 규칙
 */
export function getDifficultyGuidelines(
    difficulty: GenerateQuestionsRequest["difficulty"]
): string {
    switch (difficulty) {
        case "easy":
            return `
EASY questions should:
- Test a single basic concept, definition, term, or simple fact
- Use 1-2 sentences or very short code snippet
- Be answerable in under 10 seconds if the concept is known
- Have distractors that are easily filtered out with basic knowledge
`.trim();

        case "medium":
            return `
MEDIUM questions should:
- Require at least 1 step of reasoning or short scenario/code interpretation
- Combine 2 closely related concepts
- Use 2-4 sentences or medium-length code snippet (5-10 lines)
- Include at least 1 distractor that represents a common real-world mistake
`.trim();

        case "hard":
            return `
HARD questions should match Korean mock exam/certification test 4-point difficulty:
- Involve 2+ concepts/conditions simultaneously and their interactions
- Use multiple sentences, bulleted conditions, or 10+ lines of code
- Require 3+ steps of reasoning (condition analysis → case evaluation → conclusion)
- Have plausible distractors that differ only by one decisive factor
- Each distractor should seem correct unless carefully analyzed
- For concept questions: use multi-statement format (ㄱ,ㄴ,ㄷ,ㄹ) with combination choices
- For code questions: require understanding of timing, execution flow, or subtle language features
`.trim();

        case "mixed":
            return `
MIXED difficulty should distribute questions as:
- ~30% easy, ~40% medium, ~30% hard
- Set each question's "difficulty" field to its actual difficulty
- Follow the respective easy/medium/hard rules for each individual question
`.trim();

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
You are an expert exam question auditor specializing in Korean high school and certification exam standards.

You receive multiple-choice questions with a proposed "isCorrect" flag per choice.
For each question, you must:

1. Understand the question directive (find correct answer, find incorrect answer, choose all that apply, etc.)
   - Korean patterns to look for:
     * "옳은 것" / "올바른 것" → find correct answer(s)
     * "옳지 않은 것" / "틀린 것" → find incorrect answer(s)
     * "모두 고르시오" → multiple answers allowed
     * If no "모두" → typically single answer expected

2. Judge the factual truth of each choice based on standard technical knowledge
   - Use your knowledge of the topic to determine if each statement is factually correct
   - Consider edge cases and subtle differences

3. Compare the proposed isCorrect flags with what they SHOULD be
   - Does the question ask for correct items but mark incorrect ones?
   - Are there multiple correct answers when only one should exist?
   - Are all choices actually incorrect when one should be correct?

4. Mark the question as INVALID if:
   - The question asks for a single correct answer but multiple choices are factually correct
   - The question asks for a single incorrect answer but multiple choices are factually incorrect or all are correct
   - The proposed isCorrect flags are wrong (don't match the actual truth values)
   - The question is ambiguous or lacks sufficient information to determine correctness

5. If the proposed flags are wrong but the question is salvageable, provide corrected isCorrect array

CRITICAL RULES:
- For "옳은 것을 고르시오" (find correct answer): If multiple choices are factually true, mark as INVALID
- For "옳지 않은 것을 고르시오" (find incorrect answer): If multiple choices are factually false, mark as INVALID
- Korean mock exams typically require EXACTLY ONE correct answer unless "모두" is specified
- If you're uncertain about domain knowledge, mark isValid=false with explanation

OUTPUT FORMAT (JSON ONLY):
{
  "results": [
    {
      "index": 0,
      "isValid": true,
      "fixedIsCorrect": [false, true, false, false],
      "issueSummary": ""
    },
    {
      "index": 1,
      "isValid": false,
      "issueSummary": "Multiple choices are factually correct, but the question asks for a single answer."
    }
  ]
}

Return ONLY valid JSON. No markdown, no code blocks, no extra text.
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
export async function generateQuestionsFromAI(
    request: GenerateQuestionsRequest
): Promise<GenerateQuestionsResponse> {
    const client = getOpenAIClient();

    const { topic, subtopics, difficulty, count, choiceCount = 4, language = "ko" } = request;

    // 1. 요청에 맞는 템플릿 선택
    const selectedTemplates = pickTemplatesForRequest(request);
    const templateGuidelines = getTemplateGuidelines(selectedTemplates);

    console.log(`[OpenAI] Selected ${selectedTemplates.length} templates for ${count} questions`);

    // 2. 난이도 가이드라인 가져오기
    const difficultyGuidelines = getDifficultyGuidelines(difficulty);

    // 3. Hard 난이도 예시 문제 (참고용)
    const hardExamples = (difficulty === "hard" || difficulty === "mixed") ? `

HARD EXAMPLES (DO NOT COPY CONTENT, ONLY IMITATE STRUCTURE AND DEPTH):

[Example 1 - Conceptual with Multi-Statement Format]
다음 중 Android 권한 시스템에 대한 설명으로 옳지 않은 것을 고르시오.

ㄱ. targetSdkVersion 23 미만 앱은 설치 시 모든 위험 권한을 일괄 승인받는다.
ㄴ. targetSdkVersion 30 이상에서는 전경/백그라운드 위치 권한을 분리 요청해야 한다.
ㄷ. '다시 묻지 않음' 활성화 시 사용자는 시스템 설정에서 직접 권한을 변경해야 한다.
ㄹ. 시스템 권한은 일반 앱에서도 사용자 동의로 부여 가능하다.

보기: ① ㄱ,ㄴ ② ㄱ,ㄷ ③ ㄴ,ㄷ ④ ㄷ,ㄹ ⑤ ㄱ,ㄹ

정답: ④ (ㄷ,ㄹ)
해설: ㄹ이 틀림. 시스템 권한은 플랫폼 서명/시스템 앱만 사용 가능.

[Example 2 - Code with Timing/Flow Analysis]
다음 Kotlin 코드의 실행 결과로 출력되는 값을 시간 순서대로 나열한 것은?

suspend fun main() {
    coroutineScope {
        val state = MutableStateFlow(1)
        val flow = flow {
            emit(1)
            delay(1_000L)
            emit(2)
            delay(1_000L)
            emit(3)
        }.flatMapLatest { value ->
            state.map { inner -> value + inner }
        }
        launch {
            delay(999L)
            state.emit(2)
            delay(999L)
            state.emit(3)
        }
        flow.collect { println("value: $it") }
    }
}

보기: 1) 2,3,4,5,6  2) 2,4,5  3) 2,3,5  4) 2,4,5,6  5) 2,3,4

정답: 2) 2,4,5
해설: flatMapLatest는 새 emit 시 이전 flow 취소. 각 시점 외부+내부 state 합산.

When creating hard questions, follow the structure and depth of these examples,
but do NOT reuse the same content. Create entirely new questions on the requested topic.
`.trim() : "";

    // 4. System 메시지: 템플릿 + 난이도 규칙 + 예시 + 새 JSON 포맷
    const systemPrompt = `
You are an expert exam question creator specializing in high-quality multiple-choice questions.

Your role is to generate questions using the provided PROBLEM TEMPLATES and DIFFICULTY RULES.

PROBLEM TEMPLATES:
${templateGuidelines}

DIFFICULTY RULES:
The requested overall difficulty is "${difficulty}".
${difficultyGuidelines}
${hardExamples}

INSTRUCTIONS:
1. For each question, select an appropriate template from the list above
2. Fill in the template with content relevant to the given topic
3. Ensure the question follows the template's format and style
4. Create plausible distractors (wrong answers) that test understanding
5. Strictly follow the difficulty rules - especially for hard questions, ensure multi-step reasoning is required

OUTPUT FORMAT (JSON ONLY):
{
  "questions": [
    {
      "stem": "Question text (CAN include code snippets if question type is 'code')",
      "choices": [
        {
          "text": "Choice 1",
          "isCorrect": false,
          "reason": "짧은 이유 (왜 틀렸는지)"
        },
        {
          "text": "Choice 2",
          "isCorrect": true,
          "reason": "짧은 이유 (왜 맞는지)"
        },
        {
          "text": "Choice 3",
          "isCorrect": false,
          "reason": "짧은 이유"
        },
        {
          "text": "Choice 4",
          "isCorrect": false,
          "reason": "짧은 이유"
        }
      ],
      "explanation": "정답이 왜 맞고 나머지가 왜 틀린지 요약하는 전체 해설",
      "difficulty": "easy" | "medium" | "hard"
    }
  ]
}

CRITICAL CONSTRAINTS:
- Each question MUST have EXACTLY ONE choice with "isCorrect": true
- All other choices MUST have "isCorrect": false
- The "reason" field should be 1-2 sentences explaining why this choice is correct/incorrect
- Each question must use exactly ${choiceCount} choices
- Language: ${language === "ko" ? "Korean" : "English"}
- For CODE type questions: MUST include actual code in the "stem" field
- When including code, use \\n for line breaks (JSON escaped newlines)
- Example: "다음 코드의 실행 결과는?\\n\\nfun main() {\\n    println(\\"Hello\\")\\n}"
- The entire response must be ONLY valid JSON - no markdown code blocks wrapping the JSON
- DO NOT wrap the JSON response in \`\`\`json...\`\`\` blocks
- DO NOT include any text before or after the JSON object
- Ensure all strings are properly JSON-escaped (use \\\\ for backslash, \\" for quotes, \\n for newlines)

EXPLANATION QUALITY REQUIREMENTS (모든 난이도 공통):
The "explanation" field is critical for learning and MUST follow these rules:

1. Structure (3-6 sentences recommended):
   - First: Briefly mention the core concept/rule being tested
   - Second: Explain the logical reasoning to reach the correct answer (1-3 steps)
   - Third: Mention why incorrect choices are wrong

2. For multi-statement questions (ㄱ,ㄴ,ㄷ,ㄹ style or numbered statements):
   - If the question stem or choices contain statements like "ㄱ. ~", "ㄴ. ~", "ㄷ. ~", "ㄹ. ~"
   - OR numbered statements like "(1) ~", "(2) ~", "(3) ~"
   - The explanation MUST include a breakdown for EACH statement:
     - Example format:
       "ㄱ: 참이다. 이유는 [1-2 sentences]"
       "ㄴ: 거짓이다. 이유는 [1-2 sentences]"
       "ㄷ: 참이다. 이유는 [1-2 sentences]"
       "ㄹ: 거짓이다. 이유는 [1-2 sentences]"
   - For numbered statements: "1번 진술: [correct/incorrect]. 이유는 ~"
   - This ensures students understand WHY each individual statement is true or false

3. Avoid single-sentence explanations:
   - Do NOT just say "정답은 A이다" or "B가 틀렸기 때문"
   - Always explain the reasoning process, not just the result

STEM AND CHOICES SEPARATION RULES (지문/보기 중복 금지):
To maintain proper exam format and avoid redundancy, follow these critical rules:

1. Do NOT repeat the exact same full sentence in both the question stem and the choices.
   - The stem provides context, conditions, and the question itself
   - The choices provide the possible answers
   - These should NOT duplicate each other word-for-word

2. For multi-statement format (ㄱ,ㄴ,ㄷ,ㄹ style):
   - If the stem already lists statements like:
     "ㄱ. Statement A
      ㄴ. Statement B
      ㄷ. Statement C
      ㄹ. Statement D"
   - Then choices MUST ONLY refer to combinations of those labels:
     "① ㄱ, ㄴ"
     "② ㄱ, ㄷ"
     "③ ㄴ, ㄷ"
     "④ ㄷ, ㄹ"
   - Do NOT repeat the full statement texts in the choices

3. For regular statement-based choices:
   - If choices contain full statement sentences (① ~, ② ~, ③ ~, ④ ~),
   - Then the stem must NOT pre-list those statements
   - The stem should only ask the question in 1-2 sentences
   - Example: "다음 중 View와 ViewGroup에 대한 설명으로 옳지 않은 것은?"
   - Then each choice provides a complete statement to evaluate

4. Terminology overlap is acceptable:
   - Using the same technical terms (View, ViewGroup, etc.) in both stem and choices is fine
   - But copying entire sentences/paragraphs is prohibited
`.trim();

    // 5. User 메시지: 구체적인 요청 사항
    const userPrompt = `
Generate ${count} multiple-choice questions.

Topic: ${topic}
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

        // 9-3. 2차 검증: AI 기반 도메인 지식 검증
        // NOTE: 이 단계는 추가 비용이 발생함. 향후 환경변수/feature flag로 제어 가능
        console.log(`[Validation] Starting AI verification for ${structurallyValidQuestions.length} questions`);
        const verificationStartTime = Date.now();

        const verificationResults = await verifyQuestionsWithAI(structurallyValidQuestions);

        const verificationDuration = Date.now() - verificationStartTime;
        console.log(`[Validation] AI verification completed in ${verificationDuration}ms`);

        // 9-4. 검증 결과 적용 및 최종 문제 생성
        const validQuestions: Question[] = [];
        const errors: Array<{ index: number; error: string }> = [];

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

        // 10. 최종 검증: 유효한 문제 수 확인
        if (validQuestions.length === 0) {
            throw new Error(`No valid questions generated. Total errors: ${errors.length}`);
        }

        // 정책: 요청한 개수보다 적으면 에러 (디버깅에 유리)
        // 만약 "적은 개수라도 반환"하고 싶다면 이 체크를 제거하면 됨
        if (validQuestions.length < count) {
            console.warn(`[OpenAI] Warning: Generated ${validQuestions.length} valid questions, requested ${count}`);
            // 엄격 모드: 에러 발생
            throw new Error(
                `Generated only ${validQuestions.length} valid questions out of ${count} requested. ` +
                `${errors.length} questions failed validation.`
            );
            // 관대 모드: 경고만 하고 진행
            // (위 throw를 주석 처리하고 아래 로그만 남기면 됨)
            // console.log(`[OpenAI] Returning ${validQuestions.length} valid questions despite requesting ${count}`);
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
