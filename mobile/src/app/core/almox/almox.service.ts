import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, forkJoin, map, of, shareReplay } from 'rxjs';
import { environment } from '../../../environments/environment';
import { silentContext } from '../http/http-context';
import {
  Colaborador, EntregaRequest, Estoque, EstornoParcialRequest, FiltroMovimentacoes, FiltroSolicitacoes, Material,
  MembroEquipe, Motivo, Movimentacao, MovimentacaoRequest, Pagina, PendenciaRequest, ResumoMovimentacao, ResumoSolicitacao,
  Saldo, SaldoSerie, Solicitacao, StatusSolicitacao, Usuario,
} from './almox.models';

/**
 * Almoxarifado do perfil administrativo: tudo online, direto do servidor (aprovar,
 * entregar e movimentar dependem do saldo e da versão atuais — não há fila offline).
 * Consultas de apoio (estoques, motivos, nomes) ficam em memória durante a sessão.
 */
@Injectable({ providedIn: 'root' })
export class AlmoxService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiBaseUrl}/v1`;

  private estoques$?: Observable<Estoque[]>;
  private motivosRejeicao$?: Observable<Motivo[]>;
  private motivosPendencia$?: Observable<Motivo[]>;
  private nomesCarregados = false;
  /** id de usuário ou colaborador → nome de exibição. */
  readonly nomes = signal<ReadonlyMap<string, string>>(new Map());

  // ---------- solicitações comerciais ----------

  listarSolicitacoes(filtro: FiltroSolicitacoes): Observable<Pagina<ResumoSolicitacao>> {
    return this.http.get<Pagina<ResumoSolicitacao>>(`${this.api}/solicitacoes-material`, {
      params: params({ contexto: 'COMERCIAL', ...filtro }),
    });
  }

  /** Total de cada status no recorte (uma consulta de 1 linha por status). Falha vira `undefined`. */
  contarPorStatus(filtro: FiltroSolicitacoes, status: readonly StatusSolicitacao[]): Observable<Partial<Record<StatusSolicitacao, number>>> {
    const { statuses: _s, ...resto } = filtro;
    return forkJoin(status.map(s => this.http.get<Pagina<ResumoSolicitacao>>(`${this.api}/solicitacoes-material`, {
      params: params({ contexto: 'COMERCIAL', ...resto, statuses: [s], page: 0, size: 1 }), context: silentContext(),
    }).pipe(map(r => [s, r.page?.totalElements ?? 0] as const), catchError(() => of([s, undefined] as const)))))
      .pipe(map(pares => Object.fromEntries(pares.filter(([, n]) => n !== undefined)) as Partial<Record<StatusSolicitacao, number>>));
  }

  obterSolicitacao(id: string): Observable<Solicitacao> {
    return this.http.get<Solicitacao>(`${this.api}/solicitacoes-material/${encodeURIComponent(id)}`, { context: silentContext() });
  }

  aprovar(id: string): Observable<void> {
    return this.http.post<void>(`${this.api}/solicitacoes-material/${encodeURIComponent(id)}/aprovar`, {}, { context: silentContext() });
  }

  rejeitar(id: string, idMotivoRejeicao: string, justificativa: string): Observable<void> {
    return this.http.post<void>(`${this.api}/solicitacoes-material/${encodeURIComponent(id)}/rejeitar`,
      { idMotivoRejeicao, justificativa }, { context: silentContext() });
  }

  entregar(id: string, pedido: EntregaRequest): Observable<void> {
    return this.http.post<void>(`${this.api}/solicitacoes-material/${encodeURIComponent(id)}/entregar`, pedido, { context: silentContext() });
  }

  registrarPendencia(id: string, pedido: PendenciaRequest): Observable<void> {
    return this.http.post<void>(`${this.api}/solicitacoes-material/${encodeURIComponent(id)}/pendencia`, pedido, { context: silentContext() });
  }

  resolverPendencia(id: string, idItem?: string | null): Observable<void> {
    return this.http.delete<void>(`${this.api}/solicitacoes-material/${encodeURIComponent(id)}/pendencia`, {
      params: idItem ? { idItem } : {}, context: silentContext(),
    });
  }

  estornarParcial(id: string, pedido: EstornoParcialRequest): Observable<void> {
    return this.http.patch<void>(`${this.api}/solicitacoes-material/${encodeURIComponent(id)}/estorno-parcial`, pedido, { context: silentContext() });
  }

  // ---------- movimentações ----------

  listarMovimentacoes(filtro: FiltroMovimentacoes): Observable<Pagina<ResumoMovimentacao>> {
    return this.http.get<Pagina<ResumoMovimentacao>>(`${this.api}/movimentacoes-estoque`, { params: params({ ...filtro }) });
  }

  obterMovimentacao(idReferencia: string): Observable<Movimentacao> {
    return this.http.get<Movimentacao>(`${this.api}/movimentacoes-estoque/operacao/${encodeURIComponent(idReferencia)}`, { context: silentContext() });
  }

  registrarEntrada(pedido: MovimentacaoRequest): Observable<unknown> {
    return this.http.post(`${this.api}/movimentacoes-estoque/entrada`, pedido, { context: silentContext() });
  }

  registrarSaida(pedido: MovimentacaoRequest): Observable<unknown> {
    return this.http.post(`${this.api}/movimentacoes-estoque/baixa`, pedido, { context: silentContext() });
  }

  registrarTransferencia(pedido: MovimentacaoRequest): Observable<unknown> {
    return this.http.post(`${this.api}/movimentacoes-estoque/transferencia`, pedido, { context: silentContext() });
  }

  // ---------- saldos e catálogos ----------

  listarSaldos(idEstoque: string): Observable<Saldo[]> {
    return this.http.get<Pagina<Saldo>>(`${this.api}/saldos`, { params: params({ idEstoque, ativo: true, size: 1000 }), context: silentContext() })
      .pipe(map(r => (r.content ?? []).filter(s => Number(s.quantidade) > 0)));
  }

  listarSeries(idEstoque: string, idMaterial: string, condicao?: string | null): Observable<SaldoSerie[]> {
    return this.http.get<Pagina<SaldoSerie>>(`${this.api}/saldos/seriais`, {
      params: params({ idEstoque, idMaterial, condicao: condicao ?? undefined, ativo: true, size: 1000, sort: 'numSerie,asc' }), context: silentContext(),
    }).pipe(map(r => r.content ?? []));
  }

  /** Onde está uma série num estoque (busca exata): material e condição, para a leitura direta. */
  localizarSerie(idEstoque: string, numSerie: string): Observable<SaldoSerie | null> {
    return this.http.get<Pagina<SaldoSerie>>(`${this.api}/saldos/seriais`, {
      params: params({ idEstoque, numSerie, ativo: true, size: 1 }), context: silentContext(),
    }).pipe(map(r => r.content?.[0] ?? null));
  }

  /** Séries já ativas em algum estoque: a entrada avulsa recusa série repetida no sistema. */
  verificarSeries(series: string[]): Observable<SaldoSerie[]> {
    return this.http.post<SaldoSerie[]>(`${this.api}/saldos/seriais/verificar`, { series }, { context: silentContext() });
  }

  buscarMateriais(busca: string, page = 0): Observable<Pagina<Material>> {
    return this.http.get<Pagina<Material>>(`${this.api}/materiais`, {
      params: params({ ativo: true, buscaGlobal: busca.trim() || undefined, page, size: 30, sort: 'descricao,asc' }), context: silentContext(),
    });
  }

  estoques(): Observable<Estoque[]> {
    this.estoques$ ??= this.http.get<Pagina<Estoque>>(`${this.api}/estoques`, {
      params: params({ ativo: true, size: 1000, sort: 'nomeIdentificador,asc' }), context: silentContext(),
    }).pipe(map(r => r.content ?? []), catchError(() => { this.estoques$ = undefined; return of([]); }), shareReplay(1));
    return this.estoques$;
  }

  motivosRejeicao(): Observable<Motivo[]> {
    this.motivosRejeicao$ ??= this.http.get<Pagina<Motivo>>(`${this.api}/motivos-rejeicao`, {
      params: params({ ativo: true, size: 1000, sort: 'nome,asc' }), context: silentContext(),
    }).pipe(map(r => r.content ?? []), catchError(() => { this.motivosRejeicao$ = undefined; return of([]); }), shareReplay(1));
    return this.motivosRejeicao$;
  }

  motivosPendencia(): Observable<Motivo[]> {
    this.motivosPendencia$ ??= this.http.get<Motivo[]>(`${this.api}/motivos-pendencia`, { params: { apenasAtivos: true }, context: silentContext() })
      .pipe(map(r => r ?? []), catchError(() => { this.motivosPendencia$ = undefined; return of([]); }), shareReplay(1));
    return this.motivosPendencia$;
  }

  membrosEquipe(idEquipe: string): Observable<MembroEquipe[]> {
    return this.http.get<MembroEquipe[]>(`${this.api}/equipes/${encodeURIComponent(idEquipe)}/membros`, { context: silentContext() })
      .pipe(map(r => (r ?? []).filter(m => m.vinculoAtivo !== false)));
  }

  colaboradores(): Observable<Colaborador[]> {
    return this.http.get<Pagina<Colaborador>>(`${this.api}/rh/colaboradores`, {
      params: params({ ativo: true, size: 500, sort: 'nomeCompleto,asc' }), context: silentContext(),
    }).pipe(map(r => r.content ?? []));
  }

  /** Nomes de requisitantes, aprovadores e operadores (uma vez por sessão; falha não bloqueia a tela). */
  carregarNomes(): void {
    if (this.nomesCarregados) return;
    this.nomesCarregados = true;
    forkJoin({
      usuarios: this.http.get<Pagina<Usuario>>(`${this.api}/iam/usuarios`, { params: params({ size: 1000 }), context: silentContext() }).pipe(catchError(() => of(null))),
      colaboradores: this.http.get<Pagina<Colaborador>>(`${this.api}/rh/colaboradores`, { params: params({ size: 1000 }), context: silentContext() }).pipe(catchError(() => of(null))),
    }).subscribe(({ usuarios, colaboradores }) => {
      const mapa = new Map<string, string>();
      for (const c of colaboradores?.content ?? []) {
        if (!c.nomeCompleto) continue;
        mapa.set(c.id, c.nomeCompleto);
        if (c.idUsuario) mapa.set(c.idUsuario, c.nomeCompleto);
      }
      for (const u of usuarios?.content ?? []) {
        const nome = u.colaborador?.nomeCompleto || (u.idColaborador ? mapa.get(u.idColaborador) : undefined) || mapa.get(u.id) || u.login;
        if (nome) mapa.set(u.id, nome);
      }
      if (!usuarios && !colaboradores) this.nomesCarregados = false;
      this.nomes.set(mapa);
    });
  }

  nome(id: string | null | undefined, reserva = 'Não informado'): string {
    if (!id) return reserva;
    return this.nomes().get(id) ?? reserva;
  }
}

/** Monta HttpParams sem vazios; listas viram parâmetros repetidos (`statuses=A&statuses=B`). */
function params(valores: Record<string, unknown>): HttpParams {
  let p = new HttpParams();
  for (const [chave, valor] of Object.entries(valores)) {
    if (valor === null || valor === undefined || valor === '') continue;
    if (Array.isArray(valor)) valor.forEach(v => (p = p.append(chave, String(v))));
    else p = p.set(chave, String(valor));
  }
  return p;
}
