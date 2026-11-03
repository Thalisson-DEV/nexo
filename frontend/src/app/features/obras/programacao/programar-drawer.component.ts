import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  CesButtonComponent,
  CesCalloutComponent,
  CesDatepickerComponent,
  CesFormDrawerComponent,
  CesFormSectionComponent,
  CesTextInputComponent,
  NotificationService
} from '@ces/ui';
import { Subject, catchError, of, switchMap, tap } from 'rxjs';
import { getErrorMessage } from '../../../core/models/shared.models';
import { PreviaProgramacao, ResultadoProgramacao, SituacaoIntervencao, TurnoProgramacao } from '../../../core/models/obras-programacao.models';
import { ObrasProgramacaoService } from '../../../core/services/obras-programacao.service';
import { formatarDataParaApi, formatarMoeda, parseDataBrParaDate } from '../obras-ui';
import { CondicoesProgramacaoComponent } from './condicoes-programacao.component';
import { EquipesProjecaoComponent } from './equipes-projecao.component';
import { proximoDiaDeSemana } from './programacao.logic';

/** Programa os itens selecionados no planejamento: período, equipe com projeção e condições da intervenção. */
@Component({
  selector: 'app-programar-drawer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesButtonComponent,
    CesCalloutComponent,
    CesDatepickerComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesTextInputComponent,
    CondicoesProgramacaoComponent,
    EquipesProjecaoComponent
  ],
  template: `
    <ces-form-drawer [(visible)]="aberto" title="Programar" [description]="descricaoDrawer()" [dirty]="alterado()" size="lg">
      <ces-form-section title="O que será programado" icon="pi pi-list-check">
        <ces-text-input label="Descrição do planejamento" [required]="true" [maxlength]="500" [(value)]="descricao"
          [error]="tentou() && !descricao().trim() ? 'Informe a descrição do planejamento.' : undefined"></ces-text-input>
        @if (erroPrevia(); as mensagem) {
          <ces-callout tone="danger" [dense]="true">{{ mensagem }}</ces-callout>
        }
        @if (previa(); as p) {
          <p class="programar__resumo">
            <strong>{{ p.itens }} {{ p.itens === 1 ? 'item' : 'itens' }}</strong>:
            {{ p.servicos }} {{ p.servicos === 1 ? 'serviço' : 'serviços' }} e {{ p.materiais }} {{ p.materiais === 1 ? 'material' : 'materiais' }}, {{ moeda(p.valor) }} pelo valor vigente no início.
          </p>
          @if (p.ignoradosProgramados || p.ignoradosConcluidos) {
            <ces-callout tone="warning" [dense]="true">
              {{ foraDaProgramacao(p) }}
            </ces-callout>
          }
          @if (p.materiaisIncluidos) {
            <ces-callout tone="info" [dense]="true">{{ p.materiaisIncluidos }} {{ p.materiaisIncluidos === 1 ? 'material acompanha' : 'materiais acompanham' }} os serviços selecionados.</ces-callout>
          }
          @if (p.servicosSemValor) {
            <ces-callout tone="warning" [dense]="true">Sem valor vigente no início, fora da soma: {{ p.nomesServicosSemValor.join(', ') }}. Confira as Ações do contrato.</ces-callout>
          }
          @if (!p.itens) {
            <ces-callout tone="danger" [dense]="true">Nenhum item pendente nesta seleção.</ces-callout>
          }
        }
      </ces-form-section>

      <ces-form-section title="Período" icon="pi pi-calendar">
        <div class="ces-form-grid">
          <ces-datepicker label="Início" [required]="true" [value]="inicio()" (valueChange)="definirInicio($any($event))" [error]="erroPeriodo('inicio')"></ces-datepicker>
          <ces-datepicker label="Fim" [required]="true" [value]="fim()" (valueChange)="definirFim($any($event))" [minDate]="inicio()" [error]="erroPeriodo('fim')"></ces-datepicker>
        </div>
        @if (semDiaUtil()) {
          <ces-callout tone="warning" [dense]="true">Este período não tem dia útil: só fim de semana ou feriado.</ces-callout>
        }
        @if (passaDoPrazo(); as aviso) {
          <ces-callout tone="warning" [dense]="true">{{ aviso }}</ces-callout>
        }
      </ces-form-section>

      <ces-form-section title="Condições da intervenção" icon="pi pi-bolt" description="Linha viva e desligamento precisam de resposta a cada programação.">
        <app-condicoes-programacao [validar]="tentou()" [(linhaViva)]="linhaViva" [(desligamento)]="desligamento"
          [(finalizaObra)]="finalizaObra" [(turno)]="turno" [(situacaoIntervencao)]="situacaoIntervencao" [(observacao)]="observacao"></app-condicoes-programacao>
      </ces-form-section>

      <ces-form-section title="Equipe" icon="pi pi-users">
        <app-equipes-projecao [equipes]="previa()?.equipes ?? []" [carregando]="carregando()" [turno]="turno()" [(selecionada)]="idEquipe"></app-equipes-projecao>
        @if (tentou() && !idEquipe()) {
          <ces-callout tone="danger" [dense]="true">Escolha a equipe.</ces-callout>
        }
      </ces-form-section>

      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="aberto.set(false)"></ces-button>
        <ces-button icon="pi pi-calendar-plus" label="Programar" [loading]="salvando()" [disabled]="!previa()?.itens" (pressed)="confirmar()"></ces-button>
      </div>
    </ces-form-drawer>
  `,
  styles: [`.programar__resumo { margin: 0; font-size: 0.875rem; }`]
})
export class ProgramarDrawerComponent {
  private readonly api = inject(ObrasProgramacaoService);
  private readonly notificacoes = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly aberto = model(false);
  readonly idObra = input.required<string>();
  readonly idsItens = input.required<readonly string[]>();
  /** Texto inicial da descrição, montado com o que foi selecionado. */
  readonly sugestaoDescricao = input('');
  readonly programado = output<ResultadoProgramacao>();

