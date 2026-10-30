import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthService } from '../../../core/services/auth.service';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { RhService } from '../../../core/services/rh.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { VeiculosComponent } from './veiculos.component';

const veiculo = { id: 'v1', placa: 'ABC1234', modelo: 'HILUX', fabricante: 'TOYOTA', baseOperacional: { id: 'b1', nome: 'JUAZEIRO' }, ativo: true };

describe('VeiculosComponent (RH)', () => {
  let rh: Record<string, ReturnType<typeof vi.fn>>;
  let permissoes: string[];
  const abrir = (url = '/lista') =>
    abrirListagem(VeiculosComponent, 'lista', url, [
      { provide: RhService, useValue: rh },
      { provide: ImportacaoService, useValue: { baixarModeloCsv: vi.fn() } },
      { provide: AuthService, useValue: { currentUser: () => ({ permissoes }) } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    permissoes = ['rh:veiculo:read'];
    rh = {
      listarBasesOperacionaisAtivas: vi.fn(() => paginaTeste([{ id: 'b1', nome: 'JUAZEIRO' }])),
      listarVeiculos: vi.fn(() => paginaTeste([veiculo])),
      criarVeiculo: vi.fn(() => of(veiculo)),
      atualizarVeiculo: vi.fn(() => of(veiculo)),
      ativarVeiculo: vi.fn(() => of(null)),
      desativarVeiculo: vi.fn(() => of(null)),
      importarVeiculos: vi.fn(() => of({ registros: [{ linha: 2, placa: 'X', modelo: '', fabricante: '', nomeBaseOperacional: '', status: 'COM_ERRO', mensagemErro: 'Modelo obrigatório' }], totalFalhas: 1 }))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, ordenado pela placa no servidor', async () => {
    const { el } = await abrir();
    expect(rh['listarVeiculos']).toHaveBeenCalledWith({ modelo: undefined, idBaseOperacional: null, ativo: null, buscaGlobal: undefined, page: 0, size: 25, sort: 'placa,asc' });
    expect(el.textContent).toContain('ABC1234');
    expect(chipsDoPainel(el)).toEqual([]);
  });

  it('restaura modelo e base da URL', async () => {
    const { el } = await abrir('/lista?modelo=hilux&base=b1');
    expect(rh['listarVeiculos']).toHaveBeenLastCalledWith(expect.objectContaining({ modelo: 'hilux', idBaseOperacional: 'b1' }));
    expect(chipsDoPainel(el)).toEqual(['Modelo: hilux', 'Base: JUAZEIRO']);
  });

  it('com acesso à frota, oferece o atalho para a ficha do veículo', async () => {
    permissoes = ['rh:veiculo:read', 'frota:veiculo:read'];
    const { el } = await abrir();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    (el.querySelector('button[aria-label="Abrir a ficha da frota de ABC1234"]') as HTMLButtonElement).click();
    expect(navegar).toHaveBeenCalledWith(['/frota/veiculos', 'v1']);
  });

  it('"n" abre o cadastro e grava a placa em maiúsculas; a importação usa totalFalhas', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as {
      formularioAberto(): boolean;
      placa: { set(v: string): void };
      modelo: { set(v: string): void };
      idBase: { set(v: string): void };
      salvar(): void;
      importacao: { enviarArquivo(f: File): void; linhas(): unknown[] };
    };
    expect(tela.formularioAberto()).toBe(true);
    tela.placa.set('abc1d23');
    tela.modelo.set('Strada');
    tela.idBase.set('b1');
    tela.salvar();
    expect(rh['criarVeiculo']).toHaveBeenCalledWith(expect.objectContaining({ placa: 'ABC1D23', baseOperacionalId: 'b1' }));
    tela.importacao.enviarArquivo(new File(['x'], 'v.csv'));
    expect(tela.importacao.linhas()).toHaveLength(1);
  });
});
