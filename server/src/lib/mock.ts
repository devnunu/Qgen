import { v4 as uuidv4 } from 'uuid';
import { GenerateQuestionsRequest, Question, QuestionChoice } from './types';

export function generateMockQuestions(request: GenerateQuestionsRequest): Question[] {
    const { topic, count, choiceCount = 4, difficulty, language = 'ko' } = request;

    const questions: Question[] = [];

    for (let i = 0; i < count; i++) {
        const choices: QuestionChoice[] = [];
        const choiceIds = ['A', 'B', 'C', 'D', 'E'].slice(0, choiceCount);

        choiceIds.forEach((id) => {
            choices.push({
                id,
                text: language === 'ko'
                    ? `${topic}에 대한 선택지 ${id}입니다.`
                    : `This is choice ${id} for ${topic}.`
            });
        });

        const correctChoiceId = choiceIds[Math.floor(Math.random() * choiceIds.length)];

        questions.push({
            id: uuidv4(),
            stem: language === 'ko'
                ? `[Mock] ${topic} (${difficulty}) 관련 ${i + 1}번째 문제입니다. 다음 중 옳은 것은?`
                : `[Mock] This is question #${i + 1} about ${topic} (${difficulty}). Which is correct?`,
            choices,
            correctChoiceId,
            explanation: language === 'ko'
                ? `정답은 ${correctChoiceId}입니다. 왜냐하면 이것은 목업 데이터이기 때문입니다.`
                : `The answer is ${correctChoiceId} because this is mock data.`,
            metadata: {
                topic,
                difficulty: difficulty === 'mixed' ? 'medium' : difficulty,
            }
        });
    }

    return questions;
}
