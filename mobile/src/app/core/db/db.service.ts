import { Injectable } from '@angular/core';
import { db } from './app-db';
import { maisAntiga, ordenarMaisAntigas } from '../saldo/tempo-saldo';
import { registrarIdTrocado } from '../sync/ids-trocados';
import {
  EquipeLocal,
  BaseOperacionalLocal,
  ColaboradorLocal,
  MunicipioLocal,
  VeiculoLocal,
  MaterialLocal,
  EstoqueLocal,
  ProcessoLocal,
  AcaoComercialLocal,
  SaldoLocal,
  SaldoSerieLocal,
  SolicitacaoLocal,
  TransferenciaLocal,
  ExecucaoCampoLocal,
  TurnoLocal,
  OutboxItem,
  InspecaoLocal,
  TipoInspecaoLocal,
  TipoServicoLocal,
  ComposicaoEquipeLocal
} from './models';

@Injectable({
  providedIn: 'root'
})
export class DbService {
  // Facade simplificada para acesso ao IndexedDB via Dexie

  async clearAllData(): Promise<void> {
    await db.transaction('rw', db.tables, async () => {
      await Promise.all(db.tables.map(table => table.clear()));
    });
  }

  /** Impede que outra conta receba ou envie operações deixadas neste aparelho. */
  async prepareForLogin(nextUserId: string, currentUserId: string | null): Promise<void> {
    if (currentUserId === nextUserId) return;

    await db.transaction('rw', db.tables, async () => {
      const [outbox, turnos, execucoes, transferencias, inspecoes] = await Promise.all([
        db.outbox.toArray(),
        db.turnos.toArray(),
        db.execucoesCampo.toArray(),
        db.transferencias.toArray(),
        db.inspecoes.toArray(),
      ]);
      const hasPendingWork = outbox.some(item => item.status !== 'SINCRONIZADO') ||
        turnos.some(turno => turno.status === 'ABERTO') ||
        execucoes.some(execucao => execucao.statusSync === 'LOCAL') ||
        transferencias.some(transferencia => transferencia.statusSync !== 'SINCRONIZADO') ||
        inspecoes.some(inspecao => inspecao.statusSync !== 'SINCRONIZADO');

      if (hasPendingWork) {
        throw new Error('Este aparelho contém um turno ou operações pendentes de outra conta. Entre com a conta da equipe anterior e sincronize antes de trocar de usuário.');
      }

      await Promise.all(db.tables.map(table => table.clear()));
    });
  }

  // Equipe
  async saveEquipe(equipe: EquipeLocal): Promise<void> {
    await db.equipe.put(equipe);
  }
  async getEquipe(): Promise<EquipeLocal | undefined> {
    return db.equipe.toCollection().first();
  }
  async replaceEquipes(equipes: EquipeLocal[]): Promise<void> {
    await db.transaction('rw', db.equipe, async () => {
      await db.equipe.clear();
      await db.equipe.bulkPut(equipes);
    });
  }
  async getEquipes(): Promise<EquipeLocal[]> {
    return db.equipe.toArray();
  }

  // Bases operacionais
  async replaceBasesOperacionais(bases: BaseOperacionalLocal[]): Promise<void> {
    await db.transaction('rw', db.basesOperacionais, async () => {
      await db.basesOperacionais.clear();
      await db.basesOperacionais.bulkPut(bases);
    });
  }

  async getBasesOperacionaisAtivas(): Promise<BaseOperacionalLocal[]> {
    const bases = await db.basesOperacionais.filter(base => base.ativo).toArray();
    return bases.sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
  }

  // Colaboradores
  async bulkSaveColaboradores(colaboradores: ColaboradorLocal[]): Promise<void> {
    await db.colaboradores.bulkPut(colaboradores);
  }
  async clearColaboradores(): Promise<void> {
    await db.colaboradores.clear();
  }
  async replaceColaboradores(colaboradores: ColaboradorLocal[]): Promise<void> {
    await db.transaction('rw', db.colaboradores, async () => {
      await db.colaboradores.clear();
      await db.colaboradores.bulkPut(colaboradores);
    });
  }
  async getColaboradoresAtivos(): Promise<ColaboradorLocal[]> {
    return db.colaboradores.filter(c => c.ativo).toArray();
  }

