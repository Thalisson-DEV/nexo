import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { environment } from '../../../environments/environment';
import { FrotaService } from './frota.service';

describe('FrotaService', () => {
  let service: FrotaService;
  let http: HttpTestingController;
  const base = `${environment.apiUrl}/v1/frota`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(FrotaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('envia filtros de múltipla seleção como parâmetro repetido e omite vazios', () => {
    service.listarVeiculos({ busca: 'ABC', idsBase: ['b1', 'b2'], situacoes: [], regime: null, ativo: true }).subscribe();

    const req = http.expectOne((r) => r.url === `${base}/veiculos`);
    expect(req.request.params.getAll('idsBase')).toEqual(['b1', 'b2']);
    expect(req.request.params.get('busca')).toBe('ABC');
    expect(req.request.params.get('ativo')).toBe('true');
    expect(req.request.params.has('situacoes')).toBe(false);
    expect(req.request.params.has('regime')).toBe(false);
    expect(req.request.params.getAll('sort')).toEqual(['placa']);
    req.flush({ content: [], page: { size: 500, number: 0, totalElements: 0, totalPages: 0 } });
  });

  it('cria documento em multipart com os dados em JSON e o arquivo opcional', () => {
    const arquivo = new File(['%PDF'], 'crlv.pdf', { type: 'application/pdf' });
    service
      .criarDocumento(
        {
          idVeiculo: 'v1',
          idImplemento: null,
          tipo: 'CRLV',
          descricao: null,
          numero: '123',
          dataEmissao: null,
          dataVencimento: '2027-01-31',
          observacao: null
        },
        arquivo
      )
      .subscribe();

    const req = http.expectOne(`${base}/documentos`);
    expect(req.request.method).toBe('POST');
    const corpo = req.request.body as FormData;
    expect(corpo.get('arquivo')).toBeInstanceOf(File);
    expect(corpo.get('dados')).toBeInstanceOf(Blob);
    req.flush({});
  });

  it('alterna implemento entre desativar e reativar conforme o estado', () => {
    service.alternarImplemento({ id: 'i1', ativo: true } as never).subscribe();
    http.expectOne((r) => r.method === 'DELETE' && r.url === `${base}/implementos/i1`).flush(null);

    service.alternarImplemento({ id: 'i1', ativo: false } as never).subscribe();
    http.expectOne((r) => r.method === 'PUT' && r.url === `${base}/implementos/i1/ativar`).flush(null);
  });

  it('reaproveita as opções carregadas', () => {
    service.opcoes().subscribe();
    service.opcoes().subscribe();
    http.expectOne(`${base}/opcoes`).flush({});
  });
});
