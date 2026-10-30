import '@angular/compiler';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import { FinanceiroService } from './financeiro.service';

describe('FinanceiroService buscarOpcoesReferencia', () => {
  let service: FinanceiroService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(FinanceiroService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('encaminha busca e página ao catálogo e preserva os metadados', () => {
    let result: unknown;
    service.buscarOpcoesReferencia('/v1/veiculos', 'ABC', false, 2, 30).subscribe((value) => {
      result = value;
    });

    const request = http.expectOne((req) => req.url === `${environment.apiUrl}/v1/veiculos`);
    expect(request.request.params.get('buscaGlobal')).toBe('ABC');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('30');
    request.flush({
      content: [{ id: 'v1', placa: 'ABC1D23', modelo: 'Utilitário' }],
      page: { number: 2, size: 30, totalElements: 301, totalPages: 11 }
    });

    expect(result).toEqual({
      content: [{ id: 'v1', label: 'ABC1D23 — Utilitário', raw: { id: 'v1', placa: 'ABC1D23', modelo: 'Utilitário' } }],
      page: { number: 2, size: 30, totalElements: 301, totalPages: 11 }
    });
  });

  it('normaliza endpoints legados que ainda devolvem lista simples', () => {
    let totalElements = -1;
    service.buscarOpcoesReferencia('/v1/opcoes-legadas', '').subscribe((value) => {
      totalElements = value.page.totalElements;
    });

    http.expectOne((req) => req.url.endsWith('/v1/opcoes-legadas')).flush([
      { id: '1', nome: 'Primeira opção' },
      { id: '2', descricao: 'Segunda opção' }
    ]);

    expect(totalElements).toBe(2);
  });

  it('envia busca global normalizada e ordenação global por fornecedor', () => {
    service
      .listarSolicitacoes('PAGAMENTO', {
        buscaGlobal: '  chave+pix@exemplo.com  ',
        page: 0,
        size: 20,
        sort: 'fornecedor,asc'
      })
      .subscribe();

    const request = http.expectOne((req) => req.url === `${environment.apiUrl}/v1/financeiro/solicitacoes-pagamento`);
    expect(request.request.params.get('buscaGlobal')).toBe('chave+pix@exemplo.com');
    expect(request.request.params.get('sort')).toBe('fornecedor,asc');
    expect(request.request.urlWithParams).toContain('chave%2Bpix');
    request.flush({
      content: [],
      page: { number: 0, size: 20, totalElements: 0, totalPages: 0 }
    });
  });

  it('remove busca global composta apenas por espaços', () => {
    service.listarSolicitacoes('COMPRA', { buscaGlobal: '   ', page: 0, size: 20 }).subscribe();

    const request = http.expectOne((req) => req.url.endsWith('/solicitacoes-compra'));
    expect(request.request.params.has('buscaGlobal')).toBe(false);
    request.flush({
      content: [],
      page: { number: 0, size: 20, totalElements: 0, totalPages: 0 }
    });
  });

  it('serializa filtros estruturados escalares e omite textos vazios', () => {
    service
      .listarSolicitacoes('PAGAMENTO', {
        numero: 1042,
        status: 'APROVADO',
        fornecedorId: ' fornecedor-1 ',
        apenasMinhas: false,
        valorMinimo: 1500.5,
        temNf: true,
        numeroNf: '   ',
        possuiDocumentos: false,
        dataCriacaoInicio: '2026-09-01T00:00:00.000-03:00',
        dataCriacaoFim: '2026-09-30T23:59:59.999-03:00',
        solicitacaoCompraId: 'compra-1',
        possuiCompraPrevia: true,
        motivoSemCompra: null,
        page: 0,
        size: 20,
        sort: 'dataCriacao,desc'
      })
      .subscribe();

    const request = http.expectOne((req) => req.url.endsWith('/solicitacoes-pagamento'));
    expect(request.request.params.get('numero')).toBe('1042');
    expect(request.request.params.get('status')).toBe('APROVADO');
    expect(request.request.params.get('fornecedorId')).toBe('fornecedor-1');
    expect(request.request.params.get('apenasMinhas')).toBe('false');
    expect(request.request.params.get('valorMinimo')).toBe('1500.5');
    expect(request.request.params.get('temNf')).toBe('true');
    expect(request.request.params.has('numeroNf')).toBe(false);
    expect(request.request.params.get('possuiDocumentos')).toBe('false');
    expect(request.request.params.get('solicitacaoCompraId')).toBe('compra-1');
    expect(request.request.params.get('possuiCompraPrevia')).toBe('true');
    expect(request.request.params.has('motivoSemCompra')).toBe(false);
    request.flush({
      content: [],
      page: { number: 0, size: 20, totalElements: 0, totalPages: 0 }
    });
  });

  it('usa rotas aninhadas e preserva a chave de idempotência quando fornecida', () => {
    service.baixarDocumentoDaSolicitacao('COMPRA', 'sol-1', 'doc-1').subscribe();
    const download = http.expectOne((req) => req.url.endsWith('/solicitacoes-compra/sol-1/documentos/doc-1'));
    expect(download.request.method).toBe('GET');
    download.flush(new Blob(['conteúdo'], { type: 'application/pdf' }));

    service.removerDocumentoDaSolicitacao('COMPRA', 'sol-1', 'doc-1', 'chave-remocao').subscribe();
    const remove = http.expectOne((req) => req.url.endsWith('/solicitacoes-compra/sol-1/documentos/doc-1'));
    expect(remove.request.method).toBe('DELETE');
    expect(remove.request.headers.get('Idempotency-Key')).toBe('chave-remocao');
    remove.flush(null);

    service.conferirDocumento('doc-1', { observacao: 'Conferido', }, 'chave-conferencia').subscribe();
    const conferir = http.expectOne((req) => req.url.endsWith('/documentos/doc-1/conferir'));
    expect(conferir.request.body).toEqual({ observacao: 'Conferido' });
    expect(conferir.request.headers.get('Idempotency-Key')).toBe('chave-conferencia');
    conferir.flush({});

    service.apontarPendenciaDocumento('doc-1', { motivo: 'Valor divergente' }, 'chave-pendencia').subscribe();
    const pendencia = http.expectOne((req) => req.url.endsWith('/documentos/doc-1/pendencia'));
    expect(pendencia.request.body).toEqual({ motivo: 'Valor divergente' });
    pendencia.flush({});

    service.arquivarDocumento('doc-1', 'chave-arquivar').subscribe();
    const arquivar = http.expectOne((req) => req.url.endsWith('/documentos/doc-1/arquivar'));
    expect(arquivar.request.method).toBe('PATCH');
    expect(arquivar.request.headers.get('Idempotency-Key')).toBe('chave-arquivar');
    arquivar.flush({});

    service.reabrirDocumento('doc-1').subscribe();
    const reabrir = http.expectOne((req) => req.url.endsWith('/documentos/doc-1/reabrir'));
    reabrir.flush({});

    service.desvincularDocumento('doc-1').subscribe();
    const desvincular = http.expectOne((req) => req.url.endsWith('/documentos/doc-1/desvincular'));
    desvincular.flush({});
  });

  it('anexa vários documentos repetindo a chave arquivo e sem definir Content-Type', () => {
    const primeiro = new File(['primeiro'], 'orcamento-1.pdf', { type: 'application/pdf' });
    const segundo = new File(['segundo'], 'orcamento-2.pdf', { type: 'application/pdf' });

    service
      .anexarDocumento('COMPRA', 'sol-1', [primeiro, segundo], 'ORCAMENTO', 'chave-upload')
      .subscribe();

    const request = http.expectOne((req) =>
      req.url.endsWith('/solicitacoes-compra/sol-1/documentos')
    );
    const formData = request.request.body as FormData;

    expect(request.request.method).toBe('POST');
    expect(request.request.params.get('tipo')).toBe('ORCAMENTO');
    expect(request.request.headers.has('Content-Type')).toBe(false);
    expect(request.request.headers.get('Idempotency-Key')).toBe('chave-upload');
    expect(formData.getAll('arquivo')).toEqual([primeiro, segundo]);
    expect(formData.has('arquivo[]')).toBe(false);
    expect(formData.has('arquivos')).toBe(false);
    request.flush({ documentos: [] });
  });

  it('recusa upload sem arquivo antes de chamar a API', () => {
    expect(() => service.anexarDocumento('PAGAMENTO', 'sol-1', [], 'BOLETO')).toThrow(
      'Selecione ao menos um arquivo.'
    );
  });

  it('exporta solicitações de compra em resumo sem page, size ou sort', () => {
    service
      .exportarSolicitacoesResumo('COMPRA', {
        status: 'APROVADO',
        buscaGlobal: 'termo',
        page: 2,
        size: 50,
        sort: 'dataCriacao,desc'
      })
      .subscribe((res) => {
        expect(res.body).toBeTruthy();
      });

    const req = http.expectOne(
      (r) => r.url === `${environment.apiUrl}/v1/financeiro/solicitacoes-compra/exportar/resumo`
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('status')).toBe('APROVADO');
    expect(req.request.params.get('buscaGlobal')).toBe('termo');
    expect(req.request.params.has('page')).toBe(false);
    expect(req.request.params.has('size')).toBe(false);
    expect(req.request.params.has('sort')).toBe(false);
    req.flush(new Blob(['coluna1;coluna2'], { type: 'text/csv' }));
  });

  it('exporta solicitações de pagamento em detalhado preservando booleanos false', () => {
    service
      .exportarSolicitacoesDetalhado('PAGAMENTO', {
        possuiDocumentos: false,
        possuiCompraPrevia: false,
        apenasMinhas: false
      })
      .subscribe((res) => {
        expect(res.body).toBeTruthy();
      });

    const req = http.expectOne(
      (r) => r.url === `${environment.apiUrl}/v1/financeiro/solicitacoes-pagamento/exportar/detalhado`
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('possuiDocumentos')).toBe('false');
    expect(req.request.params.get('possuiCompraPrevia')).toBe('false');
    expect(req.request.params.get('apenasMinhas')).toBe('false');
    req.flush(new Blob(['coluna1;coluna2'], { type: 'text/csv' }));
  });
});
