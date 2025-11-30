import OpenAI from 'openai';
import { v4 as uuidv4 } from 'uuid';
import { env } from './env';
import { GenerateQuestionsRequest, GenerateQuestionsResponse, Question } from './types';
import { pickTemplatesForRequest, getTemplateGuidelines, ProblemTemplate } from './templates';

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

    // 2. System 메시지: 템플릿 기반 생성 지시
    const systemPrompt = `
You are an expert exam question creator specializing in creating high-quality multiple-choice questions.

Your role is to generate questions using the provided PROBLEM TEMPLATES.
Each template defines a question structure/pattern that you must follow.

PROBLEM TEMPLATES:
${templateGuidelines}

INSTRUCTIONS:
1. For each question, select an appropriate template from the list above
2. Fill in the template with content relevant to the given topic
3. Ensure the question follows the template's format and style
4. Create plausible distractors (wrong answers) that test understanding

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

    // 3. User 메시지: 구체적인 요청 사항
    const userPrompt = `
Generate ${count} multiple-choice questions.

Topic: ${topic}
${subtopics && subtopics.length > 0 ? `Subtopics: ${subtopics.join(', ')}` : ''}
Difficulty: ${difficulty}
Choices per Question: ${choiceCount}
Language: ${language}

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
    targetDifficulty?: string,
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
