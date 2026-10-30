import { DestroyRef, Injectable, inject } from '@angular/core';
import { NavigationEnd, NavigationStart, Router } from '@angular/router';
import { filter } from 'rxjs';
import { SESSAO_DB, ID_ABA, ehQuotaCheia } from './sessao-db';
import {
  DecisaoRetomada, EstadoTela, SNAPSHOT_SCHEMA, SessaoSnapshot, decidirRetomada, lerSnapshot, selar,
} from './snapshot';
import { TelemetriaService } from './telemetria.service';
import { TokenService } from '../auth/token.service';

/** Intervalo entre a última alteração e a gravação. Curto: o sistema pode matar o app a qualquer momento. */
export const DEBOUNCE_SESSAO_MS = 300;
const MAX_TELAS = 40;
const MAX_PILHA = 20;
const ROTAS_SEM_SNAPSHOT = /^\/(login|sync|instalar)(\?|$)/;

/**
 * Onde o usuário está e como a tela estava: rota, pilha, rolagem, filtros, aba, folha aberta.
 *
 * Grava CONTINUAMENTE (debounce de 300 ms) e, como reforço, em `visibilitychange`,
 * `pagehide` e `freeze` — eventos que no celular nem sempre disparam, por isso
 * nunca são a única garantia. Cada gravação guarda a anterior como reserva, numa
 * transação: um processo morto no meio deixa sempre um retrato válido.
 */
@Injectable({ providedIn: 'root' })
export class SessaoService {
  private readonly db = inject(SESSAO_DB);
  private readonly idAba = inject(ID_ABA);
  private readonly router = inject(Router);
  private readonly tokens = inject(TokenService);
  private readonly telemetria = inject(TelemetriaService);

  private rota = '/';
  private pilha: string[] = [];
  private telas: Record<string, EstadoTela> = {};
  private seq = 0;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private iniciado = false;
  private decisao: DecisaoRetomada | null = null;
  private decisaoConsumida = false;
  private gatilho: 'imperative' | 'popstate' | 'hashchange' = 'imperative';

  /** Lê o retrato salvo e decide para onde abrir. Chamado uma vez, antes da primeira navegação. */
  async carregar(agora = Date.now()): Promise<DecisaoRetomada> {
    if (this.decisao) return this.decisao;
    let leitura = lerSnapshot((await this.lerRegistro('atual'))?.dados);
    let usouReserva = false;
    if (!leitura.ok && leitura.motivo !== 'vazio') {
      const reserva = lerSnapshot((await this.lerRegistro('anterior'))?.dados);
      if (reserva.ok) {
        void this.telemetria.registrar('retomada.falha', { motivo: leitura.motivo, recuperado: true });
        leitura = reserva;
        usouReserva = true;
      } else {
        void this.telemetria.registrar('retomada.falha', { motivo: leitura.motivo, recuperado: false });
      }
    }
    const idUsuario = this.tokens.ownerId();
    // Sessão local basta: token vencido renova sozinho quando houver rede, e mandar para
    // o login quem está sem sinal o deixaria preso fora do app.
    const autenticado = !!this.tokens.token();
    this.decisao = decidirRetomada(leitura, { agora, idUsuario, autenticado });

    if (leitura.ok && leitura.snapshot.idUsuario === idUsuario) {
      // Mesmo começando no início, a rolagem e os filtros de cada tela continuam valendo.
      this.telas = leitura.snapshot.telas;
      if (this.decisao.acao === 'retomar') this.pilha = leitura.snapshot.pilha.slice(-MAX_PILHA);
    }
    void this.telemetria.registrar('retomada.decisao', {
      acao: this.decisao.acao,
      motivo: 'motivo' in this.decisao ? this.decisao.motivo : 'ok',
      reserva: usouReserva,
      ...(leitura.ok && leitura.migradoDe ? { migradoDe: leitura.migradoDe } : {}),
    });
    return this.decisao;
  }

  /**
   * Destino da abertura a frio: só vale para a primeira navegação para a raiz
   * (abrir pelo ícone). Depois disso, a raiz é só "ir para o início".
   */
  consumirDecisao(): DecisaoRetomada | null {
    if (this.decisaoConsumida) return null;
    this.decisaoConsumida = true;
    return this.decisao;
  }

