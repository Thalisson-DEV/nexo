import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DbService } from '../db/db.service';
import { OutboxService } from '../sync/outbox.service';
import { AuthService } from '../auth/auth.service';
import { ComposicaoEquipeLocal, ColaboradorLocal, MetaDoCicloLocal, TurnoLocal } from '../db/models';
import { silentContext } from '../http/http-context';
import { calcularMetaDiariaPadrao, corteDoTurno, turnoVencido } from '../campo/campo-regras';

/** Escolhas do responsável na conferência da equipe ao abrir o turno. */
export interface ComposicaoTurnoEscolhida {
  idVeiculo: string | null;
  idsColaboradores: string[];
}

@Injectable({
  providedIn: 'root'
})
export class TurnoService {
  private readonly http = inject(HttpClient);
  private readonly dbService = inject(DbService);
  private readonly authService = inject(AuthService);
  private readonly outboxService = inject(OutboxService);

  readonly turnoAtivo = signal<TurnoLocal | null>(null);
  /**
   * Último turno da equipe, quando o servidor o encerrou sozinho porque ficou aberto
   * depois do horário de corte. Some quando a equipe abre o próximo turno.
   */
  readonly encerramentoAutomatico = signal<TurnoLocal | null>(null);
  /** Composição da equipe (veículo, membros, meta) guardada no dispositivo. */
  readonly composicao = signal<ComposicaoEquipeLocal | null>(null);
  private readonly baseUrl = environment.apiBaseUrl;

  /**
   * Carrega o turno ativo do banco de dados local.
   */
  async loadTurnoAtivo(): Promise<void> {
    let turno = await this.dbService.getTurnoAtivo();
    if (turno && turnoVencido(turno.dataInicio)) {
      // O servidor já encerrou esse turno no corte e recusaria lançamentos nele.
      await this.dbService.fecharTurnoLocal(turno.id, corteDoTurno(turno.dataInicio).toISOString(), {
        encerradoAutomaticamente: true,
        motivoEncerramento: 'Turno aberto além do horário de corte.'
      });
      turno = undefined;
    }
    this.turnoAtivo.set(turno || null);
    await this.carregarEncerramentoAutomatico();
    if (typeof navigator !== 'undefined' && navigator.onLine) {
      void this.syncTurnoAtivoComServidor().catch(() => {});
    }
  }

  /**
   * Reconcilia o status do turno ativo local com o servidor.
   * Útil para detectar quando outra sessão ou usuário da mesma equipe já encerrou o turno no backend.
   */
  async syncTurnoAtivoComServidor(): Promise<void> {
    const user = this.authService.currentUser();
    if (!user || !user.equipeId || (typeof navigator !== 'undefined' && !navigator.onLine)) return;

    try {
      const turnoAtivoServer = await firstValueFrom(
        this.http.get<any>(`${this.baseUrl}/v1/turnos/equipe/${user.equipeId}/ativo`, {
          headers: { 'X-Silent-Error': 'true' }
        })
      ).catch(error => {
        if (error?.status === 404) return null;
        throw error;
      });

      const turnoLocal = this.turnoAtivo();

      if (turnoAtivoServer && turnoAtivoServer.status === 'ABERTO') {
        if (!turnoLocal || turnoLocal.id !== turnoAtivoServer.id) {
          const novoTurno: TurnoLocal = {
            id: turnoAtivoServer.id,
            idEquipe: turnoAtivoServer.idEquipe,
            idUsuarioAbertura: turnoAtivoServer.idUsuarioAbertura,
            dataInicio: turnoAtivoServer.dataInicio,
            status: 'ABERTO'
          };
          await this.dbService.saveTurno(novoTurno);
          this.turnoAtivo.set(novoTurno);
        }
      } else {
        // Se o servidor respondeu que NÃO HÁ turno aberto na equipe, mas tínhamos um turno localmente,
        // ele foi fechado em outra sessão. Encerramos localmente para liberar o usuário.
        if (turnoLocal && turnoLocal.status === 'ABERTO') {
          const encerrado = await this.buscarTurnoNoServidor(turnoLocal.id);
          await this.dbService.fecharTurnoLocal(
            turnoLocal.id,
            encerrado?.dataFim ?? new Date().toISOString(),
            encerrado?.encerradoAutomaticamente
              ? { encerradoAutomaticamente: true, motivoEncerramento: encerrado.motivoEncerramento ?? null }
              : undefined
          );
          this.turnoAtivo.set(null);
          await this.carregarEncerramentoAutomatico();
        }
      }
    } catch (error) {
      console.error('Erro ao reconciliar turno ativo com o servidor:', error);
      if ((error as { status?: number })?.status === 401) throw error;
    }
  }

