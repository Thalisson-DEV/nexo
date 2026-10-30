import '@angular/compiler';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { VersaoAppService } from './versao-app.service';
import { ConnectivityService } from './connectivity.service';

/**
 * O aviso que evita que uma aba siga rodando um build que já saiu do ar.
 *
 * O incidente de referência (07/08/2026): a aba carregava o `main-5BENJM2M.js`, o servidor
 * já servia o `main-YBHCXDWH.js`, e a primeira rota lazy que o usuário pediu veio 404 —
 * a navegação morreu calada e o mesmo desconto acabou lançado três vezes.
 */
describe('VersaoAppService', () => {
  let fetchMock: ReturnType<typeof vi.fn>;
  let script: HTMLScriptElement;

  /** O serviço lê a versão em execução ao ser construído: o script precede a injeção. */
  function criarServico(): VersaoAppService {
    return TestBed.inject(VersaoAppService);
  }

  function indexServindo(bundle: string) {
    return {
      ok: true,
      text: () => Promise.resolve(`<!doctype html><script src="${bundle}" type="module"></script>`)
    };
  }

  /** Passa pela superfície pública: voltar para a aba é um dos gatilhos de checagem. */
  async function voltarParaAba(): Promise<void> {
    document.dispatchEvent(new Event('visibilitychange'));
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalled());
  }

  beforeEach(() => {
    script = document.createElement('script');
    script.setAttribute('src', 'main-5BENJM2M.js');
    document.head.appendChild(script);

    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    vi.spyOn(console, 'info').mockImplementation(() => {});

    TestBed.configureTestingModule({ providers: [ConnectivityService] });
  });

  afterEach(() => {
    script.remove();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('avisa quando o servidor passa a servir outro build', async () => {
    fetchMock.mockResolvedValue(indexServindo('main-YBHCXDWH.js'));
    const servico = criarServico();

    await voltarParaAba();

    await vi.waitFor(() => expect(servico.atualizacaoDisponivel()).toBe(true));
    expect(fetchMock).toHaveBeenCalledWith('index.html', { cache: 'no-store' });
  });

  it('fica calado enquanto o build servido é o mesmo que esta aba roda', async () => {
    fetchMock.mockResolvedValue(indexServindo('main-5BENJM2M.js'));
    const servico = criarServico();

    await voltarParaAba();

    expect(servico.atualizacaoDisponivel()).toBe(false);
  });

  // Offline, a ausência de resposta não é informação sobre a versão — e a fila de
  // sincronização já é quem cuida de esperar a rede voltar.
  it('não consulta o servidor quando está offline', async () => {
    TestBed.inject(ConnectivityService).isOnline.set(false);
    const servico = criarServico();

    document.dispatchEvent(new Event('visibilitychange'));

    expect(fetchMock).not.toHaveBeenCalled();
    expect(servico.atualizacaoDisponivel()).toBe(false);
  });

  it('esconde o aviso quando o usuário pede para adiar', async () => {
    fetchMock.mockResolvedValue(indexServindo('main-YBHCXDWH.js'));
    const servico = criarServico();
    await voltarParaAba();
    await vi.waitFor(() => expect(servico.atualizacaoDisponivel()).toBe(true));

    servico.adiar();

    expect(servico.atualizacaoDisponivel()).toBe(false);
  });

  // Servidor reiniciando no meio do deploy é exatamente quando a checagem falha — e não
  // há nada de útil a dizer ao usuário sobre isso.
  it('engole a falha de rede sem alarmar o usuário', async () => {
    fetchMock.mockRejectedValue(new Error('Failed to fetch'));
    const servico = criarServico();

    await voltarParaAba();

    expect(servico.atualizacaoDisponivel()).toBe(false);
  });
});
