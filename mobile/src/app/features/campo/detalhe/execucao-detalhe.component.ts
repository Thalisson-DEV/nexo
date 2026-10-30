import { Location } from '@angular/common';
import { idAtual } from '../../../core/sync/ids-trocados';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { DbService } from '../../../core/db/db.service';
import { ExecucoesService } from '../../../core/campo/execucoes.service';
import { ToastService } from '../../../core/ui/toast.service';
import { OutboxService } from '../../../core/sync/outbox.service';
import { SyncService } from '../../../core/sync/sync.service';
import { SessaoService } from '../../../core/sessao/sessao.service';
import { estadoEnvioExecucao } from '../../../core/campo/estado-envio';
import { AcaoComercialLocal, ExecucaoCampoLocal, MaterialLocal, MunicipioLocal, OrigemMaterial, OutboxItem } from '../../../core/db/models';
import { PRAZO_ALTERACAO_CAMPO_DIAS, diasParaAlterar, podeAlterarNoCampo, rotuloNota } from '../../../core/campo/campo-regras';
import { TokenService } from '../../../core/auth/token.service';
import { CorrecaoRecusadaError } from '../../../core/sync/outbox.service';
import { BarraTopoComponent, FolhaComponent, IconeComponent, StatusEnvioComponent, VazioComponent } from '../../../ui';

interface MaterialLinha {
  idMaterial: string;
  descricao: string;
  codigo: string;
  unidadeMedida: string;
  quantidade: number;
  operacao: 'APLICADO' | 'RETIRADO';
  condicao: 'NOVO' | 'SUCATA';
  numSeries: string[];
  origem: OrigemMaterial;
}

