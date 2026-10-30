import { Injectable, inject } from '@angular/core';
import { DbService } from '../db/db.service';
import { CorrecaoRecusadaError, OutboxService } from '../sync/outbox.service';
import { TurnoService } from '../turno/turno.service';
import { TokenService } from '../auth/token.service';
import { ExecucaoCampoLocal, ExecucaoMaterialLocal, TipoNota, TipoServicoLocal } from '../db/models';
import {
  PRAZO_ALTERACAO_CAMPO_DIAS,
  SaldoEquipeSnapshot,
  chaveSerie,
  diferencaDaNota,
  gerarNumeroAvulsa,
  movimentaSaldo,
  podeAlterarNoCampo,
  turnoVencido
} from './campo-regras';


/**
 * Lançamento de campo em etapa única: a execução é registrada de uma vez, com uma
 * única localização (coletada automaticamente pelo GPS do aparelho, que funciona
 * mesmo sem internet). Não há município, nem "iniciar/finalizar".
 */
export interface NovaExecucaoPayload {
  referenciaNota: string;
  tipoNota: TipoNota;
  /** Obrigatório: o tipo de serviço da nota, escolhido junto com o número. */
  tipoServico: Pick<TipoServicoLocal, 'id' | 'codigo' | 'descricao'>;
  acoes: string[];
  materiais: ExecucaoMaterialLocal[];
  latitude?: number;
  longitude?: number;
}

/**
 * Endpoint sentinela do outbox para o lançamento de execução em etapa única.
 * O processamento real é orquestrado pelo SyncService (POST /v1/execucoes-comerciais).
 */
export const EXECUCAO_LANCAR_ENDPOINT = '/_execucoes-comerciais/lancar';

/**
 * Endpoint sentinela da alteração de uma nota já sincronizada. O SyncService envia
 * PUT /v1/execucoes-comerciais/{id}/lancamentos com a nota inteira e o motivo.
 */
export const EXECUCAO_ALTERAR_ENDPOINT = '/_execucoes-comerciais/alterar';

/** Como a nota deve ficar depois da alteração feita em campo. */
export interface AlteracaoExecucaoPayload {
  acoes: string[];
  materiais: ExecucaoMaterialLocal[];
  motivo: string;
  /** Número corrigido; ausente ou igual mantém o da nota. */
  referenciaNota?: string;
}

@Injectable({
  providedIn: 'root'
})
export class ExecucoesService {
  private readonly dbService = inject(DbService);
  private readonly outboxService = inject(OutboxService);
  private readonly turnoService = inject(TurnoService);
  private readonly tokenService = inject(TokenService);

  /**
   * Tenta obter coordenadas GPS em até 15s; falha silenciosamente
   * (retorna undefined) se o usuário negar ou se o dispositivo não tiver suporte.
   */
  async captureGps(): Promise<{ latitude: number; longitude: number; precisao?: number } | undefined> {
    if (typeof navigator === 'undefined' || !navigator.geolocation) return undefined;
    // O GPS do aparelho não depende de internet: funciona offline normalmente.
    return new Promise(resolve => {
      navigator.geolocation.getCurrentPosition(
        pos => resolve({ latitude: pos.coords.latitude, longitude: pos.coords.longitude, precisao: pos.coords.accuracy }),
        () => resolve(undefined),
        { enableHighAccuracy: true, maximumAge: 60_000, timeout: 15_000 }
      );
    });
  }

