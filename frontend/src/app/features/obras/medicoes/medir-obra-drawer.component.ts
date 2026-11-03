import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import {
  CesAsyncStateComponent,
  CesButtonComponent,
  CesCalloutComponent,
  CesCellDirective,
  CesConfirmationService,
  CesDataTableColumn,
  CesDataTableComponent,
  CesFormDrawerComponent,
  CesFormSectionComponent,
  CesSelectComponent,
  CesSelectOption,
  CesTextareaComponent,
  NotificationService
} from '@ces/ui';
import { Subscription } from 'rxjs';
import {
  CicloMedicao,
  ItemPendenteMedicao,
  MedicaoDaObra,
  MedicaoDetalhe,
  TipoMedicao
} from '../../../core/models/obras-medicoes.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { ObrasMedicoesService } from '../../../core/services/obras-medicoes.service';
import { formatarMoeda } from '../obras-ui';
import { cabeNoCiclo, competenciaDe, competenciasRecentes, percentualMedido, rotuloCompetencia, somarItens } from './medicoes.logic';

const TIPOS: CesSelectOption<TipoMedicao>[] = [
  { label: 'Parcial: o que está concluído até hoje', value: 'PARCIAL' },
  { label: 'Por ciclo: o concluído até o fim da competência', value: 'CICLO' }
];

/**
 * Medir uma obra: mostra o saldo sobre o valor de projeto, os serviços concluídos ainda não medidos (todos
 * marcados) e, no ciclo, o saldo anterior e o que sobra depois. Gera só depois de confirmar o total.
 */
