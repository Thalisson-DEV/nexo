import { rotuloNota } from '../../core/campo/campo-regras';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { TokenService } from '../../core/auth/token.service';
import { TurnoService } from '../../core/turno/turno.service';
import { CampoResumoService } from '../../core/campo/campo-resumo.service';
import { DbService } from '../../core/db/db.service';
import { db } from '../../core/db/app-db';
import { ExecucaoCampoLocal } from '../../core/db/models';
import { FilaEnvioService } from '../../core/sync/fila-envio.service';
import { SyncService } from '../../core/sync/sync.service';
import { ConnectivityService } from '../../core/sync/connectivity.service';
import { ToastService } from '../../core/ui/toast.service';
import { RascunhoStore, ResumoRascunho } from '../../core/sessao/rascunho.store';
import { SessaoService } from '../../core/sessao/sessao.service';
import { estadoEnvioExecucao } from '../../core/campo/estado-envio';
import { EXECUCAO_LANCAR_ENDPOINT } from '../../core/campo/execucoes.service';
import {
  EstadoEnvio, FolhaComponent, IconeComponent, IndicadorRedeComponent, StatusEnvioComponent, TituloGrandeComponent, VazioComponent,
} from '../../ui';
import { FolhaEnviosComponent } from './folha-envios.component';
import { catchError, forkJoin, map, of } from 'rxjs';
import { AlmoxService } from '../../core/almox/almox.service';
import { FiltroSolicitacoes, StatusSolicitacao } from '../../core/almox/almox.models';
import { isoCivil } from '../../core/almox/almox-status';

interface LancamentoHoje {
  id: string;
  nota: string;
  servicos: string;
  contagem: string;
  hora: string;
  estado: EstadoEnvio;
}

const DIAS = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'];
const MESES = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];

/**
 * Tela inicial. Responde três perguntas: meu turno está aberto, o que falta
 * enviar, o que eu estava fazendo. A ação principal fica no polegar.
 */