  async getMembrosEquipe(): Promise<ColaboradorLocal[]> {
    return db.colaboradores.toArray();
  }

  // Config/Sync stats
  async setConfig(key: string, value: any): Promise<void> {
    await db.config.put({ key, value });
  }

  async getConfig(key: string): Promise<any> {
    const item = await db.config.get(key);
    return item?.value;
  }

  // Municipios
  async bulkSaveMunicipios(municipios: MunicipioLocal[]): Promise<void> {
    await db.municipios.bulkPut(municipios);
  }
  async replaceMunicipios(municipios: MunicipioLocal[]): Promise<void> {
    await db.transaction('rw', db.municipios, async () => {
      await db.municipios.clear();
      await db.municipios.bulkPut(municipios);
    });
  }
  async getMunicipios(): Promise<MunicipioLocal[]> {
    // `ativo` é boolean — Dexie não indexa booleans, então filtramos em memória.
    return db.municipios.filter(m => m.ativo).toArray();
  }

  // Turnos
  async saveTurno(turno: TurnoLocal): Promise<void> {
    await db.turnos.put(turno);
  }

  async getTurnoAtivo(): Promise<TurnoLocal | undefined> {
    return db.turnos.where('status').equals('ABERTO').first();
  }

  async fecharTurnoLocal(
    id: string,
    dataFim: string,
    encerramento?: Pick<TurnoLocal, 'encerradoAutomaticamente' | 'motivoEncerramento'>
  ): Promise<void> {
    await db.turnos.update(id, { status: 'FECHADO', dataFim, ...encerramento });
  }

  /** Turno mais recente do aparelho (aberto ou fechado). */
  async getUltimoTurno(): Promise<TurnoLocal | undefined> {
    const turnos = await db.turnos.toArray();
    return turnos.sort((a, b) => b.dataInicio.localeCompare(a.dataInicio))[0];
  }

  // Dashboard Stats
  async countExecucoesHoje(): Promise<number> {
    // Dia local do aparelho (não UTC): depois das 21 h ainda é "hoje".
    const hoje = new Date().toDateString();
    return db.execucoesCampo
      .filter(ex => new Date(ex.dataLancamento).toDateString() === hoje)
      .count();
  }

  async countSolicitacoesHoje(): Promise<number> {
    const today = new Date().toISOString().split('T')[0];
    return db.solicitacoes
      .filter(s => !!s.dataSolicitacao && s.dataSolicitacao.startsWith(today))
      .count();
  }

  async countInspecoesHoje(): Promise<number> {
    const today = new Date().toISOString().split('T')[0];
    return db.inspecoes
      .filter(i => !!i.dataRealizacao && i.dataRealizacao.startsWith(today))
      .count();
  }

  async saveInspecao(inspecao: InspecaoLocal): Promise<void> {
    await db.inspecoes.put(inspecao);
  }

  async getInspecoes(): Promise<InspecaoLocal[]> {
    return db.inspecoes
      .orderBy('dataRealizacao')
      .reverse()
      .toArray();
  }

  async getInspecao(id: string): Promise<InspecaoLocal | undefined> {
    return db.inspecoes.get(id);
  }

  async deleteInspecao(id: string): Promise<void> {
    await db.inspecoes.delete(id);
  }

  async replaceInspecoesSincronizadas(novas: InspecaoLocal[]): Promise<void> {
    await db.transaction('rw', [db.inspecoes, db.outbox], async () => {
      const outbox = await db.outbox.toArray();
      const pendingIds = new Set(
        outbox
          .filter(o => o.status !== 'SINCRONIZADO' && o.endpoint?.includes('inspecoes'))
          .map(o => o.payload?.id || o.payload?.tempId)
          .filter(Boolean)
      );
      const serverIds = new Set(novas.map(e => e.id));

      const allLocal = await db.inspecoes.toArray();
      const toDelete = allLocal
        .filter(e => e.statusSync === 'SINCRONIZADO' && !pendingIds.has(e.id) && !serverIds.has(e.id))
        .map(e => e.id);

      if (toDelete.length > 0) {
        await db.inspecoes.bulkDelete(toDelete);
      }
      await db.inspecoes.bulkPut(novas);
    });
  }

