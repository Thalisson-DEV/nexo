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

export function limparMascarasOrfas(): void {
  document.querySelectorAll('.p-overlay-mask-leave-active').forEach((mascara) => mascara.remove());
  if (!document.querySelector('.p-overlay-mask')) document.body.classList.remove('p-overflow-hidden');
}
