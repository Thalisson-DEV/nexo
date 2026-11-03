import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import {
  CesAsyncStateComponent,
  CesButtonComponent,
  CesCalloutComponent,
  CesCellDirective,
  CesDataTableColumn,
  CesDataTableComponent,
  CesFormDrawerComponent,
  CesFormSectionComponent,
  CesStatusComponent,
  CesTextareaComponent,
  NotificationService
} from '@ces/ui';
import { ItemMedido, MedicaoDetalhe } from '../../../core/models/obras-medicoes.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AuthService } from '../../../core/services/auth.service';
import { ObrasMedicoesService } from '../../../core/services/obras-medicoes.service';
import { formatarMoeda } from '../obras-ui';
import { STATUS_MEDICAO, TIPO_MEDICAO, rotuloCompetencia } from './medicoes.logic';

/** Uma medição: serviços medidos com valores, quem gerou e, enquanto gerada, o estorno com justificativa. */
@Component({
  selector: 'app-medicao-detalhe-drawer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    RouterLink,
    CesAsyncStateComponent,
    CesButtonComponent,
    CesCalloutComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesStatusComponent,
    CesTextareaComponent
  ],
  template: `
    <ces-form-drawer [(visible)]="aberto" [title]="m() ? 'Medição #' + m()!.medicao.numero : 'Medição'"
      [description]="m() ? m()!.medicao.numeroObra + ' — ' + m()!.medicao.tituloObra : undefined" [dirty]="estornando() && !!justificativa().trim()" size="lg">
      @if (erro(); as mensagem) {
        <ces-async-state state="error" title="Não foi possível abrir a medição" [description]="mensagem"></ces-async-state>
      } @else if (!m()) {
        <ces-async-state state="loading" title="Abrindo a medição" [compact]="true"></ces-async-state>
      } @else {
        @let medicao = m()!.medicao;
        <div class="medicao__topo">
          <ces-status [tone]="status[medicao.status].tom" [icon]="status[medicao.status].icone">{{ status[medicao.status].rotulo }}</ces-status>
          <a class="medicao__link" [routerLink]="['/obras', medicao.idObra]"><i class="pi pi-building" aria-hidden="true"></i> Ficha da obra</a>
        </div>
        <dl class="medicao__dados">
          <div><dt>Tipo</dt><dd>{{ tipo[medicao.tipo] }}</dd></div>
          <div><dt>Competência</dt><dd>{{ competencia(medicao.competencia) }}</dd></div>
          <div><dt>Valor</dt><dd>{{ moeda(medicao.valorTotal) }}</dd></div>
          <div><dt>Serviços</dt><dd>{{ medicao.servicos }}</dd></div>
          <div><dt>Gerada</dt><dd>{{ medicao.dataCriacao | date: 'dd/MM/yyyy HH:mm' }} · {{ medicao.usuarioCriacao }}</dd></div>
          @if (medicao.observacao) { <div class="medicao__larga"><dt>Observação</dt><dd>{{ medicao.observacao }}</dd></div> }
        </dl>
        @if (medicao.status === 'ESTORNADA') {
          <ces-callout tone="neutral" icon="pi pi-undo" [dense]="true"
            [title]="'Estornada em ' + (medicao.dataEstorno | date: 'dd/MM/yyyy HH:mm') + ' por ' + medicao.usuarioEstorno">
            {{ medicao.justificativaEstorno }}. Os serviços voltaram a concluídos e podem entrar em outra medição.
          </ces-callout>
        }

        <ces-form-section title="Serviços medidos" icon="pi pi-list-check">
          <ces-data-table caption="Serviços medidos" [columns]="colunas" [rows]="m()!.itens" [rowKey]="chave" [rowLabel]="rotulo"
            [pageSizeOptions]="[25, 50, 100]" [pageSize]="25" [exportFileName]="'medicao-' + medicao.numero">
            <ng-template cesCell="servico" let-row>
              <span class="ces-cell-strong">{{ row.descricao }}</span>
              <span class="ces-cell-muted">{{ row.codigo }} · {{ row.codigoKit }}</span>
            </ng-template>
          </ces-data-table>
        </ces-form-section>

        @if (estornando()) {
          <ces-form-section title="Estornar a medição" icon="pi pi-undo" description="Os serviços voltam a concluídos e a medição fica no histórico como estornada.">
            <ces-textarea label="Justificativa" [required]="true" [rows]="3" [maxlength]="1000" [(value)]="justificativa"
              placeholder="Ex.: valor contestado pela fiscalização" [error]="tentou() && !justificativa().trim() ? 'Informe a justificativa do estorno.' : undefined"></ces-textarea>
          </ces-form-section>
        }
      }
      <div drawer-footer class="ces-actions-row">
        @if (estornando()) {
          <ces-button variant="ghost" label="Voltar" (pressed)="estornando.set(false)"></ces-button>
          <ces-button variant="destructive" icon="pi pi-undo" label="Estornar medição" [loading]="salvando()" (pressed)="estornar()"></ces-button>
        } @else {
          <ces-button variant="ghost" label="Fechar" (pressed)="aberto.set(false)"></ces-button>
          @if (podeEstornar()) {
            <ces-button variant="outline" icon="pi pi-undo" label="Estornar" (pressed)="iniciarEstorno()"></ces-button>
          }
        }
      </div>
    </ces-form-drawer>
  `,
  styles: `
    .medicao__topo { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 0.5rem; margin-bottom: 0.75rem; }
    .medicao__link { display: inline-flex; align-items: center; gap: 0.35rem; color: var(--ces-brand); font-size: var(--ces-font-body); }
    .medicao__dados { display: grid; grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr)); gap: 0.75rem 1rem; margin: 0 0 1rem; }
    .medicao__dados dt { color: var(--ces-foreground-muted); font-size: var(--ces-font-caption); }
    .medicao__dados dd { margin: 0.15rem 0 0; font-weight: 600; font-variant-numeric: tabular-nums; }
    .medicao__larga { grid-column: 1 / -1; }
  `
})
export class MedicaoDetalheDrawerComponent {
  private readonly api = inject(ObrasMedicoesService);
  private readonly notificacoes = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly podeGravar = (inject(AuthService).currentUser()?.permissoes ?? []).includes('obras:medicao:write');