  async countMembrosAtivos(): Promise<number> {
    return db.colaboradores.filter(c => c.ativo).count();
  }

  /**
   * @param opts.incluirInspecoes quando `false`, o feed não traz inspeções.
   * O encarregado de campo não opera esse módulo — e num tablet compartilhado
   * podem restar inspeções cacheadas de uma sessão administrativa anterior.
   */
  async getAtividadesRecentes(
    limit: number = 5,
    opts: { incluirInspecoes?: boolean } = {}
  ): Promise<any[]> {
    const { incluirInspecoes = true } = opts;

    const solicitacoes = await db.solicitacoes
      .orderBy('dataSolicitacao')
      .reverse()
      .limit(limit)
      .toArray();

    const execucoes = await db.execucoesCampo
      .orderBy('dataLancamento')
      .reverse()
      .limit(limit)
      .toArray();

    const inspecoes = incluirInspecoes
      ? await db.inspecoes
          .orderBy('dataRealizacao')
          .reverse()
          .limit(limit)
          .toArray()
      : [];

    // Combine and sort by date
    const combined = [
      ...solicitacoes.map(s => ({ ...s, tipo: 'SOLICITACAO', data: s.dataSolicitacao })),
      ...execucoes.map(e => ({ ...e, tipo: 'EXECUCAO', data: e.dataLancamento })),
      ...inspecoes.map(i => ({ ...i, tipo: 'INSPECAO', data: i.dataRealizacao }))
    ];

    return combined
      .sort((a, b) => new Date(b.data || 0).getTime() - new Date(a.data || 0).getTime())
      .slice(0, limit);
  }

  // Veiculos
  async bulkSaveVeiculos(veiculos: VeiculoLocal[]): Promise<void> {
    await db.veiculos.bulkPut(veiculos);
  }
  async replaceVeiculos(veiculos: VeiculoLocal[]): Promise<void> {
    await db.transaction('rw', db.veiculos, async () => {
      await db.veiculos.clear();
      await db.veiculos.bulkPut(veiculos);
    });
  }

  async getVeiculos(): Promise<VeiculoLocal[]> {
    return db.veiculos.toArray();
  }

  // Processos
  async bulkSaveProcessos(processos: ProcessoLocal[]): Promise<void> {
    await db.processos.bulkPut(processos);
  }
  async replaceProcessos(processos: ProcessoLocal[]): Promise<void> {
    await db.transaction('rw', db.processos, async () => {
      await db.processos.clear();
      await db.processos.bulkPut(processos);
    });
  }
  async getProcessos(): Promise<ProcessoLocal[]> {
    return db.processos.toArray();
  }

  // Acoes
  async bulkSaveAcoes(acoes: AcaoComercialLocal[]): Promise<void> {
    await db.acoes.bulkPut(acoes);
  }
  async replaceAcoes(acoes: AcaoComercialLocal[]): Promise<void> {
    await db.transaction('rw', db.acoes, async () => {
      await db.acoes.clear();
      await db.acoes.bulkPut(acoes);
    });
  }

  async getAcoes(): Promise<AcaoComercialLocal[]> {
    return db.acoes.toArray();
  }

  async getAcoesAtivas(): Promise<AcaoComercialLocal[]> {
    return db.acoes.filter(a => a.ativo).toArray();
  }

  // Tipos de serviço
  async replaceTiposServico(tipos: TipoServicoLocal[]): Promise<void> {
    await db.transaction('rw', db.tiposServico, async () => {
      await db.tiposServico.clear();
      await db.tiposServico.bulkPut(tipos);
    });
  }

  /** Tipos disponíveis para lançar, em ordem alfabética. */
  async getTiposServicoAtivos(): Promise<TipoServicoLocal[]> {
    const tipos = await db.tiposServico.filter(t => t.ativo).toArray();
    return tipos.sort((a, b) => a.descricao.localeCompare(b.descricao, 'pt-BR'));
  }

  // Materiais
  async bulkSaveMateriais(materiais: MaterialLocal[]): Promise<void> {
    await db.materiais.bulkPut(materiais);
  }
  async replaceMateriais(materiais: MaterialLocal[]): Promise<void> {
    await db.transaction('rw', db.materiais, async () => {
      await db.materiais.clear();
      await db.materiais.bulkPut(materiais);
    });
  }