  protected readonly inicio = signal<Date | null>(null);
  protected readonly fim = signal<Date | null>(null);
  protected readonly idEquipe = signal<string | null>(null);
  protected readonly descricao = signal('');
  protected readonly linhaViva = signal<boolean | null>(null);
  protected readonly desligamento = signal<boolean | null>(null);
  protected readonly finalizaObra = signal<boolean | null>(null);
  protected readonly turno = signal<TurnoProgramacao | null>(null);
  protected readonly situacaoIntervencao = signal<SituacaoIntervencao | null>(null);
  protected readonly observacao = signal('');

  protected readonly previa = signal<PreviaProgramacao | null>(null);
  protected readonly erroPrevia = signal<string | null>(null);
  protected readonly carregando = signal(false);
  protected readonly salvando = signal(false);
  protected readonly tentou = signal(false);

  /** A data ainda é a sugerida pela tela, não a escolhida pelo usuário. */
  private dataSugerida = false;
  private readonly pedidos = new Subject<{ idsItens: string[]; inicio: string; fim: string }>();

  protected readonly periodoValido = computed(() => {
    const inicio = this.inicio();
    const fim = this.fim();
    return !!inicio && !!fim && fim >= inicio;
  });
  protected readonly alterado = computed(() => !!(this.idEquipe() || this.linhaViva() !== null || this.desligamento() !== null));
  protected readonly descricaoDrawer = computed(() => {
    const total = this.idsItens().length;
    return `${total} ${total === 1 ? 'item selecionado' : 'itens selecionados'} no planejamento`;
  });
  protected readonly moeda = formatarMoeda;
  protected readonly semDiaUtil = computed(() => (this.previa()?.equipes[0]?.diasUteis ?? 1) === 0);
  protected readonly passaDoPrazo = computed(() => {
    const previa = this.previa();
    const fim = this.fim();
    if (!previa || !fim) return null;
    const regulado = parseDataBrParaDate(previa.prazoRegulado);
    if (regulado && fim > regulado) return `Termina depois do prazo regulado da obra (${previa.prazoRegulado}).`;
    const previsto = parseDataBrParaDate(previa.prazoPrevisto);
    return previsto && fim > previsto ? `Termina depois da previsão de conclusão da obra (${previa.prazoPrevisto}).` : null;
  });

