import { NextResponse } from 'next/server';

export async function GET(req: Request) {
    try {
        return NextResponse.json({ status: 'ok' });
    } catch (error) {
        return NextResponse.json(
            { status: 'error', message: 'Internal Server Error' },
            { status: 500 }
        );
    }
}