  async getMateriais(): Promise<MaterialLocal[]> {
    return db.materiais.toArray();
  }

  // Estoques
  async bulkSaveEstoques(estoques: EstoqueLocal[]): Promise<void> {
    await db.estoque.bulkPut(estoques);
  }
  async replaceEstoques(estoques: EstoqueLocal[]): Promise<void> {
    await db.transaction('rw', db.estoque, async () => {
      await db.estoque.clear();
      await db.estoque.bulkPut(estoques);
    });
  }

  async getEstoques(): Promise<EstoqueLocal[]> {
    return db.estoque.toArray();
  }

  async getEstoquesDisponiveis(idEstoqueEquipe: string): Promise<EstoqueLocal[]> {
    return db.estoque
      .filter(e => e.id !== idEstoqueEquipe)
      .toArray();
  }

  /**
   * Registra uma transferência de estoque offline:
   * 1. Deduz o saldo local da equipe.
   * 2. Enfileira a operação no Outbox.
   */
  async registrarTransferenciaOffline(
    idEstoqueOrigem: string,
    idEstoqueDestino: string,
    itens: { idMaterial: string, quantidade: number, numSerie: string | null, condicao: 'NOVO' | 'SUCATA' }[],
    assinaturaEntregador?: string,
    assinaturaRecebedor?: string
  ): Promise<void> {
    const tempId = crypto.randomUUID();
    
    await db.transaction('rw', [db.saldos, db.saldosSeriais, db.outbox, db.transferencias], async () => {
      // 1. Deduzir saldos locais
      for (const item of itens) {
        await this.deduzirSaldoLocal(
          idEstoqueOrigem,
          item.idMaterial,
          item.quantidade,
          item.condicao,
          item.numSerie ? [item.numSerie] : []
        );
      }

      // 2. Criar registro local para histórico
      const local: TransferenciaLocal = {
        id: tempId,
        idEstoqueOrigem,
        idEstoqueDestino,
        dataTransferencia: new Date().toISOString(),
        itens,
        assinaturaEntregador,
        assinaturaRecebedor,
        statusSync: 'PENDENTE'
      };
      await db.transferencias.add(local);

      // 3. Criar item no Outbox
      const outboxItem: OutboxItem = {
        id: crypto.randomUUID(),
        endpoint: '/v1/movimentacoes-estoque/transferencia',
        method: 'POST',
        payload: {
          idEstoqueOrigem,
          idEstoqueDestino,
          itens,
          assinaturaEntregador,
          assinaturaRecebedor,
          tempId // Para vincular ao registro local no sync
        },
        status: 'PENDENTE',
        tentativas: 0,
        criadoEm: new Date()
      };

      await db.outbox.add(outboxItem);
    });
  }

  // Formulários de Inspeção
  async replaceFormularios(formularios: any[]): Promise<void> {
    await db.transaction('rw', db.formularios, async () => {
      await db.formularios.clear();
      await db.formularios.bulkPut(formularios);
    });
  }

  async getFormularios(): Promise<any[]> {
    return await db.formularios.toArray();
  }

  async getFormularioById(id: string): Promise<any | undefined> {
    return await db.formularios.get(id);
  }

  // Tipos de Inspeção
  async bulkSaveTiposInspecao(tipos: TipoInspecaoLocal[]): Promise<void> {
    await db.tiposInspecao.bulkPut(tipos);
  }

  async replaceTiposInspecao(tipos: TipoInspecaoLocal[]): Promise<void> {
    await db.transaction('rw', db.tiposInspecao, async () => {
      await db.tiposInspecao.clear();
      await db.tiposInspecao.bulkPut(tipos);
    });
  }

  async getTiposInspecao(): Promise<TipoInspecaoLocal[]> {
    const tipos = await db.tiposInspecao.toArray();
    return tipos.sort((a, b) => a.ordem - b.ordem);
  }

  async getTiposInspecaoAtivos(): Promise<TipoInspecaoLocal[]> {
    const tipos = await db.tiposInspecao.filter(t => t.ativo).toArray();
    return tipos.sort((a, b) => a.ordem - b.ordem);
  }

