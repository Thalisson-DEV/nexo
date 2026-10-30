export interface PageMetadata {
  size: number;
  number: number;
  totalElements: number;
  totalPages: number;
}

export interface PagedResponse<T> {
  content: T[];
  page: PageMetadata;
}

export interface CampoAlterado {
  campo: string;
  valorAnterior?: any;
  valorNovo?: any;
}

export interface AlteracaoLog {
  logId: string;
  dataAlteracao: string;
  operacao: 'INSERT' | 'UPDATE' | 'DELETE';
  usuario: string;
  camposAlterados: CampoAlterado[];
}

export interface AuditLogResponse {
  registroId: string;
  tabela: string;
  alteracoes: AlteracaoLog[];
}

export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: { [key: string]: string[] };
  erros?: Record<string, string>;
  retryAfter?: number;
}

export function getErrorMessage(error: any): string {
  if (typeof error === 'string') return error;

  const problem = error?.error as ProblemDetails;

  if (problem?.errors) {
    const firstKey = Object.keys(problem.errors)[0];
    if (firstKey && problem.errors[firstKey].length > 0) {
      return `${firstKey}: ${problem.errors[firstKey][0]}`;
    }
  }

  if (problem?.erros) {
    const firstKey = Object.keys(problem.erros)[0];
    if (firstKey) return `${firstKey}: ${problem.erros[firstKey]}`;
  }

  return problem?.detail || problem?.title || error?.message || 'Ocorreu um erro inesperado.';
}

export function generateReferenceOptions(months: number = 24): { label: string; value: string }[] {
  const options: { label: string; value: string }[] = [];
  const now = new Date();

  for (let i = 0; i < months; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    const month = d.toLocaleString('pt-BR', { month: 'short' }).toUpperCase().replace('.', '');
    const year = d.getFullYear();
    const label = `${month}/${year}`;
    options.push({ label, value: label });
  }

  return options;
}
