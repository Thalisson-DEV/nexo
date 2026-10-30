import { Location } from '@angular/common';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

/** Uma dimensão clicável de um dashboard (`dia`, `mes`, `inspetor`, `centroCusto`…). */
export interface CesDrillTarget {
  readonly dimension: string;
  readonly value: string;
}

export const CES_DRILL_QUERY_PARAM = 'drill';

const DIMENSAO_VALIDA = /^[a-zA-Z][a-zA-Z0-9-]*$/;

/** `dia:2026-09-14` → `{ dimension: 'dia', value: '2026-09-14' }`; inválido → `null`. */
export function parseCesDrill(raw: string | null | undefined): CesDrillTarget | null {
  if (!raw) return null;
  const separador = raw.indexOf(':');
  if (separador <= 0) return null;
  const dimension = raw.slice(0, separador);
  const value = raw.slice(separador + 1);
  return DIMENSAO_VALIDA.test(dimension) && value ? { dimension, value } : null;
}

export function serializeCesDrill(target: CesDrillTarget): string {
  return `${target.dimension}:${target.value}`;
}

/**
 * Estado do drill-down de uma tela, guardado em `?drill=` para o link ser compartilhável. Abrir
 * empilha uma entrada no histórico, então o Voltar do navegador (ou do celular) fecha o painel.
 * Forneça por componente: `providers: [CesDrillStateService]`.
 */
@Injectable()
export class CesDrillStateService {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly location = inject(Location);
  /** Só volta no histórico quando a entrada foi criada por este serviço nesta visita. */
  private empilhado = false;

  private readonly current = signal<CesDrillTarget | null>(
    parseCesDrill(this.route.snapshot.queryParamMap.get(CES_DRILL_QUERY_PARAM)),
  );
  readonly target = this.current.asReadonly();

  constructor() {
    const assinatura = this.route.queryParamMap.subscribe((params) => {
      const alvo = parseCesDrill(params.get(CES_DRILL_QUERY_PARAM));
      if (!alvo) this.empilhado = false;
      const atual = this.current();
      if (atual?.dimension !== alvo?.dimension || atual?.value !== alvo?.value) this.current.set(alvo);
    });
    inject(DestroyRef).onDestroy(() => assinatura.unsubscribe());
  }

  open(target: CesDrillTarget): void {
    const jaAberto = this.current() !== null;
    this.current.set(target);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { [CES_DRILL_QUERY_PARAM]: serializeCesDrill(target) },
      queryParamsHandling: 'merge',
      // Trocar de alvo com o painel aberto não deve exigir dois "Voltar".
      replaceUrl: jaAberto,
    });
    if (!jaAberto) this.empilhado = true;
  }

  close(): void {
    if (!this.current()) return;
    this.current.set(null);
    if (this.empilhado) {
      this.empilhado = false;
      this.location.back();
      return;
    }
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { [CES_DRILL_QUERY_PARAM]: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }
}
