import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import {
  CesBooleanControlComponent,
  CesButtonComponent,
  CesCalloutComponent,
  CesCellDirective,
  CesConfirmationService,
  CesDataTableColumn,
  CesDataTableComponent,
  CesFilterPanelComponent,
  CesFormDrawerComponent,
  CesFormSectionComponent,
  CesIconButtonComponent,
  CesPageHeaderComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesStatusComponent,
  CesTextInputComponent,
  CesTextareaComponent,
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';
import {
  EstruturaChecklist,
  FinalidadeChecklist,
  GrupoChecklist,
  ItemEvidenciaModelo,
  ModeloChecklistResponse,
  PerguntaChecklist,
  TipoPerguntaChecklist
} from '../../../core/models/obras-documentacao.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AuthService } from '../../../core/services/auth.service';
import { ObrasDocumentacaoService } from '../../../core/services/obras-documentacao.service';
import { LIMITE_EXPORTACAO, OPCOES_STATUS, exportacaoPadrao, paginaPadrao } from '../../../shared/listagem/listagem';
import { FINALIDADES, TIPOS_PERGUNTA, novaPergunta, novoGrupo, novoItemEvidencia, opcoesDoTexto, problemaDaEstrutura, temOpcoes } from './checklist.logic';

function rotuloFinalidade(finalidade: FinalidadeChecklist): string {
  return FINALIDADES.find((f) => f.value === finalidade)?.label ?? finalidade;
}

/** Modelos da visita prévia e da ficha As Built, no mesmo desenho dos formulários de inspeção do SST: grupos, perguntas e tipos de resposta. A ficha também lista as fotos que o book pede em cada ponto. */
@Component({
  selector: 'app-modelos-checklist',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesBooleanControlComponent,
    CesButtonComponent,
    CesCalloutComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesFilterPanelComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesIconButtonComponent,
    CesPageHeaderComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    CesTextareaComponent
  ],
  template: `
    <div class="ces-page">
      <ces-page-header
        title="Checklists da documentação"
        icon="pi pi-list-check"
        [breadcrumb]="[{ label: 'Obras' }, { label: 'Cadastros' }, { label: 'Checklists da documentação' }]"
        description="Perguntas da visita prévia e da ficha As Built, e as fotos que o book pede em cada ponto."
      >
        @if (podeConfigurar) {
          <ces-button actions icon="pi pi-plus" label="Novo modelo" (pressed)="novo()"></ces-button>
        }
      </ces-page-header>

      <ces-filter-panel #painel storageKey="obras-checklists" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Finalidade" [options]="finalidades" [(value)]="lista.filters.finalidade" [clearable]="true" placeholder="Todas"></ces-select>
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Modelos"
        mode="server"
        storageKey="obras-checklists"
        exportFileName="checklists-documentacao"
        searchPlaceholder="Título ou descrição"
        [rowActionLabel]="podeConfigurar ? 'Editar' : 'Ver perguntas'"
        [rowActionIcon]="podeConfigurar ? 'pi pi-pencil' : 'pi pi-eye'"
        [emptyActionLabel]="podeConfigurar ? 'Novo modelo' : undefined"
        noResultsTitle="Nenhum modelo encontrado com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os modelos' : 'Nenhum modelo cadastrado'"
        [stateDescription]="resultado.error() ?? 'Sem modelo, o supervisor não consegue lançar a visita prévia nem as fichas As Built.'"
        [filtered]="lista.filtered()"
        [exportRows]="exportar"
        [exportLimit]="limiteExportacao"
        [(page)]="lista.page"
        [(pageSize)]="lista.pageSize"
        [(sort)]="lista.sort"
        [(query)]="lista.query"
        (lazyLoad)="lista.load($event)"
        (adjustFilters)="painel.expandAndFocus()"
        (clearFilters)="lista.reset()"
        (emptyAction)="novo()"
        (rowActivate)="abrir($event)"
      >
        <ng-template cesCell="titulo" let-row><span class="ces-cell-strong">{{ row.titulo }}</span></ng-template>
        <ng-template cesCell="uso" let-row>
          @if (row.emUso) {
            <ces-status tone="info" icon="pi-lock">Em uso</ces-status>
          } @else {
            <span class="ces-cell-muted">Ainda não usado</span>
          }
        </ng-template>
        <ng-template cesCell="ativo" let-row>
          <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativo' : 'Inativo' }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          @if (podeConfigurar) {
            <span class="ces-table-actions">
              <ces-icon-button icon="pi pi-copy" [label]="'Duplicar ' + row.titulo" size="icon-sm" (pressed)="duplicar(row)"></ces-icon-button>
              @if (row.ativo) {
                <ces-icon-button icon="pi pi-ban" [label]="'Desativar ' + row.titulo" size="icon-sm" (pressed)="desativar(row)"></ces-icon-button>
              } @else {
                <ces-icon-button icon="pi pi-check-circle" [label]="'Reativar ' + row.titulo" size="icon-sm" (pressed)="ativar(row)"></ces-icon-button>
              }
            </span>
          }
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="aberto" [title]="tituloDrawer()" [description]="emEdicao()?.descricao ?? undefined" [dirty]="alterado()" size="xl">
      @if (estruturaTravada() && podeConfigurar) {
        <ces-callout tone="info" icon="pi pi-lock" title="Modelo em uso" [dense]="true">
          Dá para mudar o título e a descrição. Para mudar as perguntas e as fotos pedidas, duplique o modelo: o que já foi lançado continua como foi respondido.
        </ces-callout>
      }
      <ces-form-section title="Modelo" icon="pi pi-file-edit">
        <div class="ces-form-grid">
          <ces-select label="Finalidade" [required]="true" [options]="finalidades" [value]="finalidade()" (valueChange)="finalidade.set($event ?? 'VISITA_PREVIA')"
            [disabled]="!!emEdicao()" [readonly]="somenteLeitura()"></ces-select>
          <ces-text-input label="Título" [required]="true" [(value)]="titulo" [maxlength]="150" [readonly]="somenteLeitura()" [error]="tentou() && !titulo().trim() ? 'Informe o título.' : undefined"></ces-text-input>
        </div>
        <div class="ces-form-stack">
          <ces-text-input label="Descrição" [(value)]="descricao" [maxlength]="500" [readonly]="somenteLeitura()"></ces-text-input>
        </div>
      </ces-form-section>

      @for (grupo of grupos(); track $index; let g = $index) {
        <section class="mc-grupo" [attr.aria-label]="'Grupo ' + (g + 1)">
          <div class="mc-grupo__topo">
            <ces-text-input class="mc-grupo__titulo" [label]="'Grupo ' + (g + 1)" [required]="true" placeholder="Ex.: Acesso à obra" [maxlength]="150" [value]="grupo.titulo"
              [readonly]="estruturaTravada()" (valueChange)="alterarGrupo(g, { titulo: $event })"></ces-text-input>
            @if (!estruturaTravada()) {
              <span class="mc-acoes">
                <ces-icon-button icon="pi pi-chevron-up" [label]="'Subir grupo ' + (g + 1)" size="icon-sm" [disabled]="g === 0" (pressed)="moverGrupo(g, -1)"></ces-icon-button>
                <ces-icon-button icon="pi pi-chevron-down" [label]="'Descer grupo ' + (g + 1)" size="icon-sm" [disabled]="g === grupos().length - 1" (pressed)="moverGrupo(g, 1)"></ces-icon-button>
                <ces-icon-button icon="pi pi-trash" [label]="'Remover grupo ' + (g + 1)" size="icon-sm" (pressed)="removerGrupo(g)"></ces-icon-button>
              </span>
            }
          </div>
          @for (p of grupo.perguntas; track p.id; let i = $index) {
            <div class="mc-pergunta">
              <div class="mc-pergunta__linha">
                <ces-text-input class="mc-pergunta__enunciado" [label]="'Pergunta ' + (i + 1)" [required]="true" [maxlength]="500" [value]="p.enunciado" [readonly]="estruturaTravada()"
                  (valueChange)="alterarPergunta(g, i, { enunciado: $event })"></ces-text-input>
                <ces-select class="mc-pergunta__tipo" label="Resposta" [options]="tipos" [value]="p.tipo" [readonly]="estruturaTravada()" (valueChange)="trocarTipo(g, i, $event)"></ces-select>
                @if (!estruturaTravada()) {
                  <span class="mc-acoes">
                    <ces-icon-button icon="pi pi-chevron-up" [label]="'Subir pergunta ' + (i + 1)" size="icon-sm" [disabled]="i === 0" (pressed)="moverPergunta(g, i, -1)"></ces-icon-button>
                    <ces-icon-button icon="pi pi-chevron-down" [label]="'Descer pergunta ' + (i + 1)" size="icon-sm" [disabled]="i === grupo.perguntas.length - 1" (pressed)="moverPergunta(g, i, 1)"></ces-icon-button>
                    <ces-icon-button icon="pi pi-trash" [label]="'Remover pergunta ' + (i + 1)" size="icon-sm" (pressed)="removerPergunta(g, i)"></ces-icon-button>
                  </span>
                }
              </div>
              @if (temOpcoes(p.tipo)) {
                <ces-textarea label="Opções" description="Uma por linha." [rows]="2" [value]="textoDasOpcoes(p)" [readonly]="estruturaTravada()"
                  (valueChange)="alterarOpcoes(g, i, p.id, $event)"></ces-textarea>
              }
              <div class="mc-pergunta__marcas">
                <ces-boolean-control label="Obrigatória" kind="toggle" [value]="p.obrigatoria" [readonly]="estruturaTravada()" (valueChange)="alterarPergunta(g, i, { obrigatoria: $event })"></ces-boolean-control>
                <ces-boolean-control label="Aceita &quot;Não se aplica&quot;" kind="toggle" [value]="p.aceitaNaoSeAplica" [readonly]="estruturaTravada()" (valueChange)="alterarPergunta(g, i, { aceitaNaoSeAplica: $event })"></ces-boolean-control>
              </div>
            </div>
          }
          @if (!estruturaTravada()) {
            <div class="ces-actions-row">
              <ces-button variant="ghost" size="sm" icon="pi pi-plus" label="Adicionar pergunta" (pressed)="adicionarPergunta(g)"></ces-button>
            </div>
          }
        </section>
      }
      @if (!estruturaTravada()) {
        <div class="ces-actions-row">
          <ces-button variant="outline" size="sm" icon="pi pi-plus" label="Adicionar grupo" (pressed)="adicionarGrupo()"></ces-button>
        </div>
      }
      @if (finalidade() === 'AS_BUILT') {
        <section class="mc-grupo" aria-labelledby="mc-evidencias">
          <div>
            <h3 id="mc-evidencias" class="mc-evidencias__titulo">Fotos do book</h3>
            <p class="mc-evidencias__ajuda">O que precisa ser fotografado em cada ponto. Foto obrigatória que faltar gera um aviso ao gerar o As Built, sem impedir.</p>
          </div>
          @for (item of evidencias(); track item.id; let i = $index) {
            <div class="mc-pergunta__linha">
              <ces-text-input class="mc-pergunta__enunciado" [label]="'Foto ' + (i + 1)" [required]="true" placeholder="Ex.: Aterramento" [maxlength]="150" [value]="item.titulo" [readonly]="estruturaTravada()"
                (valueChange)="alterarItem(i, { titulo: $event })"></ces-text-input>
              <ces-boolean-control class="mc-evidencias__marca" label="Obrigatória" kind="toggle" [value]="item.obrigatoria" [readonly]="estruturaTravada()" (valueChange)="alterarItem(i, { obrigatoria: $event })"></ces-boolean-control>
              @if (!estruturaTravada()) {
                <span class="mc-acoes">
                  <ces-icon-button icon="pi pi-chevron-up" [label]="'Subir foto ' + (i + 1)" size="icon-sm" [disabled]="i === 0" (pressed)="moverItem(i, -1)"></ces-icon-button>
                  <ces-icon-button icon="pi pi-chevron-down" [label]="'Descer foto ' + (i + 1)" size="icon-sm" [disabled]="i === evidencias().length - 1" (pressed)="moverItem(i, 1)"></ces-icon-button>
                  <ces-icon-button icon="pi pi-trash" [label]="'Remover foto ' + (i + 1)" size="icon-sm" (pressed)="removerItem(i)"></ces-icon-button>
                </span>
              }
            </div>
          } @empty {
            <p class="mc-evidencias__ajuda">Nenhuma foto pedida. Quem lança ainda pode anexar fotos avulsas ao ponto.</p>
          }
          @if (!estruturaTravada()) {
            <div class="ces-actions-row">
              <ces-button variant="ghost" size="sm" icon="pi pi-plus" label="Adicionar foto pedida" (pressed)="adicionarItem()"></ces-button>
            </div>
          }
        </section>
      }
      @if (tentou() && problema()) {
        <ces-callout tone="danger" icon="pi pi-exclamation-circle" title="Revise o modelo" [dense]="true">{{ problema() }}</ces-callout>
      }

      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" [label]="somenteLeitura() ? 'Fechar' : 'Cancelar'" (pressed)="aberto.set(false)"></ces-button>
        @if (!somenteLeitura()) {
          <ces-button icon="pi pi-check" label="Salvar modelo" [loading]="salvando()" (pressed)="salvar()"></ces-button>
        }
      </div>
    </ces-form-drawer>
  `,
  styles: `
    .mc-grupo { display: flex; flex-direction: column; gap: 0.75rem; margin-top: 1rem; padding: 1rem; border: 1px solid var(--ces-border); border-radius: var(--ces-radius-lg); background: var(--ces-surface-subtle); }
    .mc-grupo__topo, .mc-pergunta__linha { display: flex; flex-wrap: wrap; align-items: flex-start; gap: 0.75rem; }
    .mc-grupo__titulo, .mc-pergunta__enunciado { flex: 1 1 18rem; min-width: 0; }
    .mc-pergunta__tipo { flex: 0 1 12rem; min-width: 10rem; }
    .mc-acoes { display: inline-flex; gap: 0.125rem; margin-top: 1.5rem; }
    .mc-pergunta { display: flex; flex-direction: column; gap: 0.625rem; padding: 0.875rem; border: 1px solid var(--ces-border); border-radius: var(--ces-radius-md); background: var(--ces-surface); }
    .mc-pergunta__marcas { display: flex; flex-wrap: wrap; gap: 1.25rem; }
    .mc-evidencias__titulo { margin: 0; color: var(--ces-foreground); font-size: 0.875rem; font-weight: 700; }
    .mc-evidencias__ajuda { margin: 0.125rem 0 0; color: var(--ces-foreground-muted); font-size: 0.8125rem; }
    .mc-evidencias__marca { margin-top: 1.75rem; }
  `
})
export class ModelosChecklistComponent {
  private readonly servico = inject(ObrasDocumentacaoService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);