@Component({
  selector: 'app-medir-obra-drawer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    CesAsyncStateComponent,
    CesButtonComponent,
    CesCalloutComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesSelectComponent,
    CesTextareaComponent
  ],
  template: `
    <ces-form-drawer [(visible)]="aberto" [title]="obra() ? 'Medir a obra ' + obra()!.numero : 'Medir obra'" [description]="obra()?.titulo"
      [dirty]="!!observacao().trim()" size="lg">
      @if (erro(); as mensagem) {
        <ces-async-state state="error" title="Não foi possível abrir a medição da obra" [description]="mensagem"></ces-async-state>
      } @else if (!obra()) {
        <ces-async-state state="loading" title="Carregando os serviços a medir" [compact]="true"></ces-async-state>
      } @else {
        @let o = obra()!;
        <dl class="medir__saldo">
          <div><dt>Valor de projeto</dt><dd>{{ o.valorProjeto != null ? moeda(o.valorProjeto) : 'Não informado' }}</dd></div>
          <div><dt>Já medido</dt><dd>{{ moeda(o.valorMedido) }}@if (percentual() != null) { <small>{{ percentual() }}%</small> }</dd></div>
          <div><dt>Saldo</dt><dd>{{ o.saldo != null ? moeda(o.saldo) : '—' }}</dd></div>
          <div><dt>Pendente de medição</dt><dd>{{ moeda(o.valorPendente) }} <small>{{ o.pendentes.length }} {{ o.pendentes.length === 1 ? 'serviço' : 'serviços' }}</small></dd></div>
        </dl>
        <a class="medir__link" [routerLink]="['/obras', o.idObra]"><i class="pi pi-building" aria-hidden="true"></i> Ficha da obra</a>

        @if (!o.pendentes.length) {
          <ces-callout tone="success" [dense]="true" title="Nada pendente">
            Todos os serviços concluídos desta obra já foram medidos. As medições geradas estão na aba "Medições geradas".
          </ces-callout>
        } @else {
          <ces-form-section title="Medição" icon="pi pi-calculator">
            <div class="ces-form-grid">
              <ces-select label="Tipo" [options]="tipos" [(value)]="tipo" (valueChange)="aoTrocarTipo()"></ces-select>
              @if (tipo() === 'CICLO') {
                <ces-select label="Competência" [options]="competencias" [(value)]="competencia" (valueChange)="carregarCiclo()"></ces-select>
              }
            </div>
            @if (tipo() === 'CICLO') {
              @if (ciclo(); as c) {
                <dl class="medir__saldo medir__saldo--ciclo" aria-live="polite">
                  <div><dt>Medido antes de {{ rotuloCompetencia(c.competencia) }}</dt><dd>{{ moeda(c.medidoAnterior) }}</dd></div>
                  <div><dt>Saldo anterior</dt><dd>{{ c.saldoAnterior != null ? moeda(c.saldoAnterior) : '—' }}</dd></div>
                  <div><dt>Já medido no ciclo</dt><dd>{{ moeda(c.medidoNoCiclo) }}</dd></div>
                  <div><dt>Saldo após esta medição</dt><dd>{{ c.saldoAposCiclo != null ? moeda(c.saldoAposCiclo - totalSelecionado()) : '—' }}</dd></div>
                </dl>
              } @else {
                <ces-async-state state="loading" title="Calculando o ciclo" [compact]="true"></ces-async-state>
              }
            }
            @if (semValor()) {
              <ces-callout tone="warning" [dense]="true">
                {{ semValor() === 1 ? '1 serviço não tinha valor ao ser programado e entra' : semValor() + ' serviços não tinham valor ao serem programados e entram' }} com valor zero. Confira o valor da ação no contrato antes de medir.
              </ces-callout>
            }
          </ces-form-section>

          <ces-form-section title="Serviços" icon="pi pi-list-check"
            [description]="tipo() === 'CICLO' ? 'Concluídos até ' + fimDoCiclo() + ' e ainda não medidos.' : 'Concluídos e ainda não medidos. Desmarque o que não vai nesta medição.'">
            @if (disponiveis().length) {
              <ces-data-table caption="Serviços a medir" [columns]="colunas" [rows]="disponiveis()" [rowKey]="chave" [rowLabel]="rotulo"
                [selectable]="true" [(selection)]="selecionados" [pageSizeOptions]="[25, 50, 100]" [pageSize]="25" exportFileName="servicos-a-medir">
                <ng-template cesCell="servico" let-row>
                  <span class="ces-cell-strong">{{ row.descricao }}</span>
                  <span class="ces-cell-muted">{{ row.codigo }} · {{ row.codigoKit }}</span>
                </ng-template>
              </ces-data-table>
            } @else {
              <ces-callout tone="info" [dense]="true">Nenhum serviço foi concluído até o fim de {{ rotuloCompetencia(competencia()) }}.</ces-callout>
            }
          </ces-form-section>

          <ces-form-section title="Observação" icon="pi pi-comment">
            <ces-textarea label="Observação" [showOptional]="true" [rows]="2" [maxlength]="1000" [(value)]="observacao"
              placeholder="Ex.: medição referente à primeira etapa"></ces-textarea>
          </ces-form-section>
        }
      }
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Fechar" (pressed)="aberto.set(false)"></ces-button>
        @if (obra()?.pendentes?.length) {
          <ces-button icon="pi pi-check" [label]="'Gerar medição · ' + moeda(totalSelecionado())" [loading]="gerando()"
            [disabled]="!selecionados().length" (pressed)="gerar()"></ces-button>
        }
      </div>
    </ces-form-drawer>
  `,
  styles: `
    .medir__saldo { display: grid; grid-template-columns: repeat(auto-fit, minmax(7.5rem, 1fr)); gap: 0.75rem; margin: 0 0 0.5rem; }
    .medir__saldo div { padding: 0.6rem 0.75rem; border: 1px solid var(--ces-border); border-radius: var(--ces-radius-md); background: var(--ces-surface-muted); }
    .medir__saldo dt { color: var(--ces-foreground-muted); font-size: var(--ces-font-caption); }
    .medir__saldo dd { margin: 0.15rem 0 0; font-weight: 700; font-variant-numeric: tabular-nums; }
    .medir__saldo small { margin-left: 0.35rem; font-weight: 400; color: var(--ces-foreground-muted); }
    .medir__saldo--ciclo { margin-top: 0.75rem; }
    .medir__link { display: inline-flex; align-items: center; gap: 0.35rem; margin-bottom: 0.75rem; color: var(--ces-brand); font-size: var(--ces-font-body); }
  `
})
export class MedirObraDrawerComponent {
  private readonly api = inject(ObrasMedicoesService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly aberto = model(false);
  readonly idObra = input<string | null>(null);
  readonly gerada = output<MedicaoDetalhe>();

  protected readonly tipos = TIPOS;
  protected readonly competencias = competenciasRecentes(new Date());
  protected readonly moeda = formatarMoeda;
  protected readonly rotuloCompetencia = rotuloCompetencia;

  protected readonly obra = signal<MedicaoDaObra | null>(null);
  protected readonly ciclo = signal<CicloMedicao | null>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly tipo = signal<TipoMedicao>('PARCIAL');
  protected readonly competencia = signal(competenciaDe(new Date()));
  protected readonly selecionados = signal<readonly ItemPendenteMedicao[]>([]);
  protected readonly observacao = signal('');
  protected readonly gerando = signal(false);
  private cargaCiclo?: Subscription;

  protected readonly disponiveis = computed(() => {
    const pendentes = this.obra()?.pendentes ?? [];
    return this.tipo() === 'CICLO' ? pendentes.filter((p) => cabeNoCiclo(p, this.competencia())) : pendentes;
  });
  protected readonly totalSelecionado = computed(() => somarItens(this.selecionados()));
  protected readonly semValor = computed(() => this.selecionados().filter((i) => i.valorTotal == null).length);
  protected readonly percentual = computed(() => {
    const o = this.obra();
    return o ? percentualMedido(o.valorMedido, o.valorProjeto) : null;
  });
  protected readonly fimDoCiclo = computed(() => {
    const [ano, mes] = this.competencia().split('-').map(Number);
    return new Date(ano, mes, 0).toLocaleDateString('pt-BR');
  });

  protected readonly colunas: CesDataTableColumn<ItemPendenteMedicao>[] = [
    { key: 'servico', header: 'Serviço', type: 'custom', priority: 1, value: (i) => i.descricao, display: (i) => `${i.descricao ?? ''} (${i.codigo ?? ''})` },
    { key: 'local', header: 'Local', value: (i) => i.local },
    { key: 'quantidade', header: 'Qtd.', type: 'number', value: (i) => i.quantidade, display: (i) => `${i.quantidade.toLocaleString('pt-BR')} ${i.unidade ?? ''}`.trim() },
    { key: 'concluidoEm', header: 'Concluído em', value: (i) => i.concluidoEm },
    { key: 'valorTotal', header: 'Valor', type: 'currency', priority: 1, value: (i) => i.valorTotal }
  ];
  protected readonly chave = (i: ItemPendenteMedicao) => i.idItem;
  protected readonly rotulo = (i: ItemPendenteMedicao) => i.descricao ?? 'serviço';

  constructor() {
    effect(() => {
      const id = this.idObra();
      if (this.aberto() && id) untracked(() => this.carregar(id));
    });
  }

  private carregar(id: string): void {
    this.obra.set(null);
    this.ciclo.set(null);
    this.erro.set(null);
    this.tipo.set('PARCIAL');
    this.competencia.set(competenciaDe(new Date()));
    this.observacao.set('');
    this.api.daObra(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (o) => {
        this.obra.set(o);
        this.selecionados.set(o.pendentes);
      },
      error: (e) => this.erro.set(getErrorMessage(e))
    });
  }

