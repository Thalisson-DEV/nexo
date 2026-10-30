import { WritableSignal, effect, inject, untracked } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

export interface StepUrlOptions {
  /** Nome do parâmetro. Default: `passo`. */
  param?: string;
  /** Passo inicial (omitido da URL). Default: 1. */
  first?: number;
}

export interface StepUrl {
  /**
   * Aplica o passo da URL se `podeRestaurar` permitir (ex.: o rascunho com os dados dos passos
   * anteriores foi restaurado). Caso contrário, mantém o passo inicial.
   */
  restore(podeRestaurar: (passo: number) => boolean): void;
}

/**
 * Passo de wizard/stepper na URL (replace): F5 e Voltar reabrem no mesmo passo.
 * Os dados do wizard ficam no rascunho (`createFormDraft`), não na URL.
 */
export function bindStepToUrl(step: WritableSignal<number>, options: StepUrlOptions = {}): StepUrl {
  const router = inject(Router);
  const route = inject(ActivatedRoute);
  const param = options.param ?? 'passo';
  const first = options.first ?? 1;
  let ready = false;

  effect(() => {
    const atual = step();
    untracked(() => {
      if (!ready) return;
      const naUrl = route.snapshot.queryParamMap.get(param);
      const desejado = atual === first ? null : String(atual);
      if (naUrl === desejado) return;
      void router.navigate([], {
        relativeTo: route,
        queryParams: { [param]: desejado },
        queryParamsHandling: 'merge',
        replaceUrl: true,
      });
    });
  });

  return {
    restore(podeRestaurar) {
      const bruto = route.snapshot.queryParamMap.get(param);
      const valor = Number(bruto);
      ready = true;
      if (Number.isInteger(valor) && valor !== first && podeRestaurar(valor)) {
        step.set(valor);
      } else if (bruto !== null) {
        // Passo sem dados que o sustentem (link copiado, rascunho descartado): limpa a URL.
        void router.navigate([], {
          relativeTo: route,
          queryParams: { [param]: null },
          queryParamsHandling: 'merge',
          replaceUrl: true,
        });
      }
    },
  };
}