  async getTipoInspecaoById(id: string): Promise<TipoInspecaoLocal | undefined> {
    return db.tiposInspecao.get(id);
  }

  // Saldos
  async bulkSaveSaldos(saldos: SaldoLocal[]): Promise<void> {
    await db.saldos.bulkPut(saldos);
  }

  async clearSaldos(): Promise<void> {
    await db.saldos.clear();
  }

  /**
   * Substitui todos os saldos de forma atômica para evitar flicker na UI reativa.
   */
  async replaceSaldos(saldos: SaldoLocal[]): Promise<void> {
    await db.transaction('rw', db.saldos, async () => {
      await db.saldos.clear();
      await db.saldos.bulkPut(saldos);
    });
  }

  async getSaldos(): Promise<SaldoLocal[]> {
    return db.saldos.toArray();
  }

  // Saldos Seriais
  async bulkSaveSaldosSeriais(seriais: SaldoSerieLocal[]): Promise<void> {
    await db.saldosSeriais.bulkPut(seriais);
  }

  async clearSaldosSeriais(): Promise<void> {
    await db.saldosSeriais.clear();
  }

  /**
   * Substitui todos os seriais de forma atômica para evitar flicker na UI reativa.
   */
  async replaceSaldosSeriais(seriais: SaldoSerieLocal[]): Promise<void> {
    await db.transaction('rw', db.saldosSeriais, async () => {
      await db.saldosSeriais.clear();
      await db.saldosSeriais.bulkPut(seriais);
    });
  }

  async deduzirSaldoLocal(idEstoque: string, idMaterial: string, quantidade: number, condicao: 'NOVO' | 'SUCATA', numSeries?: string[]): Promise<void> {
    const saldos = await db.saldos
      .where('idEstoque').equals(idEstoque)
      .and(s => s.idMaterial === idMaterial && s.condicao === condicao)
      .toArray();

    if (saldos.length > 0) {
      const saldo = saldos[0];
      saldo.quantidade = Math.max(0, saldo.quantidade - quantidade);
      await db.saldos.put(saldo);
    }

    if (numSeries && numSeries.length > 0) {
      const seriais = await db.saldosSeriais
        .where('idEstoque').equals(idEstoque)
        .and(s => s.idMaterial === idMaterial && s.condicao === condicao && numSeries.includes(s.numSerie))
        .toArray();

      for (const serie of seriais) {
        await db.saldosSeriais.delete(serie.id);
      }
    }
  }

  /**
   * Devolve ao saldo local o material que saiu de uma nota alterada no aparelho. O
   * servidor faz o estorno de verdade; aqui é só a projeção até o próximo sync.
   */
  async devolverSaldoLocal(idEstoque: string, idMaterial: string, quantidade: number, condicao: 'NOVO' | 'SUCATA'): Promise<void> {
    const saldo = await db.saldos
      .where('idEstoque').equals(idEstoque)
      .and(s => s.idMaterial === idMaterial && s.condicao === condicao)
      .first();
    if (saldo) {
      saldo.quantidade = saldo.quantidade + quantidade;
      await db.saldos.put(saldo);
    }
  }

  async getSeriaisDoEstoque(idEstoque: string): Promise<SaldoSerieLocal[]> {
    return db.saldosSeriais.where('idEstoque').equals(idEstoque).toArray();
  }

  async getSeriaisPorMaterial(idEstoque: string, idMaterial: string, condicao?: 'NOVO' | 'SUCATA'): Promise<SaldoSerieLocal[]> {
    return db.saldosSeriais
      .where('idEstoque')
      .equals(idEstoque)
      .filter(s => s.idMaterial === idMaterial && s.ativo && (!condicao || s.condicao === condicao))
      .toArray()
      // A mais antiga primeiro: é a que deve sair antes.
      .then(ordenarMaisAntigas);
  }

  // Outbox
  async enqueueOutbox(item: OutboxItem): Promise<string> {
    return db.outbox.add(item) as Promise<string>;
  }
  async getPendingOutbox(): Promise<OutboxItem[]> {
    return db.outbox
      .where('status')
      .anyOf(['PENDENTE', 'ERRO', 'RESULTADO_INCERTO'])
      .toArray();
  }