  /** Turno no servidor; nulo quando não dá para consultar (o fechamento local segue). */
  private async buscarTurnoNoServidor(
    id: string
  ): Promise<{ dataFim?: string | null; encerradoAutomaticamente?: boolean; motivoEncerramento?: string | null } | null> {
    try {
      return await firstValueFrom(
        this.http.get<any>(`${this.baseUrl}/v1/turnos/${encodeURIComponent(id)}`, { context: silentContext() })
      );
    } catch {
      return null;
    }
  }

  private async carregarEncerramentoAutomatico(): Promise<void> {
    const ultimo = await this.dbService.getUltimoTurno();
    this.encerramentoAutomatico.set(
      ultimo && ultimo.status === 'FECHADO' && ultimo.encerradoAutomaticamente ? ultimo : null
    );
  }

  /**
   * Carrega a composição da equipe guardada no dispositivo (funciona offline).
   */
  async loadComposicaoLocal(): Promise<ComposicaoEquipeLocal | null> {
    const local = (await this.dbService.getComposicaoEquipe()) ?? null;
    this.composicao.set(local);
    return local;
  }

  /**
   * Baixa a composição atual da equipe, as opções de colaboradores/veículos e a
   * meta do mês, e guarda no dispositivo. Também atualiza a lista local de
   * membros para que as telas reflitam a equipe confirmada no turno.
   */
  async sincronizarComposicao(): Promise<ComposicaoEquipeLocal> {
    const resp = await firstValueFrom(
      this.http.get<any>(`${this.baseUrl}/v1/turnos/minha-equipe/composicao`, { context: silentContext() })
    );
    const composicao = this.mapearComposicao(resp);
    await this.dbService.saveComposicaoEquipe(composicao);
    await this.atualizarMembrosLocais(composicao);
    if (Array.isArray(resp?.acoes) && resp.acoes.length > 0) {
      const acoesSalvas = resp.acoes.map((a: any) => ({
        id: a.idAcao,
        codigo: a.codigo,
        nome: a.nome,
        ativo: true,
        valor: a.valor !== undefined && a.valor !== null ? Number(a.valor) : undefined
      }));
      await this.dbService.bulkSaveAcoes(acoesSalvas);

      // Backfill valorTotalFaturado em execuções locais que estavam pendentes de precificação
      try {
        const mapaNovasAcoes = new Map<string, { valor?: number }>();
        for (const a of acoesSalvas) {
          if (a.id) {
            mapaNovasAcoes.set(a.id, a);
          }
        }
        const execs = await this.dbService.getExecucoes();
        for (const e of execs) {
          if ((e.valorTotalFaturado === undefined || e.valorTotalFaturado === null) && e.acoes?.length > 0) {
            let soma = 0;
            let temValor = false;
            for (const idAcao of e.acoes) {
              const acao = mapaNovasAcoes.get(idAcao);
              if (acao?.valor !== undefined && acao?.valor !== null) {
                soma += Number(acao.valor);
                temValor = true;
              }
            }
            if (temValor) {
              await this.dbService.saveExecucao({
                ...e,
                valorTotalFaturado: Math.round(soma * 100) / 100
              });
            }
          }
        }
      } catch (err) {
        console.warn('Erro ao atualizar valores de execuções locais existentes:', err);
      }
    }
    this.composicao.set(composicao);
    return composicao;
  }

