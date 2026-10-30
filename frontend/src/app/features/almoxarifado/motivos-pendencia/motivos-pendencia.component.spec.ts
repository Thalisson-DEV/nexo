import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { MotivosPendenciaComponent } from './motivos-pendencia.component';

const registro = { id: 'r1', nome: 'VALOR TESTE', descricao: 'Descrição longa', ativo: true };

describe('MotivosPendenciaComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') =>
    abrirListagem(MotivosPendenciaComponent, 'lista', url, [
      { provide: AlmoxarifadoService, useValue: servico }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listarMotivosPendencia: vi.fn(() => of([registro])),
      criarMotivoPendencia: vi.fn(() => of({ ...registro, id: 'novo' })),
      atualizarMotivoPendencia: vi.fn(() => of(registro)),
      ativarMotivoPendencia: vi.fn(() => of(null)),
      desativarMotivoPendencia: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, sem etapa de pesquisa', async () => {
    const { el } = await abrir();
    expect(servico['listarMotivosPendencia']).toHaveBeenCalledOnce();
    expect(el.textContent).toContain('VALOR TESTE');
    expect(chipsDoPainel(el)).toEqual([]);
  });

  it('restaura o filtro da URL e mostra o chip', async () => {
    const { el } = await abrir('/lista?status=false');
    expect(el.textContent).not.toContain('VALOR TESTE');
    expect(chipsDoPainel(el)).toEqual(['Status: Inativo']);
  });

  it('"n" abre o cadastro em drawer e salvar valida antes de enviar', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as { formularioAberto(): boolean; principal: { set(v: string): void }; descricao?: { set(v: string): void }; salvar(): void };
    expect(tela.formularioAberto()).toBe(true);
    tela.salvar();
    expect(servico['criarMotivoPendencia']).not.toHaveBeenCalled();
    tela.principal.set('Valor novo');
    tela.descricao?.set('Descrição de teste');
    tela.salvar();
    expect(servico['criarMotivoPendencia']).toHaveBeenCalledWith(expect.objectContaining({ nome: 'Valor novo' }));
  });
});
