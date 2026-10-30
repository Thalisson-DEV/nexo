import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { PermissoesService } from '../../../core/services/permissoes.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { PermissoesComponent } from './permissoes.component';

describe('PermissoesComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') => abrirListagem(PermissoesComponent, 'lista', url, [{ provide: PermissoesService, useValue: servico }]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listar: vi.fn(() => paginaTeste([{ id: 'p1', nome: 'CONTRATO:ACAO:READ', ativo: true }])),
      criar: vi.fn(() => of({ id: 'p2', nome: 'RH:BASE-OPERACIONAL:WRITE', ativo: true })),
      atualizar: vi.fn(() => of(null)),
      alterarStatus: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados e separa módulo, recurso e ação', async () => {
    const { el } = await abrir();
    expect(servico['listar']).toHaveBeenCalledWith({ ativo: null, buscaGlobal: undefined, page: 0, size: 25, sort: 'nome,asc' });
    const celulas = Array.from(el.querySelectorAll('tbody tr td')).map((td) => td.textContent!.trim());
    expect(celulas.slice(0, 4)).toEqual(['CONTRATO', 'ACAO', 'READ', 'CONTRATO:ACAO:READ']);
    expect(chipsDoPainel(el)).toEqual([]);
  });

  it('restaura o status da URL', async () => {
    const { el } = await abrir('/lista?status=false');
    expect(servico['listar']).toHaveBeenLastCalledWith(expect.objectContaining({ ativo: false }));
    expect(chipsDoPainel(el)).toEqual(['Status: Inativo']);
  });

  it('"n" abre o cadastro e monta o identificador', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as {
      formularioAberto(): boolean;
      modulo: { set(v: string): void };
      acao: { set(v: string): void };
      recurso: { set(v: string): void };
      salvar(): void;
    };
    expect(tela.formularioAberto()).toBe(true);
    tela.salvar();
    expect(servico['criar']).not.toHaveBeenCalled();
    tela.modulo.set('RH');
    tela.recurso.set('BASE-OPERACIONAL');
    tela.acao.set('WRITE');
    tela.salvar();
    expect(servico['criar']).toHaveBeenCalledWith({ nome: 'RH:BASE-OPERACIONAL:WRITE' });
  });
});
