import '@angular/compiler';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import {
  ConfirmarEntregaTecnicaRequest,
  CriarSolicitacaoTecnicaRequest,
  PlanejarSolicitacaoTecnicaRequest,
} from '../models/solicitacao-material-tecnica.models';
import { SolicitacaoMaterialTecnicaService } from './solicitacao-material-tecnica.service';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

describe('SolicitacaoMaterialTecnicaService v2', () => {
  let service: SolicitacaoMaterialTecnicaService;
  let http: HttpTestingController;
  const baseUrl = `${environment.apiUrl}/v1/solicitacoes-material`;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), SolicitacaoMaterialTecnicaService],
    });
    service = TestBed.inject(SolicitacaoMaterialTecnicaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('cria solicitacoes atomicas com Idempotency-Key', () => {
    const body: CriarSolicitacaoTecnicaRequest = {
      idObra: 'obra-1',
      ocorrencia: 'OC 1',
      idAlmoxarifadoAtendimento: 'almox-1',
      idEquipe: null,
      idColaboradorSaque: 'colab-1',
      saqueForaEquipe: true,
      dataPrevista: '2026-09-01',
      observacao: null,
      itens: [{ idMaterial: 'mat-1', tipoMovimentacao: 'ADICAO_COMUM', quantidade: 1 }],
    };

    service.criar(body, 'create-key').subscribe((response) => {
      expect(response.solicitacoesCriadas[0].id).toBe('sol-1');
    });

    const request = http.expectOne(`${baseUrl}/tecnicas`);
    expect(request.request.headers.get('Idempotency-Key')).toBe('create-key');
    expect(request.request.body).toMatchObject({
      idColaboradorSaque: 'colab-1',
      saqueForaEquipe: true,
    });
    request.flush({ solicitacoesCriadas: [{ id: 'sol-1', tipo: 'ADICAO_COMUM', codigo: '221' }] });
  });

  it('normaliza a linha técnica aninhada retornada pela listagem', () => {
    service.listar({
      contexto: 'TECNICO',
      etapa: 'AGUARDANDO_PLANEJAMENTO',
      page: 0,
      size: 20,
    }).subscribe((response) => {
      expect(response.content[0]).toMatchObject({
        id: 'sol-1',
        tipo: 'ADICAO_COMUM',
        tipoObra: 'OBRA',
        idObra: 'obra-1',
        idAlmoxarifadoAtendimento: 'almox-1',
        almoxarifadoNome: 'Almoxarifado Central',
        status: 'APROVADO',
        statusSeparacao: 'AGUARDANDO_AGENDAMENTO',
        etapaOperacional: 'AGUARDANDO_PLANEJAMENTO',
        quantidadeItens: 1,
        contexto: 'TECNICO',
        dataSolicitacao: '2026-08-27T23:03:12.472315Z',
        dataEntrega: '2026-08-28T19:46:25.491015Z',
        estoqueDestinoNome: 'Almoxarifado Central',
        idRequisitante: 'user-1',
        quantidadeSolicitada: 76,
        quantidadeEntregaLiquida: 0,
        quantidadeDevolucaoLiquida: 0,
        quantidadePendente: 76,
        totalOperacoes: 0,
        atrasada: false,
      });
    });

    const request = http.expectOne((candidate) => candidate.url === baseUrl);
    expect(request.request.params.get('contexto')).toBe('TECNICO');
    expect(request.request.params.get('etapa')).toBe('AGUARDANDO_PLANEJAMENTO');
    request.flush({
      content: [{
        acoesPermitidas: ['EDITAR_PLANEJAMENTO', 'REGISTRAR_RESERVA', 'VER_HISTORICO'],
        contexto: 'TECNICO',
        dataSolicitacao: '2026-08-27T23:03:12.472315Z',
        dataEntrega: '2026-08-28T19:46:25.491015Z',
        estoqueOrigemNome: null,
        estoqueDestinoNome: 'Almoxarifado Central',
        id: 'sol-1',
        idRequisitante: 'user-1',
        status: 'APROVADO',
        statusSeparacao: 'AGUARDANDO_AGENDAMENTO',
        etapaOperacional: 'AGUARDANDO_PLANEJAMENTO',
        tecnico: {
          tipoTecnico: 'ADICAO_COMUM',
          tipoObra: 'OBRA',
          idObra: 'obra-1',
          idAlmoxarifadoAtendimento: 'almox-1',
          statusSeparacao: 'AGUARDANDO_AGENDAMENTO',
          totais: {
            solicitada: 76,
            entregaBruta: 0,
            entregaEstornada: 0,
            entregaLiquida: 0,
            devolucaoBruta: 0,
            devolucaoEstornada: 0,
            devolucaoLiquida: 0,
            pendente: 76,
          },
          operacoes: [],
          versao: 0,
          acoesPermitidas: ['EDITAR_PLANEJAMENTO', 'REGISTRAR_RESERVA', 'VER_HISTORICO'],
          atrasada: false,
        },
        totalItens: 1,
        versao: 0,
      }],
      page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
    });
  });

  it('herda o tipo atomico nos itens quando o detalhe v2 nao repete tipoMovimentacao', () => {
    service.buscarPorId('9c0bddbf-3e50-4f80-aadc-87aa6ab96199').subscribe((response) => {
      expect(response.tecnico?.tipoTecnico).toBe('ADICAO_COMUM');
      expect(response.itens[0].tipoMovimentacao).toBe('ADICAO_COMUM');
      expect(response.acoesPermitidas).toEqual([
        'EDITAR_PLANEJAMENTO',
        'REGISTRAR_RESERVA',
        'VER_HISTORICO',
      ]);
    });

    const request = http.expectOne(
      `${baseUrl}/9c0bddbf-3e50-4f80-aadc-87aa6ab96199`,
    );
    request.flush({
      id: '9c0bddbf-3e50-4f80-aadc-87aa6ab96199',
      estoqueDestino: {
        id: 'estoque-1',
        nomeIdentificador: 'OBRA 2123123 - ABERTURA DE CHAMADOS',
      },
      idRequisitante: 'usuario-1',
      status: 'APROVADO',
      dataSolicitacao: '2026-08-27T23:03:12.472315Z',
      itens: [
        {
          id: 'item-1',
          material: {
            id: 'material-1',
            codigo: '89127398',
            descricao: 'Material de teste',
            unidadeMedida: 'MT',
            detalhaSerie: false,
            ativo: true,
          },
          quantidade: 76,
          quantidadeEntregue: 0,
          quantidadePendente: 76,
        },
      ],
      tecnico: {
        tipoTecnico: 'ADICAO_COMUM',
        tipoObra: 'OBRA',
        idObra: 'obra-1',
        idAlmoxarifadoAtendimento: 'almox-1',
        statusSeparacao: 'AGUARDANDO_AGENDAMENTO',
        totais: {
          solicitada: 76,
          entregaBruta: 0,
          entregaEstornada: 0,
          entregaLiquida: 0,
          devolucaoBruta: 0,
          devolucaoEstornada: 0,
          devolucaoLiquida: 0,
          pendente: 76,
        },
        operacoes: [],
        versao: 0,
        acoesPermitidas: [
          'EDITAR_PLANEJAMENTO',
          'REGISTRAR_RESERVA',
          'VER_HISTORICO',
        ],
        atrasada: false,
      },
      contexto: 'TECNICO',
      versao: 0,
      statusSeparacao: 'AGUARDANDO_AGENDAMENTO',
      acoesPermitidas: [
        'EDITAR_PLANEJAMENTO',
        'REGISTRAR_RESERVA',
        'VER_HISTORICO',
      ],
    });
  });

  it('envia planejamento atomico sem Idempotency-Key e preserva envelope', () => {
    const body: PlanejarSolicitacaoTecnicaRequest = {
      versaoEsperada: 3,
      idObra: 'obra-1',
      ocorrencia: null,
      idAlmoxarifadoAtendimento: 'almox-1',
      idEquipe: null,
      idColaboradorSaque: 'colab-1',
      saqueForaEquipe: true,
      dataPrevista: '2026-09-01',
      observacao: null,
      itens: [{ idItem: 'item-1', idMaterial: 'mat-1', tipoMovimentacao: 'ADICAO_COMUM', quantidade: 1 }],
    };

    service.substituirPlanejamento('sol-1', body).subscribe((response) => {
      expect(response.solicitacaoAtualizada.id).toBe('sol-1');
    });

    const request = http.expectOne(`${baseUrl}/tecnicas/sol-1/planejamento`);
    expect(request.request.headers.has('Idempotency-Key')).toBe(false);
    expect(request.request.body).toEqual(body);
    request.flush({
      solicitacaoAtualizada: {
        id: 'sol-1',
        status: 'APROVADO',
        contexto: 'TECNICO',
        versao: 4,
        itens: [],
        tecnico: null,
        statusSeparacao: null,
        acoesPermitidas: [],
      },
    });
  });

  it('envia o override de responsavel pelo saque na entrega tecnica', () => {
    const body: ConfirmarEntregaTecnicaRequest = {
      versaoEsperada: 4,
      idEquipe: 'equipe-1',
      idColaboradorSaque: 'colab-2',
      saqueForaEquipe: true,
      assinaturaAlmox: 'FISICA',
      assinaturaEquipe: 'FISICA',
      itens: [{ idItemSolicitacao: 'item-1', quantidade: 1 }],
    };

    service.entregar('sol-1', body, 'delivery-key').subscribe((response) => {
      expect(response.idOperacao).toBe('op-1');
    });

    const request = http.expectOne(`${baseUrl}/tecnicas/sol-1/entregas`);
    expect(request.request.headers.get('Idempotency-Key')).toBe('delivery-key');
    expect(request.request.body).toEqual(body);
    request.flush({ idOperacao: 'op-1' });
  });

  it('usa historico atomico fora de /tecnicas', () => {
    service.historico('sol-1').subscribe();
    const request = http.expectOne((candidate) => candidate.url === `${baseUrl}/sol-1/historico`);
    expect(request.request.url).not.toContain('/tecnicas/');
    request.flush({
      eventos: [],
      page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
    });
  });

  it('nao envia chave na validacao e envia chave na confirmacao de importacao', () => {
    const file = new File(['csv'], 'solicitacoes.csv', { type: 'text/csv' });
    service.validarImportacao(file).subscribe();
    const validation = http.expectOne(`${baseUrl}/importacoes/validacao`);
    expect(validation.request.headers.has('Idempotency-Key')).toBe(false);
    validation.flush({
      token: 'token-1',
      hashArquivo: 'hash',
      expiraEm: '2026-09-02T00:00:00Z',
      totalLinhas: 1,
      totalConfirmaveis: 1,
      totalErros: 0,
      totalAvisos: 0,
      errosPorLinha: [],
      paresDisponiveis: [{ chave: 'OC 1', tipo: '221' }],
    });

    service.confirmarImportacao(
      { token: 'token-1', combinacoes: [{ chave: 'OC 1', tipo: '221' }], reconhecerAvisos: false },
      'confirm-key',
    ).subscribe();
    const confirmation = http.expectOne(`${baseUrl}/importacoes/confirmacao`);
    expect(confirmation.request.headers.get('Idempotency-Key')).toBe('confirm-key');
    confirmation.flush({ resultados: [{ chave: 'OC 1', tipo: '221', erro: null }] });
  });

  it('baixa somente o modelo CSV', () => {
    service.baixarModelo().subscribe();
    const request = http.expectOne((candidate) => candidate.url === `${baseUrl}/importacoes/modelo`);
    expect(request.request.params.get('formato')).toBe('csv');
    request.flush(new Blob(['csv'], { type: 'text/csv' }));
  });

  it('exporta resumo e detalhado chamando os endpoints corretos', () => {
    service.exportarResumo({ contexto: 'TECNICO' }).subscribe((blob) => {
      expect(blob).toBeTruthy();
    });
    const resumoReq = http.expectOne((req) => req.url === `${baseUrl}/exportar/resumo`);
    expect(resumoReq.request.params.get('contexto')).toBe('TECNICO');
    expect(resumoReq.request.responseType).toBe('blob');
    resumoReq.flush(new Blob(['resumo-csv'], { type: 'text/csv' }));

    service.exportarDetalhado({ idObra: 'obra-1' }).subscribe((blob) => {
      expect(blob).toBeTruthy();
    });
    const detalhadoReq = http.expectOne((req) => req.url === `${baseUrl}/exportar/detalhado`);
    expect(detalhadoReq.request.params.get('idObra')).toBe('obra-1');
    expect(detalhadoReq.request.responseType).toBe('blob');
    detalhadoReq.flush(new Blob(['detalhado-csv'], { type: 'text/csv' }));
  });
});
