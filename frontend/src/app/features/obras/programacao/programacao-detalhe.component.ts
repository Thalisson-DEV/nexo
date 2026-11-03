import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  CesAsyncStateComponent,
  CesButtonComponent,
  CesCalloutComponent,
  CesConfirmationService,
  CesDatepickerComponent,
  CesFormDrawerComponent,
  CesFormSectionComponent,
  CesIconButtonComponent,
  CesStatusComponent,
  CesTextInputComponent,
  CesTextareaComponent,
  NotificationService
} from '@ces/ui';
import { take } from 'rxjs';
import { getErrorMessage } from '../../../core/models/shared.models';
import { EquipeProjetada, EventoProgramacao, ItemProgramado, ProgramacaoDetalhe, SituacaoIntervencao, TurnoProgramacao } from '../../../core/models/obras-programacao.models';
import { AuthService } from '../../../core/services/auth.service';
import { ObrasProgramacaoService } from '../../../core/services/obras-programacao.service';
import { formatarDataHora, formatarDataParaApi, formatarMoeda, parseDataBrParaDate } from '../obras-ui';
import { CondicoesProgramacaoComponent } from './condicoes-programacao.component';
import { EquipesProjecaoComponent } from './equipes-projecao.component';
import { ROTULO_INTERVENCAO, ROTULO_TURNO, SITUACAO_PROGRAMACAO, TIPO_EVENTO, formatarNumero } from './programacao.logic';

type Modo = 'ver' | 'editar' | 'reprogramar' | 'cancelar';