  // Solicitacoes
  async getSolicitacoes(): Promise<SolicitacaoLocal[]> {
    return db.solicitacoes
      .orderBy('dataSolicitacao')
      .reverse()
      .toArray();
  }

  async getSolicitacaoById(id: string): Promise<SolicitacaoLocal | undefined> {
    return db.solicitacoes.get(id);
  }

  async saveSolicitacao(solicitacao: SolicitacaoLocal): Promise<void> {
    await db.solicitacoes.put(solicitacao);
  }

  async deleteSolicitacao(id: string): Promise<void> {
    await db.solicitacoes.delete(id);
  }

  /**
   * Substitui as solicitações sincronizadas pelo servidor, preservando as pendentes no outbox.
   */
  async replaceSolicitacoesSincronizadas(novas: SolicitacaoLocal[]): Promise<void> {
    await db.transaction('rw', [db.solicitacoes, db.outbox], async () => {
      const outbox = await db.outbox.toArray();
      const pendingIds = new Set(
        outbox
          .filter(o => o.status !== 'SINCRONIZADO' && (o.endpoint?.includes('solicitacoes') || o.payload?.tempId))
          .map(o => o.payload?.id || o.payload?.tempId)
          .filter(Boolean)
      );
      const serverIds = new Set(novas.map(s => s.id));

      const allLocal = await db.solicitacoes.toArray();
      const toDelete = allLocal
        .filter(s => s.status !== 'PENDENTE' && !pendingIds.has(s.id) && !serverIds.has(s.id))
        .map(s => s.id);

      if (toDelete.length > 0) {
        await db.solicitacoes.bulkDelete(toDelete);
      }
      await db.solicitacoes.bulkPut(novas);
    });
  }

  // Transferencias
  async getTransferencias(): Promise<TransferenciaLocal[]> {
    return db.transferencias
      .orderBy('dataTransferencia')
      .reverse()
      .toArray();
  }

  async getTransferenciaById(id: string): Promise<TransferenciaLocal | undefined> {
    return db.transferencias.get(id);
  }

  async saveTransferencia(transferencia: TransferenciaLocal): Promise<void> {
    await db.transferencias.put(transferencia);
  }

  async deleteTransferencia(id: string): Promise<void> {
    await db.transferencias.delete(id);
  }

  /**
   * Substitui as transferências sincronizadas pelo servidor, preservando as pendentes no outbox
   * e mantendo as assinaturas digitais já coletadas localmente.
   */
  async replaceTransferenciasSincronizadas(novas: TransferenciaLocal[]): Promise<void> {
    await db.transaction('rw', [db.transferencias, db.outbox], async () => {
      const outbox = await db.outbox.toArray();
      const pendingIds = new Set(
        outbox
          .filter(o => o.status !== 'SINCRONIZADO' && o.endpoint?.includes('movimentacoes-estoque/transferencia'))
          .map(o => o.payload?.id || o.payload?.tempId)
          .filter(Boolean)
      );
      const serverIds = new Set(novas.map(t => t.id));

      const allLocal = await db.transferencias.toArray();
      const localMap = new Map(allLocal.map(t => [t.id, t]));
      const unmappedLocals = allLocal.filter(l => !serverIds.has(l.id) && !pendingIds.has(l.id));

      const toDelete = allLocal
        .filter(t => t.statusSync === 'SINCRONIZADO' && !pendingIds.has(t.id) && !serverIds.has(t.id))
        .map(t => t.id);

      if (toDelete.length > 0) {
        await db.transferencias.bulkDelete(toDelete);
      }

      const mergedNovas = novas.map(nova => {
        let local = localMap.get(nova.id);
        if (!local) {
          local = unmappedLocals.find(u =>
            u.idEstoqueOrigem === nova.idEstoqueOrigem &&
            u.idEstoqueDestino === nova.idEstoqueDestino &&
            (u.assinaturaEntregador?.startsWith('data:image/') || u.assinaturaRecebedor?.startsWith('data:image/'))
          );
        }

        const assinaturaEntregador = (local?.assinaturaEntregador?.startsWith('data:image/') ? local.assinaturaEntregador : undefined)
          || nova.assinaturaEntregador
          || local?.assinaturaEntregador;
        const assinaturaRecebedor = (local?.assinaturaRecebedor?.startsWith('data:image/') ? local.assinaturaRecebedor : undefined)
          || nova.assinaturaRecebedor
          || local?.assinaturaRecebedor;

        return {
          ...nova,
          assinaturaEntregador,
          assinaturaRecebedor
        };
      });

      await db.transferencias.bulkPut(mergedNovas);
    });
  }

