import { DOCUMENT } from '@angular/common';
import { DestroyRef, inject } from '@angular/core';

const CLASSES_TEMA_ESCURO = ['app-dark', 'dark'];

/**
 * Faz o relatório sair sempre no tema claro na impressão (botão ou Ctrl+P): papel
 * escuro gasta tinta e fica ilegível em preto e branco. O tema da tela volta logo
 * depois. Chame em contexto de injeção.
 */
export function imprimirSempreNoTemaClaro(): void {
  const documento = inject(DOCUMENT);
  const janela = documento.defaultView;
  if (!janela) return;

  const raiz = documento.documentElement;
  let removidas: string[] = [];

  const antes = () => {
    removidas = CLASSES_TEMA_ESCURO.filter((classe) => raiz.classList.contains(classe));
    raiz.classList.remove(...removidas);
  };
  const depois = () => {
    raiz.classList.add(...removidas);
    removidas = [];
  };

  janela.addEventListener('beforeprint', antes);
  janela.addEventListener('afterprint', depois);
  inject(DestroyRef).onDestroy(() => {
    janela.removeEventListener('beforeprint', antes);
    janela.removeEventListener('afterprint', depois);
    if (removidas.length) depois();
  });
}
