import '@angular/compiler';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { idempotencyInterceptor } from './idempotency.interceptor';
import { HEADER_IDEMPOTENCIA } from '../utils/idempotencia.util';

/**
 * O contrato que impede a repetição do incidente de 06/08/2026, em que o mesmo desconto
 * foi gravado três vezes porque o usuário, sem resposta na tela, apertou salvar de novo.
 *
 * Este arquivo cobre o lado do cliente. A outra metade da garantia — reconhecer a chave e
 * repetir a resposta sem reexecutar — vive no backend (`IdempotenciaFilterTest`).
 */
describe('idempotencyInterceptor', () => {
  let http: HttpClient;
  let mock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([idempotencyInterceptor])),
        provideHttpClientTesting()
      ]
    });
    http = TestBed.inject(HttpClient);
    mock = TestBed.inject(HttpTestingController);
  });

  it('carimba toda escrita com uma chave de idempotência', () => {
    http.post('/api/v1/descontos', { motivoId: 'x' }).subscribe();

    const req = mock.expectOne('/api/v1/descontos');
    expect(req.request.headers.get(HEADER_IDEMPOTENCIA)).toBeTruthy();
    req.flush({ id: 'abc' });
    mock.verify();
  });

  it('não carimba leituras — a promessa é sobre escrita', () => {
    http.get('/api/v1/descontos').subscribe();

    const req = mock.expectOne('/api/v1/descontos');
    expect(req.request.headers.has(HEADER_IDEMPOTENCIA)).toBe(false);
    req.flush({ content: [] });
    mock.verify();
  });

  it('preserva a chave de quem já a trouxe — retentativa precisa reusar a mesma', () => {
    http
      .post('/api/v1/descontos', { motivoId: 'x' }, { headers: { [HEADER_IDEMPOTENCIA]: 'chave-do-formulario' } })
      .subscribe();

    const req = mock.expectOne('/api/v1/descontos');
    expect(req.request.headers.get(HEADER_IDEMPOTENCIA)).toBe('chave-do-formulario');
    req.flush({ id: 'abc' });
    mock.verify();
  });

  it('duplo clique não sai do navegador: uma requisição só, dois resultados', () => {
    const recebidos: unknown[] = [];

    // Dois cliques idênticos com milissegundos de diferença — o segundo antes de o
    // primeiro voltar. É a forma exata do incidente.
    http.post('/api/v1/descontos', { motivoId: 'x' }).subscribe((r) => recebidos.push(r));
    http.post('/api/v1/descontos', { motivoId: 'x' }).subscribe((r) => recebidos.push(r));

    const req = mock.expectOne('/api/v1/descontos');
    req.flush({ id: 'abc' });

    expect(recebidos).toEqual([{ id: 'abc' }, { id: 'abc' }]);
    mock.verify();
  });

  it('depois que a primeira volta, um novo clique é uma nova intenção e sai de verdade', () => {
    http.post('/api/v1/descontos', { motivoId: 'x' }).subscribe();
    mock.expectOne('/api/v1/descontos').flush({ id: 'abc' });

    http.post('/api/v1/descontos', { motivoId: 'x' }).subscribe();
    const segunda = mock.expectOne('/api/v1/descontos');
    segunda.flush({ id: 'def' });

    mock.verify();
  });

  it('corpos diferentes são intenções diferentes e não são coalescidos', () => {
    http.post('/api/v1/descontos', { motivoId: 'a' }).subscribe();
    http.post('/api/v1/descontos', { motivoId: 'b' }).subscribe();

    const requisicoes = mock.match('/api/v1/descontos');
    expect(requisicoes.length).toBe(2);
    expect(requisicoes[0].request.headers.get(HEADER_IDEMPOTENCIA)).not.toBe(
      requisicoes[1].request.headers.get(HEADER_IDEMPOTENCIA)
    );
    requisicoes.forEach((r) => r.flush({}));
    mock.verify();
  });

  it('anexos iguais em voo também são coalescidos, apesar do FormData', () => {
    const arquivo = new File(['conteudo'], 'foto.jpg', { type: 'image/jpeg' });
    const corpo = () => {
      const fd = new FormData();
      fd.append('arquivo', arquivo);
      return fd;
    };

    http.post('/api/v1/descontos/1/anexos', corpo()).subscribe();
    http.post('/api/v1/descontos/1/anexos', corpo()).subscribe();

    const req = mock.expectOne('/api/v1/descontos/1/anexos');
    req.flush({ id: 'anexo-1' });
    mock.verify();
  });

  it('a falha libera a digital: quem corrige e reenvia não fica preso ao erro anterior', () => {
    http.post('/api/v1/descontos', { motivoId: 'x' }).subscribe({ error: () => undefined });
    mock.expectOne('/api/v1/descontos').flush(
      { detail: 'erro' },
      { status: 500, statusText: 'Server Error' }
    );

    http.post('/api/v1/descontos', { motivoId: 'x' }).subscribe();
    mock.expectOne('/api/v1/descontos').flush({ id: 'abc' });

    mock.verify();
  });

  it('carimba DELETE com uma chave de idempotência', () => {
    http.delete('/api/v1/descontos/abc').subscribe();

    const req = mock.expectOne('/api/v1/descontos/abc');
    expect(req.request.headers.get(HEADER_IDEMPOTENCIA)).toBeTruthy();
    req.flush(null);
    mock.verify();
  });

  it('preserva uma chave explícita em DELETE', () => {
    http
      .delete('/api/v1/descontos/abc', { headers: { [HEADER_IDEMPOTENCIA]: 'chave-remocao' } })
      .subscribe();

    const req = mock.expectOne('/api/v1/descontos/abc');
    expect(req.request.headers.get(HEADER_IDEMPOTENCIA)).toBe('chave-remocao');
    req.flush(null);
    mock.verify();
  });

  it('coalesce DELETEs idênticos em voo e libera nova intenção após a resposta', () => {
    const recebidos: unknown[] = [];
    http.delete('/api/v1/descontos/abc').subscribe((r) => recebidos.push(r));
    http.delete('/api/v1/descontos/abc').subscribe((r) => recebidos.push(r));

    const primeira = mock.expectOne('/api/v1/descontos/abc');
    primeira.flush({ removido: true });
    expect(recebidos).toEqual([{ removido: true }, { removido: true }]);

    http.delete('/api/v1/descontos/abc').subscribe();
    const segunda = mock.expectOne('/api/v1/descontos/abc');
    expect(segunda.request.headers.get(HEADER_IDEMPOTENCIA)).toBeTruthy();
    expect(segunda.request.headers.get(HEADER_IDEMPOTENCIA)).not.toBe(
      primeira.request.headers.get(HEADER_IDEMPOTENCIA)
    );
    segunda.flush(null);
    mock.verify();
  });
});
