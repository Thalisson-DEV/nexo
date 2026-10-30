import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import {
  CesButtonComponent, CesCalloutComponent, CesCellDirective, CesContentState, CesDataTableColumn, CesDataTableComponent,
  CesDialogShellComponent, CesFilterChip, CesFilterPanelComponent, CesPageHeaderComponent, CesSelectComponent, CesSelectOption,
  CesStatusComponent, CesTextareaComponent, CesTone, NotificationService
} from '@ces/ui';

import {
  Aprovacao, AutomacaoResumo, Modelo, OrigemAutomacao, StatusAutomacao, STATUS_EXECUCAO_ROTULO, STATUS_ROTULO, TIPO_ROTULO, TipoAutomacao
} from '../../../core/models/automacao.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AutomacoesService } from '../../../core/services/automacoes.service';
import { definicaoBranca } from './lib/arvore';
import { icone } from './lib/dinamico';

type FiltroExecucao = 'falhou' | 'sucesso' | 'nunca';

interface Filtros {
  readonly tipo: TipoAutomacao | null;
  readonly status: StatusAutomacao | null;
  readonly origem: OrigemAutomacao | null;
  readonly execucao: FiltroExecucao | null;
}

const SEM_FILTROS: Filtros = { tipo: null, status: null, origem: null, execucao: null };

const ORIGEM_ROTULO: Record<OrigemAutomacao, string> = {
  USUARIO: 'Criada na tela', COPILOTO: 'Montada pelo Copiloto', SISTEMA: 'Do sistema', REGRA_CONVERTIDA: 'Convertida de regra'
};

const EXECUCAO_FILTRO_ROTULO: Record<FiltroExecucao, string> = { falhou: 'Falhou', sucesso: 'Deu certo', nunca: 'Nunca rodou' };

function casaExecucao(a: AutomacaoResumo, f: FiltroExecucao): boolean {
  const u = a.ultimaExecucao;
  if (f === 'nunca') return !u;
  if (!u) return false;
  return f === 'sucesso' ? u.status === 'SUCESSO' : ['FALHA', 'TEMPO_ESGOTADO'].includes(u.status);
}

