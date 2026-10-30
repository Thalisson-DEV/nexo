import { defineConfig } from 'vitest/config';

/**
 * Base do Vitest para o `ng test` (lida pelo builder via `runnerConfig: true` no angular.json).
 * O builder monta o resto (setup do TestBed, include, ambiente); aqui ficam só os ajustes da suíte.
 */
export default defineConfig({
  test: {
    // O builder usa `isolate: false` por padrão: todos os arquivos dividem o mesmo processo e a
    // memória só cresce até o worker morrer ("JavaScript heap out of memory") no meio da suíte.
    isolate: true,
    // Telas de listagem montam data-table, filtros e URL de verdade; em máquina carregada (runner do
    // CI, dev server e backend rodando) passam dos 5 s padrão sem estarem erradas.
    testTimeout: 30_000,
    hookTimeout: 30_000
  }
});
