import { routes } from '../../../app.routes';
import { TipoCatalogoSst } from '../../../core/models/sst-evento.models';
import { CATALOGO_SST_PAGE_CONFIG } from './catalogo-sst-page.config';

describe('cadastros específicos do catálogo SST', () => {
  const cadastros: Array<{ path: string; tipo: TipoCatalogoSst; titulo: string }> = [
    {
      path: 'classificacoes-eventos',
      tipo: 'CLASSIFICACAO_EVENTO',
      titulo: 'Classificações de Eventos'
    },
    { path: 'locais-lesao', tipo: 'LOCAL_LESAO', titulo: 'Locais de Lesão' },
    { path: 'naturezas-lesao', tipo: 'NATUREZA_LESAO', titulo: 'Naturezas de Lesão' },
    { path: 'agentes-causadores', tipo: 'AGENTE_CAUSADOR', titulo: 'Agentes Causadores' }
  ];

  it('mantém uma apresentação própria para cada tipo do backend', () => {
    expect(Object.keys(CATALOGO_SST_PAGE_CONFIG)).toHaveLength(cadastros.length);

    for (const cadastro of cadastros) {
      expect(CATALOGO_SST_PAGE_CONFIG[cadastro.tipo].titulo).toBe(cadastro.titulo);
      expect(CATALOGO_SST_PAGE_CONFIG[cadastro.tipo].entityLabel).not.toContain('Entrada');
    }
  });

  it('fixa o tipo correto em cada rota e preserva o endereço antigo', () => {
    const rotasSst = routes.find((route) => route.path === 'sst')?.children ?? [];

    for (const cadastro of cadastros) {
      const rota = rotasSst.find((item) => item.path === cadastro.path);
      expect(rota?.data?.['tipoCatalogo']).toBe(cadastro.tipo);
      expect(rota?.data?.['permission']).toBe('sst:catalogo:read');
    }

    const rotaAntiga = rotasSst.find((item) => item.path === 'catalogos');
    expect(rotaAntiga?.redirectTo).toBe('classificacoes-eventos');
  });
});