@Component({
  selector: 'app-hoje',
  standalone: true,
  imports: [
    CurrencyPipe, RouterLink, TituloGrandeComponent, IndicadorRedeComponent, IconeComponent, StatusEnvioComponent,
    VazioComponent, FolhaComponent, FolhaEnviosComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="tela">
      <ces-titulo-grande titulo="Hoje" [subtitulo]="subtitulo()">
        <ces-indicador-rede (tocar)="abrirEnvios()" />
      </ces-titulo-grande>

      @if (rascunho(); as r) {
        <section class="retomar" aria-label="Trabalho não terminado">
          <span class="ces-selo ces-selo--destaque"><ces-icone nome="retomar" /></span>
          <div class="txt">
            <strong>{{ r.antigo ? 'Rascunho antigo' : 'Lançamento pela metade' }}</strong>
            <span class="ces-num">{{ r.titulo ?? 'Continue de onde parou' }}</span>
          </div>
          <div class="retomar__acoes">
            <button type="button" class="ces-btn ces-btn--principal ces-btn--compacto" (click)="continuar(r)">Continuar</button>
            <button type="button" class="ces-btn ces-btn--texto ces-btn--compacto" (click)="confirmarDescarte.set(true)">Descartar</button>
          </div>
        </section>
      }

      @if (auth.isCampo()) {
        <h2 class="ces-secao">Turno</h2>
        <div class="ces-grupo">
          @if (turno.turnoAtivo(); as t) {
            <div class="ces-linha">
              <span class="ces-selo ces-selo--ok"><ces-icone nome="check" /></span>
              <div class="ces-linha__texto">
                <span class="ces-linha__titulo">Turno aberto</span>
                <span class="ces-linha__apoio ces-num">desde {{ hora(t.dataInicio) }}{{ pessoas() }}</span>
              </div>
              <button type="button" class="ces-btn ces-btn--texto ces-btn--compacto encerrar" (click)="confirmarEncerrar.set(true)">Encerrar</button>
            </div>
          } @else {
            <div class="ces-linha">
              <span class="ces-selo"><ces-icone nome="relogio" /></span>
              <div class="ces-linha__texto">
                <span class="ces-linha__titulo">Turno fechado</span>
                <span class="ces-linha__apoio">Abra para lançar execuções</span>
              </div>
            </div>
            @if (turno.encerramentoAutomatico(); as e) {
              <div class="ces-linha" role="status">
                <span class="ces-selo ces-selo--atencao"><ces-icone nome="alerta" /></span>
                <div class="ces-linha__texto">
                  <span class="ces-linha__titulo">Turno encerrado pelo sistema</span>
                  <span class="ces-linha__apoio">O turno aberto em {{ diaHora(e.dataInicio) }} ficou aberto além do horário e foi encerrado automaticamente. Encerre o turno ao fim do dia.</span>
                </div>
              </div>
            }
          }
          @if (progresso(); as p) {
            <div class="meta">
              <span class="meta__rotulo">Valor dos serviços feitos hoje</span>
              <div class="meta__linha">
                <span class="meta__valor ces-num">{{ p.executado | currency:'BRL':'symbol':'1.2-2':'pt-BR' }}</span>
                @if (p.meta) {
                  <span class="meta__de ces-num">meta do dia {{ p.meta | currency:'BRL':'symbol':'1.2-2':'pt-BR' }}</span>
                }
              </div>
              @if (p.meta) {
                <div class="barra" role="progressbar" aria-label="Meta do dia" [attr.aria-valuenow]="p.percentual" aria-valuemin="0" aria-valuemax="100">
                  <i [style.width.%]="p.percentual ?? 0" [class.batida]="p.atingida"></i>
                </div>
                <span class="meta__falta ces-num">
                  @if (p.atingida) { <ces-icone nome="check" [tamanho]="16" /> Meta batida }
                  @else { Faltam {{ p.falta | currency:'BRL':'symbol':'1.2-2':'pt-BR' }} }
                </span>
              }
            </div>
          }
          @if (ciclo(); as c) {
            <div class="meta" data-testid="meta-ciclo">
              <span class="meta__rotulo">{{ c.titulo }} · {{ dataCurta(c.inicio) }} a {{ dataCurta(c.fim) }}</span>
              <div class="meta__linha">
                <span class="meta__valor ces-num">{{ c.realizado | currency:'BRL':'symbol':'1.2-2':'pt-BR' }}</span>
                <span class="meta__de ces-num">meta {{ c.meta | currency:'BRL':'symbol':'1.2-2':'pt-BR' }}</span>
              </div>
              <div class="barra" role="progressbar" [attr.aria-label]="c.titulo" [attr.aria-valuenow]="c.percentual" aria-valuemin="0" aria-valuemax="100">
                <i [style.width.%]="c.percentual" [class.batida]="c.situacao === 'batida'" [class.abaixo]="c.situacao === 'abaixo'"></i>
              </div>
              @switch (c.situacao) {
                @case ('batida') {
                  <span class="meta__falta meta__falta--ok"><ces-icone nome="check" [tamanho]="16" /> Meta do ciclo batida ({{ c.percentual }}%)</span>
                }
                @case ('desatualizada') {
                  <span class="meta__falta"><ces-icone nome="atualizar" [tamanho]="16" /> O ciclo terminou em {{ dataCurta(c.fim) }}. Sincronize para ver o novo.</span>
                }
                @default {
                  <span class="meta__falta ces-num" [class.meta__falta--atencao]="c.situacao === 'abaixo'">
                    <ces-icone [nome]="c.situacao === 'abaixo' ? 'alerta' : 'check'" [tamanho]="16" />
                    {{ c.situacao === 'abaixo' ? 'Abaixo do ritmo' : 'No ritmo' }} · {{ c.percentual }}% da meta
                  </span>
                  <span class="meta__falta ces-num">
                    Faltam {{ c.falta | currency:'BRL':'symbol':'1.2-2':'pt-BR' }}
                    @if (c.ritmoNecessario !== null) {
                      · {{ c.ritmoNecessario | currency:'BRL':'symbol':'1.2-2':'pt-BR' }} por dia útil ({{ c.diasUteisRestantes }} {{ c.diasUteisRestantes === 1 ? 'dia' : 'dias' }})
                    }
                  </span>
                }
              }
              @if (c.pendenteEnvio > 0) {
                <span class="meta__falta ces-num"><ces-icone nome="nuvem" [tamanho]="16" /> Inclui {{ c.pendenteEnvio | currency:'BRL':'symbol':'1.2-2':'pt-BR' }} aguardando envio</span>
              }
            </div>
          }
        </div>
      }

      @if (auth.isCampo() || !auth.podeAcessarAlmoxarifado() || fila.total() > 0) {
      <h2 class="ces-secao">
        <span>{{ auth.isCampo() ? 'Lançamentos de hoje' : 'Acompanhar' }}</span>
        @if (auth.isCampo() && lancamentos().length) { <span class="ces-num">{{ lancamentos().length }}</span> }
      </h2>
      <ul class="ces-grupo">
        @if (fila.total() > 0) {
          <li>
            <button type="button" class="ces-linha ces-linha--acao" (click)="abrirEnvios()">
              <span class="ces-selo" [class.ces-selo--destaque]="!fila.comProblema()" [class.ces-selo--atencao]="fila.comProblema() > 0">
                <ces-icone [nome]="fila.comProblema() ? 'alerta' : 'nuvem'" />
              </span>
              <span class="ces-linha__texto">
                <span class="ces-linha__titulo ces-num">{{ fila.total() }} {{ fila.total() === 1 ? 'aguardando envio' : 'aguardando envio' }}</span>
                <span class="ces-linha__apoio">
                  @if (fila.comProblema()) { {{ fila.comProblema() }} {{ fila.comProblema() === 1 ? 'precisa' : 'precisam' }} de atenção }
                  @else if (!rede.isOnline()) { Vão sozinhos quando o sinal voltar }
                  @else { Enviando pela ordem de lançamento }
                </span>
              </span>
              <ces-icone nome="avancar" [tamanho]="18" />
            </button>
          </li>
        }
        @if (auth.isCampo()) {
          @for (l of lancamentos(); track l.id) {
            <li [attr.data-ancora]="l.id">
              <a class="ces-linha ces-linha--acao" [routerLink]="['/campo', l.id]">
                <span class="ces-linha__texto">
                  <span class="ces-linha__titulo ces-num">{{ l.nota }}</span>
                  @if (l.servicos) { <span class="ces-linha__resumo">{{ l.servicos }}</span> }
                  <span class="ces-linha__apoio">{{ l.contagem }} · {{ l.hora }}</span>
                </span>
                <ces-status-envio [estado]="l.estado" />
              </a>
            </li>
          }
        } @else if (!auth.podeAcessarAlmoxarifado()) {
          <li><a class="ces-linha ces-linha--acao" routerLink="/inspecoes"><span class="ces-selo"><ces-icone nome="inspecao" /></span>
            <span class="ces-linha__texto"><span class="ces-linha__titulo">Inspeções</span><span class="ces-linha__apoio">Registradas e em rascunho</span></span>
            <ces-icone nome="avancar" [tamanho]="18" /></a></li>
          <li><a class="ces-linha ces-linha--acao" routerLink="/desvios"><span class="ces-selo"><ces-icone nome="desvio" /></span>
            <span class="ces-linha__texto"><span class="ces-linha__titulo">Desvios</span><span class="ces-linha__apoio">Pendentes de tratamento</span></span>
            <ces-icone nome="avancar" [tamanho]="18" /></a></li>
        }
      </ul>
      }
      @if (!auth.isCampo() && auth.podeAcessarAlmoxarifado()) {
        @if (auth.podeVerSolicitacoesAlmox()) {
          <h2 class="ces-secao"><span>Almoxarifado</span>
            @if (!rede.isOnline()) { <span>precisa de internet</span> }</h2>
          <ul class="ces-grupo" aria-label="Solicitações comerciais">
            <li><button type="button" class="ces-linha ces-linha--acao" (click)="abrirSolicitacoes(['PENDENTE'])">
              <span class="ces-selo" [class.ces-selo--atencao]="(almox().aprovar ?? 0) > 0"><ces-icone nome="relogio" /></span>
              <span class="ces-linha__texto"><span class="ces-linha__titulo">Aguardando aprovação</span><span class="ces-linha__apoio">Solicitações comerciais para decidir</span></span>
              <span class="ces-qtd">{{ almox().aprovar ?? '–' }}</span></button></li>
            <li><button type="button" class="ces-linha ces-linha--acao" (click)="abrirSolicitacoes(['APROVADO', 'ENTREGUE_PARCIAL'])">
              <span class="ces-selo" [class.ces-selo--destaque]="(almox().entregar ?? 0) > 0"><ces-icone nome="caminhao" /></span>
              <span class="ces-linha__texto"><span class="ces-linha__titulo">Aguardando retirada</span>
                <span class="ces-linha__apoio">
                  @if (almox().atrasadas) { <span class="atraso">{{ almox().atrasadas }} com saque atrasado</span> }
                  @else if (almox().hoje) { {{ almox().hoje }} com saque hoje }
                  @else { Aprovadas e entregues em parte }
                </span></span>
              <span class="ces-qtd">{{ almox().entregar ?? '–' }}</span></button></li>
          </ul>
        }
        @if (auth.podeMovimentarEstoque() || auth.podeAcessarInspecoes()) {
          <div class="atalhos" role="group" aria-label="Atalhos">
            @if (auth.podeMovimentarEstoque()) {
              <a class="atalho" routerLink="/almox/movimentacoes/nova/entrada"><span class="ces-selo ces-selo--ok"><ces-icone nome="entrada" /></span>Entrada</a>
              <a class="atalho" routerLink="/almox/movimentacoes/nova/saida"><span class="ces-selo ces-selo--erro"><ces-icone nome="saida" /></span>Saída</a>
              <a class="atalho" routerLink="/almox/movimentacoes/nova/transferencia"><span class="ces-selo ces-selo--destaque"><ces-icone nome="transferencia" /></span>Transferir</a>
            }
            @if (auth.podeAcessarInspecoes()) {
              <a class="atalho" routerLink="/inspecoes/nova"><span class="ces-selo"><ces-icone nome="inspecao" /></span>Inspecionar</a>
            }
          </div>
        }
        <h2 class="ces-secao">Segurança</h2>
        <ul class="ces-grupo">
          <li><a class="ces-linha ces-linha--acao" routerLink="/inspecoes"><span class="ces-selo"><ces-icone nome="inspecao" /></span>
            <span class="ces-linha__texto"><span class="ces-linha__titulo">Inspeções</span><span class="ces-linha__apoio">Registradas e em rascunho</span></span>
            <ces-icone nome="avancar" [tamanho]="18" /></a></li>
          <li><a class="ces-linha ces-linha--acao" routerLink="/desvios"><span class="ces-selo"><ces-icone nome="desvio" /></span>
            <span class="ces-linha__texto"><span class="ces-linha__titulo">Desvios</span><span class="ces-linha__apoio">Pendentes de tratamento</span></span>
            <ces-icone nome="avancar" [tamanho]="18" /></a></li>
        </ul>
      }
      @if (auth.isCampo() && carregado() && !lancamentos().length && fila.total() === 0) {
        <ces-vazio icone="servicos" titulo="Nenhuma execução hoje"
          [texto]="turno.turnoAtivo() ? 'Toque em Nova execução para lançar a primeira.' : 'Abra o turno para começar.'" />
      }
      <div class="respiro"></div>
    </div>

    @if (auth.isCampo() || !auth.podeAcessarAlmoxarifado()) {
    <div class="doca">
      @if (!auth.isCampo()) {
        <a class="ces-btn ces-btn--principal ces-btn--bloco" routerLink="/inspecoes/nova"><ces-icone nome="mais" />Nova inspeção</a>
      } @else if (turno.turnoAtivo()) {
        <a class="ces-btn ces-btn--principal ces-btn--bloco" routerLink="/campo/nova"><ces-icone nome="mais" />Nova execução</a>
      } @else {
        <a class="ces-btn ces-btn--principal ces-btn--bloco" routerLink="/turno/abrir"><ces-icone nome="relogio" />Abrir turno</a>
      }
    </div>
    }

    <app-folha-envios [aberta]="folhaEnvios()" (fechar)="fecharEnvios()" />

    <ces-folha [aberta]="confirmarDescarte()" (fechar)="confirmarDescarte.set(false)" titulo="Descartar o que não terminou?"
      descricao="O que foi preenchido e as fotos tiradas nele saem do aparelho. Não dá para desfazer.">
      <div rodape class="ces-folha-rodape">
        <button type="button" class="ces-btn ces-btn--neutro" (click)="confirmarDescarte.set(false)">Manter</button>
        <button type="button" class="ces-btn ces-btn--perigo" (click)="descartarRascunho()">Descartar</button>
      </div>
    </ces-folha>

    <ces-folha [aberta]="confirmarEncerrar()" (fechar)="confirmarEncerrar.set(false)" titulo="Encerrar o turno?"
      [descricao]="rede.isOnline() ? 'A equipe fica sem turno aberto até a próxima abertura.' : 'Encerrar precisa de internet. Tente quando o sinal voltar.'">
      <div rodape class="acoes-folha">
        <button type="button" class="ces-btn ces-btn--neutro" (click)="confirmarEncerrar.set(false)">Voltar</button>
        <button type="button" class="ces-btn ces-btn--perigo" (click)="encerrar()" [disabled]="!rede.isOnline() || encerrando()">
          {{ encerrando() ? 'Encerrando…' : 'Encerrar turno' }}
        </button>
      </div>
    </ces-folha>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; min-height: 100%; }
    /* Nada encolhe: com conteúdo maior que a tela, a página rola (grupos com overflow:hidden eram cortados). */
    :host > * { flex-shrink: 0; }
    .tela { flex: 1; }
    .retomar { margin: var(--ces-e4) var(--ces-e3) 0; padding: var(--ces-e3) var(--ces-e4); border-radius: var(--ces-r-grupo);
      background: var(--ces-destaque-suave); display: flex; align-items: center; gap: var(--ces-e3); }
    .retomar .txt { flex: 1; min-width: 0; display: grid; }
    .retomar__acoes { display: grid; justify-items: center; gap: 2px; flex: none; }
    .retomar strong { font: var(--ces-t-corpo-forte); }
    .retomar span { font: var(--ces-t-apoio); color: var(--ces-texto-2); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .encerrar { color: var(--ces-texto-2); }
    .meta { display: grid; gap: var(--ces-e2); padding: var(--ces-e4); border-top: 1px solid var(--ces-linha); }
    .meta__linha { display: flex; justify-content: space-between; align-items: baseline; gap: var(--ces-e2); flex-wrap: wrap; }
    .meta__valor { font: var(--ces-t-numero); letter-spacing: -.01em; }
    .meta__rotulo { font: var(--ces-t-apoio); color: var(--ces-texto-2); margin-bottom: -6px; }
    .meta__de, .meta__falta { font: var(--ces-t-apoio); color: var(--ces-texto-2); display: inline-flex; align-items: center; gap: 4px; }
    .barra { height: 6px; border-radius: var(--ces-r-pilula); background: var(--ces-linha); overflow: hidden; }
    .barra i { display: block; height: 100%; border-radius: inherit; background: var(--ces-destaque); transition: width var(--ces-m-folha) var(--ces-curva); }
    .barra i.batida { background: var(--ces-ok); }
    .barra i.abaixo { background: var(--ces-atencao); }
    .meta__falta--ok { color: var(--ces-ok); }
    .meta__falta--atencao { color: var(--ces-texto); }
    .meta__falta--atencao ces-icone { color: var(--ces-atencao); }
    .respiro { height: var(--ces-e6); }
    .doca { position: sticky; bottom: 0; padding: var(--ces-e3) var(--ces-e4); background: linear-gradient(to top, var(--ces-fundo) 72%, transparent); }
    .atalhos { display: grid; grid-template-columns: repeat(auto-fit, minmax(72px, 1fr)); gap: var(--ces-e2); margin: var(--ces-e3) var(--ces-e3) 0; }
    .atalho { display: grid; justify-items: center; gap: 4px; padding: var(--ces-e2) var(--ces-e1) 10px; border-radius: var(--ces-r-grupo); background: var(--ces-superficie);
      color: var(--ces-texto); text-decoration: none; font: 500 .75rem/1.2 var(--ces-fonte); text-align: center; }
    .atalho .ces-selo { width: 34px; height: 34px; border-radius: 10px; }
    .atalho:active { transform: scale(.97); background: var(--ces-superficie-2); }
    .atraso { color: var(--ces-erro); font-weight: 600; }
    .acoes-folha { display: flex; gap: var(--ces-e3); padding: var(--ces-e3) var(--ces-e4) 0; }
    .acoes-folha .ces-btn { flex: 1; }
  `],
})
export class HojeComponent implements OnInit {
  readonly auth = inject(AuthService);
  readonly turno = inject(TurnoService);
  readonly fila = inject(FilaEnvioService);
  readonly rede = inject(ConnectivityService);
  private readonly tokens = inject(TokenService);
  private readonly resumo = inject(CampoResumoService);
  private readonly dbService = inject(DbService);
  private readonly sync = inject(SyncService);
  private readonly rascunhos = inject(RascunhoStore);
  private readonly sessao = inject(SessaoService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly almoxService = inject(AlmoxService);

  /** Fila do almoxarifado para o administrativo: o que aprovar e o que entregar. */
  readonly almox = signal<{ aprovar?: number; entregar?: number; atrasadas?: number; hoje?: number }>({});

  readonly progresso = this.resumo.progresso;
  readonly ciclo = this.resumo.ciclo;
  readonly lancamentos = signal<LancamentoHoje[]>([]);
  readonly rascunho = signal<ResumoRascunho | null>(null);
  readonly carregado = signal(false);
  readonly equipe = signal('');
  readonly folhaEnvios = signal(!!this.sessao.estadoTela<{ folha?: string }>().folha);
  readonly confirmarEncerrar = signal(false);
  readonly confirmarDescarte = signal(false);
  readonly encerrando = signal(false);

  readonly subtitulo = computed(() => {
    const d = new Date();
    const data = `${DIAS[d.getDay()]}, ${d.getDate()} de ${MESES[d.getMonth()]}`;
    return this.equipe() ? `${data} · ${this.equipe()}` : data;
  });

  readonly pessoas = computed(() => {
    const n = this.turno.composicao()?.membros?.length ?? 0;
    return n ? ` · ${n} ${n === 1 ? 'pessoa' : 'pessoas'}` : '';
  });

  async ngOnInit(): Promise<void> {
    const dono = this.tokens.ownerId();
    const [lista] = await Promise.all([
      dono ? this.rascunhos.listar(dono) : Promise.resolve([]),
      this.turno.loadTurnoAtivo(),
      this.auth.isCampo() ? this.turno.loadComposicaoLocal() : Promise.resolve(null),
      this.auth.isCampo() ? this.resumo.carregar() : Promise.resolve(),
    ]);
    // Cópias de conflito entre abas não viram cartão: o original é o que importa.
    this.rascunho.set(lista.find(r => r.rota && !r.chave.includes('#conflito-')) ?? null);
    if (this.auth.isCampo()) {
      this.equipe.set((await this.dbService.getEquipe())?.codigo ?? '');
      await this.carregarLancamentos();
    }
    this.carregado.set(true);
    if (!this.auth.isCampo() && this.auth.podeVerSolicitacoesAlmox() && this.rede.isOnline()) this.carregarAlmox();
  }

  abrirSolicitacoes(statuses: StatusSolicitacao[]): void {
    // A lista abre já no recorte tocado (o estado de tela é lido ao entrar).
    this.sessao.definirEstadoTela({ statuses, visao: 'lista' }, '/almox/solicitacoes');
    void this.router.navigate(['/almox/solicitacoes']);
  }

  private carregarAlmox(): void {
    const hoje = new Date();
    const ontem = isoCivil(new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate() - 1));
    const total = (f: FiltroSolicitacoes) =>
      this.almoxService.listarSolicitacoes({ ...f, page: 0, size: 1 }).pipe(map(r => r.page?.totalElements ?? 0), catchError(() => of(undefined)));
    forkJoin({
      aprovar: total({ statuses: ['PENDENTE'] }),
      entregar: total({ statuses: ['APROVADO', 'ENTREGUE_PARCIAL'] }),
      atrasadas: total({ statuses: ['APROVADO', 'ENTREGUE_PARCIAL'], previstaAte: ontem }),
      hoje: total({ statuses: ['APROVADO', 'ENTREGUE_PARCIAL'], previstaDe: isoCivil(hoje), previstaAte: isoCivil(hoje) }),
    }).subscribe(r => this.almox.set(r));
  }

  /** `AAAA-MM-DD` → `dd/mm`, sem passar por `Date` (evita o deslocamento de fuso). */
  dataCurta(iso: string): string {
    const [, mes, dia] = iso.slice(0, 10).split('-');
    return `${dia}/${mes}`;
  }

  hora(iso: string): string {
    return new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  }

  diaHora(iso: string): string {
    return new Date(iso).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
  }

  async descartarRascunho(): Promise<void> {
    const r = this.rascunho();
    this.confirmarDescarte.set(false);
    if (!r) return;
    await this.rascunhos.descartar(r.chave);
    const dono = this.tokens.ownerId();
    const lista = dono ? await this.rascunhos.listar(dono) : [];
    this.rascunho.set(lista.find(x => x.rota && !x.chave.includes('#conflito-')) ?? null);
    this.toast.info('Descartado.');
  }

  continuar(r: ResumoRascunho): void {
    if (r.rota) void this.router.navigateByUrl(r.rota);
  }

  abrirEnvios(): void {
    this.folhaEnvios.set(true);
    this.sessao.definirEstadoTela({ folha: 'envios' });
  }

  fecharEnvios(): void {
    this.folhaEnvios.set(false);
    this.sessao.definirEstadoTela({ folha: null });
  }

  async encerrar(): Promise<void> {
    this.encerrando.set(true);
    try {
      await this.turno.fecharTurno();
      this.confirmarEncerrar.set(false);
      this.toast.success('Turno encerrado');
    } catch {
      this.toast.error('Não foi possível encerrar agora. Tente de novo.');
    } finally {
      this.encerrando.set(false);
    }
  }

  private async carregarLancamentos(): Promise<void> {
    const hoje = new Date().toDateString();
    const [execs, acoes, filaItens] = await Promise.all([
      this.dbService.getExecucoes(),
      this.dbService.getAcoes(),
      db.outbox.filter(i => i.endpoint === EXECUCAO_LANCAR_ENDPOINT).toArray(),
    ]);
    const nomes = new Map(acoes.map(a => [a.id, a.nome]));
    const filaPorExec = new Map(filaItens.map(i => [i.payload?.tempId as string, i]));
    const enviando = this.sync.isSyncing() || this.fila.enviandoAgora();
    this.lancamentos.set(
      execs
        // Data local do aparelho (não UTC): depois das 21 h em Fortaleza ainda é "hoje".
        .filter((e: ExecucaoCampoLocal) => new Date(e.dataLancamento).toDateString() === hoje)
        .map(e => ({
          id: e.id,
          nota: rotuloNota(e),
          servicos: e.acoes.map(id => nomes.get(id) ?? 'Serviço').join(' · '),
          contagem: `${e.tipoServico?.descricao ?? 'Tipo não informado'} · ${e.acoes.length ? e.acoes.length + (e.acoes.length === 1 ? ' serviço' : ' serviços') : 'Sem serviço'}`,
          hora: this.hora(e.dataLancamento),
          estado: estadoEnvioExecucao(e, filaPorExec.get(e.id), enviando),
        })),
    );
  }
}
