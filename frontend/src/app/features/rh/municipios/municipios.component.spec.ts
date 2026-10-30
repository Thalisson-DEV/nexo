import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ImportacaoService } from '../../../core/services/importacao.service';
import { RhService } from '../../../core/services/rh.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { MunicipiosComponent } from './municipios.component';

const salvador = { id: 'm1', nome: 'SALVADOR', ativo: true, baseOperacional: { id: 'b1', nome: 'JUAZEIRO' } };

describe('MunicipiosComponent', () => {
  let rh: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/municipios') =>
    abrirListagem(MunicipiosComponent, 'municipios', url, [
      { provide: RhService, useValue: rh },
      { provide: ImportacaoService, useValue: { baixarModeloCsv: vi.fn() } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    rh = {
      listarBasesOperacionaisAtivas: vi.fn(() => paginaTeste([{ id: 'b1', nome: 'JUAZEIRO' }])),
      listarMunicipios: vi.fn(() => paginaTeste([salvador])),
      criarMunicipio: vi.fn(() => of(salvador)),
      atualizarMunicipio: vi.fn(() => of(salvador)),
      importarMunicipios: vi.fn(() =>
        of({ registros: [{ linha: 2, nome: 'FEIRA', nomeBaseOperacional: 'X', status: 'COM_ERRO', mensagemErro: 'Base não encontrada' }], totalFalha: 1 })
      )
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, sem "Pesquisar", paginado e ordenado no servidor', async () => {
    const { el } = await abrir();
    expect(rh['listarMunicipios']).toHaveBeenCalledOnce();
    expect(rh['listarMunicipios']).toHaveBeenCalledWith({ idBaseOperacional: null, ativo: null, buscaGlobal: undefined, page: 0, size: 25, sort: 'nome,asc' });
    expect(el.textContent).toContain('SALVADOR');
    expect(el.textContent).not.toContain('PESQUISAR');
    expect(chipsDoPainel(el)).toEqual([]);
  });

  it('restaura filtros da URL e a busca única vai como buscaGlobal', async () => {
    const { el, componente } = await abrir('/municipios?base=b1&status=true&busca=salv');
    expect(rh['listarMunicipios']).toHaveBeenLastCalledWith(
      expect.objectContaining({ idBaseOperacional: 'b1', ativo: true, buscaGlobal: 'salv' })
    );
    expect(chipsDoPainel(el)).toEqual(['Base: JUAZEIRO', 'Status: Ativo']);
    (componente as unknown as { lista: { reset(): void } }).lista.reset();
    expect(rh['listarMunicipios']).toHaveBeenLastCalledWith(expect.objectContaining({ idBaseOperacional: null, ativo: null }));
  });

  it('"n" abre o cadastro em drawer e salvar valida antes de enviar', async () => {
    const { componente, harness } = await abrir();
    tecla('n');
    harness.detectChanges();
    const tela = componente as unknown as { formularioAberto(): boolean; nome: { set(v: string): void }; idBase: { set(v: string): void }; salvar(): void };
    expect(tela.formularioAberto()).toBe(true);
    tela.salvar();
    expect(rh['criarMunicipio']).not.toHaveBeenCalled();
    tela.nome.set('Feira de Santana');
    tela.idBase.set('b1');
    tela.salvar();
    expect(rh['criarMunicipio']).toHaveBeenCalledWith({ nome: 'Feira de Santana', idBaseOperacional: 'b1' });
    expect(rh['listarMunicipios']).toHaveBeenCalledTimes(2);
  });

  it('a importação mostra a pré-visualização no drawer, sem esconder a lista', async () => {
    const { componente, harness, el } = await abrir();
    const tela = componente as unknown as { importacaoAberta: { set(v: boolean): void }; enviarArquivo(f: File): void };
    tela.importacaoAberta.set(true);
    tela.enviarArquivo(new File(['x'], 'municipios.csv'));
    harness.detectChanges();
    await harness.fixture.whenStable();
    expect(document.body.textContent).toContain('Base não encontrada');
    expect(document.body.textContent).toContain('1 linha · 1 com erro');
    expect(el.textContent).toContain('SALVADOR');
  });
});
