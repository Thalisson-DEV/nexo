import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';
import { CesSelectOption } from '@ces/ui';
import { RhService } from '../../../core/services/rh.service';

interface ItemRecorte {
  id: string;
  nome: string;
}

/**
 * Opções de base operacional, processo e equipe dos dashboards e consultas de execução.
 * As listas são pequenas e o `RhService` já as mantém em cache, então as telas
 * compartilham uma carga só.
 */
@Injectable({ providedIn: 'root' })
export class RecorteDashboardService {
  private readonly rh = inject(RhService);

  private readonly bases = signal<ItemRecorte[]>([]);
  private readonly processos = signal<ItemRecorte[]>([]);
  private readonly equipes = signal<ItemRecorte[]>([]);
  readonly carregando = signal(false);
  readonly carregandoEquipes = signal(false);

  readonly opcoesBases = computed<CesSelectOption<string>[]>(() =>
    this.bases().map((b) => ({ label: b.nome, value: b.id }))
  );
  readonly opcoesProcessos = computed<CesSelectOption<string>[]>(() =>
    this.processos().map((p) => ({ label: p.nome, value: p.id }))
  );

  /** Equipes ativas pelo código, para os filtros das consultas. */
  readonly opcoesEquipes = computed<CesSelectOption<string>[]>(() =>
    this.equipes().map((e) => ({ label: e.nome, value: e.id }))
  );

  carregarEquipes(destroyRef: DestroyRef): void {
    if (this.equipes().length || this.carregandoEquipes()) return;
    this.carregandoEquipes.set(true);
    this.rh
      .listarEquipes({ ativo: true, page: 0, size: 1000, sort: 'codigo,asc' })
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe({
        next: (res) => {
          this.equipes.set(ordenar(res.content.map((e) => ({ id: e.id, nome: e.codigo }))));
          this.carregandoEquipes.set(false);
        },
        // Sem as opções a consulta continua funcionando pela busca rápida.
        error: () => this.carregandoEquipes.set(false)
      });
  }

  carregar(destroyRef: DestroyRef): void {
    if (this.bases().length || this.carregando()) return;
    this.carregando.set(true);
    forkJoin({ bases: this.rh.listarBasesOperacionaisAtivas(), processos: this.rh.listarProcessosAtivos() })
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe({
        next: ({ bases, processos }) => {
          this.bases.set(ordenar(bases.content));
          this.processos.set(ordenar(processos.content));
          this.carregando.set(false);
        },
        // Sem as opções o dashboard continua funcionando, só sem filtrar por base/processo.
        error: () => this.carregando.set(false)
      });
  }

  nomeDaBase(id: string | null | undefined): string | null {
    return id ? (this.bases().find((b) => b.id === id)?.nome ?? id) : null;
  }

  nomeDoProcesso(id: string | null | undefined): string | null {
    return id ? (this.processos().find((p) => p.id === id)?.nome ?? id) : null;
  }

  /** Bases escolhidas para títulos e contexto: até duas por extenso, depois a contagem; `null` = todas. */
  resumoDasBases(ids: readonly string[] | undefined): string | null {
    return resumir(ids, (id) => this.nomeDaBase(id), 'bases');
  }

  resumoDosProcessos(ids: readonly string[] | undefined): string | null {
    return resumir(ids, (id) => this.nomeDoProcesso(id), 'processos');
  }
}

function resumir(ids: readonly string[] | undefined, nome: (id: string) => string | null, plural: string): string | null {
  if (!ids?.length) return null;
  if (ids.length > 2) return `${ids.length} ${plural}`;
  return ids.map((id) => nome(id) ?? id).join(' e ');
}

function ordenar(itens: readonly ItemRecorte[]): ItemRecorte[] {
  return [...itens]
    .map(({ id, nome }) => ({ id, nome }))
    .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
}
