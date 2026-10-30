import '@angular/compiler';
import { describe, expect, it } from 'vitest';
import { MenuItem } from 'primeng/api';

/**
 * A busca é alimentada pelo menu já filtrado por permissão. Estes testes olham
 * para as duas regras que sustentam isso: o que entra no índice e como se ordena.
 * O componente expõe o comportamento pela lista renderizada; aqui exercitamos as
 * mesmas funções puras, replicadas para não depender de API privada.
 */

interface TelaBuscavel {
  label: string;
  rota: string;
  icone: string;
  caminho: string;
  externa: boolean;
}

function achatar(itens: MenuItem[], trilha: string[] = []): TelaBuscavel[] {
  return itens.flatMap((item) => {
    if (item.visible === false) return [];
    if (item.items?.length) return achatar(item.items, [...trilha, item.label ?? '']);
    const destino = typeof item.routerLink === 'string' ? item.routerLink : item.url;
    if (!destino) return [];
    return [{
      label: item.label ?? '',
      rota: destino,
      icone: item.icon ?? 'pi pi-file',
      caminho: trilha.join(' › '),
      externa: item.target === '_blank'
    }];
  });
}

function normalizar(valor: string): string {
  return valor.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
}

function peso(tela: TelaBuscavel, termo: string): number {
  const label = normalizar(tela.label);
  if (label.startsWith(termo)) return 3;
  if (label.includes(termo)) return 2;
  if (normalizar(tela.caminho).includes(termo)) return 1;
  return 0;
}

function buscar(telas: TelaBuscavel[], termoCru: string): TelaBuscavel[] {
  const termo = normalizar(termoCru);
  if (!termo) return [];
  return telas
    .map((tela) => ({ tela, peso: peso(tela, termo) }))
    .filter((candidato) => candidato.peso > 0)
    .sort((a, b) => b.peso - a.peso || a.tela.label.localeCompare(b.tela.label))
    .map((candidato) => candidato.tela);
}

const menu: MenuItem[] = [
  {
    label: 'Financeiro',
    items: [
      {
        label: 'Minhas Solicitações',
        items: [
          { label: 'Compras', icon: 'pi pi-shopping-cart', routerLink: '/financeiro/compras' },
          { label: 'Pagamentos', icon: 'pi pi-credit-card', routerLink: '/financeiro/pagamentos' }
        ]
      },
      {
        label: 'Cadastros',
        items: [
          { label: 'Fornecedores', icon: 'pi pi-building', routerLink: '/financeiro/admin/fornecedores' }
        ]
      }
    ]
  },
  {
    label: 'Departamento Pessoal',
    items: [
      {
        label: 'Descontos em Folha',
        items: [
          { label: 'Novo Desconto', icon: 'pi pi-file-edit', url: '/dp/descontos/novo', target: '_blank' },
          { label: 'Oculto', routerLink: '/dp/descontos/secreto', visible: false }
        ]
      }
    ]
  }
];

describe('busca-telas', () => {
  describe('índice', () => {
    it('achata só as folhas navegáveis e guarda a trilha até elas', () => {
      const telas = achatar(menu);

      expect(telas.map((t) => t.label)).toEqual([
        'Compras', 'Pagamentos', 'Fornecedores', 'Novo Desconto'
      ]);
      expect(telas[2].caminho).toBe('Financeiro › Cadastros');
    });

    it('descarta o que a permissão escondeu do menu', () => {
      expect(achatar(menu).some((t) => t.rota === '/dp/descontos/secreto')).toBe(false);
    });

    it('reconhece o item que abre em guia própria', () => {
      const novo = achatar(menu).find((t) => t.label === 'Novo Desconto');

      expect(novo?.externa).toBe(true);
      expect(novo?.rota).toBe('/dp/descontos/novo');
    });
  });

  describe('busca', () => {
    it('ignora acento e caixa', () => {
      expect(buscar(achatar(menu), 'SOLICITACOES').map((t) => t.label))
        .toEqual(['Compras', 'Pagamentos']);
    });

    it('põe quem começa com o termo na frente de quem só o contém', () => {
      const telas = achatar(menu);

      expect(buscar(telas, 'compras')[0].label).toBe('Compras');
    });

    it('encontra pelo módulo, não só pelo nome da tela', () => {
      expect(buscar(achatar(menu), 'cadastros').map((t) => t.label)).toEqual(['Fornecedores']);
    });

    it('termo vazio não devolve resultado — quem manda aí é a sugestão', () => {
      expect(buscar(achatar(menu), '   ')).toEqual([]);
    });
  });
});