  constructor() {
    this.pedidos
      .pipe(
        tap(() => {
          this.carregando.set(true);
          this.erroPrevia.set(null);
        }),
        switchMap((pedido) =>
          this.api.previa(this.idObra(), pedido).pipe(
            catchError((e) => {
              this.erroPrevia.set(getErrorMessage(e));
              return of(null);
            })
          )
        ),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((previa) => {
        this.carregando.set(false);
        this.previa.set(previa);
        this.pularFeriadoDaSugestao(previa);
      });

    effect(() => {
      if (this.aberto()) this.limpar();
    });
    effect(() => {
      if (!this.aberto() || !this.periodoValido()) return;
      this.pedidos.next({ idsItens: [...this.idsItens()], inicio: formatarDataParaApi(this.inicio())!, fim: formatarDataParaApi(this.fim())! });
    });
  }

  private limpar(): void {
    const dia = proximoDiaDeSemana(new Date());
    this.inicio.set(dia);
    this.fim.set(dia);
    this.dataSugerida = true;
    this.idEquipe.set(null);
    this.descricao.set(untracked(this.sugestaoDescricao));
    this.linhaViva.set(null);
    this.desligamento.set(null);
    this.finalizaObra.set(false);
    this.turno.set('DIA_TODO');
    this.situacaoIntervencao.set(null);
    this.observacao.set('');
    this.tentou.set(false);
    this.previa.set(null);
  }

  /** A tela não conhece os feriados, o servidor sim: se o dia sugerido caiu num, passa para o seguinte. */
  private pularFeriadoDaSugestao(previa: PreviaProgramacao | null): void {
    const inicio = this.inicio();
    if (!this.dataSugerida || !inicio || !previa?.equipes.length || previa.equipes[0].diasUteis > 0) return;
    const seguinte = proximoDiaDeSemana(new Date(inicio.getFullYear(), inicio.getMonth(), inicio.getDate() + 1));
    this.inicio.set(seguinte);
    this.fim.set(seguinte);
  }

  /** O fim acompanha o início para não sobrar período invertido a cada troca de data. */
  protected definirInicio(data: Date | null): void {
    this.dataSugerida = false;
    this.inicio.set(data);
    const fim = this.fim();
    if (data && (!fim || fim < data)) this.fim.set(data);
  }

  protected definirFim(data: Date | null): void {
    this.dataSugerida = false;
    this.fim.set(data);
  }

  protected foraDaProgramacao(p: PreviaProgramacao): string {
    const partes: string[] = [];
    if (p.ignoradosProgramados) partes.push(`${p.ignoradosProgramados} já ${p.ignoradosProgramados === 1 ? 'programado' : 'programados'}`);
    if (p.ignoradosConcluidos) partes.push(`${p.ignoradosConcluidos} já ${p.ignoradosConcluidos === 1 ? 'concluído' : 'concluídos'}`);
    return `Ficam de fora da programação: ${partes.join(' e ')}.`;
  }

  protected erroPeriodo(campo: 'inicio' | 'fim'): string | undefined {
    if (campo === 'inicio') return this.tentou() && !this.inicio() ? 'Informe o início.' : undefined;
    if (!this.fim()) return this.tentou() ? 'Informe o fim.' : undefined;
    return this.inicio() && this.fim()! < this.inicio()! ? 'O fim não pode ser antes do início.' : undefined;
  }

  protected confirmar(): void {
    this.tentou.set(true);
    const linhaViva = this.linhaViva();
    const desligamento = this.desligamento();
    const finalizaObra = this.finalizaObra();
    const turno = this.turno();
    const situacao = this.situacaoIntervencao();
    const idEquipe = this.idEquipe();
    if (!this.periodoValido() || !idEquipe || !this.descricao().trim() || linhaViva === null || desligamento === null
      || finalizaObra === null || !turno || !situacao) {
      return;
    }
    this.salvando.set(true);
    this.api.programar(this.idObra(), {
      idsItens: [...this.idsItens()],
      idEquipe,
      inicio: formatarDataParaApi(this.inicio())!,
      fim: formatarDataParaApi(this.fim())!,
      descricao: this.descricao().trim(),
      linhaViva,
      desligamento,
      finalizaObra,
      turno,
      situacaoIntervencao: situacao,
      observacao: this.observacao().trim() || null
    }).subscribe({
      next: (resultado) => {
        this.salvando.set(false);
        this.aberto.set(false);
        const ignorados = resultado.ignoradosProgramados + resultado.ignoradosConcluidos;
        this.notificacoes.success(`Programação #${resultado.programacao.numero} criada para ${resultado.programacao.codigoEquipe}.`
          + (ignorados ? ` ${ignorados} ${ignorados === 1 ? 'item ficou' : 'itens ficaram'} de fora.` : ''));
        this.programado.emit(resultado);
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }
}
