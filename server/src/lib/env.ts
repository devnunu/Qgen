export const env = {
    OPENAI_API_KEY: process.env.OPENAI_API_KEY,
};

export function validateEnv() {
    if (!env.OPENAI_API_KEY) {
        throw new Error("Missing OPENAI_API_KEY environment variable");
    }
}
