import { NextResponse } from 'next/server';
import { generateMockQuestions } from '@/lib/mock';
import { GenerateQuestionsRequest } from '@/lib/types';

export async function POST(req: Request) {
    try {
        let body: any;
        try {
            body = await req.json();
        } catch (e) {
            return NextResponse.json(
                { success: false, error: "Invalid JSON body" },
                { status: 400 }
            );
        }

        // Basic validation to ensure client sends correct structure
        const { topic, difficulty, count, choiceCount, language } = body;

        if (!topic || typeof topic !== 'string') {
            return NextResponse.json(
                { success: false, error: "Missing or invalid 'topic'" },
                { status: 400 }
            );
        }

        if (!difficulty || !['easy', 'medium', 'hard', 'mixed'].includes(difficulty)) {
            return NextResponse.json(
                { success: false, error: "Missing or invalid 'difficulty'" },
                { status: 400 }
            );
        }

        if (!count || typeof count !== 'number') {
            return NextResponse.json(
                { success: false, error: "Missing or invalid 'count'" },
                { status: 400 }
            );
        }

        const request: GenerateQuestionsRequest = {
            topic,
            subtopics: body.subtopics,
            difficulty,
            count: Number(count),
            choiceCount: choiceCount || 4,
            language: language || "ko",
        };

        const questions = generateMockQuestions(request);

        return NextResponse.json({
            success: true,
            data: { questions },
        });

    } catch (error: any) {
        console.error("Mock API Error:", error);
        return NextResponse.json(
            { success: false, error: error.message || "Internal Server Error" },
            { status: 500 }
        );
    }
}
