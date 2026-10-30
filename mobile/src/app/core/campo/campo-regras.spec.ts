import { ExecucaoCampoLocal, ExecucaoMaterialLocal, MetaDoCicloLocal } from '../db/models';
import {
  calcularProgressoCiclo,
  calcularProgressoMeta,
  contarDiasUteis,
  calcularDiasUteisNoMes,
  calcularMetaDiariaPadrao,
  chaveSerie,
  corteDoTurno,
  gerarNumeroAvulsa,
  linhaComSerie,
  preverFaltasDeSaldo,
  rotuloNota,
  SaldoEquipeSnapshot,
  turnoVencido
} from './campo-regras';

describe('campo-regras', () => {
  describe('corte do turno', () => {
    it('vale até as 22h do dia da abertura, no horário de Brasília', () => {
      expect(corteDoTurno('2026-10-20T09:10:00-03:00').toISOString()).toBe('2026-10-21T01:00:00.000Z');
    });

    it('turno aberto depois das 22h vale até as 22h do dia seguinte', () => {
      expect(corteDoTurno('2026-10-20T22:30:00-03:00').toISOString()).toBe('2026-10-22T01:00:00.000Z');
    });

    it('o turno de ontem está vencido na manhã seguinte', () => {
      const abertura = '2026-10-20T09:10:00-03:00';
      expect(turnoVencido(abertura, new Date('2026-10-20T21:59:00-03:00'))).toBeFalse();
      expect(turnoVencido(abertura, new Date('2026-10-20T22:00:00-03:00'))).toBeFalse();
      expect(turnoVencido(abertura, new Date('2026-10-21T09:48:00-03:00'))).toBeTrue();
    });
  });

  describe('linhaComSerie', () => {
    const linhas = [
      { idMaterial: 'medidor', operacao: 'APLICADO', numSeries: ['1010098751'] },
      { idMaterial: 'sucata', operacao: 'RETIRADO', numSeries: ['5265844032'] },
    ];

    it('acha a linha que já usa a série, sem diferenciar maiúsculas e espaços', () => {
      expect(linhaComSerie(linhas, '1010098751')).toBe(linhas[0]);
      expect(linhaComSerie(linhas, ' 5265844032 ')).toBe(linhas[1]);
    });

    it('série nova não conflita', () => {
      expect(linhaComSerie(linhas, '999')).toBeNull();
    });
  });

  const saldo: SaldoEquipeSnapshot = {
    novoPorMaterial: new Map([['cabo', 3]]),
    materiaisComSaldo: new Set(['cabo', 'medidor']),
    seriesNovas: new Set([chaveSerie('medidor', 'SN-1')]),
  };

  const mat = (m: Partial<ExecucaoMaterialLocal>): ExecucaoMaterialLocal => ({
    idMaterial: 'cabo', quantidade: 1, operacao: 'APLICADO', condicao: 'NOVO', ...m,
  });

  describe('nota avulsa e ocorrência', () => {
    it('material da Concessionária aplicado não gera falta de saldo', () => {
      expect(preverFaltasDeSaldo([
        mat({ quantidade: 50, origem: 'CONCESSIONARIA' }),
        mat({ idMaterial: 'medidor', numSerie: 'SN-77', origem: 'CONCESSIONARIA' }),
      ], saldo)).toEqual([]);
    });

    it('gera o número provisório da avulsa seguindo a maior sequência do dia', () => {
      const dia = new Date(2026, 9, 19);
      expect(gerarNumeroAvulsa('EQ-12', [], dia)).toBe('AV-EQ12-261019-01');
      expect(gerarNumeroAvulsa('EQ-12', ['AV-EQ12-261019-01', 'AV-EQ12-261019-07', 'AV-EQ12-261018-30', '123'], dia)).toBe('AV-EQ12-261019-08');
      expect(gerarNumeroAvulsa(null, [], dia)).toBe('AV-EQ-261019-01');
    });

    it('nomeia o lançamento pelo tipo', () => {
      expect(rotuloNota({ referenciaNota: '123' })).toBe('Nota 123');
      expect(rotuloNota({ referenciaNota: 'AV-1', tipoNota: 'AVULSA' })).toBe('Nota avulsa AV-1');
      expect(rotuloNota({ referenciaNota: 'OC-9', tipoNota: 'OCORRENCIA' })).toBe('Ocorrência OC-9');
    });
  });

  describe('preverFaltasDeSaldo', () => {
    it('não aponta falta quando o aplicado está no saldo', () => {
      expect(preverFaltasDeSaldo([
        mat({ quantidade: 3 }),
        mat({ idMaterial: 'medidor', numSerie: 'SN-1' }),
      ], saldo)).toEqual([]);
    });

    it('retirada não entra na conta (o material vai no veículo e é devolvido depois)', () => {
      expect(preverFaltasDeSaldo([
        mat({ quantidade: 2, operacao: 'RETIRADO', condicao: 'SUCATA' }),
        mat({ idMaterial: 'poste', quantidade: 1, operacao: 'RETIRADO', condicao: 'SUCATA' }),
        mat({ idMaterial: 'medidor', operacao: 'RETIRADO', condicao: 'SUCATA', numSerie: 'SN-50' }),
      ], saldo)).toEqual([]);
    });

    it('aponta só a quantidade que excede o saldo', () => {
      expect(preverFaltasDeSaldo([mat({ quantidade: 5 })], saldo))
        .toEqual([{ idMaterial: 'cabo', quantidade: 2 }]);
    });

    it('aponta série que não está no saldo', () => {
      expect(preverFaltasDeSaldo([mat({ idMaterial: 'medidor', numSerie: 'SN-9' })], saldo))
        .toEqual([{ idMaterial: 'medidor', quantidade: 1, numSerie: 'SN-9' }]);
    });

    it('soma aplicações do mesmo material antes de comparar com o saldo', () => {
      expect(preverFaltasDeSaldo([mat({ quantidade: 2 }), mat({ quantidade: 2 })], saldo))
        .toEqual([{ idMaterial: 'cabo', quantidade: 1 }]);
    });
  });

  describe('calcularDiasUteisNoMes', () => {
    it('calcula dias úteis excluindo sábados, domingos e feriados nacionais', () => {
      // Outubro 2026: 22 dias de segunda a sexta, menos 12/10 = 21 dias úteis
      expect(calcularDiasUteisNoMes(2026, 10)).toBe(21);
      // Fevereiro 2026: 20 dias úteis; Carnaval é ponto facultativo e conta
      expect(calcularDiasUteisNoMes(2026, 2)).toBe(20);
      // Abril 2026: 22 dias de segunda a sexta, menos Sexta-feira Santa (03/04) e Tiradentes (21/04)
      expect(calcularDiasUteisNoMes(2026, 4)).toBe(20);
    });
  });

  describe('calcularMetaDiariaPadrao', () => {
    it('divide a meta mensal pelos dias úteis do mês', () => {
      const agora = new Date(2026, 9, 15); // Outubro 2026 (21 dias úteis)
      expect(calcularMetaDiariaPadrao(21000, agora)).toBe(1000);
      expect(calcularMetaDiariaPadrao(null, agora)).toBeNull();
      expect(calcularMetaDiariaPadrao(0, agora)).toBeNull();
    });
  });

  describe('calcularProgressoMeta', () => {
    const agora = new Date(2026, 9, 15, 12);
    const exec = (e: Partial<ExecucaoCampoLocal>): ExecucaoCampoLocal => ({
      id: Math.random().toString(), referenciaNota: 'N', idTurno: 't', idEquipe: 'e',
      dataLancamento: new Date(2026, 9, 15, 9).toISOString(), statusExecucao: 'CONCLUIDO',
      acoes: [], materiais: [], statusSync: 'SINCRONIZADO', valorTotalFaturado: 0, ...e,
    });

    it('soma o valor das execuções de hoje (sincronizadas e offline/locais) e informa quanto falta', () => {
      const progresso = calcularProgressoMeta([
        exec({ valorTotalFaturado: 1000 }),
        exec({ valorTotalFaturado: 500.5 }),
        // Execução de outro dia não entra no progresso do dia
        exec({ valorTotalFaturado: 9999, dataLancamento: new Date(2026, 9, 14).toISOString() }),
        // Execução offline de hoje: contabilizada no faturamento e marcada em aguardandoEnvio
        exec({ statusSync: 'LOCAL', valorTotalFaturado: 300 }),
      ], 2000, agora);

      expect(progresso.competencia).toBe('2026-10');
      expect(progresso.dataReferencia).toBe('2026-10-15');
      expect(progresso.executado).toBe(1800.5); // 1000 + 500.5 + 300
      expect(progresso.falta).toBe(199.5);
      expect(progresso.percentual).toBe(90);
      expect(progresso.aguardandoEnvio).toBe(1);
      expect(progresso.quantidadeExecucoes).toBe(3);
      expect(progresso.atingida).toBeFalse();
    });

    it('sem meta mostra só o executado', () => {
      const progresso = calcularProgressoMeta([exec({ valorTotalFaturado: 200 })], null, agora);
      expect(progresso.meta).toBeNull();
      expect(progresso.falta).toBeNull();
      expect(progresso.executado).toBe(200);
    });

    it('meta atingida não tem valor faltante negativo', () => {
      const progresso = calcularProgressoMeta([exec({ valorTotalFaturado: 5000 })], 4000, agora);
      expect(progresso.falta).toBe(0);
      expect(progresso.percentual).toBe(100);
      expect(progresso.atingida).toBeTrue();
    });

    it('inclui execuções do turno ativo mesmo que tenham iniciado no dia anterior (virada da meia-noite)', () => {
      const agoraDepoisMeiaNoite = new Date(2026, 9, 15, 1, 30); // 15 de outubro às 01:30
      const progresso = calcularProgressoMeta([
        // Executada dia 14 às 23:30 no mesmo turno ativo 'turno-noturno'
        exec({ idTurno: 'turno-noturno', valorTotalFaturado: 600, dataLancamento: new Date(2026, 9, 14, 23, 30).toISOString() }),
        // Executada dia 15 às 01:00 no mesmo turno ativo 'turno-noturno'
        exec({ idTurno: 'turno-noturno', valorTotalFaturado: 400, dataLancamento: new Date(2026, 9, 15, 1, 0).toISOString() }),
        // Executada dia 14 em outro turno não ativo: não entra
        exec({ idTurno: 'turno-anterior', valorTotalFaturado: 900, dataLancamento: new Date(2026, 9, 14, 15, 0).toISOString() }),
      ], 1500, agoraDepoisMeiaNoite, 'turno-noturno');

      expect(progresso.executado).toBe(1000);
      expect(progresso.falta).toBe(500);
      expect(progresso.quantidadeExecucoes).toBe(2);
    });
  });

  describe('calcularProgressoCiclo', () => {
    // Ciclo de outubro/2026: 26/09 (sábado) a 25/10 (domingo), 19 dias úteis (12/10 é feriado).
    const meta: MetaDoCicloLocal = {
      periodicidade: 'CICLO_COMERCIAL',
      competencia: '2026-10',
      inicio: '2026-09-26',
      fim: '2026-10-25',
      valor: 19000,
      metaDiaria: 1000,
      diasUteis: 19,
      realizado: 8500,
      apuradoEm: '2026-10-13T08:00:00-03:00',
    };
    const execucao = (e: Partial<ExecucaoCampoLocal>): ExecucaoCampoLocal => ({
      id: crypto.randomUUID(), referenciaNota: 'N', idTurno: 't', idEquipe: 'eq',
      dataLancamento: '2026-10-13T10:00:00-03:00', statusExecucao: 'CONCLUIDO', acoes: [], materiais: [],
      statusSync: 'LOCAL', valorTotalFaturado: 500, ...e,
    });
    const terca13 = new Date(2026, 9, 13, 15, 0);

    it('conta os dias úteis do ciclo', () => {
      expect(contarDiasUteis(new Date(2026, 8, 26), new Date(2026, 9, 25))).toBe(19);
    });

    it('sem meta não há cartão do ciclo', () => {
      expect(calcularProgressoCiclo(null, [], terca13)).toBeNull();
    });

    it('soma ao realizado do servidor o que ainda está só no aparelho', () => {
      const p = calcularProgressoCiclo(meta, [
        execucao({ statusSync: 'LOCAL' }),
        execucao({ statusSync: 'SINCRONIZADO', dataLancamento: '2026-10-13T07:00:00-03:00' }), // já apurado
        execucao({ statusSync: 'SINCRONIZADO', dataLancamento: '2026-10-13T09:00:00-03:00' }), // depois da apuração
        execucao({ statusSync: 'LOCAL', dataLancamento: '2026-09-25T10:00:00-03:00' }),        // ciclo anterior
      ], terca13)!;

      expect(p.titulo).toBe('Ciclo de outubro');
      expect(p.pendenteEnvio).toBe(1000);
      expect(p.realizado).toBe(9500);
      expect(p.percentual).toBe(50);
      expect(p.falta).toBe(9500);
    });

    it('avalia o ritmo pelo que já deveria ter sido feito até ontem', () => {
      // Até 12/10: 28, 29, 30/09 e 01 a 09/10 = 3 + 7 = 10 dias úteis (12/10 é feriado) → esperado 10.000.
      const abaixo = calcularProgressoCiclo(meta, [], terca13)!;
      expect(abaixo.esperadoAteOntem).toBe(10000);
      expect(abaixo.situacao).toBe('abaixo');
      // De 13 a 23/10 restam 9 dias úteis.
      expect(abaixo.diasUteisRestantes).toBe(9);
      expect(abaixo.ritmoNecessario).toBe(1166.67);

      const noRitmo = calcularProgressoCiclo({ ...meta, realizado: 12000 }, [], terca13)!;
      expect(noRitmo.situacao).toBe('no-ritmo');
      const batida = calcularProgressoCiclo({ ...meta, realizado: 21000 }, [], terca13)!;
      expect(batida.situacao).toBe('batida');
      expect(batida.percentual).toBe(100);
      expect(batida.ritmoNecessario).toBeNull();
    });

    it('depois do fim do ciclo pede sincronização', () => {
      expect(calcularProgressoCiclo(meta, [], new Date(2026, 9, 27))!.situacao).toBe('desatualizada');
    });
  });
});