/** Detalhe de uma programação, com editar, reprogramar, cancelar, excluir e retirar item no mesmo painel. */
@Component({
  selector: 'app-programacao-detalhe',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    CesAsyncStateComponent,
    CesButtonComponent,
    CesCalloutComponent,
    CesDatepickerComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesIconButtonComponent,
    CesStatusComponent,
    CesTextInputComponent,
    CesTextareaComponent,
    CondicoesProgramacaoComponent,
    EquipesProjecaoComponent
  ],
  template: `
    <ces-form-drawer [(visible)]="aberto" [title]="p() ? 'Programação #' + p()!.numero : 'Programação'" [description]="p() ? p()!.numeroObra + ' — ' + p()!.tituloObra : undefined"
      [dirty]="modo() !== 'ver'" size="lg">
      @if (erro(); as mensagem) {
        <ces-async-state state="error" title="Não foi possível abrir a programação" [description]="mensagem"></ces-async-state>
      } @else if (!p()) {
        <ces-async-state state="loading" title="Abrindo a programação" [compact]="true"></ces-async-state>
      } @else {
        @let programacao = p()!;
        @switch (modo()) {
          @case ('editar') {
            <ces-form-section title="Condições da intervenção" icon="pi pi-bolt">
              <ces-text-input label="Descrição do planejamento" [required]="true" [maxlength]="500" [(value)]="descricao"
                [error]="tentou() && !descricao().trim() ? 'Informe a descrição do planejamento.' : undefined"></ces-text-input>
              <app-condicoes-programacao [validar]="tentou()" [(linhaViva)]="linhaViva" [(desligamento)]="desligamento"
                [(finalizaObra)]="finalizaObra" [(turno)]="turno" [(situacaoIntervencao)]="situacaoIntervencao" [(observacao)]="observacao"></app-condicoes-programacao>
            </ces-form-section>
          }
          @case ('reprogramar') {
            <ces-form-section title="Novo período" icon="pi pi-calendar">
              <div class="ces-form-grid">
                <ces-datepicker label="Início" [required]="true" [value]="inicio()" (valueChange)="inicio.set($any($event)); projetar()"></ces-datepicker>
                <ces-datepicker label="Fim" [required]="true" [value]="fim()" [minDate]="inicio()" (valueChange)="fim.set($any($event)); projetar()"></ces-datepicker>
              </div>
            </ces-form-section>
            <ces-form-section title="Equipe" icon="pi pi-users" description="O valor congelado na confirmação não muda.">
              <app-equipes-projecao [equipes]="equipes()" [carregando]="carregandoEquipes()" [(selecionada)]="idEquipe"></app-equipes-projecao>
            </ces-form-section>
            <ces-form-section title="Motivo" icon="pi pi-comment">
              <ces-textarea label="Observação" [required]="true" [rows]="2" [maxlength]="1000" [(value)]="motivo" placeholder="Ex.: turma errada"
                [error]="tentou() && !motivo().trim() ? 'Informe a observação da reprogramação.' : undefined"></ces-textarea>
            </ces-form-section>
          }
          @case ('cancelar') {
            <ces-form-section title="Cancelar a programação" icon="pi pi-ban" description="Os itens sem execução voltam a pendentes.">
              <ces-textarea label="Observação" [required]="true" [rows]="3" [maxlength]="1000" [(value)]="motivo"
                [error]="tentou() && !motivo().trim() ? 'Informe a observação do cancelamento.' : undefined"></ces-textarea>
            </ces-form-section>
          }
          @default {
            <div class="detalhe__situacao">
              <ces-status [tone]="situacao().tom" [icon]="situacao().icone">{{ situacao().rotulo }}</ces-status>
              <span class="detalhe__atalhos">
                <button type="button" class="detalhe__link" (click)="copiarResumo(programacao)"><i class="pi pi-copy" aria-hidden="true"></i> Copiar para a equipe</button>
                <a class="detalhe__link" [routerLink]="['/obras', programacao.idObra]"><i class="pi pi-building" aria-hidden="true"></i> Ficha da obra</a>
                <a class="detalhe__link" [routerLink]="['/obras', programacao.idObra, 'planejamento']"><i class="pi pi-map" aria-hidden="true"></i> Planejamento</a>
              </span>
            </div>
            <dl class="detalhe__dados">
              <div><dt>Equipe</dt><dd>{{ programacao.codigoEquipe }}</dd></div>
              <div><dt>Período</dt><dd>{{ programacao.inicio }} a {{ programacao.fim }}</dd></div>
              <div><dt>Valor congelado</dt><dd>{{ moeda(programacao.valor) }}</dd></div>
              <div><dt>Turno</dt><dd>{{ rotuloTurno[programacao.turno] }}</dd></div>
              <div><dt>Linha viva</dt><dd>{{ programacao.linhaViva ? 'Sim' : 'Não' }}</dd></div>
              <div><dt>Desligamento</dt><dd>{{ programacao.desligamento ? 'Sim' : 'Não' }}</dd></div>
              <div><dt>Finaliza obra</dt><dd>{{ programacao.finalizaObra ? 'Sim' : 'Não' }}</dd></div>
              <div><dt>Situação da intervenção</dt><dd>{{ rotuloIntervencao[programacao.situacaoIntervencao] }}</dd></div>
              <div class="detalhe__larga"><dt>Descrição</dt><dd>{{ programacao.descricao }}</dd></div>
              @if (programacao.observacao) { <div class="detalhe__larga"><dt>Observação</dt><dd>{{ programacao.observacao }}</dd></div> }
            </dl>
            @if (eventos().length) {
              <ces-form-section title="O que mudou" icon="pi pi-history">
                <ol class="detalhe__eventos">
                  @for (e of eventos(); track e.id) {
                    <li>
                      <strong>{{ tipoEvento[e.tipo].rotulo }}</strong>
                      <small>{{ dataHora(e.data) }} · {{ e.usuario }}</small>
                      @if (e.equipeOrigem !== e.equipeDestino) { <span>{{ e.equipeOrigem }} → {{ e.equipeDestino }}</span> }
                      @if (e.inicioOriginal !== e.inicioNovo || e.fimOriginal !== e.fimNovo) { <span>{{ e.inicioOriginal }} a {{ e.fimOriginal }} → {{ e.inicioNovo }} a {{ e.fimNovo }}</span> }
                      @if (e.observacao) { <span>{{ e.observacao }}</span> }
                    </li>
                  }
                </ol>
              </ces-form-section>
            }
            <ces-form-section title="Itens" icon="pi pi-list">
              @if (!programacao.itens.length) {
                <ces-callout tone="info" [dense]="true">Os itens voltaram a pendentes.</ces-callout>
              }
              <ul class="detalhe__itens">
                @for (item of programacao.itens; track item.idItem) {
                  <li>
                    <span class="detalhe__tipo" [attr.data-material]="item.tipo === 'MATERIAL'">{{ item.tipo === 'MATERIAL' ? 'M' : 'S' }}</span>
                    <span class="detalhe__item">
                      <strong>{{ item.descricao }}</strong>
                      <small>{{ item.local }} · {{ numero(item.quantidade) }} {{ item.unidade }}@if (item.valorTotal != null) { · {{ moeda(item.valorTotal) }} }@if (item.quantidadeExecutada > 0) { · {{ numero(item.quantidadeExecutada) }} executado }</small>
                    </span>
                    @if (podeEditar && aberta() && !item.quantidadeExecutada) {
                      <ces-icon-button icon="pi pi-minus-circle" size="icon-sm" [label]="'Retirar ' + item.descricao + ' da programação'" tooltip="Retirar da programação" (pressed)="retirar(item)"></ces-icon-button>
                    }
                  </li>
                }
              </ul>
            </ces-form-section>
          }
        }
      }

      <div drawer-footer class="ces-actions-row">
        @if (modo() === 'ver') {
          @if (podeEditar && p()) {
            <ces-button variant="ghost" icon="pi pi-trash" label="Excluir" (pressed)="excluir()"></ces-button>
            @if (aberta()) {
              <ces-button variant="ghost" icon="pi pi-ban" label="Cancelar programação" (pressed)="abrirModo('cancelar')"></ces-button>
              <ces-button variant="outline" icon="pi pi-pencil" label="Editar" (pressed)="abrirModo('editar')"></ces-button>
              <ces-button icon="pi pi-calendar-clock" label="Reprogramar" (pressed)="abrirModo('reprogramar')"></ces-button>
            }
          }
        } @else {
          <ces-button variant="ghost" label="Voltar" (pressed)="modo.set('ver')"></ces-button>
          <ces-button icon="pi pi-check" [label]="rotuloConfirmar()" [loading]="salvando()" (pressed)="confirmar()"></ces-button>
        }
      </div>
    </ces-form-drawer>
  `,
  styles: [`
    .detalhe__situacao { display: flex; gap: 0.75rem; align-items: center; justify-content: space-between; }
    .detalhe__atalhos { display: flex; flex-wrap: wrap; gap: 0.75rem; justify-content: flex-end; }
    .detalhe__link { display: inline-flex; gap: 0.375rem; align-items: center; padding: 0; border: 0; background: transparent; color: var(--ces-brand); font: inherit; font-size: 0.8125rem; cursor: pointer; }
    .detalhe__link:focus-visible { outline: 2px solid var(--ces-focus-ring); outline-offset: 2px; }
    .detalhe__eventos { display: flex; flex-direction: column; gap: 0.625rem; margin: 0; padding: 0; list-style: none; }
    .detalhe__eventos li { display: flex; flex-direction: column; gap: 0.0625rem; padding-left: 0.625rem; border-left: 2px solid var(--ces-border); font-size: 0.8125rem; }
    .detalhe__eventos small { color: var(--ces-foreground-muted); font-size: 0.75rem; }
    .detalhe__dados { display: grid; grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr)); gap: 0.625rem 1rem; margin: 0; }
    .detalhe__dados dt { color: var(--ces-foreground-muted); font-size: 0.6875rem; }
    .detalhe__dados dd { margin: 0; font-size: 0.875rem; }
    .detalhe__larga { grid-column: 1 / -1; }
    .detalhe__itens { display: flex; flex-direction: column; margin: 0; padding: 0; list-style: none; }
    .detalhe__itens li { display: flex; gap: 0.5rem; align-items: center; padding: 0.375rem 0; border-bottom: 1px solid var(--ces-border); }
    .detalhe__tipo { flex: none; width: 1rem; border-radius: var(--ces-radius-sm); background: var(--ces-success-soft); color: var(--ces-success-text); font-size: 0.625rem; font-weight: 700; line-height: 1rem; text-align: center; }
    .detalhe__tipo[data-material='true'] { background: var(--ces-info-soft); color: var(--ces-info-text); }
    .detalhe__item { display: flex; flex: 1; flex-direction: column; min-width: 0; font-size: 0.8125rem; }
    .detalhe__item small { color: var(--ces-foreground-muted); font-size: 0.75rem; }
  `]
})
export class ProgramacaoDetalheComponent {
  private readonly api = inject(ObrasProgramacaoService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);