  /**
   * Lança a execução de campo: grava no dispositivo já como CONCLUIDO e enfileira
   * o envio único (POST /v1/execucoes-comerciais). O envio efetivo acontece no
   * SyncService quando houver conexão.
   *
   * Materiais fora do saldo são aceitos: o servidor gera as pendências de saldo ou
   * ao processar o lançamento (aplicação fora do saldo).
   * Retorna o id local (tempId) da execução.
   */
  async salvarExecucao(novo: NovaExecucaoPayload, opts: { editId?: string | null } = {}): Promise<string> {
    const turno = this.turnoService.turnoAtivo();
    if (!turno) {
      throw new Error('Não há turno aberto. Abra um turno antes de registrar uma execução.');
    }
    if (turnoVencido(turno.dataInicio)) {
      // O app ficou aberto desde ontem: o turno passou do corte e o servidor recusaria a nota.
      await this.turnoService.loadTurnoAtivo();
      throw new Error('O turno anterior foi encerrado no horário de corte. Abra o turno de hoje e lance de novo: seus dados continuam aqui.');
    }
    const idEquipe = this.tokenService.equipeId();
    if (!idEquipe) {
      throw new Error('Equipe não identificada no token de acesso.');
    }
    if (!novo.tipoServico?.id) {
      throw new Error('Escolha o tipo de serviço da nota.');
    }

    // Edição: o registro antigo permanece até a nova intenção ser persistida.
    const id = crypto.randomUUID();
    const dataLancamento = new Date().toISOString();

    // Calcula o valor estimado faturado a partir das ações salvas localmente no aparelho
    let valorEstimado: number | undefined = undefined;
    if (novo.acoes && novo.acoes.length > 0) {
      try {
        const todasAcoes = await this.dbService.getAcoes();
        const acoesMap = new Map((todasAcoes || []).map(a => [a.id, a]));
        let soma = 0;
        let temValor = false;
        for (const idAcao of novo.acoes) {
          const acao = acoesMap.get(idAcao);
          if (acao?.valor !== undefined && acao?.valor !== null) {
            soma += Number(acao.valor);
            temValor = true;
          }
        }
        if (temValor) {
          valorEstimado = Math.round(soma * 100) / 100;
        }
      } catch (err) {
        console.warn('Não foi possível obter valores das ações para cálculo local:', err);
      }
    }

    const execucao: ExecucaoCampoLocal = {
      id,
      referenciaNota: novo.referenciaNota,
      tipoNota: novo.tipoNota,
      referenciaProvisoria: novo.tipoNota === 'AVULSA' ? novo.referenciaNota : null,
      aguardandoNumeroOficial: novo.tipoNota === 'AVULSA',
      idTurno: turno.id,
      idEquipe,
      tipoServico: { id: novo.tipoServico.id, codigo: novo.tipoServico.codigo, descricao: novo.tipoServico.descricao },
      idMunicipio: null,
      latitude: novo.latitude,
      longitude: novo.longitude,
      dataLancamento,
      statusExecucao: 'CONCLUIDO',
      acoes: novo.acoes,
      materiais: novo.materiais,
      statusSync: 'LOCAL',
      valorTotalFaturado: valorEstimado
    };

    const antigo = opts.editId ? await this.dbService.getOutboxItemByExecucaoId(opts.editId) : undefined;
    await this.outboxService.enqueueExecucao({
      endpoint: EXECUCAO_LANCAR_ENDPOINT,
      method: 'POST',
      payload: {
        tempId: execucao.id,
        body: this.montarCorpo(execucao)
      }
    }, execucao, opts.editId ?? undefined, antigo?.id);

    // A operação e a intenção de envio já estão comprometidas no IndexedDB.
    // Só então aplicamos a projeção local do estoque; uma falha aqui não perde
    // a execução, que continuará disponível para reconciliação.
    const idEstoque = this.tokenService.estoqueId();
    if (idEstoque) {
      for (const mat of novo.materiais) {
        if (mat.operacao === 'APLICADO' && movimentaSaldo(mat)) {
          await this.dbService.deduzirSaldoLocal(idEstoque, mat.idMaterial, mat.quantidade, mat.condicao, mat.numSerie ? [mat.numSerie] : undefined);
        }
      }
    }

    return id;
  }