  protected aoTrocarTipo(): void {
    if (this.tipo() === 'CICLO') {
      this.carregarCiclo();
    } else {
      this.selecionados.set(this.disponiveis());
    }
  }

  protected carregarCiclo(): void {
    const id = this.idObra();
    if (!id) return;
    this.selecionados.set(this.disponiveis());
    this.ciclo.set(null);
    this.cargaCiclo?.unsubscribe();
    this.cargaCiclo = this.api.ciclo(id, this.competencia()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (c) => this.ciclo.set(c),
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected async gerar(): Promise<void> {
    const o = this.obra();
    const itens = this.selecionados();
    if (!o || !itens.length) return;
    const quantos = itens.length === 1 ? '1 serviço' : `${itens.length} serviços`;
    const ciclo = this.tipo() === 'CICLO' ? ` do ciclo de ${rotuloCompetencia(this.competencia())}` : ' parcial';
    const ok = await this.confirmacao.confirm({
      title: `Gerar a medição${ciclo}?`,
      message: `${quantos} da obra ${o.numero}, no total de ${formatarMoeda(this.totalSelecionado())}. Os serviços medidos não entram em outra medição, a não ser que esta seja estornada.`,
      confirmLabel: 'Gerar medição'
    });
    if (!ok) return;
    this.gerando.set(true);
    const todos = itens.length === this.disponiveis().length;
    this.api
      .gerar(o.idObra, {
        tipo: this.tipo(),
        competencia: this.tipo() === 'CICLO' ? this.competencia() : undefined,
        idsItens: todos && this.tipo() === 'PARCIAL' ? undefined : itens.map((i) => i.idItem),
        observacao: this.observacao().trim() || undefined
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (m) => {
          this.gerando.set(false);
          this.notificacoes.success(`Medição #${m.medicao.numero} gerada: ${formatarMoeda(m.medicao.valorTotal)}.`);
          this.aberto.set(false);
          this.gerada.emit(m);
        },
        error: (e) => {
          this.gerando.set(false);
          this.notificacoes.error(getErrorMessage(e));
        }
      });
  }
}