  readonly aberto = model(false);
  readonly idProgramacao = input<string | null>(null);
  /** Avisa que a programação mudou ou deixou de existir, para a tela recarregar. */
  readonly alterada = output<void>();

  protected readonly podeEditar = (inject(AuthService).currentUser()?.permissoes ?? []).includes('obras:programacao:write');
  protected readonly rotuloTurno = ROTULO_TURNO;
  protected readonly rotuloIntervencao = ROTULO_INTERVENCAO;
  protected readonly moeda = formatarMoeda;
  protected readonly numero = formatarNumero;
  protected readonly dataHora = formatarDataHora;
  protected readonly tipoEvento = TIPO_EVENTO;
  protected readonly eventos = signal<EventoProgramacao[]>([]);

  protected readonly p = signal<ProgramacaoDetalhe | null>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly modo = signal<Modo>('ver');
  protected readonly salvando = signal(false);
  protected readonly tentou = signal(false);

  protected readonly descricao = signal('');
  protected readonly linhaViva = signal<boolean | null>(null);
  protected readonly desligamento = signal<boolean | null>(null);
  protected readonly finalizaObra = signal<boolean | null>(null);
  protected readonly turno = signal<TurnoProgramacao | null>(null);
  protected readonly situacaoIntervencao = signal<SituacaoIntervencao | null>(null);
  protected readonly observacao = signal('');

