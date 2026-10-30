import { HttpEvent, HttpInterceptorFn } from '@angular/common/http';
import { Observable } from 'rxjs';
import { finalize, shareReplay } from 'rxjs/operators';
import {
  HEADER_IDEMPOTENCIA,
  digitalDaMutacao,
  novaChaveIdempotencia
} from '../utils/idempotencia.util';

// DELETE também é uma escrita e precisa da mesma proteção contra reenvio.
const METODOS_DE_ESCRITA = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

/**
 * Mutações idênticas ainda em voo, endereçadas pela impressão digital da requisição.
 *
 * Vive no módulo (e não em um serviço) porque é estado de transporte, sem ciclo de vida
 * próprio: nasce e morre com a requisição que representa.
 */
const emVoo = new Map<string, Observable<HttpEvent<unknown>>>();

/**
 * Garante que uma escrita repetida não vire um registro repetido.
 *
 * <p>Atua em duas frentes, porque o problema tem duas caras:</p>
 *
 * <ol>
 *   <li><b>Duplo clique</b> — dois cliques em milissegundos viram duas requisições
 *       idênticas. Aqui a segunda não sai do navegador: ela é atendida pela resposta da
 *       primeira, que já está a caminho. Do ponto de vista da tela, os dois cliques
 *       simplesmente deram certo.</li>
 *   <li><b>Retentativa</b> — o usuário que vê um erro e aperta salvar de novo, ou a fila
 *       offline reenviando o que ficou pendente. Aqui a requisição sai mesmo, carimbada
 *       com a chave de idempotência; quem decide se executa ou repete a resposta anterior
 *       é o backend, que é o único lado que sabe o que já foi gravado.</li>
 * </ol>
 *
 * <p>Quem já traz o header — a fila de sincronização e o formulário de desconto, que
 * precisam de uma chave <i>estável entre tentativas</i> — mantém a sua. Gerar uma chave
 * nova a cada tentativa anularia toda a proteção: seriam duas intenções distintas aos
 * olhos do servidor.</p>
 */
export const idempotencyInterceptor: HttpInterceptorFn = (req, next) => {
  if (!METODOS_DE_ESCRITA.has(req.method.toUpperCase())) {
    return next(req);
  }

  const requisicao = req.headers.has(HEADER_IDEMPOTENCIA)
    ? req
    : req.clone({ setHeaders: { [HEADER_IDEMPOTENCIA]: novaChaveIdempotencia() } });

  // Requisição com progresso emite muitos eventos e costuma ser upload longo: coalescer
  // exigiria bufferizar o fluxo inteiro, e o ganho não paga o custo. A chave já protege.
  if (requisicao.reportProgress) {
    return next(requisicao);
  }

  const digital = digitalDaMutacao(requisicao.method, requisicao.urlWithParams, requisicao.body);
  if (digital === null) {
    return next(requisicao);
  }

  const jaEmVoo = emVoo.get(digital);
  if (jaEmVoo) {
    return jaEmVoo;
  }

  // bufferSize infinito para que o segundo assinante receba a sequência inteira de eventos,
  // e não apenas o último — quem usa `observe: 'events'` depende disso.
  const compartilhada = next(requisicao).pipe(
    finalize(() => emVoo.delete(digital)),
    shareReplay({ bufferSize: Infinity, refCount: false })
  );

  emVoo.set(digital, compartilhada);
  return compartilhada;
};
