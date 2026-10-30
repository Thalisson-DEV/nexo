import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PoliticaRoteamentoRequest } from '../models/notificacao.models';
import { NotificacoesService } from './notificacoes.service';

describe('NotificacoesService — políticas de roteamento', () => {
  let service: NotificacoesService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(NotificacoesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('usa a coleção não paginada para listar políticas', () => {
    service.listarPoliticas().subscribe((politicas) => expect(politicas).toEqual([]));

    const request = http.expectOne((req) => req.url.endsWith('/v1/notificacoes/politicas-roteamento'));
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('envia o estado completo no PUT e não inclui ativo no payload', () => {
    const payload = politicaRequest();
    service.atualizarPolitica('politica-1', payload).subscribe();

    const request = http.expectOne((req) =>
      req.url.endsWith('/v1/notificacoes/politicas-roteamento/politica-1')
    );
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(payload);
    expect(request.request.body.ativo).toBeUndefined();
    request.flush({});
  });

  it.each([
    [true, 'ativacao'],
    [false, 'desativacao']
  ])('usa o endpoint próprio ao alternar ativação', (ativar, acao) => {
    service.alternarPolitica('politica-1', ativar as boolean).subscribe();

    const request = http.expectOne((req) =>
      req.url.endsWith(`/v1/notificacoes/politicas-roteamento/politica-1/${acao}`)
    );
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toBeNull();
    request.flush({});
  });
});

function politicaRequest(): PoliticaRoteamentoRequest {
  return {
    chaveGatilho: 'financeiro.solicitacao_submetida',
    nome: 'Política',
    descricao: null,
    rotas: [
      {
        ordem: 10,
        padrao: false,
        usuarioId: 'usuario-1',
        condicoes: [{ campo: 'base', operador: 'EM', valor: 'JUAZEIRO,REMANSO' }]
      },
      { ordem: 100, padrao: true, usuarioId: 'usuario-2', condicoes: [] }
    ]
  };
}