  protected readonly inicio = signal<Date | null>(null);
  protected readonly fim = signal<Date | null>(null);
  protected readonly idEquipe = signal<string | null>(null);
  protected readonly motivo = signal('');
  protected readonly equipes = signal<EquipeProjetada[]>([]);
  protected readonly carregandoEquipes = signal(false);

  protected readonly situacao = computed(() => SITUACAO_PROGRAMACAO[this.p()?.situacao ?? 'PROGRAMADA']);
  protected readonly aberta = computed(() => this.p()?.situacao !== 'CANCELADA' && this.p()?.situacao !== 'CONCLUIDA');
  protected readonly rotuloConfirmar = computed(() => {
    switch (this.modo()) {
      case 'editar': return 'Salvar alterações';
      case 'reprogramar': return 'Reprogramar';
      default: return 'Cancelar programação';
    }
  });

  constructor() {
    effect(() => {
      const id = this.idProgramacao();
      if (this.aberto() && id) untracked(() => this.carregar(id));
    });
  }

  private carregar(id: string): void {
    this.p.set(null);
    this.erro.set(null);
    this.modo.set('ver');
    this.api.buscar(id).pipe(take(1)).subscribe({
      next: (programacao) => this.p.set(programacao),
      error: (e) => this.erro.set(getErrorMessage(e))
    });
    this.carregarEventos(id);
  }

  private carregarEventos(id: string): void {
    this.api.eventosDa(id).pipe(take(1)).subscribe({
      next: (pagina) => this.eventos.set(pagina.content ?? []),
      error: () => this.eventos.set([])
    });
  }

  /** Texto pronto para mandar à equipe pelo aplicativo de mensagens. */
  protected copiarResumo(p: ProgramacaoDetalhe): void {
    const servicos = p.itens.filter((i) => i.tipo === 'SERVICO');
    const materiais = p.itens.filter((i) => i.tipo === 'MATERIAL');
    const linha = (i: ItemProgramado) => `- ${i.local}: ${i.descricao} (${formatarNumero(i.quantidade)} ${i.unidade ?? ''})`.replace(' )', ')');
    const texto = [
      `Programação #${p.numero} - ${p.codigoEquipe}`,
      `Obra ${p.numeroObra} - ${p.tituloObra}`,
      `Período: ${p.inicio} a ${p.fim} (${ROTULO_TURNO[p.turno]})`,
      `Linha viva: ${p.linhaViva ? 'Sim' : 'Não'} | Desligamento: ${p.desligamento ? 'Sim' : 'Não'}`,
      p.descricao,
      p.observacao ? `Obs.: ${p.observacao}` : null,
      servicos.length ? `\nServiços:\n${servicos.map(linha).join('\n')}` : null,
      materiais.length ? `\nMateriais:\n${materiais.map(linha).join('\n')}` : null
    ].filter(Boolean).join('\n');
    navigator.clipboard.writeText(texto).then(
      () => this.notificacoes.success('Resumo copiado. É só colar na mensagem para a equipe.'),
      () => this.notificacoes.error('Não foi possível copiar. O navegador bloqueou o acesso à área de transferência.')
    );
  }

  protected abrirModo(modo: Modo): void {
    const p = this.p();
    if (!p) return;
    this.tentou.set(false);
    this.motivo.set('');
    if (modo === 'editar') {
      this.descricao.set(p.descricao);
      this.linhaViva.set(p.linhaViva);
      this.desligamento.set(p.desligamento);
      this.finalizaObra.set(p.finalizaObra);
      this.turno.set(p.turno);
      this.situacaoIntervencao.set(p.situacaoIntervencao);
      this.observacao.set(p.observacao ?? '');
    }
    if (modo === 'reprogramar') {
      this.inicio.set(parseDataBrParaDate(p.inicio));
      this.fim.set(parseDataBrParaDate(p.fim));
      this.idEquipe.set(p.idEquipe);
      this.projetar();
    }
    this.modo.set(modo);
  }