  protected readonly podeConfigurar = (inject(AuthService).currentUser()?.permissoes ?? []).includes('obras:documentacao:manage');
  protected readonly finalidades = FINALIDADES;
  protected readonly tipos = TIPOS_PERGUNTA;
  protected readonly statusOpcoes = OPCOES_STATUS;
  protected readonly temOpcoes = temOpcoes;
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly rotulo = (m: ModeloChecklistResponse) => m.titulo;

  protected readonly lista = cesListState({
    storageKey: 'obras-checklists',
    filters: {
      finalidade: cesFilter.value<FinalidadeChecklist | null>(null, { label: 'Finalidade', param: 'finalidade', options: () => FINALIDADES }),
      ativo: cesFilter.value<boolean | null>(true, { label: 'Status', param: 'status', options: () => OPCOES_STATUS })
    },
    sort: [{ key: 'titulo', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<ModeloChecklistResponse>(
    (pedido) => this.servico.listarModelos({ ...this.filtro(), ...paginaPadrao(pedido) }),
    { errorMessage: getErrorMessage }
  );
  protected readonly exportar = exportacaoPadrao((pagina) => this.servico.listarModelos({ ...this.filtro(), ...pagina }), () => this.lista.request());

  protected readonly colunas: CesDataTableColumn<ModeloChecklistResponse>[] = [
    { key: 'titulo', header: 'Título', type: 'custom', priority: 1, sortField: 'titulo', value: (m) => m.titulo, display: (m) => m.titulo },
    { key: 'finalidade', header: 'Finalidade', sortField: 'finalidade', width: '10rem', display: (m) => rotuloFinalidade(m.finalidade) },
    { key: 'perguntas', header: 'Perguntas', width: '8rem', display: (m) => `${m.perguntas} em ${m.grupos} ${m.grupos === 1 ? 'grupo' : 'grupos'}` },
    { key: 'uso', header: 'Uso', type: 'custom', width: '9rem', value: (m) => (m.emUso ? 'Em uso' : 'Ainda não usado'), display: (m) => (m.emUso ? 'Em uso' : 'Ainda não usado') },
    { key: 'ativo', header: 'Status', type: 'custom', width: '7rem', value: (m) => (m.ativo ? 'Ativo' : 'Inativo'), display: (m) => (m.ativo ? 'Ativo' : 'Inativo') }
  ];

  protected readonly aberto = signal(false);
  protected readonly emEdicao = signal<ModeloChecklistResponse | null>(null);
  protected readonly finalidade = signal<FinalidadeChecklist>('VISITA_PREVIA');
  protected readonly titulo = signal('');
  protected readonly descricao = signal('');
  protected readonly grupos = signal<GrupoChecklist[]>([]);
  protected readonly evidencias = signal<ItemEvidenciaModelo[]>([]);
  protected readonly tentou = signal(false);
  protected readonly salvando = signal(false);
  private readonly original = signal('');
  private readonly textosDeOpcoes = signal<Record<string, string>>({});

  protected readonly somenteLeitura = computed(() => !this.podeConfigurar);
  protected readonly estruturaTravada = computed(() => this.somenteLeitura() || !!this.emEdicao()?.emUso);
  protected readonly tituloDrawer = computed(() => (this.emEdicao() ? this.emEdicao()!.titulo : 'Novo modelo de checklist'));
  protected readonly problema = computed(() => problemaDaEstrutura(this.estrutura()));
  protected readonly alterado = computed(() => this.instantaneo() !== this.original());

  protected novo(): void {
    this.preencher(null, 'VISITA_PREVIA', '', '', [novoGrupo('VISITA_PREVIA')], []);
  }

  protected abrir(modelo: ModeloChecklistResponse): void {
    this.preencher(modelo, modelo.finalidade, modelo.titulo, modelo.descricao ?? '', structuredClone(modelo.estrutura.grupos), structuredClone(modelo.estrutura.evidencias ?? []));
  }

  private preencher(modelo: ModeloChecklistResponse | null, finalidade: FinalidadeChecklist, titulo: string, descricao: string, grupos: GrupoChecklist[], evidencias: ItemEvidenciaModelo[]): void {
    this.emEdicao.set(modelo);
    this.finalidade.set(finalidade);
    this.titulo.set(titulo);
    this.descricao.set(descricao);
    this.grupos.set(grupos);
    this.evidencias.set(evidencias);
    this.tentou.set(false);
    this.textosDeOpcoes.set({});
    this.original.set(this.instantaneo());
    this.aberto.set(true);
  }

  protected alterarGrupo(g: number, mudanca: Partial<GrupoChecklist>): void {
    this.grupos.update((grupos) => grupos.map((grupo, i) => (i === g ? { ...grupo, ...mudanca } : grupo)));
  }

  protected adicionarGrupo(): void {
    this.grupos.update((grupos) => [...grupos, novoGrupo(this.finalidade())]);
  }

  protected removerGrupo(g: number): void {
    this.grupos.update((grupos) => grupos.filter((_, i) => i !== g));
  }

  protected moverGrupo(g: number, direcao: number): void {
    this.grupos.update((grupos) => mover(grupos, g, direcao));
  }

  protected adicionarPergunta(g: number): void {
    this.alterarGrupo(g, { perguntas: [...this.grupos()[g].perguntas, novaPergunta(this.finalidade())] });
  }

  protected removerPergunta(g: number, i: number): void {
    this.alterarGrupo(g, { perguntas: this.grupos()[g].perguntas.filter((_, n) => n !== i) });
  }

  protected moverPergunta(g: number, i: number, direcao: number): void {
    this.alterarGrupo(g, { perguntas: mover(this.grupos()[g].perguntas, i, direcao) });
  }

  protected alterarPergunta(g: number, i: number, mudanca: Partial<PerguntaChecklist>): void {
    this.alterarGrupo(g, { perguntas: this.grupos()[g].perguntas.map((p, n) => (n === i ? { ...p, ...mudanca } : p)) });
  }

  /** Guarda o texto como foi digitado: a linha em branco no fim é a próxima opção sendo escrita. */
  protected alterarOpcoes(g: number, i: number, id: string, texto: string): void {
    this.textosDeOpcoes.update((textos) => ({ ...textos, [id]: texto }));
    this.alterarPergunta(g, i, { opcoes: opcoesDoTexto(texto) });
  }

  protected textoDasOpcoes(p: PerguntaChecklist): string {
    return this.textosDeOpcoes()[p.id] ?? (p.opcoes ?? []).join(String.fromCharCode(10));
  }

  protected trocarTipo(g: number, i: number, tipo: TipoPerguntaChecklist | null): void {
    if (!tipo) return;
    const atuais = this.grupos()[g].perguntas[i].opcoes;
    this.alterarPergunta(g, i, { tipo, opcoes: temOpcoes(tipo) ? (atuais?.length ? atuais : ['Sim', 'Não']) : null });
  }

  protected adicionarItem(): void {
    this.evidencias.update((itens) => [...itens, novoItemEvidencia()]);
  }

  protected alterarItem(i: number, mudanca: Partial<ItemEvidenciaModelo>): void {
    this.evidencias.update((itens) => itens.map((item, n) => (n === i ? { ...item, ...mudanca } : item)));
  }

  protected removerItem(i: number): void {
    this.evidencias.update((itens) => itens.filter((_, n) => n !== i));
  }

  protected moverItem(i: number, direcao: number): void {
    this.evidencias.update((itens) => mover(itens, i, direcao));
  }

  protected salvar(): void {
    this.tentou.set(true);
    if (!this.titulo().trim() || this.problema()) return;
    const request = { finalidade: this.finalidade(), titulo: this.titulo().trim(), descricao: this.descricao().trim() || null, estrutura: this.estrutura() };
    const id = this.emEdicao()?.id;
    this.salvando.set(true);
    (id ? this.servico.atualizarModelo(id, request) : this.servico.criarModelo(request)).subscribe({
      next: () => {
        this.salvando.set(false);
        this.aberto.set(false);
        this.notificacoes.success('Modelo salvo.');
        this.lista.reload();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected duplicar(modelo: ModeloChecklistResponse): void {
    this.servico.duplicarModelo(modelo.id).subscribe({
      next: (copia) => {
        this.lista.reload();
        this.abrir(copia);
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected async desativar(modelo: ModeloChecklistResponse): Promise<void> {
    const ok = await this.confirmacao.confirm({
      title: `Desativar ${modelo.titulo}?`,
      message: 'Ele deixa de aparecer nos novos lançamentos. O que já foi lançado continua como está.',
      confirmLabel: 'Desativar'
    });
    if (!ok) return;
    this.servico.desativarModelo(modelo.id).subscribe({
      next: () => this.lista.reload(),
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected ativar(modelo: ModeloChecklistResponse): void {
    this.servico.ativarModelo(modelo.id).subscribe({
      next: () => this.lista.reload(),
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  private estrutura(): EstruturaChecklist {
    return {
      grupos: this.grupos().map((g) => ({ ...g, titulo: g.titulo.trim(), perguntas: g.perguntas.map((p) => ({ ...p, enunciado: p.enunciado.trim() })) })),
      evidencias: this.finalidade() === 'AS_BUILT' ? this.evidencias().map((item) => ({ ...item, titulo: item.titulo.trim() })) : []
    };
  }

  private instantaneo(): string {
    return JSON.stringify([this.finalidade(), this.titulo(), this.descricao(), this.grupos(), this.evidencias()]);
  }

  private filtro() {
    const { filters, query } = this.lista.request();
    return { buscaGlobal: query || null, finalidade: filters.finalidade, ativo: filters.ativo };
  }
}

function mover<T>(lista: T[], indice: number, direcao: number): T[] {
  const nova = [...lista];
  const [item] = nova.splice(indice, 1);
  nova.splice(indice + direcao, 0, item);
  return nova;
}