  /**
   * Altera uma nota já sincronizada: grava a nota corrigida no aparelho e enfileira
   * o envio. Funciona offline; o servidor confere o prazo pela hora da alteração.
   * Uma segunda alteração antes do envio substitui a primeira na fila.
   */
  async alterarExecucao(id: string, alteracao: AlteracaoExecucaoPayload): Promise<void> {
    const exec = await this.dbService.getExecucaoById(id);
    if (!exec) throw new Error('Esta nota não está mais no aparelho. Sincronize e tente de novo.');
    if (!podeAlterarNoCampo(exec, this.tokenService.equipeId())) {
      throw new Error(exec.statusSync !== 'SINCRONIZADO'
        ? 'Esta nota ainda não chegou ao servidor. Use Corrigir.'
        : `Notas com mais de ${PRAZO_ALTERACAO_CAMPO_DIAS} dias só podem ser alteradas pelo escritório.`);
    }
    const motivo = alteracao.motivo.trim();
    if (!motivo) throw new Error('Escreva o motivo da alteração.');

    const anterior = exec.alteracaoPendente?.anterior
      ?? { acoes: [...exec.acoes], materiais: structuredClone(exec.materiais), valorTotalFaturado: exec.valorTotalFaturado,
        referenciaNota: exec.referenciaNota };
    const numeroServidor = anterior.referenciaNota ?? exec.referenciaNota;
    const numero = alteracao.referenciaNota?.trim() || numeroServidor;
    const diferenca = diferencaDaNota(anterior, alteracao);
    if (!diferenca.acoesAdicionadas.length && !diferenca.acoesRemovidas.length
      && !diferenca.materiaisAdicionados.length && !diferenca.materiaisRemovidos.length && numero === numeroServidor) {
      throw new Error('Nada mudou na nota. Ajuste o número, os serviços ou os materiais.');
    }

    const alteradoEm = new Date().toISOString();
    const idOperacao = crypto.randomUUID();
    const atualizada: ExecucaoCampoLocal = {
      ...exec,
      referenciaNota: numero,
      acoes: [...alteracao.acoes],
      materiais: structuredClone(alteracao.materiais),
      valorTotalFaturado: await this.valorEstimado(alteracao.acoes) ?? exec.valorTotalFaturado,
      alteracaoPendente: { idOperacao, motivo, alteradoEm, anterior },
    };
    await this.outboxService.enqueueAlteracaoExecucao({
      endpoint: EXECUCAO_ALTERAR_ENDPOINT,
      method: 'PUT',
      payload: {
        tempId: id,
        referenciaNota: numero,
        body: this.montarCorpoAlteracao(atualizada)
      }
    }, atualizada);

    // Projeção local do saldo: só a diferença em relação ao que o aparelho mostrava.
    await this.projetarDiferencaNoSaldo({ acoes: exec.acoes, materiais: exec.materiais }, alteracao);
  }

  /**
   * Desfaz no aparelho a alteração que ainda não foi aplicada no servidor: a nota volta
   * a ser como está lá. Recusa quando a alteração pode já ter chegado.
   */
  async desfazerAlteracao(id: string): Promise<void> {
    const exec = await this.dbService.getExecucaoById(id);
    const pendente = exec?.alteracaoPendente;
    if (!exec || !pendente) return;
    const fila = await this.dbService.getOutboxAlteracaoByExecucaoId(id);
    if (fila && !(await this.outboxService.retirarAlteracao(fila))) throw new CorrecaoRecusadaError();
    await this.dbService.saveExecucao({
      ...exec,
      referenciaNota: pendente.anterior.referenciaNota ?? exec.referenciaNota,
      acoes: pendente.anterior.acoes,
      materiais: pendente.anterior.materiais,
      valorTotalFaturado: pendente.anterior.valorTotalFaturado,
      alteracaoPendente: null,
    });
    await this.projetarDiferencaNoSaldo({ acoes: exec.acoes, materiais: exec.materiais }, pendente.anterior);
  }