  // Outbox lookup helper
  async getOutboxItemBySolicitacaoId(solicitacaoId: string): Promise<OutboxItem | undefined> {
    // Busca um item que tenha o id da solicitação no payload (seja id real ou tempId)
    return db.outbox
      .filter(item =>
        item.payload?.id === solicitacaoId ||
        item.payload?.tempId === solicitacaoId
      )
      .first();
  }

  async getOutboxItemByTransferenciaId(transferenciaId: string): Promise<OutboxItem | undefined> {
    return db.outbox
      .filter(item =>
        item.endpoint === '/v1/movimentacoes-estoque/transferencia' &&
        item.payload?.tempId === transferenciaId
      )
      .first();
  }

  // Execucoes Comerciais (Campo)
  async getExecucoes(): Promise<ExecucaoCampoLocal[]> {
    return db.execucoesCampo
      .orderBy('dataLancamento')
      .reverse()
      .toArray();
  }

  async getExecucaoById(id: string): Promise<ExecucaoCampoLocal | undefined> {
    return db.execucoesCampo.get(id);
  }

  async saveExecucao(execucao: ExecucaoCampoLocal): Promise<void> {
    await db.execucoesCampo.put(execucao);
  }

  async deleteExecucao(id: string): Promise<void> {
    await db.execucoesCampo.delete(id);
  }

  /**
   * Substitui as execuções sincronizadas pelo servidor, preservando as pendentes no outbox.
   */
  async replaceExecucoesSincronizadas(novas: ExecucaoCampoLocal[]): Promise<void> {
    await db.transaction('rw', [db.execucoesCampo, db.outbox], async () => {
      const outbox = await db.outbox.toArray();
      const pendingIds = new Set(
        outbox
          .filter(o => o.status !== 'SINCRONIZADO' && o.endpoint?.includes('execucoes-comerciais'))
          .map(o => o.payload?.id || o.payload?.tempId)
          .filter(Boolean)
      );
      const serverIds = new Set(novas.map(e => e.id));

      const allLocal = await db.execucoesCampo.toArray();
      const toDelete = allLocal
        .filter(e => e.statusSync === 'SINCRONIZADO' && !pendingIds.has(e.id) && !serverIds.has(e.id))
        .map(e => e.id);

      // Lançamento que chegou ao servidor por outro caminho (ex.: o escritório tratou a
      // operação recusada e reprocessou o envio original): o servidor criou a nota com
      // id próprio e o registro temporário do aparelho ficaria duplicado. É a mesma nota
      // quando bate equipe, número e o instante do lançamento, que o reprocessamento
      // preserva. Só sai o temporário cujo envio já não pode partir (resolvido ou fora
      // da fila); um envio ainda pendente nunca é apagado.
      const filaDoTemporario = new Map(
        outbox.filter(o => o.endpoint === '/_execucoes-comerciais/lancar' && o.payload?.tempId)
          .map(o => [o.payload.tempId as string, o] as const)
      );
      for (const local of allLocal) {
        if (local.statusSync === 'SINCRONIZADO' || serverIds.has(local.id)) continue;
        const fila = filaDoTemporario.get(local.id);
        if (fila && fila.status !== 'SINCRONIZADO' && fila.receiptStatus !== 'RESOLVED') continue;
        const doServidor = novas.find(n => mesmaNotaLancada(n, local));
        if (!doServidor) continue;
        toDelete.push(local.id);
        if (fila) await db.outbox.delete(fila.id);
        registrarIdTrocado(local.id, doServidor.id);
      }

      if (toDelete.length > 0) {
        await db.execucoesCampo.bulkDelete(toDelete);
      }
      // Nota com alteração ainda na fila: a versão do servidor é a anterior à correção
      // e não pode apagar o que a equipe mudou no aparelho.
      const comAlteracao = new Set(allLocal.filter(e => e.alteracaoPendente && pendingIds.has(e.id)).map(e => e.id));
      await db.execucoesCampo.bulkPut(novas.filter(e => !comAlteracao.has(e.id)));
    });
  }

