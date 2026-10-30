import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ExecucaoService } from '../../../core/services/execucao.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { TiposServicoComponent } from './tipos-servico.component';

const registro = { id: 'ts1', codigo: 'CL', descricao: 'LIGAÇÃO NOVA', ativo: true };

describe('TiposServicoComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') => abrirListagem(TiposServicoComponent, 'lista', url, [{ provide: ExecucaoService, useValue: servico }]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listarTiposServico: vi.fn(() => paginaTeste([registro])),
      criarTipoServico: vi.fn(() => of(registro)),
      atualizarTipoServico: vi.fn(() => of(registro)),
      alternarStatusTipoServico: vi.fn(() => of(undefined))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, paginado e ordenado pela descrição no servidor', async () => {
    const { el } = await abrir();
    expect(servico['listarTiposServico']).toHaveBeenCalledWith({
      codigo: undefined, ativo: null, buscaGlobal: undefined, page: 0, size: 25, sort: 'descricao,asc'
    });
    expect(el.textContent).toContain('LIGAÇÃO NOVA');
    expect(chipsDoPainel(el)).toEqual([]);
  });

  it('restaura código e status da URL', async () => {
    const { el } = await abrir('/lista?codigo=CM&status=true');
    expect(servico['listarTiposServico']).toHaveBeenLastCalledWith(expect.objectContaining({ codigo: 'CM', ativo: true }));
    expect(chipsDoPainel(el)).toEqual(['Código: CM', 'Status: Ativo']);
  });

  it('"n" abre o cadastro; salvar exige código e descrição e envia o código em maiúsculas', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as {
      formularioAberto(): boolean;
      codigo: { set(v: string): void };
      descricao: { set(v: string): void };
      salvar(): void;
    };
    expect(tela.formularioAberto()).toBe(true);
    tela.salvar();
    expect(servico['criarTipoServico']).not.toHaveBeenCalled();
    tela.codigo.set(' cm ');
    tela.descricao.set('Enlace');
    tela.salvar();
    expect(servico['criarTipoServico']).toHaveBeenCalledWith({ codigo: 'CM', descricao: 'Enlace' });
    expect(servico['listarTiposServico']).toHaveBeenCalledTimes(2);
  });
});
