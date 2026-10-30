import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import {
  CesButtonComponent,
  CesFilterPanelComponent,
  CesListShortcutsDirective,
  CesMultiSelectComponent,
  CesPageHeaderComponent,
  CesSelectComponent,
  CesSelectOption,
  cesFilter,
  cesListState
} from '@ces/ui';
import { catchError, forkJoin, map, of, shareReplay, switchMap } from 'rxjs';
import {
  DocumentoFrota,
  FiltroDocumentosFrota,
  OpcoesFrota,
  ROTULO_VENCIMENTO,
  SituacaoVencimento,
  TipoDocumentoFrota,
  contar,
  formatarPlaca
} from '../../../core/models/frota.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { FrotaService, PAGINA_OPCOES } from '../../../core/services/frota.service';
import { acessoFrota } from '../shared/frota-acesso';
import { LIMITE_EXPORTACAO_FROTA, basesFrota, exportacaoFrota, paginaFrota } from '../shared/frota-lista';
import { FrotaDocumentoFormComponent, TitularDocumento } from './frota-documento-form.component';
import { FrotaDocumentosTabelaComponent } from './frota-documentos-tabela.component';

/** "Vencidos e a vencer": a lista de trabalho de quem renova documentos (padrão da tela). */
export type SituacaoDocumentoFiltro = SituacaoVencimento | 'PENDENTE';

const SITUACOES: CesSelectOption<SituacaoDocumentoFiltro>[] = [
  { label: 'Vencidos e a vencer', value: 'PENDENTE' },
  ...(['VENCIDO', 'A_VENCER', 'VIGENTE', 'SEM_VENCIMENTO'] as SituacaoVencimento[]).map((s) => ({ label: ROTULO_VENCIMENTO[s], value: s }))
];

/** Data local `yyyy-mm-dd` daqui a `dias` dias. */
function daquiA(dias: number): string {
  const data = new Date();
  data.setDate(data.getDate() + dias);
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`;
}

/** Visão de vencimentos: abre filtrada no que já venceu ou vence na janela de aviso. */
@Component({
  selector: 'app-frota-documentos',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesButtonComponent,
    CesFilterPanelComponent,
    CesListShortcutsDirective,
    CesMultiSelectComponent,
    CesPageHeaderComponent,
    CesSelectComponent,
    FrotaDocumentoFormComponent,
    FrotaDocumentosTabelaComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="acesso.editarDocumento()" (newRequested)="novo()">
      <ces-page-header
        title="Documentos e vencimentos"
        icon="pi pi-file"
        [breadcrumb]="[{ label: 'Frota' }, { label: 'Documentos' }]"
        description="Documentos de veículos e implementos. Abre no que está vencido ou perto de vencer; quem cuida da documentação recebe aviso nas faixas configuradas. Atalhos: / busca, F filtros, N novo documento."
      >
        @if (acesso.editarDocumento()) {
          <ces-button actions icon="pi pi-plus" label="Novo documento" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
        }
      </ces-page-header>

      <ces-filter-panel
        #painel
        storageKey="frota-documentos"
        [activeCount]="lista.activeCount()"
        [summary]="lista.summary()"
        [loading]="resultado.loading()"
        (applied)="lista.apply()"
        (cleared)="lista.reset()"
        (chipRemoved)="lista.remove($any($event))"
      >
        <div class="ces-filter-grid">
          <ces-multiselect label="Base" [options]="bases.opcoes()" [(value)]="lista.filters.idsBase" placeholder="Todas"></ces-multiselect>
          <ces-multiselect label="Tipo" [options]="tipos()" [(value)]="lista.filters.tipos" placeholder="Todos"></ces-multiselect>
          <ces-select label="Situação" [options]="situacoes" [(value)]="lista.filters.situacao" [clearable]="true" placeholder="Todas"></ces-select>
        </div>
      </ces-filter-panel>

      <app-frota-documentos-tabela
        titulo="Documentos"
        modo="server"
        [descricao]="descricaoTabela()"
        [erro]="resultado.error()"
        [tituloVazio]="lista.applied().situacao === 'PENDENTE' ? 'Nada vencido nem a vencer' : undefined"
        [vazio]="lista.applied().situacao === 'PENDENTE' ? 'Nenhum documento vence na janela de aviso. Para ver todos, remova a situação nos filtros.' : 'Cadastre CRLV, seguro, licenciamento e os demais documentos com vencimento.'"
        storageKey="frota-documentos"
        [mostrarTitular]="true"
        [exportar]="true"
        [exportarLinhas]="exportarLinhas"
        [limiteExportacao]="limiteExportacao"
        [documentos]="resultado.rows()"
        [total]="resultado.total()"
        [estado]="resultado.state()"
        [filtrado]="lista.filtered()"
        [podeEditar]="acesso.editarDocumento()"
        [(pagina)]="lista.page"
        [(tamanho)]="lista.pageSize"
        [(ordem)]="lista.sort"
        [(busca)]="lista.query"
        (carregar)="lista.load($event)"
        (ajustarFiltros)="painel.expandAndFocus()"
        (limparFiltros)="lista.reset()"
        (editar)="editar($event)"
        (alterado)="lista.reload()"
      >
        <div filtros-rapidos class="ces-quick-filters" role="group" aria-label="Filtros rápidos de situação">
          @for (rapido of filtrosRapidos; track rapido.value) {
            <button type="button" class="ces-quick-filter" [attr.aria-pressed]="lista.applied().situacao === rapido.value"
              (click)="alternarRapido(rapido.value)">
              <i class="pi" [class.pi-check]="lista.applied().situacao === rapido.value" [class.pi-filter]="lista.applied().situacao !== rapido.value" aria-hidden="true"></i>
              {{ rapido.label }}
            </button>
          }
        </div>
      </app-frota-documentos-tabela>
    </div>

    <app-frota-documento-form [(visivel)]="formularioAberto" [documento]="emEdicao()" [titulares]="titulares()"
      [opcoes]="opcoes()" (salvo)="lista.reload()"></app-frota-documento-form>
  `
})
export class FrotaDocumentosComponent {
  private readonly frota = inject(FrotaService);
  protected readonly acesso = acessoFrota();
  protected readonly bases = basesFrota();