  /** Alteração de nota sincronizada ainda na fila (ou retida com erro). */
  async getOutboxAlteracaoByExecucaoId(execucaoId: string): Promise<OutboxItem | undefined> {
    return db.outbox
      .filter(item =>
        item.endpoint === '/_execucoes-comerciais/alterar' &&
        item.payload?.tempId === execucaoId &&
        item.status !== 'SINCRONIZADO'
      )
      .first();
  }

  async getOutboxItemByExecucaoId(execucaoId: string): Promise<OutboxItem | undefined> {
    return db.outbox
      .filter(item =>
        item.endpoint === '/_execucoes-comerciais/lancar' &&
        item.payload?.tempId === execucaoId
      )
      .first();
  }

  // Saldos com Materiais (Join)
  async getSaldosComMateriais(idEstoque: string): Promise<any[]> {
    const saldos = await db.saldos
      .where('idEstoque')
      .equals(idEstoque)
      .and(s => s.ativo && s.quantidade > 0)
      .toArray();

    const [materiais, seriais] = await Promise.all([
      db.materiais.toArray(),
      db.saldosSeriais.where('idEstoque').equals(idEstoque).filter(s => s.ativo).toArray(),
    ]);
    const materiaisMap = new Map(materiais.map(m => [m.id, m]));
    // Material com série: a idade que importa é a da série há mais tempo no caminhão.
    const datasSeries = new Map<string, (string | null | undefined)[]>();
    for (const s of seriais) {
      const chave = `${s.idMaterial}|${s.condicao}`;
      const datas = datasSeries.get(chave) ?? [];
      datas.push(s.noSaldoDesde);
      datasSeries.set(chave, datas);
    }

    return saldos.map(s => {
      const material = materiaisMap.get(s.idMaterial);
      return {
        id: s.id,
        idMaterial: s.idMaterial,
        quantidade: s.quantidade,
        condicao: s.condicao,
        ultimaMovimentacao: s.ultimaMovimentacao ?? null,
        serieMaisAntiga: maisAntiga(datasSeries.get(`${s.idMaterial}|${s.condicao}`) ?? []),
        codigo: material?.codigo || 'N/A',
        descricao: material?.descricao || 'Material não encontrado',
        unidadeMedida: material?.unidadeMedida || 'UN',
        detalhaSerie: material?.detalhaSerie || false
      };
    }).sort((a, b) => a.descricao.localeCompare(b.descricao) || a.condicao.localeCompare(b.condicao));
  }

  // Composição da equipe (conferência na abertura do turno)
  async saveComposicaoEquipe(composicao: ComposicaoEquipeLocal): Promise<void> {
    await this.setConfig('composicaoEquipe', composicao);
  }

  async getComposicaoEquipe(): Promise<ComposicaoEquipeLocal | undefined> {
    return this.getConfig('composicaoEquipe');
  }

  // ... Outros métodos serão adicionados conforme a necessidade das features
}

/** Mesma nota lançada: mesma equipe, mesmo número e o mesmo instante de lançamento (tolerância de 1 s). */
function mesmaNotaLancada(a: ExecucaoCampoLocal, b: ExecucaoCampoLocal): boolean {
  if (a.idEquipe && b.idEquipe && a.idEquipe !== b.idEquipe) return false;
  const numero = (e: ExecucaoCampoLocal) => (e.referenciaProvisoria ?? e.referenciaNota ?? '').trim().toUpperCase();
  // Avulsa: o número oficial pode ter trocado a referência no servidor; o provisório fica.
  if (numero(a) !== numero(b)) return false;
  const ta = new Date(a.dataLancamento).getTime();
  const tb = new Date(b.dataLancamento).getTime();
  return Number.isFinite(ta) && Number.isFinite(tb) && Math.abs(ta - tb) < 1000;
}
