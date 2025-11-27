export interface ApiResponse<T = any> {
  success: boolean;
  data?: T;
  error?: string;
}

// Placeholder for Question type
export interface Question {
  // TODO: Define Question structure
  id: string;
  text: string;
  options: string[];
  answer: string;
}
