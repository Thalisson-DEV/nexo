/** Configuração injetada no bundle pela implantação do frontend. */
export const STATUS_PAGE_URL = globalThis.__CES_CONFIG__?.statusPageUrl ?? '';
export const STATUS_PUBLIC_API_URL = globalThis.__CES_CONFIG__?.statusPublicApiUrl ?? '';

export const environment = {
  production: true,
  apiUrl: '/api',
  /** Publicados pela implantação do plano de observabilidade, sem depender do Nexo. */
  statusPageUrl: STATUS_PAGE_URL,
  statusPublicApiUrl: STATUS_PUBLIC_API_URL,
};