/** Detalhe de uma execução: o que foi lançado, em que pé está o envio e o que dá para fazer. */
@Component({
  selector: 'app-execucao-detalhe',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, BarraTopoComponent, FolhaComponent, IconeComponent, StatusEnvioComponent, VazioComponent],
  template: `
    <ces-barra-topo [titulo]="execucao() ? rotulo(execucao()!) : 'Execução'" [subtitulo]="quando()" (voltar)="voltar()" />

    @if (carregando()) {
      <div class="esqueletos" aria-busy="true" aria-label="Carregando">
        <div class="ces-esqueleto" style="height: 72px"></div><div class="ces-esqueleto" style="height: 140px"></div>
      </div>
    } @else if (execucao(); as e) {
      <section class="estado" [class.atencao]="estado() === 'error' || estado() === 'conflict'" aria-live="polite">
        <ces-status-envio [estado]="estado()" />
        <p>{{ explicacao() }}</p>
      </section>

      @if (e.alteracaoPendente; as alt) {
        <section class="alteracao" aria-label="Alteração desta nota">
          <span class="ces-selo ces-selo--atencao"><ces-icone nome="editar" /></span>
          <div>
            <strong>{{ estadoAlteracao() === 'error' ? 'Alteração não aceita' : 'Nota alterada neste aparelho' }}</strong>
            <p>Abaixo já aparece a nota corrigida. No servidor ela continua como antes até a alteração chegar.</p>
            <p class="motivo">Motivo: {{ alt.motivo }}</p>
          </div>
        </section>
      } @else if (e.totalAlteracoes) {
        <section class="alteracao" aria-label="Nota alterada">
          <span class="ces-selo ces-selo--atencao"><ces-icone nome="editar" /></span>
          <div>
            <strong>Nota alterada{{ e.totalAlteracoes > 1 ? ' ' + e.totalAlteracoes + ' vezes' : '' }}</strong>
            <p>Não é mais como foi lançada. O histórico fica no relatório da nota, no escritório.</p>
          </div>
        </section>
      }

      @if (e.valorTotalFaturado) {
        <div class="valor"><span>Valor</span><strong class="ces-num">{{ e.valorTotalFaturado | currency:'BRL':'symbol':'1.2-2':'pt-BR' }}</strong></div>
      }

      @if (e.tipoNota === 'AVULSA') {
        <h2 class="ces-secao">Nota avulsa</h2>
        <div class="ces-grupo">
          <div class="ces-linha">
            <span class="ces-selo" [class.ces-selo--atencao]="!numeroOficialInformado(e)" [class.ces-selo--ok]="numeroOficialInformado(e)"><ces-icone nome="documento" /></span>
            <span class="ces-linha__texto">
              @if (numeroOficialInformado(e)) {
                <span class="ces-linha__titulo">Número oficial informado</span>
                <span class="ces-linha__apoio ces-num">Provisório {{ e.referenciaProvisoria }}</span>
              } @else {
                <span class="ces-linha__titulo">Aguardando número oficial</span>
                <span class="ces-linha__apoio">O escritório informa o número depois</span>
              }
            </span>
          </div>
        </div>
      }

      <h2 class="ces-secao">Tipo de serviço</h2>
      <div class="ces-grupo">
        <div class="ces-linha"><span class="ces-linha__texto">
          @if (e.tipoServico; as tipo) {
            <span class="ces-linha__titulo">{{ tipo.descricao }}</span><span class="ces-linha__apoio ces-num">Cód. {{ tipo.codigo }}</span>
          } @else {
            <span class="ces-linha__titulo">Não informado</span><span class="ces-linha__apoio">Nota lançada antes do cadastro de tipos</span>
          }
        </span></div>
      </div>

      <h2 class="ces-secao ces-secao--marcada"><span class="ces-marca ces-marca--servico" aria-hidden="true"><ces-icone nome="servicos" [tamanho]="14" /></span>
        <span>Serviços</span><span class="ces-contagem">{{ e.acoes.length }}</span></h2>
      <ul class="ces-grupo" aria-label="Serviços">
        @for (id of e.acoes; track id) {
          <li><div class="ces-linha"><span class="ces-linha__texto">
            <span class="ces-linha__titulo">{{ acao(id).nome }}</span><span class="ces-linha__apoio ces-num">Cód. {{ acao(id).codigo }}</span>
          </span></div></li>
        } @empty {
          <li><div class="ces-linha"><span class="ces-linha__apoio">Nenhum serviço marcado</span></div></li>
        }
      </ul>

      @for (g of grupos(); track g.operacao) {
        <h2 class="ces-secao ces-secao--marcada">
          <span class="ces-marca" [class.ces-marca--aplicado]="g.operacao === 'APLICADO'" [class.ces-marca--retirado]="g.operacao === 'RETIRADO'" aria-hidden="true">
            <ces-icone [nome]="g.operacao === 'APLICADO' ? 'baixar' : 'subir'" [tamanho]="14" /></span>
          <span>{{ g.titulo }}</span><span class="ces-contagem">{{ g.itens.length }}</span></h2>
        <ul class="ces-grupo" [attr.aria-label]="g.titulo">
          @for (m of g.itens; track m.idMaterial + m.condicao + m.origem) {
            <li><div class="ces-linha"><span class="ces-linha__texto">
              <span class="ces-linha__titulo">{{ m.descricao }}</span>
              <span class="ces-linha__apoio ces-num">@if (m.origem === 'CONCESSIONARIA') {<span class="ces-cond ces-cond--sucata">Concessionária</span>} @else {<span class="ces-cond" [class.ces-cond--sucata]="m.condicao !== 'NOVO'">{{ m.condicao === 'NOVO' ? 'Novo' : 'Sucata' }}</span>}
                · {{ m.numSeries.length ? 'Série ' + m.numSeries.join(', ') : 'Cód. ' + m.codigo }}</span>
            </span>
            <span class="ces-qtd">{{ m.numSeries.length || num(m.quantidade) }}<small>{{ m.unidadeMedida }}</small></span></div></li>
          } @empty {
            <li><div class="ces-linha"><span class="ces-linha__apoio">{{ g.vazio }}</span></div></li>
          }
        </ul>
      }

      <h2 class="ces-secao">Local</h2>
      <div class="ces-grupo">
        <div class="ces-linha">
          <span class="ces-selo"><ces-icone nome="local" /></span>
          <span class="ces-linha__texto">
            <span class="ces-linha__titulo ces-num">{{ coordenadas() || municipio() || 'Sem coordenadas' }}</span>
            @if (coordenadas() && municipio()) { <span class="ces-linha__apoio">{{ municipio() }}</span> }
          </span>
        </div>
      </div>
      <div class="respiro"></div>

      @if (prazoVencido()) {
        <p class="prazo">Passou o prazo de {{ prazoDias }} dias para alterar pelo app. Se algo estiver errado, peça ao escritório.</p>
      }

      @if (podeAlterar() || podeDesfazer()) {
        <footer class="acoes">
          @if (podeDesfazer()) { <button type="button" class="ces-btn ces-btn--neutro" (click)="confirmarDesfazer.set(true)">Desfazer</button> }
          @if (podeAlterar()) {
            <button type="button" class="ces-btn ces-btn--principal corrigir" (click)="alterar()">
              <ces-icone nome="editar" [tamanho]="18" />{{ e.alteracaoPendente ? 'Alterar de novo' : 'Alterar nota' }}
            </button>
          }
        </footer>
      } @else if (podeEditar() || podeDescartar() || incerto()) {
        <footer class="acoes">
          @if (incerto()) {
            <button type="button" class="ces-btn ces-btn--principal ces-btn--bloco" (click)="conferir()" [disabled]="conferindo()">
              <ces-icone nome="atualizar" [tamanho]="18" />{{ conferindo() ? 'Conferindo…' : 'Conferir no servidor' }}
            </button>
          } @else {
            @if (podeDescartar()) { <button type="button" class="ces-btn ces-btn--perigo" (click)="confirmarDescarte.set(true)">Descartar</button> }
            @if (podeEditar()) { <button type="button" class="ces-btn ces-btn--principal corrigir" (click)="corrigir()"><ces-icone nome="editar" [tamanho]="18" />Corrigir</button> }
          }
        </footer>
      }
    } @else {
      <ces-vazio icone="documento" titulo="Execução não encontrada" texto="Ela não está mais neste aparelho. Pode ter sido enviada e atualizada pelo servidor.">
        <button type="button" class="ces-btn ces-btn--tonal" (click)="voltar()">Ver serviços</button>
      </ces-vazio>
    }

    <ces-folha [aberta]="confirmarDesfazer()" (fechar)="confirmarDesfazer.set(false)" titulo="Desfazer a alteração?"
      descricao="A nota volta a ser como está no servidor e o material volta ao saldo do caminhão como estava.">
      <div rodape class="folha-rodape">
        <button type="button" class="ces-btn ces-btn--neutro" (click)="confirmarDesfazer.set(false)">Manter</button>
        <button type="button" class="ces-btn ces-btn--perigo" (click)="desfazer()">Desfazer</button>
      </div>
    </ces-folha>

    <ces-folha [aberta]="confirmarDescarte()" (fechar)="confirmarDescarte.set(false)" titulo="Descartar esta execução?"
      descricao="Ela ainda não saiu do aparelho. Descartar apaga o lançamento e devolve o material ao saldo do caminhão.">
      <div rodape class="folha-rodape">
        <button type="button" class="ces-btn ces-btn--neutro" (click)="confirmarDescarte.set(false)">Manter</button>
        <button type="button" class="ces-btn ces-btn--perigo" (click)="descartar()">Descartar</button>
      </div>
    </ces-folha>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; min-height: 100%; background: var(--ces-fundo); }
    /* Nada encolhe: com conteúdo maior que a tela, a página rola (grupos com overflow:hidden eram cortados). */
    :host > * { flex-shrink: 0; }
    .esqueletos { display: grid; gap: var(--ces-e3); padding: var(--ces-e4); }
    .estado { margin: var(--ces-e2) var(--ces-e3) 0; padding: var(--ces-e4); border-radius: var(--ces-r-grupo); background: var(--ces-superficie);
      display: grid; gap: var(--ces-e2); justify-items: start; }
    .estado.atencao { background: var(--ces-atencao-suave); }
    .estado p { margin: 0; font: var(--ces-t-corpo); color: var(--ces-texto-2); }
    .valor { margin: var(--ces-e3) var(--ces-e3) 0; padding: var(--ces-e3) var(--ces-e4); display: flex; justify-content: space-between; align-items: baseline;
      background: var(--ces-superficie); border-radius: var(--ces-r-grupo); }
    .valor span { color: var(--ces-texto-2); }
    .valor strong { font: var(--ces-t-numero); }
    .respiro { flex: 1; min-height: 96px; }
    .alteracao { margin: var(--ces-e3) var(--ces-e3) 0; padding: var(--ces-e3) var(--ces-e4); border-radius: var(--ces-r-grupo);
      background: var(--ces-atencao-suave); display: flex; gap: var(--ces-e3); align-items: flex-start; }
    .alteracao strong { font: var(--ces-t-corpo-forte); color: var(--ces-texto); }
    .alteracao p { margin: var(--ces-e1) 0 0; font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .alteracao .motivo { color: var(--ces-texto); }
    .prazo { margin: 0 var(--ces-e4) var(--ces-e3); font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .acoes { position: fixed; left: 0; right: 0; bottom: 0; z-index: 5; display: flex; gap: var(--ces-e3); padding: var(--ces-e3) var(--ces-e4) calc(var(--ces-e3) + var(--ces-safe-base));
      background: var(--ces-fundo); border-top: 1px solid var(--ces-linha); }
    .acoes .corrigir { flex: 1; min-height: var(--ces-alvo-principal); }
    .folha-rodape { display: flex; gap: var(--ces-e3); padding: var(--ces-e3) var(--ces-e4) 0; }
    .folha-rodape .ces-btn { flex: 1; }
  `],
})
export class ExecucaoDetalheComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dbService = inject(DbService);
  private readonly execucoesService = inject(ExecucoesService);
  private readonly outboxService = inject(OutboxService);
  private readonly sync = inject(SyncService);
  private readonly sessao = inject(SessaoService);
  private readonly toast = inject(ToastService);
  private readonly tokenService = inject(TokenService);

  id = this.route.snapshot.paramMap.get('id')!;
  private readonly location = inject(Location);
  readonly carregando = signal(true);
  readonly conferindo = signal(false);
  readonly confirmarDescarte = signal(false);
  readonly confirmarDesfazer = signal(false);
  readonly filaAlteracao = signal<OutboxItem | null>(null);
  readonly prazoDias = PRAZO_ALTERACAO_CAMPO_DIAS;
  readonly execucao = signal<ExecucaoCampoLocal | null>(null);
  readonly fila = signal<OutboxItem | null>(null);
  private readonly municipios = signal<MunicipioLocal[]>([]);
  private readonly acoes = signal<Map<string, AcaoComercialLocal>>(new Map());
  private readonly catalogo = signal<Map<string, MaterialLocal>>(new Map());

  readonly podeDescartar = computed(() => this.outboxService.canDiscard(this.fila()) && this.execucao()?.statusSync !== 'SINCRONIZADO');
  readonly podeEditar = computed(() => this.outboxService.canEdit(this.fila()) && this.execucao()?.statusSync !== 'SINCRONIZADO');
  readonly incerto = computed(() => this.outboxService.isResultadoIncerto(this.fila()));

  /** Nota no servidor, da equipe, dentro do prazo, sem alteração que já possa ter saído. */
  readonly podeAlterar = computed(() => {
    const e = this.execucao();
    if (!e || !podeAlterarNoCampo(e, this.tokenService.equipeId())) return false;
    return !e.alteracaoPendente || this.outboxService.canEdit(this.filaAlteracao());
  });
  readonly podeDesfazer = computed(() => {
    const f = this.filaAlteracao();
    return !!this.execucao()?.alteracaoPendente
      && (!f || this.outboxService.canEdit(f) || f.status === 'ERRO');
  });
  readonly prazoVencido = computed(() => {
    const e = this.execucao();
    return !!e && e.statusSync === 'SINCRONIZADO' && !e.alteracaoPendente && !podeAlterarNoCampo(e, this.tokenService.equipeId());
  });
  readonly estadoAlteracao = computed(() =>
    estadoEnvioExecucao({ statusSync: 'LOCAL' }, this.filaAlteracao() ?? undefined, false));
  readonly explicacaoAlteracao = computed(() => {
    const f = this.filaAlteracao();
    switch (this.estadoAlteracao()) {
      case 'error': return (f?.erroDetalhes || 'O servidor recusou a alteração.') + ' Desfaça para voltar a nota ao que está no servidor.';
      case 'conflict': return 'O sinal caiu no meio do envio. Confira no servidor antes de alterar de novo.';
      case 'syncing': return 'Enviando a alteração agora.';
      default: {
        const dias = diasParaAlterar(this.execucao()!.dataLancamento);
        return `Alteração na fila deste aparelho. Sai sozinha quando houver internet. ${dias > 0 ? `Dá para mudar de novo por mais ${dias} ${dias === 1 ? 'dia' : 'dias'}.` : 'O prazo para mudar de novo vence hoje.'}`;
      }
    }
  });
  readonly backoffice = computed(() => this.outboxService.isEmTratamentoBackoffice(this.fila()));
  // Com alteração pendente, o que importa é o envio dela: a nota no servidor ainda é a antiga.
  readonly estado = computed(() => {
    const e = this.execucao();
    if (!e) return 'committed';
    return e.alteracaoPendente ? this.estadoAlteracao() : estadoEnvioExecucao(e, this.fila() ?? undefined, false);
  });

  readonly explicacao = computed(() => {
    if (this.execucao()?.alteracaoPendente) return this.explicacaoAlteracao();
    if (this.backoffice()) return 'Chegou ao escritório e está em análise. Não precisa lançar de novo.';
    if (this.incerto()) return 'O sinal caiu no meio do envio. Confira no servidor antes de lançar de novo.';
    switch (this.estado()) {
      case 'synced': return 'Registrada no servidor.';
      case 'queued': return 'Na fila deste aparelho. Sai sozinha quando houver internet.';
      case 'syncing': return 'Enviando agora.';
      case 'error': return this.fila()?.erroDetalhes || 'Não foi aceita pelo servidor.';
      default: return 'Salva neste aparelho.';
    }
  });

  readonly quando = computed(() => {
    const e = this.execucao();
    if (!e) return null;
    const d = new Date(e.dataLancamento);
    return `${d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })} às ${d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}`;
  });

  readonly coordenadas = computed(() => {
    const e = this.execucao();
    return e?.latitude !== undefined && e?.longitude !== undefined
      ? `${e.latitude.toFixed(5).replace('.', ',')}, ${e.longitude.toFixed(5).replace('.', ',')}`
      : '';
  });

  readonly municipio = computed(() => {
    const id = this.execucao()?.idMunicipio;
    return id ? this.municipios().find(m => m.id === id)?.nome ?? '' : '';
  });

  readonly materiais = computed<MaterialLinha[]>(() => {
    const e = this.execucao();
    if (!e) return [];
    const cat = this.catalogo();
    // Materiais com série chegam como N linhas de quantidade 1: consolida numa linha com as séries.
    const grupos = new Map<string, MaterialLinha>();
    for (const em of e.materiais) {
      const m = cat.get(em.idMaterial);
      const origem = em.origem ?? 'EQUIPE';
      const chave = `${em.idMaterial}|${em.operacao}|${em.condicao}|${origem}`;
      const existente = grupos.get(chave);
      if (existente) {
        existente.quantidade += em.quantidade;
        if (em.numSerie) existente.numSeries.push(em.numSerie);
      } else {
        grupos.set(chave, {
          idMaterial: em.idMaterial, descricao: m?.descricao || 'Material', codigo: m?.codigo || 'N/A',
          unidadeMedida: m?.unidadeMedida || 'UN', quantidade: em.quantidade, operacao: em.operacao, condicao: em.condicao,
          numSeries: em.numSerie ? [em.numSerie] : [], origem,
        });
      }
    }
    return [...grupos.values()];
  });

  /** O registro foi aceito pelo servidor e ganhou id novo: segue com ele e corrige a URL. */
  private seguirIdNovo(): boolean {
    const novo = idAtual(this.id);
    if (!novo) return false;
    const antigo = this.id;
    this.id = novo;
    this.location.replaceState(this.location.path().replace(antigo, novo));
    return true;
  }

  async ngOnInit(): Promise<void> {
    await this.carregar();
  }

  readonly grupos = computed(() => [
    { operacao: 'APLICADO', titulo: 'Aplicado no cliente', vazio: 'Nenhum material aplicado', itens: this.materiais().filter(m => m.operacao === 'APLICADO') },
    { operacao: 'RETIRADO', titulo: 'Retirado do cliente', vazio: 'Nenhum material retirado', itens: this.materiais().filter(m => m.operacao === 'RETIRADO') },
  ]);

  /** Número no formato brasileiro (vírgula decimal). */
  num(n: number): string {
    return String(n).replace('.', ',');
  }

  rotulo(e: ExecucaoCampoLocal): string {
    return rotuloNota(e);
  }

  /** Avulsa já sincronizada com o número oficial (a referência deixou de ser o provisório). */
  numeroOficialInformado(e: ExecucaoCampoLocal): boolean {
    return e.tipoNota === 'AVULSA' && e.statusSync === 'SINCRONIZADO' && e.aguardandoNumeroOficial === false;
  }

  acao(id: string): { nome: string; codigo: string } {
    const a = this.acoes().get(id);
    return { nome: a?.nome ?? 'Serviço', codigo: a?.codigo ?? '—' };
  }

  voltar(): void {
    // Depois de alterar ou corrigir, a rota anterior é o formulário: voltar para ele
    // reabriria a edição (e o fechar dela traria de volta para cá, em loop).
    void this.router.navigateByUrl(this.sessao.rotaAnteriorFora('/campo/nova', `/campo/${this.id}`) ?? '/campo');
  }

  alterar(): void {
    void this.router.navigate(['/campo/nova'], { queryParams: { alterarId: this.id } });
  }

  async desfazer(): Promise<void> {
    try {
      await this.execucoesService.desfazerAlteracao(this.id);
      this.toast.success('Alteração desfeita. A nota voltou a ser como está no servidor.');
      await this.carregar();
    } catch (err: any) {
      this.toast.error(err instanceof CorrecaoRecusadaError ? 'A alteração já pode ter chegado ao servidor. Confira antes de desfazer.' : (err?.message || 'Não foi possível desfazer.'));
    } finally {
      this.confirmarDesfazer.set(false);
    }
  }

  corrigir(): void {
    void this.router.navigate(['/campo/nova'], { queryParams: { editId: this.id } });
  }

  async conferir(): Promise<void> {
    this.conferindo.set(true);
    try {
      await this.sync.syncOutbox({ reconciliar: true });
      await this.carregar();
      this.toast.success('Conferido com o servidor');
    } catch {
      this.toast.error('Não foi possível conferir agora. Tente com internet.');
    } finally {
      this.conferindo.set(false);
    }
  }

  async descartar(): Promise<void> {
    try {
      await this.execucoesService.descartar(this.id);
      this.toast.success('Execução descartada');
      void this.router.navigate(['/campo'], { replaceUrl: true });
    } catch (err: any) {
      this.toast.error(err?.message || 'Não foi possível descartar.');
    } finally {
      this.confirmarDescarte.set(false);
    }
  }

  private async carregar(): Promise<void> {
    this.carregando.set(true);
    try {
      if (!(await this.dbService.getExecucaoById(this.id))) this.seguirIdNovo();
      const [exec, fila, filaAlteracao, municipios, acoes, materiais] = await Promise.all([
        this.dbService.getExecucaoById(this.id),
        this.dbService.getOutboxItemByExecucaoId(this.id),
        this.dbService.getOutboxAlteracaoByExecucaoId(this.id),
        this.dbService.getMunicipios(),
        this.dbService.getAcoes(),
        this.dbService.getMateriais(),
      ]);
      this.execucao.set(exec ?? null);
      this.fila.set(fila ?? null);
      this.filaAlteracao.set(filaAlteracao ?? null);
      this.municipios.set(municipios);
      this.acoes.set(new Map(acoes.map(a => [a.id, a])));
      this.catalogo.set(new Map(materiais.map(m => [m.id, m])));
    } finally {
      this.carregando.set(false);
    }
  }
}
