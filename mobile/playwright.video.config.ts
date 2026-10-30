import base from './playwright.config';
import { defineConfig } from '@playwright/test';

/** Grava vídeo dos cenários de retomada para o relatório (npm run e2e:videos). */
export default defineConfig({
  ...base,
  outputDir: './e2e/.videos',
  reporter: 'line',
  use: { ...base.use, video: { mode: 'on', size: { width: 412, height: 915 } } },
  projects: [base.projects![0]],
});
