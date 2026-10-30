interface CesRuntimeConfig {
  readonly statusPageUrl?: string;
  readonly statusPublicApiUrl?: string;
}

declare var __CES_CONFIG__: CesRuntimeConfig | undefined;
