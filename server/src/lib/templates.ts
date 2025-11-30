import { GenerateQuestionsRequest } from "./types";

export type TemplateDifficulty = "easy" | "medium" | "hard" | "mixed";
export type TemplateKind = "concept" | "code" | "scenario" | "multiple_true" | "compare";

/**
 * 문제 템플릿 정의
 * 실제 시험지에서 자주 보이는 문제 형태의 뼈대만 정의
 */
export interface ProblemTemplate {
    id: string;
    name: string;
    description: string;
    // 문제 유형 (개념, 코드, 시나리오 등)
    kind: TemplateKind;
    // 지원하는 난이도 (없으면 전 난이도 지원)
    supportedDifficulties?: TemplateDifficulty[];
    // 지원하는 언어 (없으면 ko/en 둘 다 지원)
    supportedLanguages?: Array<"ko" | "en">;
}

/**
 * 주제와 무관하게 사용할 수 있는 문제 템플릿 목록
 */
export const PROBLEM_TEMPLATES: ProblemTemplate[] = [
    // 1. 개념 설명형 (기본)
    {
        id: "concept-definition",
        name: "개념 정의형",
        description: "특정 개념이나 용어의 정의를 묻는 문제",
        kind: "concept",
        supportedDifficulties: ["easy", "medium"],
    },

    // 2. 개념 특징형
    {
        id: "concept-characteristics",
        name: "개념 특징형",
        description: "특정 개념의 특징이나 속성을 묻는 문제",
        kind: "concept",
        supportedDifficulties: ["easy", "medium"],
    },

    // 3. 코드 결과 예측형
    {
        id: "code-output",
        name: "코드 실행 결과형",
        description: "주어진 코드를 실행했을 때의 출력 결과를 묻는 문제",
        kind: "code",
        supportedDifficulties: ["medium", "hard"],
    },

    // 4. 코드 오류 찾기형
    {
        id: "code-error",
        name: "코드 오류 분석형",
        description: "코드에서 잘못된 부분이나 개선할 부분을 찾는 문제",
        kind: "code",
        supportedDifficulties: ["medium", "hard"],
    },

    // 5. 실무 시나리오형
    {
        id: "scenario-practical",
        name: "실무 상황 분석형",
        description: "실제 업무 상황에서의 적절한 해결책을 묻는 문제",
        kind: "scenario",
        supportedDifficulties: ["medium", "hard"],
    },

    // 6. 문제 해결형
    {
        id: "scenario-problem-solving",
        name: "문제 해결 시나리오형",
        description: "주어진 문제 상황에 대한 최선의 해결 방법을 묻는 문제",
        kind: "scenario",
        supportedDifficulties: ["medium", "hard"],
    },

    // 7. 두 개념 비교형
    {
        id: "compare-concepts",
        name: "개념 비교형",
        description: "두 개 이상의 개념이나 기술을 비교하는 문제",
        kind: "compare",
        supportedDifficulties: ["medium", "hard"],
    },

    // 8. 장단점 비교형
    {
        id: "compare-pros-cons",
        name: "장단점 분석형",
        description: "특정 방법이나 기술의 장단점을 비교하는 문제",
        kind: "compare",
        supportedDifficulties: ["medium", "hard"],
    },

    // 9. 참/거짓 조합형
    {
        id: "multiple-true-false",
        name: "복수 진위 판단형",
        description: "여러 보기 중 옳은 것을 모두 고르는 문제",
        kind: "multiple_true",
        supportedDifficulties: ["hard"],
    },

    // 10. 순서/절차형
    {
        id: "concept-sequence",
        name: "순서 및 절차형",
        description: "올바른 순서나 절차를 묻는 문제",
        kind: "concept",
        supportedDifficulties: ["easy", "medium", "hard"],
    },
];

/**
 * 요청에 맞는 템플릿을 선택하는 로직
 * difficulty, count 등을 고려하여 적절한 템플릿 조합을 반환
 */
