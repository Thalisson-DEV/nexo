import { describe, expect, it } from 'vitest';
import { agruparPermissoes, decompor, descreverAcao } from './permissoes-catalogo';

const p = (id: string, nome: string) => ({ id, nome, ativo: true });

describe('permissoes-catalogo', () => {
  it('decompõe recurso e ação, inclusive ação composta e permissão de módulo', () => {
    expect(decompor('estoque:solicitacao:audit:read')).toEqual({ modulo: 'estoque', recurso: 'solicitacao', acao: 'audit:read' });
    expect(decompor('desconto:validar')).toEqual({ modulo: 'desconto', recurso: '', acao: 'validar' });
  });

  it('agrupa por módulo com nomes em português e ações em ordem (Ver antes de Editar)', () => {
    const modulos = agruparPermissoes([
      p('w', 'estoque:solicitacao:write'),
      p('a', 'estoque:solicitacao:approve'),
      p('r', 'estoque:solicitacao:read'),
      p('f', 'frota:veiculo:read'),
      p('x', 'modulo-novo:coisa-nova:fazer')
    ]);
    expect(modulos.map((m) => m.rotulo)).toEqual(['Almoxarifado', 'Frota', 'Modulo novo']);
    const solicitacoes = modulos[0].recursos[0];
    expect(solicitacoes.rotulo).toBe('Solicitações de material');
    expect(solicitacoes.acoes.map((a) => a.rotulo)).toEqual(['Ver', 'Operar', 'Aprovar e reprovar']);
    expect(modulos[2].recursos[0].rotulo).toBe('Coisa nova');
    expect(modulos[0].ids).toEqual(['r', 'w', 'a']);
  });

  it('explica as ações que não são óbvias', () => {
    expect(descreverAcao(p('a', 'estoque:solicitacao:approve')).ajuda).toContain('Aprova');
    expect(descreverAcao(p('r', 'frota:veiculo:read'))).toEqual(expect.objectContaining({ rotulo: 'Ver', tipo: 'ver', ajuda: undefined }));
  });
});