  /** Corpo do PUT /v1/execucoes-comerciais/{id}/lancamentos. */
  montarCorpoAlteracao(execucao: ExecucaoCampoLocal) {
    const numeroServidor = execucao.alteracaoPendente?.anterior.referenciaNota;
    return {
      // Só vai quando muda: a fila de versões anteriores do app não manda o número.
      ...(numeroServidor && execucao.referenciaNota !== numeroServidor ? { referenciaNota: execucao.referenciaNota } : {}),
      acoes: execucao.acoes,
      materiais: execucao.materiais.map(m => ({
        idMaterial: m.idMaterial,
        quantidade: m.quantidade,
        operacao: m.operacao,
        numSerie: m.numSerie ?? null,
        origem: m.origem ?? 'EQUIPE'
      })),
      motivo: execucao.alteracaoPendente?.motivo ?? '',
      idOperacao: execucao.alteracaoPendente?.idOperacao ?? null,
      alteradoEm: execucao.alteracaoPendente?.alteradoEm ?? null
    };
  }

  /** Aplicado que saiu da nota volta ao saldo local; aplicado que entrou é baixado. */
  private async projetarDiferencaNoSaldo(
    antes: { acoes: string[]; materiais: ExecucaoMaterialLocal[] },
    depois: { acoes: string[]; materiais: ExecucaoMaterialLocal[] },
  ): Promise<void> {
    const idEstoque = this.tokenService.estoqueId();
    if (!idEstoque) return;
    const d = diferencaDaNota(antes, depois);
    for (const mat of d.materiaisRemovidos) {
      if (mat.operacao === 'APLICADO' && movimentaSaldo(mat)) {
        await this.dbService.devolverSaldoLocal(idEstoque, mat.idMaterial, mat.quantidade, mat.condicao);
      }
    }
    for (const mat of d.materiaisAdicionados) {
      if (mat.operacao === 'APLICADO' && movimentaSaldo(mat)) {
        await this.dbService.deduzirSaldoLocal(idEstoque, mat.idMaterial, mat.quantidade, mat.condicao, mat.numSerie ? [mat.numSerie] : undefined);
      }
    }
  }

  /** Soma dos valores dos serviços no aparelho; o servidor recalcula pela tabela do dia. */
  private async valorEstimado(acoes: string[]): Promise<number | undefined> {
    if (!acoes.length) return 0;
    try {
      const mapa = new Map((await this.dbService.getAcoes()).map(a => [a.id, a]));
      const valores = acoes.map(id => mapa.get(id)?.valor).filter((v): v is number => v !== undefined && v !== null);
      return valores.length ? Math.round(valores.reduce((s, v) => s + Number(v), 0) * 100) / 100 : undefined;
    } catch {
      return undefined;
    }
  }

  /** Próximo número provisório de nota avulsa da equipe no dia. */
  async proximoNumeroAvulsa(): Promise<string> {
    const [composicao, execucoes] = await Promise.all([
      this.dbService.getComposicaoEquipe(),
      this.dbService.getExecucoes(),
    ]);
    const usados = execucoes.map(e => e.referenciaProvisoria ?? e.referenciaNota);
    return gerarNumeroAvulsa(composicao?.codigo, usados);
  }

  /**
   * Retrato do saldo da equipe no aparelho, usado para avisar o encarregado quando
   * o lançamento vai deixar o saldo de algum material negativo.
   */
  async carregarSaldoSnapshot(): Promise<SaldoEquipeSnapshot> {
    const idEstoque = this.tokenService.estoqueId();
    const novoPorMaterial = new Map<string, number>();
    const materiaisComSaldo = new Set<string>();
    const seriesNovas = new Set<string>();
    if (!idEstoque) return { novoPorMaterial, materiaisComSaldo, seriesNovas };

    const [saldos, seriais] = await Promise.all([
      this.dbService.getSaldos(),
      this.dbService.getSeriaisDoEstoque(idEstoque)
    ]);
    for (const s of saldos) {
      if (s.idEstoque !== idEstoque || !s.ativo) continue;
      if (s.quantidade > 0) materiaisComSaldo.add(s.idMaterial);
      if (s.condicao === 'NOVO') {
        novoPorMaterial.set(s.idMaterial, (novoPorMaterial.get(s.idMaterial) ?? 0) + Math.max(0, s.quantidade));
      }
    }
    for (const serie of seriais) {
      if (!serie.ativo) continue;
      materiaisComSaldo.add(serie.idMaterial);
      if (serie.condicao === 'NOVO') seriesNovas.add(chaveSerie(serie.idMaterial, serie.numSerie));
    }
    return { novoPorMaterial, materiaisComSaldo, seriesNovas };
  }

