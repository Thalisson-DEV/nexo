import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ContratoService } from '../../../core/services/contrato.service';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { AcoesComponent } from './acoes.component';

const acao = { id: 'a1', codigo: 'SERV01', nome: 'LIGAÇÃO NOVA', ativo: true };

describe('AcoesComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') =>
    abrirListagem(AcoesComponent, 'lista', url, [
      { provide: ContratoService, useValue: servico },
      { provide: ImportacaoService, useValue: { baixarModeloCsv: vi.fn() } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listarAcoes: vi.fn(() => paginaTeste([acao])),
      criarAcao: vi.fn(() => of({ ...acao, id: 'nova' })),
      atualizarAcao: vi.fn(() => of(acao)),
      ativarAcao: vi.fn(() => of(null)),
      desativarAcao: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, ordenada pelo código', async () => {
    const { el } = await abrir();
    expect(servico['listarAcoes']).toHaveBeenCalledWith({ ativo: null, buscaGlobal: undefined, page: 0, size: 25, sort: 'codigo,asc' });
    expect(el.textContent).toContain('LIGAÇÃO NOVA');
    expect(chipsDoPainel(el)).toEqual([]);
  });

  it('restaura o status da URL', async () => {
    const { el } = await abrir('/lista?status=false');
    expect(servico['listarAcoes']).toHaveBeenLastCalledWith(expect.objectContaining({ ativo: false }));
    expect(chipsDoPainel(el)).toEqual(['Status: Inativo']);
  });

  it('"n" cria a ação já inativa quando pedido', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as Record<string, { set(v: unknown): void }> & { salvar(): void };
    tela['codigo'].set('serv02');
    tela['nome'].set('Religação');
    tela['ativo'].set(false);
    tela.salvar();
    expect(servico['criarAcao']).toHaveBeenCalledWith({ codigo: 'serv02', nome: 'Religação' });
    expect(servico['desativarAcao']).toHaveBeenCalledWith('nova');
  });
});
