import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { MotivosRejeicaoComponent } from './motivos-rejeicao.component';

const registro = { id: 'r1', nome: 'VALOR TESTE', descricao: 'Descrição longa', ativo: true };

describe('MotivosRejeicaoComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') =>
    abrirListagem(MotivosRejeicaoComponent, 'lista', url, [
      { provide: AlmoxarifadoService, useValue: servico }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listarMotivosRejeicao: vi.fn(() => paginaTeste([registro])),
      criarMotivoRejeicao: vi.fn(() => of({ ...registro, id: 'novo' })),
      atualizarMotivoRejeicao: vi.fn(() => of(registro)),
      ativarMotivoRejeicao: vi.fn(() => of(null)),
      desativarMotivoRejeicao: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, sem etapa de pesquisa', async () => {
    const { el } = await abrir();
    expect(servico['listarMotivosRejeicao']).toHaveBeenCalledWith({ ativo: null, buscaGlobal: undefined, page: 0, size: 25, sort: 'nome,asc' });
    expect(el.textContent).toContain('VALOR TESTE');
    expect(chipsDoPainel(el)).toEqual([]);
  });

  it('restaura o filtro da URL e mostra o chip', async () => {
    const { el, componente } = await abrir('/lista?status=true&busca=val');
    expect(servico['listarMotivosRejeicao']).toHaveBeenLastCalledWith(expect.objectContaining({ ativo: true, buscaGlobal: 'val' }));
    expect(chipsDoPainel(el)).toEqual(['Status: Ativo']);
    (componente as unknown as { lista: { reset(): void } }).lista.reset();
    expect(servico['listarMotivosRejeicao']).toHaveBeenLastCalledWith(expect.objectContaining({ ativo: null }));
  });

  it('"n" abre o cadastro em drawer e salvar valida antes de enviar', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as { formularioAberto(): boolean; principal: { set(v: string): void }; descricao?: { set(v: string): void }; salvar(): void };
    expect(tela.formularioAberto()).toBe(true);
    tela.salvar();
    expect(servico['criarMotivoRejeicao']).not.toHaveBeenCalled();
    tela.principal.set('Valor novo');
    tela.descricao?.set('Descrição de teste');
    tela.salvar();
    expect(servico['criarMotivoRejeicao']).toHaveBeenCalledWith(expect.objectContaining({ nome: 'Valor novo' }));
  });
});
