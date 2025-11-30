import OpenAI from 'openai';
import { v4 as uuidv4 } from 'uuid';
import { env } from './env';
import {
    GenerateQuestionsRequest,
    GenerateQuestionsResponse,
    Question,
    AiQuestion,
    AiChoice
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
- The "explanation" field should summarize the overall reasoning
- Each question must use exactly ${choiceCount} choices
- Language: ${language === "ko" ? "Korean" : "English"}
- For CODE type questions: MUST include actual code in the "stem" field
- When including code, use \\n for line breaks (JSON escaped newlines)
- Example: "다음 코드의 실행 결과는?\\n\\nfun main() {\\n    println(\\"Hello\\")\\n}"
- The entire response must be ONLY valid JSON - no markdown code blocks wrapping the JSON
- DO NOT wrap the JSON response in \`\`\`json...\`\`\` blocks
- DO NOT include any text before or after the JSON object
- Ensure all strings are properly JSON-escaped (use \\\\ for backslash, \\" for quotes, \\n for newlines)
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

        // 9. Question 타입으로 변환 (검증 포함)
        const validQuestions: Question[] = [];
        const errors: Array<{ index: number; error: string }> = [];

        parsed.questions.forEach((aiQuestion: any, index: number) => {
            try {
                const question = mapAiQuestionToInternalQuestion(aiQuestion, request);
                validQuestions.push(question);
            } catch (error) {
                const errorMessage = error instanceof Error ? error.message : String(error);
                console.warn(`[OpenAI] Question ${index} validation failed: ${errorMessage}`);
                console.warn(`[OpenAI] Skipping question ${index}:`, JSON.stringify(aiQuestion, null, 2));
                errors.push({ index, error: errorMessage });
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
