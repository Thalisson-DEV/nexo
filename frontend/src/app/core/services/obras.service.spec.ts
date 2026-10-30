import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import {
  ObraRequest,
  ObraResponse,
  ObraResumoResponse,
  SubprojetoVinculoRequest,
  TipoVinculoSubprojeto
} from '../models/obras.models';
import { ObrasService } from './obras.service';

describe('ObrasService', () => {
  let service: ObrasService;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiUrl}/v1/obras`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ObrasService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(ObrasService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('deve listar obras com filtros serializados corretamente', () => {
    const mockResponse = {
      content: [
        { id: '1', numero: '1228694', titulo: 'OBRA 1', statusNome: 'EM EXECUÇÃO' } as ObraResumoResponse
      ],
      page: { size: 20, number: 0, totalElements: 1, totalPages: 1 }
    };

    service.listarObras({ numero: '1228694', statusIds: ['st-1', 'st-2'] }).subscribe((res) => {
      expect(res.content.length).toBe(1);
      expect(res.content[0].numero).toBe('1228694');
    });

    const req = httpMock.expectOne((r) => r.url === baseUrl && r.method === 'GET');
    expect(req.request.params.get('numero')).toBe('1228694');
    expect(req.request.params.getAll('statusIds')).toEqual(['st-1', 'st-2']);
    req.flush(mockResponse);
  });

  it('deve buscar obra por ID', () => {
    const mockObra: ObraResponse = {
      id: 'obra-123',
      numero: '1228694',
      titulo: 'SUBESTAÇÃO CENTRAL',
      dataInicio: '01/03/2026',
      dataPrevisaoFim: '30/09/2026',
      statusId: 'st-1',
      statusNome: 'EM EXECUÇÃO',
      statusConcluida: false,
      equipes: [],
      subprojetos: [],
      anexos: [],
      ativo: true,
      dataCriacao: '2026-03-01T09:00:00-03:00'
    };

    service.buscarObraPorId('obra-123').subscribe((res) => {
      expect(res.id).toBe('obra-123');
      expect(res.numero).toBe('1228694');
    });

    const req = httpMock.expectOne(`${baseUrl}/obra-123`);
    expect(req.request.method).toBe('GET');
    req.flush(mockObra);
  });

  it('deve alterar status da obra via PATCH', () => {
    const mockAtualizada: ObraResponse = {
      id: 'obra-123',
      numero: '1228694',
      titulo: 'SUBESTAÇÃO CENTRAL',
      dataInicio: '01/03/2026',
      dataPrevisaoFim: '30/09/2026',
      dataFimEfetiva: '15/08/2026',
      statusId: 'st-concluida',
      statusNome: 'CONCLUÍDA',
      statusConcluida: true,
      equipes: [],
      subprojetos: [],
      anexos: [],
      ativo: true,
      dataCriacao: '2026-03-01T09:00:00-03:00'
    };

    service.alterarStatus('obra-123', 'st-concluida', '15/08/2026').subscribe((res) => {
      expect(res.statusNome).toBe('CONCLUÍDA');
      expect(res.dataFimEfetiva).toBe('15/08/2026');
    });

    const req = httpMock.expectOne((r) => r.url === `${baseUrl}/obra-123/status` && r.method === 'PATCH');
    expect(req.request.params.get('idNovoStatus')).toBe('st-concluida');
    expect(req.request.params.get('dataFimEfetiva')).toBe('15/08/2026');
    req.flush(mockAtualizada);
  });

  it('deve vincular e desvincular subprojeto', () => {
    const vinculoReq: SubprojetoVinculoRequest = {
      idObraFilha: 'obra-filha-1',
      tipoVinculo: TipoVinculoSubprojeto.KIT
    };

    service.vincularSubprojeto('obra-pai-1', vinculoReq).subscribe();
    const reqVincular = httpMock.expectOne(`${baseUrl}/obra-pai-1/subprojetos`);
    expect(reqVincular.request.method).toBe('POST');
    expect(reqVincular.request.body).toEqual(vinculoReq);
    reqVincular.flush({ id: 'obra-pai-1' });

    service.desvincularSubprojeto('obra-pai-1', 'obra-filha-1').subscribe();
    const reqDesvincular = httpMock.expectOne(`${baseUrl}/obra-pai-1/subprojetos/obra-filha-1`);
    expect(reqDesvincular.request.method).toBe('DELETE');
    reqDesvincular.flush({ id: 'obra-pai-1' });
  });

  it('deve gerenciar anexos tecnicos (listar, upload, download, remover)', () => {
    service.listarAnexos('obra-123').subscribe((anexos) => {
      expect(anexos.length).toBe(0);
    });
    const reqListar = httpMock.expectOne(`${baseUrl}/obra-123/anexos`);
    reqListar.flush([]);

    const file = new File(['teste'], 'projeto.pdf', { type: 'application/pdf' });
    service.anexarArquivo('obra-123', 'tipo-cad', file, 'Memorial descritivo').subscribe();
    const reqUpload = httpMock.expectOne(`${baseUrl}/obra-123/anexos`);
    expect(reqUpload.request.method).toBe('POST');
    expect(reqUpload.request.body instanceof FormData).toBe(true);
    reqUpload.flush({ id: 'anexo-1', nomeArquivo: 'projeto.pdf' });

    service.removerAnexo('obra-123', 'anexo-1').subscribe();
    const reqRemover = httpMock.expectOne(`${baseUrl}/obra-123/anexos/anexo-1`);
    expect(reqRemover.request.method).toBe('DELETE');
    reqRemover.flush(null);
  });

  it('deve realizar importacao em lote (modelo, validacao e confirmacao)', () => {
    service.baixarModeloCsv().subscribe((blob) => {
      expect(blob).toBeTruthy();
    });
    const reqModelo = httpMock.expectOne(`${baseUrl}/importar/modelo`);
    expect(reqModelo.request.method).toBe('GET');
    reqModelo.flush(new Blob(['numero;titulo'], { type: 'text/csv' }));

    const file = new File(['csv data'], 'obras.csv', { type: 'text/csv' });
    service.validarImportacao(file).subscribe((res) => {
      expect(res.totalProcessados).toBe(2);
    });
    const reqValidar = httpMock.expectOne(`${baseUrl}/importar/validar`);
    expect(reqValidar.request.method).toBe('POST');
    reqValidar.flush({ registros: [], totalProcessados: 2, totalSucesso: 2, totalFalha: 0 });

    service.confirmarImportacao({ registros: [] }).subscribe();
    const reqConfirmar = httpMock.expectOne(`${baseUrl}/importar/confirmar`);
    expect(reqConfirmar.request.method).toBe('POST');
    reqConfirmar.flush({ registros: [], totalProcessados: 2, totalSucesso: 2, totalFalha: 0 });
  });

  it('deve listar status de obras ativos com cache shareReplay', () => {
    const mockStatus = [
      { id: '1', nome: 'PLANEJADA', ordem: 0, concluida: false, ativo: true },
      { id: '2', nome: 'EM EXECUÇÃO', ordem: 1, concluida: false, ativo: true }
    ];

    service.listarStatusAtivos().subscribe((res) => {
      expect(res.length).toBe(2);
    });

    const req = httpMock.expectOne(`${baseUrl}/status/todos-ativos`);
    req.flush(mockStatus);

    // Segunda chamada deve usar o cache
    service.listarStatusAtivos().subscribe((res) => {
      expect(res.length).toBe(2);
    });
    httpMock.expectNone(`${baseUrl}/status/todos-ativos`);
  });
});