export function pickTemplatesForRequest(
    req: GenerateQuestionsRequest
): ProblemTemplate[] {
    const { difficulty, count, language = "ko" } = req;

    // 1. 난이도와 언어에 맞는 템플릿 필터링
    let availableTemplates = PROBLEM_TEMPLATES.filter((template) => {
        // 난이도 체크
        const difficultyMatch =
            !template.supportedDifficulties ||
            template.supportedDifficulties.includes(difficulty as TemplateDifficulty) ||
            difficulty === "mixed";

        // 언어 체크
        const languageMatch =
            !template.supportedLanguages ||
            template.supportedLanguages.includes(language as "ko" | "en");

        return difficultyMatch && languageMatch;
    });

    // 2. 난이도별 템플릿 종류 비율 조정
    let selectedTemplates: ProblemTemplate[] = [];

    if (difficulty === "easy") {
        // Easy: concept 위주 (70%), scenario (30%)
        const conceptTemplates = availableTemplates.filter((t) => t.kind === "concept");
        const scenarioTemplates = availableTemplates.filter((t) => t.kind === "scenario");

        selectedTemplates = [
            ...conceptTemplates.slice(0, 2),
            ...scenarioTemplates.slice(0, 1),
        ];
    } else if (difficulty === "medium") {
        // Medium: concept (40%), compare (30%), scenario (30%)
        const conceptTemplates = availableTemplates.filter((t) => t.kind === "concept");
        const compareTemplates = availableTemplates.filter((t) => t.kind === "compare");
        const scenarioTemplates = availableTemplates.filter((t) => t.kind === "scenario");

        selectedTemplates = [
            ...conceptTemplates.slice(0, 2),
            ...compareTemplates.slice(0, 1),
            ...scenarioTemplates.slice(0, 1),
        ];
    } else if (difficulty === "hard") {
        // Hard: code (40%), scenario (30%), compare (20%), multiple_true (10%)
        const codeTemplates = availableTemplates.filter((t) => t.kind === "code");
        const scenarioTemplates = availableTemplates.filter((t) => t.kind === "scenario");
        const compareTemplates = availableTemplates.filter((t) => t.kind === "compare");
        const multipleTemplates = availableTemplates.filter((t) => t.kind === "multiple_true");

        selectedTemplates = [
            ...codeTemplates.slice(0, 2),
            ...scenarioTemplates.slice(0, 1),
            ...compareTemplates.slice(0, 1),
            ...multipleTemplates.slice(0, 1),
        ];
    } else {
        // Mixed: 모든 종류를 골고루 섞기
        const kindGroups = new Map<TemplateKind, ProblemTemplate[]>();
        availableTemplates.forEach((template) => {
            if (!kindGroups.has(template.kind)) {
                kindGroups.set(template.kind, []);
            }
            kindGroups.get(template.kind)!.push(template);
        });

        // 각 kind에서 1~2개씩 선택
        kindGroups.forEach((templates) => {
            selectedTemplates.push(...templates.slice(0, 2));
        });
    }

    // 3. 템플릿이 부족하면 사용 가능한 모든 템플릿 추가
    if (selectedTemplates.length < 3) {
        selectedTemplates = availableTemplates.slice(0, Math.min(5, availableTemplates.length));
    }

    // 4. 문제 수가 많으면 템플릿을 더 다양하게
    if (count > 30 && selectedTemplates.length < availableTemplates.length) {
        selectedTemplates = availableTemplates;
    }

    return selectedTemplates;
}

/**
 * 템플릿 종류별 설명을 생성
 * OpenAI 프롬프트에 포함될 템플릿 가이드라인
 */
export function getTemplateGuidelines(templates: ProblemTemplate[]): string {
    const guidelines: string[] = [];

    templates.forEach((template) => {
        let guide = `- ${template.name} (${template.id}): ${template.description}`;

        // 종류별 추가 가이드라인
        switch (template.kind) {
            case "concept":
                guide += "\n  형식: '다음 중 ~에 대한 설명으로 옳은 것은?' 또는 '~의 특징으로 옳지 않은 것은?'";
                break;
            case "code":
                guide += "\n  형식: **MUST include actual code snippet** in the question stem, then ask '다음 코드의 실행 결과는?' 또는 '이 코드의 문제점은?'";
                guide += "\n  중요: 코드는 실제로 작성되어야 하며, 단순히 '다음 코드'라고만 언급하지 말 것";
                break;
            case "scenario":
                guide += "\n  형식: 실무 상황을 제시하고 '이 상황에서 가장 적절한 해결책은?' 또는 '다음 중 올바른 접근 방법은?'";
                break;
            case "compare":
                guide += "\n  형식: '다음 중 A와 B의 차이점으로 옳은 것은?' 또는 '~의 장점으로 옳지 않은 것은?'";
                break;
            case "multiple_true":
                guide += "\n  형식: '다음 설명 중 옳은 것을 모두 고르면?' (보기에 번호 조합 제시)";
                break;
        }

        guidelines.push(guide);
    });

    return guidelines.join("\n");
}
