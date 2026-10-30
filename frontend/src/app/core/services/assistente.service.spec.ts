import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';
import { AssistenteService, dividirNdjson } from './assistente.service';

describe('AssistenteService', () => {
  let service: AssistenteService;
  let http: HttpTestingController;
  const auth = {
    token: () => 'token-de-teste',
    tokenAtualizado: async () => 'token-de-teste',
    tratarNaoAutorizado: vi.fn(),
    reautenticacaoNecessaria: () => false,
    logout: vi.fn(),
  };
  const base = `${environment.apiUrl}/v1/assistente`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AuthService, useValue: auth }],
    });
    service = TestBed.inject(AssistenteService);
    http = TestBed.inject(HttpTestingController);
    auth.logout.mockClear();
  });

  afterEach(() => {
    http.verify();
    vi.unstubAllGlobals();
  });

  it('separa linhas NDJSON partidas entre blocos e ignora linhas vazias', () => {
    const primeira = dividirNdjson('{"placement":{"turnIndex":0,"tabIndex":0},"obj":{"type":"message_delta","content":"Oi"}}\r\n\n{"obj":');
    expect(primeira.pacotes).toHaveLength(1);
    expect(primeira.resto).toBe('{"obj":');
    expect(dividirNdjson(primeira.resto + '{"type":"stop"}}\n').pacotes[0].obj.type).toBe('stop');
  });

  it('lista conversas com paginação e busca', () => {
    service.listarConversas(2, 15, '  estoque  ').subscribe();
    const pedido = http.expectOne((req) => req.url === `${base}/conversas`);
    expect(pedido.request.params.get('pagina')).toBe('2');
    expect(pedido.request.params.get('tamanho')).toBe('15');
    expect(pedido.request.params.get('busca')).toBe('estoque');
    pedido.flush({ content: [], totalElements: 0 });
  });

  it('cria, consulta, renomeia, para e exclui uma conversa', () => {
    service.criarConversa({ titulo: 'Análise' }).subscribe();
    const criacao = http.expectOne(`${base}/conversas`);
    expect(criacao.request.body).toEqual({ titulo: 'Análise' });
    criacao.flush({ id: 'c1' });
    service.buscarConversa('c1').subscribe();
    http.expectOne(`${base}/conversas/c1`).flush({ id: 'c1', mensagens: [] });
    service.renomearConversa('c1', 'Novo título').subscribe();
    const titulo = http.expectOne(`${base}/conversas/c1`);
    expect(titulo.request.method).toBe('PATCH');
    expect(titulo.request.body).toEqual({ titulo: 'Novo título' });
    titulo.flush({ id: 'c1' });
    service.parar('c1').subscribe();
    http.expectOne(`${base}/conversas/c1/parar`).flush({ interrompida: true });
    service.excluirConversa('c1').subscribe();
    const excluir = http.expectOne(`${base}/conversas/c1`);
    expect(excluir.request.method).toBe('DELETE');
    excluir.flush(null);
  });

  it('busca capacidades, envia arquivo e baixa o conteúdo como blob', () => {
    service.capacidades().subscribe();
    http.expectOne(`${base}/capacidades`).flush({ disponivel: true });
    service.enviarArquivo(new File(['dados'], 'dados.csv', { type: 'text/csv' })).subscribe();
    const envio = http.expectOne(`${base}/arquivos`);
    expect((envio.request.body as FormData).get('arquivo')).toBeInstanceOf(File);
    expect(envio.request.reportProgress).toBe(true);
    envio.flush({ id: 'f1' });
    service.baixarArquivo('f1').subscribe();
    const download = http.expectOne((req) => req.url === `${base}/arquivos/f1/conteudo`);
    expect(download.request.responseType).toBe('blob');
    download.flush(new Blob(['arquivo']));
  });

  it('transmite pacotes na ordem, com token e sem chave de idempotência', async () => {
    const bytes = new TextEncoder().encode('{"placement":{"turnIndex":0,"tabIndex":0},"obj":{"type":"message_delta","content":"Oi"}}\n');
    vi.stubGlobal('fetch', vi.fn(async (_url: string, init: RequestInit) => {
      expect((init.headers as Record<string, string>)['Authorization']).toBe('Bearer token-de-teste');
      expect((init.headers as Record<string, string>)['Idempotency-Key']).toBeUndefined();
      return new Response(new ReadableStream({ start(controller) {
        controller.enqueue(bytes.slice(0, 19));
        controller.enqueue(bytes.slice(19));
        controller.close();
      } }));
    }));
    const tipos: string[] = [];
    await service.transmitir('c1', { clientMessageId: 'm1', conteudo: 'Oi' }, pacote => tipos.push(pacote.obj.type), new AbortController().signal);
    expect(tipos).toEqual(['message_delta']);
  });

  it('expõe o detalhe do erro HTTP ao retomar', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ detail: 'Conversa já em execução' }), { status: 409 })));
    await expect(service.retomar('c1', 3, () => undefined, new AbortController().signal))
      .rejects.toMatchObject({ status: 409, message: 'Conversa já em execução' });
    expect(vi.mocked(fetch).mock.calls[0][0]).toContain('cursor=3');
  });
});
