import { AssistenteAcao } from '../../../core/models/assistente.models';
import { COPILOTO_DESTINOS } from './copiloto-destinos';
import { resolveChatNavigation } from './copiloto-navegacao';

describe('navegação do Copiloto', () => {
  const uuid = '550e8400-e29b-41d4-a716-446655440000';

  it('publica exatamente as 86 chaves ativas do catálogo 1.1', () => {
    expect(Object.keys(COPILOTO_DESTINOS)).toHaveLength(86);
    // Espelha navigation-actions.yaml do backend (cadastro de tipos de serviço).
    expect(COPILOTO_DESTINOS['EXECUCAO_TIPOS_SERVICO'].routeAuthority).toBe('execucao:tipo-servico:read');
    for (const blocked of [
      'IAM_STATUS',
      'ALMOX_TRANSFERENCIA_NOVA',
      'ALMOX_ENTRADA_AVULSA',
      'ALMOX_BAIXA_AVULSA',
      // Alçadas de aprovação deixaram de existir: quem decide é definido por
      // permissão, não por faixa de valor.
      'FINANCEIRO_ALCADAS'
    ]) {
      expect(COPILOTO_DESTINOS[blocked]).toBeUndefined();
    }
    expect(COPILOTO_DESTINOS['DESCONTO_NOVO'].routeAuthority)
      .toBe('desconto:solicitar');
    expect(COPILOTO_DESTINOS['EXECUCAO_COMERCIAL'].routeAuthority)
      .toBe('execucao:comercial:read');
  });

  it('mantém rotas e parâmetros de caminho somente na allowlist estática', () => {
    for (const target of Object.values(COPILOTO_DESTINOS)) {
      expect(target.route.startsWith('/')).toBe(true);
      expect(target.route).not.toContain('://');
      const routeParameters = [...target.route.matchAll(/:([A-Za-z][A-Za-z0-9_]*)/g)]
        .map(match => match[1]);
      const declaredPathParameters = target.parameters
        .filter(parameter => parameter.destination === 'PATH')
        .map(parameter => parameter.name);
      expect(routeParameters).toEqual(declaredPathParameters);
    }
  });

  it('resolve uma rota estática autorizada', () => {
    expect(resolveChatNavigation(action({
      chaveDestino: 'DESCONTO_NOVO',
      autoridade: 'desconto:solicitar'
    }), ['desconto:solicitar'])).toEqual({
      key: 'DESCONTO_NOVO',
      route: '/dp/descontos/novo',
      queryParams: {}
    });
  });

  it('prioriza chaveDestino e usa destino somente para mensagens antigas', () => {
    const canonical = resolveChatNavigation(action({
      chaveDestino: 'CES_HOME',
      destino: 'DESCONTO_NOVO',
      autoridade: 'assistente:chat:use'
    }), ['assistente:chat:use', 'desconto:solicitar']);
    const legacy = resolveChatNavigation(action({
      destino: 'CES_HOME',
      autoridade: 'assistente:chat:use'
    }), ['assistente:chat:use']);

    expect(canonical?.route).toBe('/home');
    expect(legacy?.route).toBe('/home');
  });

  it('substitui UUID declarado no PATH e envia ENUM declarado na QUERY', () => {
    expect(resolveChatNavigation(action({
      chaveDestino: 'DESCONTO_DETALHE',
      autoridade: 'desconto:read',
      parametros: { id: uuid, mode: 'print' }
    }), ['desconto:read'])).toEqual({
      key: 'DESCONTO_DETALHE',
      route: `/dp/descontos/${uuid}`,
      queryParams: { mode: 'print' }
    });
  });

  it.each([
    ['UUID ausente', { mode: 'print' }],
    ['UUID inválido', { id: '../admin' }],
    ['ENUM inválido', { id: uuid, mode: 'edit' }],
    ['parâmetro extra', { id: uuid, redirect: 'https://example.com' }]
  ])('rejeita %s', (_case, parametros) => {
    expect(resolveChatNavigation(action({
      chaveDestino: 'DESCONTO_DETALHE',
      autoridade: 'desconto:read',
      parametros
    }), ['desconto:read'])).toBeNull();
  });

  it('rejeita chave, tipo, autoridade ou permissão incompatíveis', () => {
    expect(resolveChatNavigation(action({
      chaveDestino: 'https://example.com', autoridade: 'assistente:chat:use'
    }), ['assistente:chat:use'])).toBeNull();
    expect(resolveChatNavigation(action({
      tipo: 'navigate', chaveDestino: 'CES_HOME', autoridade: 'assistente:chat:use'
    }), ['assistente:chat:use'])).toBeNull();
    expect(resolveChatNavigation(action({
      chaveDestino: 'CES_HOME', autoridade: 'iam:usuario:read'
    }), ['assistente:chat:use', 'iam:usuario:read'])).toBeNull();
    expect(resolveChatNavigation(action({
      chaveDestino: 'CES_HOME', autoridade: 'assistente:chat:use'
    }), [])).toBeNull();
  });

  function action(overrides: Partial<AssistenteAcao>): AssistenteAcao {
    return {
      tipo: 'NAVIGATE',
      rotulo: 'Abrir tela',
      autoridade: 'assistente:chat:use',
      ...overrides
    };
  }
});
