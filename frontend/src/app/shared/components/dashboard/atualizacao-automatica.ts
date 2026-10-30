import { DestroyRef, inject } from '@angular/core';

/** Intervalo padrão em que os dashboards se renovam sozinhos com a tela aberta. */
export const ATUALIZACAO_DASHBOARD_MS = 5 * 60_000;

interface OpcoesAtualizacao {
  readonly intervaloMs?: number;
  /** Quando `false`, a rodada é pulada (ex.: carregando, sem dados ou fora do dia corrente). */
  readonly pode?: () => boolean;
}

/**
 * Renova um dashboard sem o usuário clicar em "Atualizar": a cada intervalo, só com a aba
 * visível, e ao voltar para a aba depois de o intervalo ter passado. A ação deve repetir o
 * recorte já aplicado sem mostrar carregamento nem apagar o que está em tela.
 *
 * Chamar em contexto de injeção (inicializador de campo ou construtor): para sozinho quando a
 * tela é destruída.
 */
export function atualizarPeriodicamente(acao: () => void, opcoes: OpcoesAtualizacao = {}): void {
  const intervaloMs = opcoes.intervaloMs ?? ATUALIZACAO_DASHBOARD_MS;
  let ultima = Date.now();

  const talvezAtualizar = () => {
    if (document.visibilityState !== 'visible') return;
    // Pequena folga: o timer do navegador pode disparar alguns milissegundos antes.
    if (Date.now() - ultima < intervaloMs - 1_000) return;
    if (opcoes.pode && !opcoes.pode()) return;
    ultima = Date.now();
    acao();
  };

  const timer = setInterval(talvezAtualizar, intervaloMs);
  document.addEventListener('visibilitychange', talvezAtualizar);
  inject(DestroyRef).onDestroy(() => {
    clearInterval(timer);
    document.removeEventListener('visibilitychange', talvezAtualizar);
  });
}
