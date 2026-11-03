import { DestroyRef, inject } from '@angular/core';

/**
 * Sair de um drawer para outra rota (ex.: "Relatório completo") destrói o drawer no meio da
 * animação de saída da máscara, que leva cerca de 1 s. A máscara ficava órfã no `body`, por cima
 * da tela nova, e nada mais recebia clique até recarregar a página.
 *
 * Navega na hora e, depois que a rota troca, remove as máscaras que ficaram saindo e devolve a
 * rolagem ao `body` quando não resta nenhum overlay aberto.
 */
export function navegarSaindoDoDrawer(navegar: () => Promise<boolean>): Promise<boolean> {
  return navegar().finally(() => requestAnimationFrame(limparMascarasOrfas));
}

/** Duração da saída do painel: desmontar antes disso corta a animação. */
const SAIDA_DO_DRAWER_MS = 300;

/**
 * Saída de um drawer que a tela monta só enquanto está aberto. O `closed` do `ces-form-drawer` não
 * dispara quando ele fecha sozinho, então quem fecha chama a função devolvida: ela espera o painel
 * sair, avisa a tela e devolve o foco a quem abriu. Chame no inicializador de um campo.
 */
export function saidaDoDrawer(aoSair: () => void): () => void {
  const origem = document.activeElement as HTMLElement | null;
  let espera: ReturnType<typeof setTimeout> | undefined;
  inject(DestroyRef).onDestroy(() => clearTimeout(espera));
  return () => {
    clearTimeout(espera);
    espera = setTimeout(() => {
      aoSair();
      requestAnimationFrame(limparMascarasOrfas);
      if (origem?.isConnected) origem.focus();
    }, SAIDA_DO_DRAWER_MS);
  };
}

export function limparMascarasOrfas(): void {
  document.querySelectorAll('.p-overlay-mask-leave-active').forEach((mascara) => mascara.remove());
  // Drawer desmontado antes do fim da saída deixa a máscara sem dono, e ela nunca sai sozinha.
  const paineis = document.querySelectorAll('.p-drawer').length;
  Array.from(document.querySelectorAll('.p-drawer-mask')).slice(paineis).forEach((mascara) => mascara.remove());
  if (!document.querySelector('.p-overlay-mask')) document.body.classList.remove('p-overflow-hidden');
}
