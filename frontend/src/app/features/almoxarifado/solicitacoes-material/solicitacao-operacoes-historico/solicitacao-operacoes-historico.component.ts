import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input, computed, inject, signal } from '@angular/core';
import { OperacaoSolicitacaoComercialResponse } from '../../../../core/models/almoxarifado.models';
import { UsuarioLookupService } from '../../../../core/services/usuario-lookup.service';
import { SerieGradeItem, SeriesGradeComponent } from '../../../../shared/components/series-grade/series-grade.component';

/** Material de uma operação: o backend manda um item por série; aqui viram uma linha com as séries juntas. */
export interface MaterialOperacaoAgrupado {
  readonly chave: string;
  readonly codigo: string;
  readonly descricao: string;
  readonly quantidade: number;
  readonly series: readonly string[];
  readonly grade: readonly SerieGradeItem[];
}

export function agruparItensDaOperacao(itens: OperacaoSolicitacaoComercialResponse['itens'] | null | undefined): MaterialOperacaoAgrupado[] {
  const grupos = new Map<string, { codigo: string; descricao: string; quantidade: number; series: string[] }>();
  for (const item of itens ?? []) {
    const chave = item.idMaterial || item.codigoMaterial || item.id;
    const grupo = grupos.get(chave) ?? { codigo: item.codigoMaterial, descricao: item.descricaoMaterial, quantidade: 0, series: [] };
    grupo.quantidade += Number(item.quantidade) || 0;
    if (item.numeroSerie) grupo.series.push(item.numeroSerie);
    grupos.set(chave, grupo);
  }
  return Array.from(grupos, ([chave, g]) => {
    const series = [...g.series].sort((a, b) => a.localeCompare(b, 'pt-BR', { numeric: true }));
    return { chave, codigo: g.codigo, descricao: g.descricao, quantidade: g.quantidade, series, grade: series.map((numSerie) => ({ numSerie })) };
  });
}

@Component({
  selector: 'app-solicitacao-operacoes-historico',
  standalone: true,
  imports: [CommonModule, SeriesGradeComponent],
  templateUrl: './solicitacao-operacoes-historico.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SolicitacaoOperacoesHistoricoComponent {
  private readonly userLookup = inject(UsuarioLookupService);
  private readonly _operacoes = signal<OperacaoSolicitacaoComercialResponse[]>([]);

  @Input()
  set operacoes(value: OperacaoSolicitacaoComercialResponse[] | null | undefined) {
    this._operacoes.set(value ?? []);
  }

  @Input() equipeNome = 'Não informada';

  /** No relatório o histórico é um painel recolhível; no detalhe rápido ele já está numa aba e abre direto. */
  @Input()
  set recolhivel(value: boolean) {
    this._recolhivel.set(value);
    // Sem o painel, a movimentação mais recente já vem aberta.
    if (!value && !this.expandedOperations().length && this._operacoes().length) {
      this.expandedOperations.set([this.ultimaOperacao()]);
    }
  }

  protected readonly _recolhivel = signal(true);
  protected readonly operationsOpen = signal(false);
  protected readonly painelAberto = computed(() => !this._recolhivel() || this.operationsOpen());
  protected readonly expandedOperations = signal<string[]>([]);
  protected readonly operations = this._operacoes.asReadonly();
  protected readonly materiaisPorOperacao = computed(
    () => new Map(this._operacoes().map((operacao) => [operacao.id, agruparItensDaOperacao(operacao.itens)]))
  );
  /** Materiais com a grade de séries aberta, por operação (`idOperacao|chave`). */
  private readonly seriesAbertas = signal<ReadonlySet<string>>(new Set());
  protected readonly operationsSummary = computed(() => {
    const operacoes = this.operations();
    if (!operacoes.length) return 'Nenhuma movimentação realizada ainda';
    const entregas = operacoes.filter((operacao) => operacao.tipo === 'ENTREGA').length;
    const estornos = operacoes.filter((operacao) => operacao.tipo === 'ESTORNO').length;
    const partes: string[] = [];
    if (entregas) partes.push(`${entregas} ${entregas > 1 ? 'entregas realizadas' : 'entrega realizada'}`);
    if (estornos) partes.push(`${estornos} ${estornos > 1 ? 'correções / estornos' : 'correção / estorno'}`);
    return partes.join(' · ');
  });

  private ultimaOperacao(): string {
    const ops = this._operacoes();
    return [...ops].sort((a, b) => (b.realizadaEm ?? '').localeCompare(a.realizadaEm ?? ''))[0]?.id ?? ops[0]?.id;
  }

  protected toggleOperations(): void {
    this.operationsOpen.update((aberta) => !aberta);
  }

  protected toggleOperation(id: string): void {
    this.expandedOperations.update((ids) =>
      ids.includes(id) ? ids.filter((atual) => atual !== id) : [...ids, id],
    );
  }

  protected isOperationExpanded(id: string): boolean {
    return this.expandedOperations().includes(id);
  }

  protected expandAllOperations(): void {
    this.expandedOperations.set(this.operations().map((operacao) => operacao.id));
  }

  protected collapseAllOperations(): void {
    this.expandedOperations.set([]);
  }

  protected toggleSeries(idOperacao: string, chave: string): void {
    this.seriesAbertas.update((atual) => {
      const proximo = new Set(atual);
      const id = `${idOperacao}|${chave}`;
      if (!proximo.delete(id)) proximo.add(id);
      return proximo;
    });
  }

  protected isSeriesExpanded(idOperacao: string, chave: string): boolean {
    return this.seriesAbertas().has(`${idOperacao}|${chave}`);
  }

  protected operationFriendlyTitle(tipo: string): string {
    return tipo === 'ENTREGA' ? 'Entrega de Materiais' : 'Correção / Estorno de Movimentação';
  }

  protected operationIcon(tipo: string): string {
    return tipo === 'ENTREGA' ? 'pi pi-box' : 'pi pi-undo';
  }

  protected formatUser(id?: string | null): string {
    return this.userLookup.resolverNome(id, 'Sistema');
  }
}
