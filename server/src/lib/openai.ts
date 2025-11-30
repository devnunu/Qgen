import OpenAI from 'openai';
import { v4 as uuidv4 } from 'uuid';
import { env } from './env';
import { GenerateQuestionsRequest, GenerateQuestionsResponse, Question } from './types';
import { pickTemplatesForRequest, getTemplateGuidelines, ProblemTemplate } from './templates';

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
- Use 2-4 sentences or medium-length code snippet
- Include at least 1 distractor that represents a common real-world mistake
`.trim();

        case "hard":
            return `
HARD questions should match Korean mock exam/certification test 4-point difficulty:
- Involve 2+ concepts/conditions simultaneously and their interactions
- Use multiple sentences, bulleted conditions, or 10+ lines of code
- Require 3+ steps of reasoning (condition analysis → case evaluation → conclusion)
- Have plausible distractors that differ only by subtle nuances
- Each distractor should seem correct unless carefully analyzed
`.trim();

        case "mixed":
            return `
MIXED difficulty should distribute questions as:
- ~30% easy, ~40% medium, ~30% hard
- Set each question's "difficulty" field correctly
- Follow the respective easy/medium/hard rules for each question
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
 * Skeletal Question Generation
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

HARD EXAMPLES (DO NOT COPY, ONLY IMITATE STYLE):

[Example 1 - Conceptual]
다음 중 Android 권한 시스템에 대한 설명으로 옳지 않은 것을 고르시오.

ㄱ. targetSdkVersion 23 미만을 대상으로 하는 앱은 설치 시 모든 위험 권한을 일괄적으로 승인받는다.
ㄴ. targetSdkVersion 30 이상에서 위치 권한을 요청할 때는 전경 위치 권한과 백그라운드 위치 권한을 분리하여 요청해야 한다.
ㄷ. 사용자가 권한 요청을 여러 번 거부하면 '다시 묻지 않음' 옵션이 활성화될 수 있으며, 이 경우 사용자는 시스템 설정에서 직접 권한을 변경해야 한다.
ㄹ. 시스템 권한은 일반 앱에서도 요청할 수 있으며 사용자가 동의하면 정상적으로 부여된다.

① ㄱ, ㄴ ② ㄱ, ㄷ ③ ㄴ, ㄷ ④ ㄷ, ㄹ ⑤ ㄱ, ㄹ

정답: ④
설명: 시스템 권한은 일반 앱에서 사용할 수 없으며, 플랫폼 서명이나 시스템 앱에만 허용된다.

[Example 2 - Code]
다음 Kotlin 코드의 실행 결과로 출력되는 값을 시간 순서대로 바르게 나열한 것은?

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
        flow.collect { value ->
            println("value: $value")
        }
    }
}

보기:
1) 2, 3, 4, 5, 6
2) 2, 4, 5
3) 2, 3, 5
4) 2, 4, 5, 6
5) 2, 3, 4

정답: 2) 2, 4, 5
설명: flatMapLatest 특성상 마지막 emit 이전의 내부 flow는 취소되며, 각 시점에서 외부 값과 내부 state 값을 더한 결과만 출력된다.

When creating hard questions, follow the structure and depth of reasoning of these examples,
but do NOT reuse the same wording or content. Create entirely new questions.
`.trim() : "";

    // 4. System 메시지: 템플릿 + 난이도 규칙 + 예시
    const systemPrompt = `
You are an expert exam question creator specializing in creating high-quality multiple-choice questions.

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
      "stem": "Question text",
      "choices": [
        { "text": "Choice 1" },
        { "text": "Choice 2" },
        { "text": "Choice 3" },
        { "text": "Choice 4" }
      ],
      "correctChoiceIndex": 0,
      "explanation": "Explanation text",
      "difficulty": "easy" | "medium" | "hard"
    }
  ]
}

CONSTRAINTS:
- choices array must contain ONLY "text" field (no "id" field)
- correctChoiceIndex is 0-based integer (0 = first choice, 1 = second choice, etc.)
- Each question must use exactly ${choiceCount} choices
- Language: ${language === "ko" ? "Korean" : "English"}
- NO Markdown formatting, NO code blocks, NO extra text - ONLY JSON
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