  iniciar(destroyRef?: DestroyRef): void {
    if (this.iniciado) return;
    this.iniciado = true;

    const sub = this.router.events.pipe(filter(e => e instanceof NavigationStart || e instanceof NavigationEnd)).subscribe(e => {
      if (e instanceof NavigationStart) {
        this.gatilho = e.navigationTrigger ?? 'imperative';
        return;
      }
      this.registrarNavegacao((e as NavigationEnd).urlAfterRedirects);
    });

    const reforco = () => void this.gravarAgora();
    const aoMudarVisibilidade = () => { if (document.visibilityState === 'hidden') reforco(); };
    document.addEventListener('visibilitychange', aoMudarVisibilidade);
    document.addEventListener('freeze', reforco);
    window.addEventListener('pagehide', reforco);

    destroyRef?.onDestroy(() => {
      sub.unsubscribe();
      document.removeEventListener('visibilitychange', aoMudarVisibilidade);
      document.removeEventListener('freeze', reforco);
      window.removeEventListener('pagehide', reforco);
    });
  }

  rotaAtual(): string {
    return this.rota;
  }

  /** Para o botão voltar do app: a rota anterior desta sessão, se houver. */
  rotaAnterior(): string | null {
    return this.pilha.length ? this.pilha[this.pilha.length - 1] : null;
  }

  /**
   * A rota anterior mais recente que não comece por nenhum dos prefixos. Serve para o
   * voltar pular o formulário que acabou de salvar (e a própria tela): sem isso,
   * detalhe e formulário se reabrem um ao outro sem fim.
   */
  rotaAnteriorFora(...prefixos: string[]): string | null {
    for (let i = this.pilha.length - 1; i >= 0; i--) {
      const rota = this.pilha[i];
      if (!prefixos.some(p => rota.startsWith(p))) return rota;
    }
    return null;
  }

  estadoTela<T extends object = EstadoTela>(rota = this.rota): Partial<T> {
    return (this.telas[rota] ?? {}) as Partial<T>;
  }

  definirEstadoTela(patch: EstadoTela, rota = this.rota): void {
    const atual = this.telas[rota] ?? {};
    // Reinsere no fim: a ordem de inserção é a ordem de uso (para podar as mais antigas).
    delete this.telas[rota];
    this.telas[rota] = { ...atual, ...patch };
    const chaves = Object.keys(this.telas);
    for (const velha of chaves.slice(0, Math.max(0, chaves.length - MAX_TELAS))) delete this.telas[velha];
    this.agendar();
  }

  registrarNavegacao(url: string): void {
    if (url === this.rota) return;
    const anterior = this.rota;
    if (this.gatilho === 'popstate') {
      const i = this.pilha.lastIndexOf(url);
      this.pilha = i >= 0 ? this.pilha.slice(0, i) : this.pilha;
    } else if (anterior !== '/' && !ROTAS_SEM_SNAPSHOT.test(anterior)) {
      this.pilha = [...this.pilha, anterior].slice(-MAX_PILHA);
    }
    this.rota = url;
    this.agendar();
  }

  agendar(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => void this.gravarAgora(), DEBOUNCE_SESSAO_MS);
  }

  /** Grava já. Seguro para chamar de qualquer evento; gravações concorrentes são serializadas pelo IndexedDB. */
  async gravarAgora(agora = Date.now()): Promise<boolean> {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    const idUsuario = this.tokens.ownerId();
    if (!idUsuario || this.rota === '/' || ROTAS_SEM_SNAPSHOT.test(this.rota)) return false;

    const snapshot: SessaoSnapshot = {
      schema: SNAPSHOT_SCHEMA,
      salvoEm: agora,
      idUsuario,
      rota: this.rota,
      pilha: this.pilha,
      telas: this.telas,
      idAba: this.idAba,
      seq: ++this.seq,
    };
    try {
      const gravado = selar(JSON.parse(JSON.stringify(snapshot)));
      await this.db.transaction('rw', this.db.snapshots, async () => {
        const atual = await this.db.snapshots.get('atual');
        if (atual) await this.db.snapshots.put({ chave: 'anterior', dados: atual.dados });
        await this.db.snapshots.put({ chave: 'atual', dados: gravado });
      });
      return true;
    } catch (erro) {
      if (ehQuotaCheia(erro)) void this.telemetria.registrar('armazenamento.cheio', { origem: 'sessao' });
      return false;
    }
  }

  /** Logout confirmado: a próxima abertura não deve voltar para dentro da conta anterior. */
  async esquecer(): Promise<void> {
    if (this.timer) clearTimeout(this.timer);
    this.telas = {};
    this.pilha = [];
    this.rota = '/';
    await this.db.snapshots.clear();
  }

  private async lerRegistro(chave: 'atual' | 'anterior') {
    try {
      return await this.db.snapshots.get(chave);
    } catch {
      return undefined;
    }
  }
}