  protected readonly opcoes = signal<OpcoesFrota | null>(null);
  protected readonly titulares = signal<TitularDocumento[]>([]);
  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<DocumentoFrota | null>(null);

  protected readonly situacoes = SITUACOES;
  protected readonly filtrosRapidos: CesSelectOption<SituacaoDocumentoFiltro>[] = [
    { label: 'Vencidos', value: 'VENCIDO' },
    { label: 'A vencer', value: 'A_VENCER' }
  ];
  protected readonly tipos = computed(() => (this.opcoes()?.tiposDocumento ?? []).map((t) => ({ label: t.rotulo, value: t.valor })));

  protected readonly lista = cesListState({
    storageKey: 'frota-documentos',
    filters: {
      idsBase: cesFilter.list<string>([], { label: 'Base', param: 'base', itemLabel: (id) => this.bases.nome(id), countLabel: (n) => `${n} selecionadas` }),
      tipos: cesFilter.list<TipoDocumentoFrota>([], {
        label: 'Tipo',
        param: 'tipo',
        itemLabel: (t) => this.tipos().find((o) => o.value === t)?.label ?? t
      }),
      situacao: cesFilter.value<SituacaoDocumentoFiltro | null>('PENDENTE', { label: 'Situação', options: () => SITUACOES })
    },
    sort: [{ key: 'dataVencimento', direction: 'asc' }]
  });

  /** Janela de aviso (primeira faixa configurada, como no backend). */
  private readonly diasAviso$ = this.frota.parametros().pipe(
    map((p) => p.faixasAlertaDias[0] ?? 30),
    catchError(() => of(30)),
    shareReplay(1)
  );

  protected readonly resultado = this.lista.results<DocumentoFrota>(
    (pedido) => this.diasAviso$.pipe(switchMap((dias) => this.frota.listarDocumentos(this.filtro(dias), paginaFrota(pedido)))),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO_FROTA;
  protected readonly exportarLinhas = exportacaoFrota(
    (pagina) => this.diasAviso$.pipe(switchMap((dias) => this.frota.listarDocumentos(this.filtro(dias), pagina))),
    () => this.lista.request()
  );

  protected readonly descricaoTabela = computed(() => {
    if (this.resultado.loading() && !this.resultado.total()) return undefined;
    const total = contar(this.resultado.total(), 'documento', 'documentos', 'Nenhum documento');
    return this.lista.applied().situacao === 'PENDENTE' ? `${total} vencidos ou a vencer.` : `${total}.`;
  });

  constructor() {
    this.frota.opcoes().subscribe({ next: (o) => this.opcoes.set(o), error: () => undefined });
    forkJoin({
      veiculos: this.frota.listarVeiculos({ ativo: true }, PAGINA_OPCOES),
      implementos: this.frota.listarImplementos({ ativo: true }, PAGINA_OPCOES)
    }).subscribe({
      next: (r) =>
        this.titulares.set([
          ...r.veiculos.content.map((v) => ({ tipo: 'VEICULO' as const, id: v.idVeiculo, rotulo: `Veículo ${formatarPlaca(v.placa)} — ${v.modelo ?? ''}` })),
          ...r.implementos.content.map((i) => ({ tipo: 'IMPLEMENTO' as const, id: i.id, rotulo: `Implemento ${i.identificacao} — ${i.tipoImplemento}` }))
        ]),
      error: () => undefined
    });
  }

  protected novo(): void {
    this.emEdicao.set(null);
    this.formularioAberto.set(true);
  }

  protected editar(documento: DocumentoFrota): void {
    this.emEdicao.set(documento);
    this.formularioAberto.set(true);
  }

  /** Filtro rápido: equivale ao campo Situação; acionar de novo volta ao padrão. */
  protected alternarRapido(valor: SituacaoDocumentoFiltro): void {
    if (this.lista.applied().situacao === valor) this.lista.remove('situacao');
    else this.lista.set('situacao', valor);
  }

  private filtro(diasAviso: number): FiltroDocumentosFrota {
    const { filters, query } = this.lista.request();
    const pendentes = filters.situacao === 'PENDENTE';
    return {
      idsBase: filters.idsBase,
      tipos: filters.tipos,
      situacao: pendentes ? null : (filters.situacao as SituacaoVencimento | null),
      // Vencidos + a vencer = vencimento até o fim da janela de aviso (mesma regra do backend).
      vencimentoAte: pendentes ? daquiA(diasAviso) : null,
      busca: query || null
    };
  }
}
