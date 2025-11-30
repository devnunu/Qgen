import { NextRequest, NextResponse } from 'next/server';
import { regenerateQuestion } from '@/lib/openai';
import { Question } from '@/lib/types';

/**
 * POST /api/regenerate-question
 * 
 * 기존 문제를 변형하여 새로운 버전을 생성
 * 정답 의미는 유지하되 표현만 다르게 재작성
 */
export async function POST(request: NextRequest) {
    try {
        const body = await request.json();

        // 요청 검증
        const { question, targetDifficulty, targetLanguage } = body;

        if (!question || !question.stem || !question.choices) {
            return NextResponse.json(
                {
                    success: false,
                    error: 'Invalid request: question object is required with stem and choices',
                },
                { status: 400 }
            );
        }

        // 문제 재생성
        console.log(`[API] Regenerating question: ${question.id || 'unknown'}`);
        const regeneratedQuestion = await regenerateQuestion(
            question as Question,
            targetDifficulty,
            targetLanguage
        );

        return NextResponse.json({
            success: true,
            data: {
                question: regeneratedQuestion,
            },
        });

    } catch (error: any) {
        console.error('[API] Error in /api/regenerate-question:', error);

        return NextResponse.json(
            {
                success: false,
                error: error.message || 'Internal server error while regenerating question',
            },
            { status: 500 }
        );
    }
}
