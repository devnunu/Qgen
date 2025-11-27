import { NextResponse } from 'next/server';
import { validateEnv } from '@/lib/env';
import { generateQuestionsFromAI } from '@/lib/openai';
import { GenerateQuestionsRequest } from '@/lib/types';

export async function POST(req: Request) {
    try {
        // 1. Validate Environment
        validateEnv();

        // 2. Parse Body
        let body: any;
        try {
            body = await req.json();
        } catch (e) {
            return NextResponse.json(
                { success: false, error: "Invalid JSON body" },
                { status: 400 }
            );
        }

        // 3. Validate Request Fields
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

        if (!count || typeof count !== 'number' || count < 1 || count > 50) {
            return NextResponse.json(
                { success: false, error: "'count' must be a number between 1 and 50" },
                { status: 400 }
            );
        }

        // Construct Request Object with defaults
        const request: GenerateQuestionsRequest = {
            topic,
            subtopics: body.subtopics, // Optional
            difficulty,
            count,
            choiceCount: choiceCount || 4,
            language: language || "ko",
        };

        // 4. Call AI Service
        const result = await generateQuestionsFromAI(request);

        // 5. Return Response
        return NextResponse.json({
            success: true,
            data: result,
        });

    } catch (error: any) {
        console.error("API Error:", error);
        return NextResponse.json(
            {
                success: false,
                error: error.message || "Internal Server Error"
            },
            { status: 500 }
        );
    }
}
