import { describe, it, expect } from 'vitest';
import { contarSolicitacoesTecnicasPrevistas } from './solicitacao-tecnica-importacao-contagem.util';
import { SolicitacaoTecnicaImportRow } from '../../../core/models/solicitacao-material-tecnica.models';

describe('contarSolicitacoesTecnicasPrevistas', () => {
  it('calcula itens válidos, com erro e grupos formados corretamente', () => {
    const rows: SolicitacaoTecnicaImportRow[] = [
      {
        linha: 2,
        numeroObra: 'OBRA-1',
        almoxarifado: 'ALMOX-A',
        codigoEquipe: 'EQ-01',
        dataPrevista: '2026-09-24',
        tipoMovimentacao: '221',
        codigoMaterial: 'M-1',
        quantidade: 10,
        status: 'AGUARDANDO_PROCESSAMENTO',
        ocorrencia: '',
        observacao: '',
        numeroReserva: '',
        dataCriacaoReserva: '',
        agenteResponsavel: '',
      },
      {
        linha: 3,
        numeroObra: 'OBRA-1',
        almoxarifado: 'ALMOX-A',
        codigoEquipe: 'EQ-01',
        dataPrevista: '2026-09-24',
        tipoMovimentacao: '221',
        codigoMaterial: 'M-2',
        quantidade: 5,
        status: 'AGUARDANDO_PROCESSAMENTO',
        ocorrencia: '',
        observacao: '',
        numeroReserva: '',
        dataCriacaoReserva: '',
        agenteResponsavel: '',
      },
      {
        linha: 4,
        numeroObra: 'OBRA-1',
        almoxarifado: 'ALMOX-A',
        codigoEquipe: 'EQ-01',
        dataPrevista: '2026-09-24',
        tipoMovimentacao: '222',
        codigoMaterial: 'M-3',
        quantidade: 2,
        status: 'AGUARDANDO_PROCESSAMENTO',
        ocorrencia: '',
        observacao: '',
        numeroReserva: '',
        dataCriacaoReserva: '',
        agenteResponsavel: '',
      },
      {
        linha: 5,
        numeroObra: 'OBRA-2',
        almoxarifado: 'ALMOX-B',
        codigoEquipe: 'EQ-02',
        dataPrevista: '2026-09-25',
        tipoMovimentacao: '221',
        codigoMaterial: '',
        quantidade: 0,
        status: 'COM_ERRO',
        campoErro: 'codigoMaterial',
        mensagemErro: 'Material obrigatório',
        ocorrencia: '',
        observacao: '',
        numeroReserva: '',
        dataCriacaoReserva: '',
        agenteResponsavel: '',
      },
    ];

    const contagem = contarSolicitacoesTecnicasPrevistas(rows);

    expect(contagem.totalItens).toBe(4);
    expect(contagem.totalValidos).toBe(3);
    expect(contagem.totalComErro).toBe(1);
    expect(contagem.totalSolicitacoes).toBe(1); // OBRA-1 + ALMOX-A + EQ-01 + 2026-09-24
    expect(contagem.totalGrupos).toBe(2); // tipo 221 e tipo 222
  });

  it('separa em solicitações independentes quando as equipes forem diferentes', () => {
    const rows: SolicitacaoTecnicaImportRow[] = [
      {
        linha: 2,
        numeroObra: 'OBRA-1',
        almoxarifado: 'ALMOX-A',
        codigoEquipe: 'EQ-01',
        dataPrevista: '2026-09-24',
        tipoMovimentacao: '221',
        codigoMaterial: 'M-1',
        quantidade: 10,
        status: 'AGUARDANDO_PROCESSAMENTO',
        ocorrencia: '',
        observacao: '',
        numeroReserva: '',
        dataCriacaoReserva: '',
        agenteResponsavel: '',
      },
      {
        linha: 3,
        numeroObra: 'OBRA-1',
        almoxarifado: 'ALMOX-A',
        codigoEquipe: 'EQ-02',
        dataPrevista: '2026-09-24',
        tipoMovimentacao: '221',
        codigoMaterial: 'M-1',
        quantidade: 10,
        status: 'AGUARDANDO_PROCESSAMENTO',
        ocorrencia: '',
        observacao: '',
        numeroReserva: '',
        dataCriacaoReserva: '',
        agenteResponsavel: '',
      },
    ];

    const contagem = contarSolicitacoesTecnicasPrevistas(rows);

    expect(contagem.totalItens).toBe(2);
    expect(contagem.totalValidos).toBe(2);
    expect(contagem.totalComErro).toBe(0);
    expect(contagem.totalSolicitacoes).toBe(2);
    expect(contagem.totalGrupos).toBe(2);
  });

  it('separa em solicitações independentes quando os números de reserva forem diferentes', () => {
    const rows: SolicitacaoTecnicaImportRow[] = [
      {
        linha: 2,
        numeroObra: 'OBRA-1',
        almoxarifado: 'ALMOX-A',
        codigoEquipe: 'EQ-01',
        dataPrevista: '2026-09-24',
        tipoMovimentacao: '221',
        codigoMaterial: 'M-1',
        quantidade: 10,
        status: 'AGUARDANDO_PROCESSAMENTO',
        ocorrencia: '',
        observacao: '',
        numeroReserva: '1001',
        dataCriacaoReserva: '2026-09-20',
        agenteResponsavel: '',
      },
      {
        linha: 3,
        numeroObra: 'OBRA-1',
        almoxarifado: 'ALMOX-A',
        codigoEquipe: 'EQ-01',
        dataPrevista: '2026-09-24',
        tipoMovimentacao: '221',
        codigoMaterial: 'M-1',
        quantidade: 10,
        status: 'AGUARDANDO_PROCESSAMENTO',
        ocorrencia: '',
        observacao: '',
        numeroReserva: '1002',
        dataCriacaoReserva: '2026-09-20',
        agenteResponsavel: '',
      },
    ];

    const contagem = contarSolicitacoesTecnicasPrevistas(rows);

    expect(contagem.totalItens).toBe(2);
    expect(contagem.totalValidos).toBe(2);
    expect(contagem.totalComErro).toBe(0);
    expect(contagem.totalSolicitacoes).toBe(2);
    expect(contagem.totalGrupos).toBe(2);
  });

  it('separa em solicitações independentes quando os almoxarifados forem diferentes', () => {
    const rows: SolicitacaoTecnicaImportRow[] = [
      {
        linha: 2,
        numeroObra: 'OBRA-1',
        almoxarifado: 'ALMOX-A',
        codigoEquipe: 'EQ-01',
        dataPrevista: '2026-09-24',
        tipoMovimentacao: '221',
        codigoMaterial: 'M-1',
        quantidade: 10,
        status: 'AGUARDANDO_PROCESSAMENTO',
        ocorrencia: '',
        observacao: '',
        numeroReserva: '',
        dataCriacaoReserva: '',
        agenteResponsavel: '',
      },
      {
        linha: 3,
        numeroObra: 'OBRA-1',
        almoxarifado: 'ALMOX-B',
        codigoEquipe: 'EQ-01',
        dataPrevista: '2026-09-24',
        tipoMovimentacao: '221',
        codigoMaterial: 'M-1',
        quantidade: 10,
        status: 'AGUARDANDO_PROCESSAMENTO',
        ocorrencia: '',
        observacao: '',
        numeroReserva: '',
        dataCriacaoReserva: '',
        agenteResponsavel: '',
      },
    ];

    const contagem = contarSolicitacoesTecnicasPrevistas(rows);

    expect(contagem.totalItens).toBe(2);
    expect(contagem.totalValidos).toBe(2);
    expect(contagem.totalComErro).toBe(0);
    expect(contagem.totalSolicitacoes).toBe(2);
    expect(contagem.totalGrupos).toBe(2);
  });
});