  protected projetar(): void {
    const p = this.p();
    const inicio = this.inicio();
    const fim = this.fim();
    if (!p || !inicio || !fim || fim < inicio) return;
    this.carregandoEquipes.set(true);
    this.api.previa(p.idObra, { inicio: formatarDataParaApi(inicio)!, fim: formatarDataParaApi(fim)!, idProgramacao: p.id }).pipe(take(1)).subscribe({
      next: (previa) => {
        this.carregandoEquipes.set(false);
        this.equipes.set(previa.equipes);
      },
      error: (e) => {
        this.carregandoEquipes.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected confirmar(): void {
    this.tentou.set(true);
    const p = this.p();
    if (!p) return;
    switch (this.modo()) {
      case 'editar': return this.salvarEdicao(p);
      case 'reprogramar': return this.salvarReprogramacao(p);
      default: return this.salvarCancelamento(p);
    }
  }

  private salvarEdicao(p: ProgramacaoDetalhe): void {
    const [linhaViva, desligamento, finalizaObra, turno, situacao] =
      [this.linhaViva(), this.desligamento(), this.finalizaObra(), this.turno(), this.situacaoIntervencao()];
    if (!this.descricao().trim() || linhaViva === null || desligamento === null || finalizaObra === null || !turno || !situacao) return;
    this.gravar(this.api.editar(p.id, {
      descricao: this.descricao().trim(), linhaViva, desligamento, finalizaObra, turno,
      situacaoIntervencao: situacao, observacao: this.observacao().trim() || null
    }, p.versao), 'Programação atualizada.');
  }

  private salvarReprogramacao(p: ProgramacaoDetalhe): void {
    const [inicio, fim, idEquipe] = [this.inicio(), this.fim(), this.idEquipe()];
    if (!inicio || !fim || fim < inicio || !idEquipe || !this.motivo().trim()) return;
    this.gravar(this.api.reprogramar(p.id, {
      idEquipe, inicio: formatarDataParaApi(inicio)!, fim: formatarDataParaApi(fim)!, observacao: this.motivo().trim(), versao: p.versao
    }), 'Programação reprogramada.');
  }

  private salvarCancelamento(p: ProgramacaoDetalhe): void {
    if (!this.motivo().trim()) return;
    this.gravar(this.api.cancelar(p.id, this.motivo().trim(), p.versao), 'Programação cancelada.');
  }

  private gravar(comando: ReturnType<ObrasProgramacaoService['buscar']>, mensagem: string): void {
    this.salvando.set(true);
    comando.pipe(take(1)).subscribe({
      next: (atualizada) => {
        this.salvando.set(false);
        this.p.set(atualizada);
        this.modo.set('ver');
        this.notificacoes.success(mensagem);
        this.carregarEventos(atualizada.id);
        this.alterada.emit();
      },
      error: (e) => this.tratarErro(e)
    });
  }

  private tratarErro(e: { status?: number }): void {
    this.salvando.set(false);
    if (e?.status === 409 && this.p()) {
      this.notificacoes.warning('Outra pessoa alterou esta programação. Atualizei a tela: confira e tente de novo.');
      this.carregar(this.p()!.id);
      return;
    }
    this.notificacoes.error(getErrorMessage(e));
  }

  protected async excluir(): Promise<void> {
    const p = this.p();
    if (!p) return;
    const ok = await this.confirmacao.confirm({
      title: `Excluir a programação #${p.numero}?`,
      message: 'Os itens voltam a pendentes e a exclusão fica no histórico.',
      variant: 'destructive'
    });
    if (!ok) return;
    this.api.excluir(p.id, null, p.versao).pipe(take(1)).subscribe({
      next: () => {
        this.notificacoes.success(`Programação #${p.numero} excluída.`);
        this.aberto.set(false);
        this.alterada.emit();
      },
      error: (e) => this.tratarErro(e)
    });
  }

  protected async retirar(item: ItemProgramado): Promise<void> {
    const p = this.p();
    if (!p) return;
    const ultimo = p.itens.length === 1;
    const ok = await this.confirmacao.confirm({
      title: 'Retirar o item da programação?',
      message: ultimo
        ? `${item.descricao} volta a pendente. É o último item, então a programação #${p.numero} será excluída.`
        : `${item.descricao} volta a pendente. Sem serviço do kit na programação, os materiais dele saem junto.`,
      variant: 'destructive'
    });
    if (!ok) return;
    this.api.retirarItem(p.id, item.idItem).pipe(take(1)).subscribe({
      next: (atualizada) => {
        this.notificacoes.success('Item retirado da programação.');
        if (atualizada) {
          this.p.set(atualizada);
          this.carregarEventos(atualizada.id);
        } else {
          this.aberto.set(false);
        }
        this.alterada.emit();
      },
      error: (e) => this.tratarErro(e)
    });
  }
}