Return ONLY the JSON object. No other text.
`.trim();

    try {
        console.log(`[OpenAI] Starting skeletal generation for ${count} questions on topic: "${topic}"`);
        const startTime = Date.now();

        // 4. OpenAI API 호출
        const completion = await client.chat.completions.create({
            model: MODEL_NAME,
            messages: [
                { role: "system", content: systemPrompt },
                { role: "user", content: userPrompt },
            ],
            temperature: 0.7,
        });

        const apiDuration = Date.now() - startTime;
        console.log(`[OpenAI] API call completed in ${apiDuration}ms (${(apiDuration / 1000).toFixed(2)}s)`);

        const content = completion.choices[0]?.message?.content;
        if (!content) {
            throw new Error("No content received from OpenAI");
        }

        // 5. JSON 파싱 (Markdown 제거)
        const jsonString = content.replace(/^```json\s*/, '').replace(/\s*```$/, '').trim();

        let parsed: any;
        try {
            parsed = JSON.parse(jsonString);
        } catch (e) {
            console.error("[OpenAI] Failed to parse JSON:", content);
            throw new Error("Failed to parse JSON response from AI");
        }

        // 6. 응답 검증
        if (!Array.isArray(parsed.questions)) {
            throw new Error("Invalid response format: 'questions' array is missing");
        }

        // 7. Question 타입으로 변환 (서버에서 id 생성)
        const questions: Question[] = parsed.questions.map((q: any, index: number) => {
            // 필수 필드 검증
            if (!q.stem || !Array.isArray(q.choices) || typeof q.correctChoiceIndex !== 'number' || !q.explanation) {
                console.error(`[OpenAI] Invalid question structure at index ${index}:`, q);
                throw new Error(`Invalid question structure at index ${index}`);
            }

            // choices 개수 검증
            if (q.choices.length !== choiceCount) {
                console.warn(`[OpenAI] Question ${index} has ${q.choices.length} choices, expected ${choiceCount}`);
                // 엄격하게 검증하거나, 경고만 하고 진행할 수 있음
                // 여기서는 엄격하게 검증
                throw new Error(`Question ${index} has incorrect number of choices: expected ${choiceCount}, got ${q.choices.length}`);
            }

            // correctChoiceIndex 범위 검증
            if (q.correctChoiceIndex < 0 || q.correctChoiceIndex >= q.choices.length) {
                throw new Error(`Invalid correctChoiceIndex ${q.correctChoiceIndex} for question ${index}`);
            }

            // choices에 id 부여 (A, B, C, D, ...)
            const choicesWithId = q.choices.map((choice: any, idx: number) => ({
                id: String.fromCharCode(65 + idx), // 65 = 'A'
                text: choice.text,
            }));

            // correctChoiceIndex를 correctChoiceId로 변환
            const correctChoiceId = String.fromCharCode(65 + q.correctChoiceIndex);

            return {
                id: uuidv4(),
                stem: q.stem,
                choices: choicesWithId,
                correctChoiceId: correctChoiceId,
                explanation: q.explanation,
                metadata: {
                    topic: topic,
                    difficulty: q.difficulty || (difficulty === 'mixed' ? 'medium' : difficulty),
                },
            };
        });

        const totalDuration = Date.now() - startTime;
        console.log(`[OpenAI] Total processing completed in ${totalDuration}ms (${(totalDuration / 1000).toFixed(2)}s)`);
        console.log(`[OpenAI] Successfully generated ${questions.length} questions using skeletal generation`);

        return { questions };

    } catch (error) {
        console.error("[OpenAI] Error generating questions:", error);
        throw error;
    }
}

/**
 * 기존 문제를 변형하는 경량 API
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

    const systemPrompt = `
You are an expert at rephrasing exam questions while maintaining their educational value.

Your task is to rewrite a given question with:
- Same correct answer and learning objective
- Different wording and phrasing
- Same difficulty level (unless specified otherwise)
- Plausible alternative distractors

OUTPUT FORMAT (JSON ONLY):
{
  "stem": "Rephrased question text",
  "choices": [
    { "text": "Choice 1" },
    { "text": "Choice 2" },
    { "text": "Choice 3" },
    { "text": "Choice 4" }
  ],
  "correctChoiceIndex": 0,
  "explanation": "Updated explanation"
}

NO Markdown, NO extra text - ONLY JSON.
`.trim();

    const userPrompt = `
Rephrase this question:

Original Question: ${originalQuestion.stem}
Original Choices:
${originalQuestion.choices.map((c, i) => `${i + 1}. ${c.text}`).join('\n')}
Correct Answer: ${originalQuestion.correctChoiceId}
Original Explanation: ${originalQuestion.explanation}

Target Difficulty: ${difficulty}
Target Language: ${language}

Maintain the same learning objective but use different wording.
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
        });

        const content = completion.choices[0]?.message?.content;
        if (!content) {
            throw new Error("No content received from OpenAI");
        }

        const jsonString = content.replace(/^```json\s*/, '').replace(/\s*```$/, '').trim();
        const parsed = JSON.parse(jsonString);

        // 검증
        if (!parsed.stem || !Array.isArray(parsed.choices) || typeof parsed.correctChoiceIndex !== 'number') {
            throw new Error("Invalid regenerated question structure");
        }

        // choices에 id 부여
        const choicesWithId = parsed.choices.map((choice: any, idx: number) => ({
            id: String.fromCharCode(65 + idx),
            text: choice.text,
        }));

        const correctChoiceId = String.fromCharCode(65 + parsed.correctChoiceIndex);

        const regeneratedQuestion: Question = {
            id: uuidv4(),
            stem: parsed.stem,
            choices: choicesWithId,
            correctChoiceId: correctChoiceId,
            explanation: parsed.explanation,
            metadata: {
                topic: originalQuestion.metadata.topic,
                difficulty: difficulty,
            },
        };

        const duration = Date.now() - startTime;
        console.log(`[OpenAI] Question regenerated in ${duration}ms`);

        return regeneratedQuestion;

    } catch (error) {
        console.error("[OpenAI] Error regenerating question:", error);
        throw error;
    }
}