  readonly aberto = model(false);
  readonly idMedicao = input<string | null>(null);
  readonly alterada = output<MedicaoDetalhe>();

  protected readonly status = STATUS_MEDICAO;
  protected readonly tipo = TIPO_MEDICAO;
  protected readonly competencia = rotuloCompetencia;
  protected readonly moeda = formatarMoeda;

  protected readonly m = signal<MedicaoDetalhe | null>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly estornando = signal(false);
  protected readonly justificativa = signal('');
  protected readonly tentou = signal(false);
  protected readonly salvando = signal(false);
  protected readonly podeEstornar = computed(() => this.podeGravar && this.m()?.medicao.status === 'GERADA');

  protected readonly colunas: CesDataTableColumn<ItemMedido>[] = [
    { key: 'servico', header: 'Serviço', type: 'custom', priority: 1, value: (i) => i.descricao, display: (i) => `${i.descricao ?? ''} (${i.codigo ?? ''})` },
    { key: 'local', header: 'Local', value: (i) => i.local },
    { key: 'quantidade', header: 'Qtd.', type: 'number', value: (i) => i.quantidade, display: (i) => `${i.quantidade.toLocaleString('pt-BR')} ${i.unidade ?? ''}`.trim() },
    { key: 'valorUnitario', header: 'Unitário', type: 'currency', value: (i) => i.valorUnitario },
    { key: 'valorTotal', header: 'Valor', type: 'currency', priority: 1, value: (i) => i.valorTotal },
    { key: 'concluidoEm', header: 'Concluído em', value: (i) => i.concluidoEm }
  ];
  protected readonly chave = (i: ItemMedido) => i.id;
  protected readonly rotulo = (i: ItemMedido) => i.descricao ?? 'serviço';

  constructor() {
    effect(() => {
      const id = this.idMedicao();
      if (this.aberto() && id) untracked(() => this.carregar(id));
    });
  }

  private carregar(id: string): void {
    this.m.set(null);
    this.erro.set(null);
    this.estornando.set(false);
    this.api.detalhar(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (m) => this.m.set(m),
      error: (e) => this.erro.set(getErrorMessage(e))
    });
  }

  protected iniciarEstorno(): void {
    this.justificativa.set('');
    this.tentou.set(false);
    this.estornando.set(true);
  }

  protected estornar(): void {
    const m = this.m();
    this.tentou.set(true);
    if (!m || !this.justificativa().trim()) return;
    this.salvando.set(true);
    this.api.estornar(m.medicao.id, this.justificativa().trim()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (atualizada) => {
        this.salvando.set(false);
        this.estornando.set(false);
        this.m.set(atualizada);
        this.notificacoes.success(`Medição #${atualizada.medicao.numero} estornada. Os serviços voltaram a concluídos.`);
        this.alterada.emit(atualizada);
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }
}
