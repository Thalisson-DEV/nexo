// `app.routes.ts` importa componentes de forma direta, e avaliá-los exige o compilador — que os
// specs com TestBed já carregam por outro caminho. Aqui a importação é explícita.
import '@angular/compiler';
import { Routes } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { routes } from '../../../../app.routes';
import { DESTINOS_DEEPLINK, ROTAS_CONHECIDAS, rotaConhecida } from './destinos-deeplink';

/**
 * Achata a árvore de rotas em caminhos absolutos, no mesmo formato de `ROTAS_CONHECIDAS`.
 * Redirecionamentos e o curinga `**` ficam de fora: não são destinos, são desvios.
 */
function achatar(rotas: Routes, prefixo = ''): string[] {
  return rotas.flatMap((rota) => {
    if (rota.path === undefined || rota.path === '**' || rota.redirectTo !== undefined) return [];

    const caminho = [prefixo, rota.path].filter((parte) => parte !== '').join('/');
    const proprio = rota.component || rota.loadComponent ? [`/${caminho}`] : [];
    return [...proprio, ...achatar(rota.children ?? [], caminho)];
  });
}

const ROTAS_REAIS = new Set(achatar(routes));

describe('catálogo de destinos das regras de notificação', () => {
  it('não guarda nenhuma rota que não exista mais em app.routes.ts', () => {
    // A lista é uma cópia mantida à mão. Quando uma rota é renomeada ou removida, este teste é o
    // que impede o catálogo de continuar oferecendo um caminho morto.
    const orfas = ROTAS_CONHECIDAS.filter((rota) => !ROTAS_REAIS.has(rota));
    expect(orfas).toEqual([]);
  });

  it('só oferece destinos que levam a uma tela real', () => {
    const invalidos = DESTINOS_DEEPLINK.flatMap((grupo) => grupo.items)
      .filter((destino) => !rotaConhecida(destino.valor))
      .map((destino) => `${destino.label}: ${destino.valor}`);
    expect(invalidos).toEqual([]);
  });

  it('aceita os caminhos corrigidos na V80', () => {
    const corrigidos = [
      '/dp/descontos/{{idDesconto}}',
      '/dp/descontos/lancamento',
      '/chamados/meus/{{idChamado}}',
      '/chamados/fila/{{idChamado}}',
      '/chamados/fila',
      '/financeiro/compras/{{idSolicitacao}}',
      '/financeiro/pagamentos/{{idSolicitacao}}',
      '/financeiro/aprovacoes/compras',
      '/almoxarifado/solicitacoes/{{idSolicitacao}}',
      '/sst/dashboards/desvios'
    ];
    corrigidos.forEach((caminho) => expect(rotaConhecida(caminho), caminho).toBe(true));
  });

  it('recusa os caminhos quebrados que a V80 corrigiu', () => {
    // Eram silenciosos: a rota `**` redireciona para `home` e o usuário caía na tela inicial.
    const quebrados = [
      '/descontos/{{idDesconto}}',
      '/chamados/{{idChamado}}',
      '/financeiro/solicitacoes/{{idSolicitacao}}',
      '/estoque/solicitacoes/{{idSolicitacao}}',
      '/sst/desvios'
    ];
    quebrados.forEach((caminho) => expect(rotaConhecida(caminho), caminho).toBe(false));
  });

  it('trata destino vazio como aviso não clicável, e não como erro', () => {
    expect(rotaConhecida('')).toBe(true);
    expect(rotaConhecida(null)).toBe(true);
    expect(rotaConhecida(undefined)).toBe(true);
  });

  it('ignora query string e âncora ao validar o caminho', () => {
    expect(rotaConhecida('/chamados/fila?status=ABERTO')).toBe(true);
    expect(rotaConhecida('/mural/{{idComunicado}}#anexos')).toBe(true);
  });

  it('recusa caminho relativo — o deeplink é sempre absoluto', () => {
    expect(rotaConhecida('chamados/fila')).toBe(false);
  });
});
