import { ChangeDetectionStrategy, Component, Injector, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TurnoService } from '../../core/turno/turno.service';
import { ConnectivityService } from '../../core/sync/connectivity.service';
import { ToastService } from '../../core/ui/toast.service';
import { RascunhoService, VinculoRascunho } from '../../core/sessao/rascunho.service';
import {
  ComposicaoEquipeLocal,
  MembroComposicaoLocal,
  OpcaoColaboradorLocal,
  VeiculoComposicaoLocal,
} from '../../core/db/models';
import { BarraTopoComponent, FolhaComponent, IconeComponent, IndicadorRedeComponent, VazioComponent } from '../../ui';

type Folha = 'veiculo' | 'pessoa' | null;

/** Conferência do turno gravada no aparelho: sair no meio não desfaz a troca de placa ou de pessoas. */
interface RascunhoTurno {
  idEquipe: string;
  veiculo: VeiculoComposicaoLocal | null;
  membros: MembroComposicaoLocal[];
  folha: Folha;
}

/**
 * Abertura do turno: o líder confere veículo e pessoas e abre. Só o responsável da
 * equipe altera a composição; responsável de outra equipe não pode ser puxado.
 * Abrir exige rede (o servidor valida a equipe); a conferência fica salva enquanto isso.
 */
@Component({
  selector: 'app-abrir-turno',
  standalone: true,
  imports: [BarraTopoComponent, FolhaComponent, IconeComponent, IndicadorRedeComponent, VazioComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ces-barra-topo titulo="Abrir turno" [subtitulo]="composicao()?.codigo ? 'Equipe ' + composicao()!.codigo : null" (voltar)="voltar()">
      <ces-indicador-rede />
    </ces-barra-topo>

    <div class="tela">
      @if (carregando() && !composicao()) {
        <div class="esqueletos" aria-busy="true" aria-label="Carregando equipe">
          <div class="ces-esqueleto" style="height: 64px"></div><div class="ces-esqueleto" style="height: 180px"></div>
        </div>
      } @else if (!composicao()) {
        <ces-vazio icone="pessoas" titulo="Não deu para carregar sua equipe" texto="Conecte-se à internet e tente de novo.">
          <button type="button" class="ces-btn ces-btn--tonal" (click)="carregar()">Tentar de novo</button>
        </ces-vazio>
      } @else {
        <p class="intro">Confira quem está no caminhão hoje.</p>
        @if (!podeEditar()) {
          <p class="aviso"><ces-icone nome="info" [tamanho]="18" />Só o líder da equipe pode trocar o veículo ou as pessoas.</p>
        }

        <h2 class="ces-secao">Veículo</h2>
        <div class="ces-grupo">
          <button type="button" class="ces-linha" [class.ces-linha--acao]="podeEditar()" [disabled]="!podeEditar()" (click)="abrirFolha('veiculo')">
            <span class="ces-selo"><ces-icone nome="caminhao" /></span>
            <span class="ces-linha__texto">
              <span class="ces-linha__titulo ces-num">{{ veiculo()?.placa ?? 'Nenhum veículo' }}</span>
              <span class="ces-linha__apoio">{{ veiculo()?.modelo ?? (podeEditar() ? 'Toque para escolher' : '') }}{{ veiculoAlterado() ? ' · trocado hoje' : '' }}</span>
            </span>
            @if (podeEditar()) { <span class="trocar">Trocar</span> }
          </button>
        </div>

        <h2 class="ces-secao"><span>Pessoas</span><span class="ces-num">{{ membros().length }}</span></h2>
        <ul class="ces-grupo">
          @for (m of membros(); track m.idColaborador) {
            <li>
              <div class="ces-linha">
                <span class="iniciais" aria-hidden="true">{{ iniciais(m.nome) }}</span>
                <span class="ces-linha__texto">
                  <span class="ces-linha__titulo">{{ m.nome }}</span>
                  <span class="ces-linha__apoio">{{ m.responsavel ? 'Líder' : (m.funcao || 'Colaborador') }}{{ ehNovo(m.idColaborador) ? ' · incluído hoje' : '' }}</span>
                </span>
                @if (podeEditar() && !m.responsavel) {
                  <button type="button" class="ces-btn-icone" (click)="removerPessoa(m.idColaborador)" [attr.aria-label]="'Tirar ' + m.nome + ' do turno'">
                    <ces-icone nome="fechar" [tamanho]="20" />
                  </button>
                }
              </div>
            </li>
          }
          @if (podeEditar()) {
            <li>
              <button type="button" class="ces-linha ces-linha--acao incluir" (click)="abrirFolha('pessoa')">
                <span class="ces-selo ces-selo--destaque"><ces-icone nome="mais" /></span>
                <span class="ces-linha__texto"><span class="ces-linha__titulo">Incluir pessoa</span></span>
              </button>
            </li>
          }
        </ul>
        @if (removidos().length) {
          <p class="nota">Fora do turno hoje: {{ nomesRemovidos() }}.</p>
        }
        @if (erro()) {
          <p class="erro" role="alert"><ces-icone nome="alerta" [tamanho]="18" />{{ erro() }}</p>
        }
      }
    </div>

    @if (composicao()) {
      <footer class="rodape">
        @if (!rede.isOnline()) {
          <p class="sem-rede"><ces-icone nome="sem_rede" [tamanho]="16" />Abrir o turno precisa de internet. A conferência fica salva.</p>
        }
        <button type="button" class="ces-btn ces-btn--principal ces-btn--bloco" (click)="abrirTurno()" [disabled]="enviando() || !rede.isOnline()">
          {{ enviando() ? 'Abrindo…' : (houveAlteracao() ? 'Abrir turno com as mudanças' : 'Abrir turno') }}
        </button>
      </footer>
    }

    <ces-folha [aberta]="folha() !== null" (fechar)="fecharFolha()" [titulo]="folha() === 'veiculo' ? 'Escolher veículo' : 'Incluir pessoa'">
      <div class="busca">
        <ces-icone nome="busca" [tamanho]="18" />
        <input type="search" [placeholder]="folha() === 'veiculo' ? 'Placa ou modelo' : 'Nome'" enterkeyhint="search"
          [attr.aria-label]="folha() === 'veiculo' ? 'Buscar veículo' : 'Buscar pessoa'" [value]="busca()" (input)="busca.set($any($event.target).value)">
      </div>
      @if (folha() === 'veiculo') {
        <ul class="ces-grupo opcoes">
          @for (v of veiculosFiltrados(); track v.id) {
            <li><button type="button" class="ces-linha ces-linha--acao" (click)="escolherVeiculo(v)" [attr.aria-pressed]="v.id === veiculo()?.id">
              <span class="ces-linha__texto"><span class="ces-linha__titulo ces-num">{{ v.placa }}</span><span class="ces-linha__apoio">{{ v.modelo }}</span></span>
              @if (v.id === veiculo()?.id) { <ces-icone nome="check" /> }
            </button></li>
          } @empty { <li><div class="ces-linha"><span class="ces-linha__apoio">Nenhum veículo encontrado</span></div></li> }
        </ul>
      } @else if (folha() === 'pessoa') {
        <ul class="ces-grupo opcoes">
          @for (c of pessoasDisponiveis(); track c.id) {
            <li><button type="button" class="ces-linha ces-linha--acao" (click)="adicionarPessoa(c)" [disabled]="c.responsavelEmOutraEquipe">
              <span class="ces-linha__texto">
                <span class="ces-linha__titulo">{{ c.nome }}</span>
                <span class="ces-linha__apoio">{{ c.responsavelEmOutraEquipe ? 'Líder de outra equipe' : (c.funcao || 'Colaborador') }}{{ c.equipeAtual ? ' · ' + c.equipeAtual : '' }}</span>
              </span>
            </button></li>
          } @empty { <li><div class="ces-linha"><span class="ces-linha__apoio">Ninguém encontrado com esse nome</span></div></li> }
        </ul>
      }
    </ces-folha>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; min-height: 100%; background: var(--ces-fundo); }
    /* Nada encolhe: com conteúdo maior que a tela, a página rola (grupos com overflow:hidden eram cortados). */
    :host > * { flex-shrink: 0; }
    .tela { flex: 1; padding-bottom: var(--ces-e4); }
    .esqueletos { display: grid; gap: var(--ces-e3); padding: var(--ces-e4); }
    .intro { margin: var(--ces-e1) var(--ces-e5) 0; color: var(--ces-texto-2); }
    .aviso, .nota, .erro, .sem-rede { display: flex; gap: var(--ces-e2); align-items: flex-start; font: var(--ces-t-apoio); font-size: .875rem; }
    .aviso { margin: var(--ces-e3) var(--ces-e3) 0; padding: var(--ces-e3) var(--ces-e4); border-radius: var(--ces-r-grupo); background: var(--ces-destaque-suave); color: var(--ces-texto); }
    .nota { margin: var(--ces-e3) var(--ces-e5) 0; color: var(--ces-texto-2); }
    .erro { margin: var(--ces-e3) var(--ces-e5) 0; color: var(--ces-erro); }
    .ces-linha:disabled { color: inherit; cursor: default; }
    .trocar { color: var(--ces-destaque); font-weight: 600; font-size: .875rem; }
    .iniciais { width: 40px; height: 40px; border-radius: 50%; display: grid; place-items: center; flex: none; background: var(--ces-superficie-2);
      color: var(--ces-texto-2); font: 600 .875rem/1 var(--ces-fonte); }
    .incluir .ces-linha__titulo { color: var(--ces-destaque); }
    .rodape { position: sticky; bottom: 0; display: grid; gap: var(--ces-e2); padding: var(--ces-e3) var(--ces-e4) calc(var(--ces-e3) + var(--ces-safe-base));
      background: var(--ces-fundo); border-top: 1px solid var(--ces-linha); }
    .sem-rede { margin: 0; color: var(--ces-atencao); }
    .busca { margin: 0 var(--ces-e3) var(--ces-e3); display: flex; align-items: center; gap: var(--ces-e2); padding: 0 var(--ces-e4); min-height: 48px;
      border-radius: var(--ces-r-pilula); background: var(--ces-superficie-2); color: var(--ces-texto-2); }
    .busca input { flex: 1; min-width: 0; border: 0; background: transparent; color: var(--ces-texto); font: var(--ces-t-corpo); font-size: 1rem; }
    .busca input:focus { outline: none; }
    .opcoes { background: var(--ces-fundo); }
    .opcoes .ces-linha:disabled { opacity: .55; }
  `],
})
export class AbrirTurnoComponent implements OnInit {
  private readonly turnoService = inject(TurnoService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly rascunhos = inject(RascunhoService);
  private readonly injector = inject(Injector);
  readonly rede = inject(ConnectivityService);

  readonly carregando = signal(true);
  readonly enviando = signal(false);
  readonly erro = signal<string | null>(null);
  readonly composicao = signal<ComposicaoEquipeLocal | null>(null);
  readonly veiculo = signal<VeiculoComposicaoLocal | null>(null);
  readonly membros = signal<MembroComposicaoLocal[]>([]);
  readonly folha = signal<Folha>(null);
  readonly busca = signal('');
  private rascunho: VinculoRascunho | null = null;

  readonly podeEditar = computed(() => !!this.composicao()?.usuarioResponsavel);
  readonly veiculoAlterado = computed(() => (this.veiculo()?.id ?? null) !== (this.composicao()?.veiculo?.id ?? null));
  private readonly idsOriginais = computed(() => new Set((this.composicao()?.membros ?? []).map(m => m.idColaborador)));
  private readonly idsSelecionados = computed(() => new Set(this.membros().map(m => m.idColaborador)));
  readonly removidos = computed(() => (this.composicao()?.membros ?? []).filter(m => !this.idsSelecionados().has(m.idColaborador)));
  readonly nomesRemovidos = computed(() => this.removidos().map(m => m.nome).join(', '));
  readonly membrosAlterados = computed(() => {
    const originais = this.idsOriginais();
    const atuais = this.idsSelecionados();
    return originais.size !== atuais.size || [...atuais].some(id => !originais.has(id));
  });
  readonly houveAlteracao = computed(() => this.veiculoAlterado() || this.membrosAlterados());

  readonly veiculosFiltrados = computed(() => {
    const q = normalizar(this.busca());
    const lista = this.composicao()?.opcoes.veiculos ?? [];
    return (q ? lista.filter(v => normalizar(v.placa).includes(q) || normalizar(v.modelo ?? '').includes(q)) : lista).slice(0, 60);
  });

  readonly pessoasDisponiveis = computed(() => {
    const q = normalizar(this.busca());
    const selecionados = this.idsSelecionados();
    return (this.composicao()?.opcoes.colaboradores ?? [])
      .filter(c => !selecionados.has(c.id))
      .filter(c => !q || normalizar(c.nome).includes(q))
      .slice(0, 60);
  });

  async ngOnInit(): Promise<void> {
    await this.carregar();
  }

  async carregar(): Promise<void> {
    this.carregando.set(true);
    try {
      const local = await this.turnoService.loadComposicaoLocal();
      if (local) this.aplicarComposicao(local);
      if (this.rede.isOnline()) {
        try {
          this.aplicarComposicao(await this.turnoService.sincronizarComposicao());
        } catch (error) {
          console.warn('Não foi possível atualizar a composição da equipe:', error);
        }
      }
      if (this.composicao() && !this.rascunho) await this.vincularRascunho();
    } finally {
      this.carregando.set(false);
    }
  }

  private async vincularRascunho(): Promise<void> {
    const idEquipe = this.composicao()!.idEquipe;
    this.rascunho = this.rascunhos.vincular<RascunhoTurno>({
      chave: 'turno:abrir',
      injector: this.injector,
      ler: () => ({ idEquipe, veiculo: this.veiculo(), membros: this.membros(), folha: this.folha() }),
      aplicar: r => {
        // Rascunho de outra equipe (troca de usuário) não vale aqui.
        if (r.idEquipe !== idEquipe || !this.podeEditar()) return;
        this.veiculo.set(r.veiculo);
        this.membros.set(r.membros?.length ? r.membros : this.membros());
        this.folha.set(r.folha ?? null);
      },
      titulo: () => (this.houveAlteracao() ? 'Abertura de turno com mudanças na equipe' : 'Abertura de turno'),
      rota: () => '/turno/abrir',
      vazio: () => !this.houveAlteracao() && this.folha() === null,
    });
    await this.rascunho.restaurado;
  }

  ehNovo(idColaborador: string): boolean {
    return !this.idsOriginais().has(idColaborador);
  }

  iniciais(nome: string): string {
    const partes = (nome || '?').trim().split(/\s+/);
    return ((partes[0]?.[0] ?? '') + (partes.length > 1 ? partes[partes.length - 1][0] : '')).toUpperCase();
  }

  abrirFolha(folha: Exclude<Folha, null>): void {
    if (!this.podeEditar()) return;
    this.busca.set('');
    this.folha.set(folha);
  }

  fecharFolha(): void {
    this.folha.set(null);
  }

  escolherVeiculo(v: VeiculoComposicaoLocal): void {
    this.veiculo.set(v);
    this.fecharFolha();
  }

  adicionarPessoa(c: OpcaoColaboradorLocal): void {
    if (c.responsavelEmOutraEquipe) return;
    this.membros.update(lista => [...lista, { idColaborador: c.id, nome: c.nome, funcao: c.funcao, responsavel: false }]);
    this.fecharFolha();
  }

  removerPessoa(idColaborador: string): void {
    this.membros.update(lista => lista.filter(m => m.responsavel || m.idColaborador !== idColaborador));
  }

  async abrirTurno(): Promise<void> {
    if (this.enviando()) return;
    this.enviando.set(true);
    this.erro.set(null);
    try {
      const escolha = this.podeEditar() && this.houveAlteracao()
        ? {
            idVeiculo: this.veiculoAlterado() ? this.veiculo()?.id ?? null : null,
            idsColaboradores: this.membros().filter(m => !m.responsavel).map(m => m.idColaborador),
          }
        : undefined;
      await this.turnoService.abrirTurno(escolha);
      await this.rascunho?.concluir();
      this.toast.success(escolha ? 'Turno aberto e equipe atualizada' : 'Turno aberto. Bom trabalho!');
      await this.router.navigate(['/hoje'], { replaceUrl: true });
    } catch (error: any) {
      this.erro.set(error?.detail || error?.error?.detail || error?.message || 'Tente novamente em instantes.');
    } finally {
      this.enviando.set(false);
    }
  }

  voltar(): void {
    void this.router.navigate(['/hoje']);
  }

  private aplicarComposicao(composicao: ComposicaoEquipeLocal): void {
    const editado = this.houveAlteracao() && this.composicao()?.idEquipe === composicao.idEquipe;
    this.composicao.set(composicao);
    // Atualização do servidor não apaga o que o líder já mexeu.
    if (!editado) {
      this.veiculo.set(composicao.veiculo);
      this.membros.set([...composicao.membros]);
    }
  }
}

function normalizar(valor: string): string {
  return (valor || '').normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-zA-Z0-9 ]/g, '').toLowerCase().trim();
}
