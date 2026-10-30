import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TelemetriaService } from './telemetria.service';
import { TokenService } from '../auth/token.service';
import { ConnectivityService } from '../sync/connectivity.service';
import { silentContext } from '../http/http-context';

const CHAVE_APARELHO = 'ces_id_aparelho';
const LOTE = 100;

/**
 * Envia em lote os eventos técnicos para `POST /v1/telemetria/mobile`.
 * Sem pressa e sem custo para o usuário: só com rede liberada e sessão válida,
 * em silêncio (falha de telemetria nunca vira aviso na tela) e com chave de
 * idempotência por lote. O aparelho é identificado por um id aleatório local,
 * sem relação com login, nome ou equipe.
 */
@Injectable({ providedIn: 'root' })
export class TelemetriaEnvioService {
  private readonly http = inject(HttpClient);
  private readonly telemetria = inject(TelemetriaService);
  private readonly tokens = inject(TokenService);
  private readonly rede = inject(ConnectivityService);
  private enviando = false;

  async enviarPendentes(): Promise<number> {
    if (this.enviando || !this.rede.isOnline() || !this.tokens.token() || this.tokens.reautenticacaoNecessaria()) return 0;
    this.enviando = true;
    let total = 0;
    try {
      for (let rodada = 0; rodada < 5; rodada++) {
        const eventos = await this.telemetria.pendentes(LOTE);
        if (!eventos.length) break;
        const seqs = eventos.map(e => e.seq!).filter(s => s !== undefined);
        await firstValueFrom(this.http.post(`${environment.apiBaseUrl}/v1/telemetria/mobile`, {
          idAparelho: idAparelho(),
          versaoApp: environment.versaoApp,
          eventos: eventos.map(e => ({ tipo: e.tipo, em: new Date(e.em).toISOString(), dados: e.dados ?? {} })),
        }, {
          context: silentContext(),
          headers: { 'Idempotency-Key': `telemetria:${idAparelho()}:${seqs[0]}-${seqs[seqs.length - 1]}` },
        }));
        await this.telemetria.marcarEnviados(seqs);
        total += eventos.length;
      }
    } catch {
      /* tenta de novo na próxima oportunidade */
    } finally {
      this.enviando = false;
    }
    return total;
  }
}

function idAparelho(): string {
  try {
    let id = localStorage.getItem(CHAVE_APARELHO);
    if (!id) {
      id = crypto.randomUUID();
      localStorage.setItem(CHAVE_APARELHO, id);
    }
    return id;
  } catch {
    return 'sem-armazenamento';
  }
}