  private mapearMetaDoCiclo(m: any): MetaDoCicloLocal | null {
    if (!m || m.valor === null || m.valor === undefined || !m.inicio || !m.fim) return null;
    return {
      periodicidade: m.periodicidade === 'MENSAL' ? 'MENSAL' : 'CICLO_COMERCIAL',
      competencia: m.competencia,
      inicio: m.inicio,
      fim: m.fim,
      valor: Number(m.valor),
      metaDiaria: m.metaDiaria === null || m.metaDiaria === undefined ? null : Number(m.metaDiaria),
      diasUteis: Number(m.diasUteis) || 0,
      realizado: Number(m.realizado) || 0,
      apuradoEm: m.apuradoEm ?? new Date().toISOString()
    };
  }

  private mapearComposicao(resp: any): ComposicaoEquipeLocal {
    const c = resp?.composicao ?? {};
    const meta = c.metaFaturamentoMensal;
    const metaDiaria = c.metaFaturamentoDiaria;
    const metaDiariaCalculada = metaDiaria !== null && metaDiaria !== undefined
      ? Number(metaDiaria)
      : (meta ? calcularMetaDiariaPadrao(Number(meta)) : null);
    return {
      idEquipe: c.idEquipe,
      codigo: c.codigo,
      veiculo: c.veiculo ? { id: c.veiculo.id, placa: c.veiculo.placa, modelo: c.veiculo.modelo } : null,
      membros: (c.membros ?? []).map((m: any) => ({
        idColaborador: m.idColaborador,
        nome: m.nome,
        funcao: m.funcao,
        responsavel: !!m.responsavel
      })),
      metaFaturamentoMensal: meta === null || meta === undefined ? null : Number(meta),
      metaFaturamentoDiaria: metaDiariaCalculada,
      metaDoCiclo: this.mapearMetaDoCiclo(resp?.metaDoCiclo),
      usuarioResponsavel: !!resp?.usuarioResponsavel,
      opcoes: {
        colaboradores: (resp?.opcoes?.colaboradores ?? []).map((o: any) => ({
          id: o.id,
          nome: o.nome,
          funcao: o.funcao,
          equipeAtual: o.equipeAtual,
          responsavelEmOutraEquipe: !!o.responsavelEmOutraEquipe
        })),
        veiculos: (resp?.opcoes?.veiculos ?? []).map((v: any) => ({ id: v.id, placa: v.placa, modelo: v.modelo }))
      },
      atualizadoEm: new Date().toISOString()
    };
  }

  private async atualizarMembrosLocais(composicao: ComposicaoEquipeLocal): Promise<void> {
    if (!composicao.membros.length) return;
    const user = this.authService.currentUser();
    const colaboradores: ColaboradorLocal[] = composicao.membros.map(m => ({
      id: m.idColaborador,
      idUsuario: m.responsavel && composicao.usuarioResponsavel ? (user?.id ?? '') : '',
      nomeCompleto: m.nome,
      funcao: m.responsavel ? 'RESPONSAVEL' : 'PARCEIRO',
      ativo: true
    }));
    await this.dbService.replaceColaboradores(colaboradores);

    const equipe = await this.dbService.getEquipe();
    if (equipe) {
      await this.dbService.saveEquipe({
        ...equipe,
        idVeiculo: composicao.veiculo?.id ?? equipe.idVeiculo,
        membrosIds: composicao.membros.map(m => m.idColaborador)
      });
    }
  }