/** Lista de automações (o antigo "Regras de notificação"): filtro por tipo, busca e criação. */
@Component({
  selector: 'app-automacoes-lista',
  standalone: true,
  imports: [CesTextareaComponent, CesStatusComponent, CesCalloutComponent, CesButtonComponent, CesDataTableComponent, CesCellDirective, CesSelectComponent, CesFilterPanelComponent, RouterLink, DatePipe, CesPageHeaderComponent, CesDialogShellComponent],
  styleUrls: ['./automacoes.css', './automacoes-campos.css'],
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="aut aut-page">
      <ces-page-header title="Automações" icon="pi pi-sitemap"
                       description="Quando algo acontece (ou numa agenda), o Nexo executa os passos: condições, avisos, e-mails, aprovações, consultas e análises do Copiloto.">
        <div actions style="display:flex;gap:0.5rem">
          <ces-button icon="pi pi-plus" label="Nova automação" variant="primary" size="sm" (pressed)="novoAberto.set(true)"></ces-button>
        </div>
      </ces-page-header>

      @if (aprovacoes().length) {
        <ces-callout tone="warning" [dense]="true"><span>{{ aprovacoes().length }} aprovação(ões) aguardando você.</span>
          @for (a of aprovacoes().slice(0, 3); track a.id) {
            <a class="aut-link" [routerLink]="['/notificacoes/automacoes/aprovacoes', a.id]">{{ a.titulo }}</a>
          }
        </ces-callout>
      }

      <ces-filter-panel storageKey="automacoes-filtros" [activeCount]="resumoFiltros().length" [summary]="resumoFiltros()"
                        (applied)="aplicarFiltros()" (cleared)="limparFiltros()" (chipRemoved)="removerFiltro($event)">
        <div class="ces-filter-grid">
          <ces-select label="Tipo" placeholder="Todos" [clearable]="true" [options]="opcoesTipo()" [(value)]="rascunho.tipo"></ces-select>
          <ces-select label="Situação" placeholder="Todas" [clearable]="true" [options]="opcoesStatus" [(value)]="rascunho.status"></ces-select>
          <ces-select label="Origem" placeholder="Todas" [clearable]="true" [options]="opcoesOrigem" [(value)]="rascunho.origem"></ces-select>
          <ces-select label="Última execução" placeholder="Qualquer" [clearable]="true" [options]="opcoesExecucao" [(value)]="rascunho.execucao"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table title="Automações" storageKey="automacoes-lista" searchPlaceholder="Buscar automação"
                      [columns]="colunas" [rows]="filtradas()" [rowKey]="chave" [state]="estado()" [rowClickable]="true" [filtered]="resumoFiltros().length > 0"
                      noResultsTitle="Nenhuma automação com esse recorte." (clearFilters)="limparFiltros()"
                      stateTitle="Nenhuma automação aqui" stateDescription="Crie uma em branco, a partir de um modelo, ou peça ao Copiloto."
                      emptyActionLabel="Nova automação" (emptyAction)="novoAberto.set(true)" (rowActivate)="abrir($event)">
        <ng-template cesCell="nome" let-row>
          <span class="aut-list-main">
            <strong class="aut-list-name">{{ row.nome }}</strong>
            <span class="aut-list-desc">{{ row.descricao || row.gatilhoRotulo }}</span>
          </span>
        </ng-template>
        <ng-template cesCell="ultima" let-row>
          @if (row.ultimaExecucao; as u) {
            <span class="aut-list-run" [attr.data-status]="u.status"><span class="aut-dot"></span>{{ rotuloExecucao(u.status) }} · {{ u.criadaEm | date: 'dd/MM HH:mm' }}</span>
          } @else { <span class="aut-muted">Nunca rodou</span> }
        </ng-template>
        <ng-template cesCell="status" let-row>
          <ces-status [tone]="tomStatus(row.status)">{{ rotuloStatus(row.status) }}</ces-status>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-dialog-shell [(visible)]="novoAberto" title="Nova automação" size="lg" [loading]="criando()" loadingLabel="Criando">
      <div class="aut" style="display:flex;flex-direction:column;gap:1rem">
        <label class="aut-field">
          <span class="ces-field__label">Nome</span>
          <input class="p-inputtext ces-prime-control" [value]="nome()" (input)="nome.set($any($event.target).value)" placeholder="Ex.: Desconto validado → RH da base" />
        </label>
        <div class="aut-field">
          <span class="ces-field__label">Como começar</span>
          <div class="aut-templates">
            <button type="button" class="aut-template" [attr.aria-pressed]="escolha() === 'branco'" (click)="escolha.set('branco')" data-group="data">
              <span class="aut-node-icon"><i class="pi pi-file" aria-hidden="true"></i></span>
              <span><span class="aut-template-title">Em branco</span><span class="aut-template-desc" style="display:block">Monte passo a passo no canvas.</span></span>
            </button>
            <button type="button" class="aut-template" [attr.aria-pressed]="escolha() === 'pedir'" (click)="escolha.set('pedir')" data-group="ai">
              <span class="aut-node-icon"><i class="pi pi-sparkles" aria-hidden="true"></i></span>
              <span><span class="aut-template-title">Pedir ao Copiloto</span><span class="aut-template-desc" style="display:block">Descreva em português e ele monta o rascunho.</span></span>
            </button>
            @for (m of modelos(); track m.id) {
              <button type="button" class="aut-template" [attr.aria-pressed]="escolha() === m.id" (click)="escolherModelo(m)" [attr.data-group]="grupo(m.tipo)">
                <span class="aut-node-icon"><i [class]="icone(m.icone)" aria-hidden="true"></i></span>
                <span><span class="aut-template-title">{{ m.nome }}</span><span class="aut-template-desc" style="display:block">{{ m.descricao }}</span></span>
              </button>
            }
          </div>
        </div>
        @if (escolha() === 'pedir') {
          <ces-textarea label="O que a automação deve fazer?" [rows]="4" [value]="pedido()" (valueChange)="pedido.set($event)"
                        placeholder="Quando um desconto for validado, avise por e-mail o RH da base do colaborador: Juazeiro é a Maria, Petrolina é o João; nas outras bases, quem tem a permissão desconto:baixar."></ces-textarea>
        }
        @if (erroCriar()) { <ces-callout tone="danger" [dense]="true">{{ erroCriar() }}</ces-callout> }
      </div>
      <div dialog-footer>
        <ces-button label="Cancelar" variant="ghost" size="sm" (pressed)="novoAberto.set(false)"></ces-button>
        <ces-button [label]="escolha() === 'pedir' ? 'Montar com o Copiloto' : 'Criar e abrir o editor'" variant="primary" size="sm" [disabled]="criando() || (escolha() === 'pedir' ? !pedido().trim() : !nome().trim())" (pressed)="criar()"></ces-button>
      </div>
    </ces-dialog-shell>
  `
})
export class AutomacoesListaComponent {
  private readonly service = inject(AutomacoesService);
  private readonly router = inject(Router);
  private readonly notificacao = inject(NotificationService);

  protected readonly itens = signal<AutomacaoResumo[]>([]);
  protected readonly aprovacoes = signal<Aprovacao[]>([]);
  protected readonly modelos = signal<Modelo[]>([]);
  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);
  protected readonly filtros = signal<Filtros>(SEM_FILTROS);
  /** O que está nos campos do painel; vale ao clicar em "Aplicar filtros". */
  protected rascunho: { -readonly [K in keyof Filtros]: Filtros[K] } = { ...SEM_FILTROS };
  protected readonly opcoesStatus: CesSelectOption<StatusAutomacao>[] = (['ATIVA', 'PAUSADA', 'RASCUNHO', 'ARQUIVADA'] as StatusAutomacao[])
    .map((v) => ({ label: STATUS_ROTULO[v], value: v }));
  protected readonly opcoesOrigem: CesSelectOption<OrigemAutomacao>[] = (Object.keys(ORIGEM_ROTULO) as OrigemAutomacao[])
    .map((v) => ({ label: ORIGEM_ROTULO[v], value: v }));
  protected readonly opcoesExecucao: CesSelectOption<FiltroExecucao>[] = (Object.keys(EXECUCAO_FILTRO_ROTULO) as FiltroExecucao[])
    .map((v) => ({ label: EXECUCAO_FILTRO_ROTULO[v], value: v }));
  protected readonly novoAberto = signal(false);
  protected readonly escolha = signal<string>('branco');
  protected readonly nome = signal('');
  protected readonly pedido = signal('');
  protected readonly criando = signal(false);
  protected readonly erroCriar = signal<string | null>(null);
  protected readonly tipos: TipoAutomacao[] = ['ALERTA', 'EMAIL', 'ROTINA', 'APROVACAO', 'DADOS_IA', 'GERAL'];
  protected readonly icone = icone;

  protected readonly colunas: CesDataTableColumn<AutomacaoResumo>[] = [
    { key: 'nome', header: 'Automação', priority: 1, width: '40%', value: (a) => a.nome, display: (a) => `${a.nome} ${a.descricao ?? ''}` },
    { key: 'tipo', header: 'Tipo', width: '7rem', display: (a) => TIPO_ROTULO[a.tipo] },
    { key: 'gatilho', header: 'Quando', display: (a) => a.gatilhoRotulo },
    { key: 'ultima', header: 'Última execução', value: (a) => a.ultimaExecucao?.criadaEm ?? '', display: (a) => a.ultimaExecucao ? STATUS_EXECUCAO_ROTULO[a.ultimaExecucao.status] : 'Nunca rodou' },
    { key: 'execucoes30Dias', header: '30 dias', type: 'number', width: '6rem' },
    { key: 'status', header: 'Situação', priority: 1, display: (a) => STATUS_ROTULO[a.status] }
  ];
  protected readonly chave = (a: AutomacaoResumo) => a.id;
  protected readonly estado = computed<CesContentState>(() => this.carregando() ? 'loading' : this.erro() ? 'error' : this.filtradas().length ? 'ready' : 'empty');
  protected readonly opcoesTipo = computed<CesSelectOption<TipoAutomacao>[]>(() =>
    this.tipos.filter((t) => this.contagem()[t]).map((t) => ({ label: `${TIPO_ROTULO[t]} (${this.contagem()[t]})`, value: t })));

  protected readonly contagem = computed(() => {
    const c: Partial<Record<TipoAutomacao, number>> = {};
    for (const a of this.itens()) c[a.tipo] = (c[a.tipo] ?? 0) + 1;
    return c;
  });

  protected readonly filtradas = computed(() => {
    const f = this.filtros();
    return this.itens().filter((a) => (!f.tipo || a.tipo === f.tipo)
      && (!f.status || a.status === f.status)
      && (!f.origem || a.origem === f.origem)
      && (!f.execucao || casaExecucao(a, f.execucao)));
  });

  protected readonly resumoFiltros = computed<CesFilterChip[]>(() => {
    const f = this.filtros();
    const chips: CesFilterChip[] = [];
    if (f.tipo) chips.push({ key: 'tipo', label: `Tipo: ${TIPO_ROTULO[f.tipo]}` });
    if (f.status) chips.push({ key: 'status', label: `Situação: ${STATUS_ROTULO[f.status]}` });
    if (f.origem) chips.push({ key: 'origem', label: `Origem: ${ORIGEM_ROTULO[f.origem]}` });
    if (f.execucao) chips.push({ key: 'execucao', label: `Última execução: ${EXECUCAO_FILTRO_ROTULO[f.execucao]}` });
    return chips;
  });

  constructor() {
    this.carregar();
    this.service.minhasAprovacoes().subscribe({ next: (a) => this.aprovacoes.set(a), error: () => undefined });
    this.service.modelos().subscribe({ next: (m) => this.modelos.set(m), error: () => undefined });
  }

  protected carregar(): void {
    this.carregando.set(true);
    this.erro.set(null);
    this.service.listar({ size: 500 }).subscribe({
      next: (p) => {
        this.itens.set(p.content);
        this.carregando.set(false);
      },
      error: (e) => {
        this.erro.set(getErrorMessage(e));
        this.carregando.set(false);
      }
    });
  }

  protected rotuloTipo(t: TipoAutomacao): string { return TIPO_ROTULO[t]; }
  protected rotuloStatus(s: AutomacaoResumo['status']): string { return STATUS_ROTULO[s]; }
  protected rotuloExecucao(s: keyof typeof STATUS_EXECUCAO_ROTULO): string { return STATUS_EXECUCAO_ROTULO[s]; }
  protected aplicarFiltros(): void {
    this.filtros.set({ ...this.rascunho });
  }

  protected limparFiltros(): void {
    this.rascunho = { ...SEM_FILTROS };
    this.filtros.set(SEM_FILTROS);
  }

  protected removerFiltro(chave: string): void {
    const k = chave as keyof Filtros;
    this.rascunho = { ...this.rascunho, [k]: null };
    this.filtros.update((f) => ({ ...f, [k]: null }));
  }

  protected grupo(t: TipoAutomacao): string {
    return ({ EMAIL: 'notify', ALERTA: 'approval', ROTINA: 'trigger', APROVACAO: 'control', DADOS_IA: 'ai', GERAL: 'data' } as const)[t];
  }

  protected abrir(a: AutomacaoResumo): void {
    void this.router.navigate(['/notificacoes/automacoes', a.id]);
  }

  protected tomStatus(s: AutomacaoResumo['status']): CesTone {
    return s === 'ATIVA' ? 'success' : s === 'PAUSADA' ? 'warning' : 'neutral';
  }

  protected escolherModelo(m: Modelo): void {
    this.escolha.set(m.id);
    if (!this.nome().trim()) this.nome.set(m.nome);
  }

  protected criar(): void {
    this.criando.set(true);
    this.erroCriar.set(null);
    const fim = (id: string) => {
      this.criando.set(false);
      this.novoAberto.set(false);
      void this.router.navigate(['/notificacoes/automacoes', id, 'editar']);
    };
    const falha = (e: unknown) => {
      this.criando.set(false);
      this.erroCriar.set(getErrorMessage(e));
    };
    if (this.escolha() === 'pedir') {
      this.service.rascunho(this.pedido()).subscribe({
        next: (r) => {
          this.notificacao.success('Rascunho montado pelo Copiloto. Confira e ative.');
          fim(r.automacaoId);
        },
        error: falha
      });
      return;
    }
    const modelo = this.modelos().find((m) => m.id === this.escolha());
    this.service.criar({ nome: this.nome().trim(), descricao: modelo?.descricao ?? null, tipo: modelo?.tipo ?? null,
      definicao: modelo?.definicao ?? definicaoBranca() }).subscribe({ next: (d) => fim(d.resumo.id), error: falha });
  }
}
