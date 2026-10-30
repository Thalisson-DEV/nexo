import '@angular/compiler';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  ehErroDeChunkAusente,
  recarregarPorVersaoObsoleta,
  tratarErroDeNavegacao
} from './versao-obsoleta';

/**
 * A recuperação da aba que ficou em um build antigo depois de um deploy.
 *
 * O incidente coberto aqui (07/08/2026): o desconto foi gravado — todas as requisições
 * voltaram 201 —, mas a navegação para a lista pediu um chunk que o deploy havia removido
 * e veio 404. A tela não mudou, o usuário concluiu que não tinha salvo, e lançou o mesmo
 * desconto três vezes.
 */
describe('versao-obsoleta', () => {
  let assign: ReturnType<typeof vi.fn>;
  let reload: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    assign = vi.fn();
    reload = vi.fn();
    // O runner roda sem DOM; o par sessionStorage/location é o mínimo que este módulo toca.
    const guardado = new Map<string, string>();
    vi.stubGlobal('sessionStorage', {
      getItem: (chave: string) => guardado.get(chave) ?? null,
      setItem: (chave: string, valor: string) => void guardado.set(chave, valor)
    });
    vi.stubGlobal('location', { assign, reload, href: 'https://ces.local/dp/descontos/novo' });
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  describe('ehErroDeChunkAusente', () => {
    // Cada navegador nomeia a mesma falha do seu jeito e não há código padronizado;
    // é por isso que o reconhecimento é por texto, sobre as formas conhecidas.
    it.each([
      ['Chrome', new Error('Failed to fetch dynamically imported module: /chunk-27Y3BWRZ.js')],
      ['Firefox', new Error('error loading dynamically imported module')],
      ['Safari', new Error('Importing a module script failed.')],
      ['webpack', Object.assign(new Error('Loading chunk 42 failed.'), { name: 'ChunkLoadError' })]
    ])('reconhece a falha do %s', (_navegador, erro) => {
      expect(ehErroDeChunkAusente(erro)).toBe(true);
    });

    it.each([
      ['erro de negócio', new Error('Valor da parcela excede o teto do motivo')],
      ['nulo', null],
      ['indefinido', undefined],
      ['objeto sem mensagem', {}]
    ])('não confunde %s com versão obsoleta', (_caso, erro) => {
      expect(ehErroDeChunkAusente(erro)).toBe(false);
    });
  });

  describe('recarregarPorVersaoObsoleta', () => {
    it('leva o usuário ao destino pretendido, e não de volta ao formulário', () => {
      expect(recarregarPorVersaoObsoleta('/dp/descontos/lancamento')).toBe(true);
      expect(assign).toHaveBeenCalledWith('/dp/descontos/lancamento');
      expect(reload).not.toHaveBeenCalled();
    });

    it('recarrega no lugar quando o chunk falhou fora de uma navegação', () => {
      expect(recarregarPorVersaoObsoleta()).toBe(true);
      expect(reload).toHaveBeenCalledOnce();
    });

    // Um destino absoluto só poderia vir de erro ou de manipulação; em nenhum dos dois
    // casos vale a pena mandar o usuário para fora da aplicação.
    it.each(['//evil.example.com/x', 'https://evil.example.com/x'])(
      'ignora o destino externo %s e recarrega no lugar',
      (destino) => {
        expect(recarregarPorVersaoObsoleta(destino)).toBe(true);
        expect(assign).not.toHaveBeenCalled();
        expect(reload).toHaveBeenCalledOnce();
      }
    );

    // Se o chunk continua faltando depois de recarregar, o problema não é versão obsoleta.
    // Insistir trocaria uma tela quebrada por um piscar infinito, que esconde o erro.
    it('não recarrega duas vezes seguidas', () => {
      expect(recarregarPorVersaoObsoleta('/home')).toBe(true);
      expect(recarregarPorVersaoObsoleta('/home')).toBe(false);
      expect(assign).toHaveBeenCalledOnce();
    });
  });

  describe('tratarErroDeNavegacao', () => {
    it('recupera a navegação que morreu no chunk ausente', () => {
      tratarErroDeNavegacao({
        url: '/dp/descontos/lancamento',
        error: new Error('Failed to fetch dynamically imported module: /chunk-27Y3BWRZ.js')
      });

      expect(assign).toHaveBeenCalledWith('/dp/descontos/lancamento');
    });

    it('deixa passar a falha de navegação que não é de versão', () => {
      tratarErroDeNavegacao({ url: '/frota', error: new Error('Guard negou o acesso') });

      expect(assign).not.toHaveBeenCalled();
      expect(reload).not.toHaveBeenCalled();
    });
  });
});