  /**
   * Abre um novo turno para a equipe.
   * OPERAÇÃO ONLINE: Requer conectividade.
   * Valida se já existe um turno aberto para evitar duplicidade.
   */
  async abrirTurno(escolha?: ComposicaoTurnoEscolhida): Promise<void> {
    const user = this.authService.currentUser();
    if (!user || !user.equipeId) {
      throw new Error('Usuário ou equipe não identificado para abrir o turno.');
    }

    // 1. Validar se já existe um turno aberto no servidor para esta equipe
    const turnoAtivoServer = await firstValueFrom(
      this.http.get<any>(`${this.baseUrl}/v1/turnos/equipe/${user.equipeId}/ativo`, {
        headers: { 'X-Silent-Error': 'true' }
      })
    ).catch(error => {
      if (error?.status === 404) return null;
      throw error;
    });

    if (turnoAtivoServer && turnoAtivoServer.status === 'ABERTO') {
      // Se já existe, sincronizamos o estado local e não abrimos um novo
      const turnoExistente: TurnoLocal = {
        id: turnoAtivoServer.id,
        idEquipe: turnoAtivoServer.idEquipe,
        idUsuarioAbertura: turnoAtivoServer.idUsuarioAbertura,
        dataInicio: turnoAtivoServer.dataInicio,
        status: 'ABERTO'
      };
      await this.dbService.saveTurno(turnoExistente);
      this.turnoAtivo.set(turnoExistente);
      return;
    }

    // 2. Se não existe, procede com a abertura normal
    let response: any;
    try {
      response = await firstValueFrom(
        this.http.post<any>(`${this.baseUrl}/v1/turnos/abrir`, {
          idEquipe: user.equipeId,
          idVeiculo: escolha?.idVeiculo ?? null,
          idsColaboradores: escolha ? escolha.idsColaboradores : null
        })
      );
    } catch (error: any) {
      // Preserva a intenção integral para reconciliação/intake administrativo.
      // 401 continua sendo uma pendência de autenticação, mas também não perde o payload.
      await this.outboxService.enqueue({
        endpoint: '/v1/turnos/abrir',
        method: 'POST',
        payload: { idEquipe: user.equipeId, erroOrigem: error?.error?.detail ?? error?.message }
      });
      throw error;
    }

    const novoTurno: TurnoLocal = {
      id: response.id,
      idEquipe: response.idEquipe,
      idUsuarioAbertura: response.idUsuarioAbertura,
      dataInicio: response.dataInicio,
      status: 'ABERTO',
      idVeiculo: response.idVeiculo ?? null,
      idsColaboradores: response.idsColaboradores ?? []
    };

    await this.dbService.saveTurno(novoTurno);
    this.turnoAtivo.set(novoTurno);
    this.encerramentoAutomatico.set(null);

    // A composição pode ter mudado: atualiza o retrato local sem bloquear a abertura.
    if (escolha) {
      void this.sincronizarComposicao().catch(() => {});
    }
  }

  /**
   * Fecha o turno atual da equipe.
   * OPERAÇÃO ONLINE: Requer conectividade.
   */
  async fecharTurno(): Promise<void> {
    const turno = this.turnoAtivo();
    if (!turno) return;

    const dataFim = new Date().toISOString();

    try {
      // Chamada direta para a API (Online)
      await firstValueFrom(
        this.http.post<any>(`${this.baseUrl}/v1/turnos/${turno.id}/fechar`, {})
      );
      await this.dbService.fecharTurnoLocal(turno.id, dataFim);
      this.turnoAtivo.set(null);
    } catch (error: any) {
      // Se o backend retornar erro 400, 404, 409 ou 422 indicando que o turno já foi encerrado/inválido
      const status = error?.status;
      if ([400, 404, 409, 422].includes(status)) {
        console.warn(`Turno ${turno.id} não pôde ser fechado no servidor (status ${status}). Reconciliando...`);
        // A rejeiÃ§Ã£o nÃ£o prova que o servidor fechou o turno. Preserve o
        // registro local para reconciliaÃ§Ã£o e tratamento administrativo.
        await this.outboxService.enqueue({
          endpoint: `/v1/turnos/${encodeURIComponent(turno.id)}/fechar`,
          method: 'POST',
          payload: { idTurno: turno.id, dataFim, erroOrigem: error?.error?.detail ?? error?.message }
        });
        throw error;
      } else {
        await this.outboxService.enqueue({
          endpoint: `/v1/turnos/${encodeURIComponent(turno.id)}/fechar`,
          method: 'POST',
          payload: { idTurno: turno.id, dataFim, erroOrigem: error?.error?.detail ?? error?.message }
        });
        throw error;
      }
    }
  }
}
