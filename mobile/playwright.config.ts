import { defineConfig, devices } from '@playwright/test';

/**
 * E2E do app de campo. Roda contra o build de produção (`npm run build`) servido
 * por `e2e/support/serve.mjs`, com a API inteiramente mockada por teste.
 *
 * Projetos: Android (Chromium, Pixel 7) e iOS (WebKit, iPhone 14). O WebKit do
 * Playwright não é o Safari de um iPhone real — limitações de PWA instalada no
 * iOS ficam no checklist manual (docs/mobile).
 */
export default defineConfig({
  testDir: './e2e',
  outputDir: './e2e/.results',
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: 0,
  workers: process.env['CI'] ? 2 : 4,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'e2e/.report', open: 'never' }],
    ['json', { outputFile: 'e2e/.results/results.json' }],
  ],
  use: {
    baseURL: 'http://localhost:4300',
    trace: 'retain-on-failure',
    locale: 'pt-BR',
    timezoneId: 'America/Fortaleza',
    // Os mocks são por página; o SW do Angular é coberto em specs próprias.
    serviceWorkers: 'block',
  },
  projects: [
    { name: 'android', use: { ...devices['Pixel 7'] } },
    { name: 'ios', use: { ...devices['iPhone 14'] } },
  ],
  webServer: [
    {
      command: 'node e2e/support/serve.mjs',
      url: 'http://localhost:4300/index.html',
      reuseExistingServer: !process.env['CI'],
      env: { PORT: '4300' },
    },
    {
      // Servidor só dos testes de service worker: eles trocam a versão servida
      // (/__versao/v2) e não podem afetar os outros specs rodando em paralelo.
      command: 'node e2e/support/serve.mjs',
      url: 'http://localhost:4301/index.html',
      reuseExistingServer: !process.env['CI'],
      env: { PORT: '4301' },
    },
  ],
});