  /**
   * Corpo enviado ao backend. `condicao` é controle local e não faz parte do
   * contrato (MaterialItem = idMaterial, quantidade, operacao, numSerie).
   * Lançamento de etapa única: uma data e uma localização.
   */
  montarCorpo(execucao: ExecucaoCampoLocal) {
    return {
      referenciaNota: execucao.referenciaNota,
      tipoNota: execucao.tipoNota ?? 'COMUM',
      idEquipe: execucao.idEquipe,
      // Se o servidor encerrar o turno sozinho (equipe esqueceu aberto), o lançamento
      // que ainda estava na fila continua no turno em que foi feito.
      idTurno: execucao.idTurno,
      idTipoServico: execucao.tipoServico?.id ?? null,
      dataLancamento: execucao.dataLancamento,
      latitude: execucao.latitude,
      longitude: execucao.longitude,
      acoes: execucao.acoes,
      materiais: execucao.materiais.map(m => ({
        idMaterial: m.idMaterial,
        quantidade: m.quantidade,
        operacao: m.operacao,
        numSerie: m.numSerie ?? null,
        origem: m.origem ?? 'EQUIPE'
      }))
    };
  }

  /**
   * Descarta uma execução local + seu item de outbox.
   */
  async descartar(execId: string): Promise<void> {
    const outbox = await this.dbService.getOutboxItemByExecucaoId(execId);
    if (outbox) {
      const removed = await this.outboxService.discard(outbox.id);
      if (!removed) {
        throw new Error('Esta execução foi preservada e aguarda tratamento administrativo.');
      }
    }
    await this.dbService.deleteExecucao(execId);
  }

  /**
   * Re-aplica as deduções de materiais de execuções que ainda estão no outbox local.
   * Útil após um sync de saldos que pode ter sobrescrito os valores locais com
   * dados "desatualizados" do servidor (onde as execuções ainda não foram processadas).
   */
  async reaplicarDeducoesPendentes(idEstoque: string): Promise<void> {
    // 1. Re-aplicar Execuções Pendentes
    const todasExec = await this.dbService.getExecucoes();
    const execLocais = todasExec.filter(e => e.statusSync === 'LOCAL');

    for (const exec of execLocais) {
      for (const mat of exec.materiais) {
        if (mat.operacao === 'APLICADO' && movimentaSaldo(mat)) {
          await this.dbService.deduzirSaldoLocal(
            idEstoque,
            mat.idMaterial,
            mat.quantidade,
            mat.condicao,
            mat.numSerie ? [mat.numSerie] : undefined
          );
        }
      }
    }

    // 1b. Alterações de notas sincronizadas ainda na fila: o saldo do servidor não as conhece.
    for (const exec of todasExec.filter(e => e.alteracaoPendente)) {
      await this.projetarDiferencaNoSaldo(exec.alteracaoPendente!.anterior, { acoes: exec.acoes, materiais: exec.materiais });
    }

    // 2. Re-aplicar Transferências Pendentes
    const todasTransf = await this.dbService.getTransferencias();
    const transfPendentes = todasTransf.filter(t => t.statusSync === 'PENDENTE' && t.idEstoqueOrigem === idEstoque);

    for (const transf of transfPendentes) {
      for (const item of transf.itens) {
        await this.dbService.deduzirSaldoLocal(
          idEstoque,
          item.idMaterial,
          item.quantidade,
          item.condicao,
          item.numSerie ? [item.numSerie] : undefined
        );
      }
    }
  }
}
