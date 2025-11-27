import OpenAI from 'openai';
import { v4 as uuidv4 } from 'uuid';
import { env } from './env';
import { GenerateQuestionsRequest, GenerateQuestionsResponse, Question } from './types';

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

export async function generateQuestionsFromAI(
    request: GenerateQuestionsRequest
): Promise<GenerateQuestionsResponse> {
    const client = getOpenAIClient();

    const { topic, subtopics, difficulty, count, choiceCount = 4, language = "ko" } = request;

    const systemPrompt = `
You are an expert exam question creator for various fields (e.g., High School subjects, General Knowledge, Programming, etc.).
Your task is to generate multiple-choice questions based on the user's requirements.

Output Format:
- You must respond strictly in JSON format.
- Do not include any Markdown formatting (like \`\`\`json), explanation text, or additional commentary.
- The JSON structure must be:
  {
    "questions": [
      {
        "stem": "Question text here",
        "choices": [
          { "id": "A", "text": "Choice A text" },
          { "id": "B", "text": "Choice B text" },
          ...
        ],
        "correctChoiceId": "A", // or B, C, D...
        "explanation": "Explanation of the answer",
        "metadata": {
          "topic": "Topic name",
          "difficulty": "easy" | "medium" | "hard"
        }
      }
    ]
  }

Constraints:
- "choices" must use IDs "A", "B", "C", "D"... in order.
- Ensure distractors (wrong answers) are plausible and challenging.
- If language is "ko", write stem, choices, and explanation in Korean.
- If language is "en", write them in English.
- Respect the requested difficulty and topic.
`.trim();

    const userPrompt = `
Generate ${count} multiple-choice questions.

Topic: ${topic}
${subtopics ? `Subtopics: ${subtopics.join(', ')}` : ''}
Difficulty: ${difficulty}
Number of Choices per Question: ${choiceCount}
Language: ${language}

Remember: JSON ONLY. No other text.
`.trim();

    try {
        const completion = await client.chat.completions.create({
            model: MODEL_NAME,
            messages: [
                { role: "system", content: systemPrompt },
                { role: "user", content: userPrompt },
            ],
            temperature: 0.7,
        });

        const content = completion.choices[0]?.message?.content;
        if (!content) {
            throw new Error("No content received from OpenAI");
        }

        // Clean up potential markdown code blocks if the model ignores the instruction
        const jsonString = content.replace(/^```json\s*/, '').replace(/\s*```$/, '');

        let parsed: any;
        try {
            parsed = JSON.parse(jsonString);
        } catch (e) {
            console.error("Failed to parse JSON from OpenAI:", content);
            throw new Error("Failed to parse JSON response from AI");
        }

        if (!Array.isArray(parsed.questions)) {
            throw new Error("Invalid response format: 'questions' array is missing");
        }

        // Map and Validate
        const questions: Question[] = parsed.questions.map((q: any) => {
            // Basic validation
            if (!q.stem || !Array.isArray(q.choices) || !q.correctChoiceId || !q.explanation) {
                throw new Error("Invalid question structure in AI response");
            }

            if (q.choices.length !== choiceCount) {
                // We could strictly throw here, or just log a warning. 
                // Let's be strict as per requirements to validate.
                // However, sometimes AI might generate 1 less or more. 
                // For now, let's allow it but maybe warn? 
                // The prompt asked for specific count. Let's trust it but if it fails, maybe we shouldn't crash everything?
                // Let's throw to be safe on "correctness".
                // Actually, let's just proceed, but maybe the user wants strict validation.
                // "choices 길이가 choiceCount와 일치하는지" -> requested validation.
                throw new Error(`Generated question has incorrect number of choices: expected ${choiceCount}, got ${q.choices.length}`);
            }

            const validIds = q.choices.map((c: any) => c.id);
            if (!validIds.includes(q.correctChoiceId)) {
                throw new Error(`Invalid correctChoiceId: ${q.correctChoiceId} not found in choices`);
            }

            return {
                id: uuidv4(),
                stem: q.stem,
                choices: q.choices,
                correctChoiceId: q.correctChoiceId,
                explanation: q.explanation,
                metadata: {
                    topic: q.metadata?.topic || topic,
                    difficulty: q.metadata?.difficulty || (difficulty === 'mixed' ? 'medium' : difficulty),
                },
            };
        });

        return { questions };

    } catch (error) {
        console.error("Error generating questions:", error);
        throw error;
    }
}
