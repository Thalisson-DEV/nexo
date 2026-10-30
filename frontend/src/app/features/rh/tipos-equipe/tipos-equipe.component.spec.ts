import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ImportacaoService } from '../../../core/services/importacao.service';
import { RhService } from '../../../core/services/rh.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { TiposEquipeComponent } from './tipos-equipe.component';

const registro = { id: 'f1', nome: 'ELETRICISTA', descricao: 'Linha viva', ativo: true };

describe('TiposEquipeComponent', () => {
  let rh: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') =>
    abrirListagem(TiposEquipeComponent, 'lista', url, [
      { provide: RhService, useValue: rh },
      { provide: ImportacaoService, useValue: { baixarModeloCsv: vi.fn() } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    rh = {
      listarTiposEquipe: vi.fn(() => paginaTeste([registro])),
      criarTipoEquipe: vi.fn(() => of({ ...registro, id: 'novo' })),
      atualizarTipoEquipe: vi.fn(() => of(registro)),
      ativarTipoEquipe: vi.fn(() => of(null)),
      desativarTipoEquipe: vi.fn(() => of(null)),
      importarTiposEquipe: vi.fn(() => of({ registros: [{ linha: 2, nome: '', status: 'COM_ERRO', mensagemErro: 'Nome obrigatório' }], totalFalha: 1 }))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, paginado e ordenado no servidor', async () => {
    const { el } = await abrir();
    expect(rh['listarTiposEquipe']).toHaveBeenCalledWith({ ativo: null, buscaGlobal: undefined, page: 0, size: 25, sort: 'nome,asc' });
    expect(el.textContent).toContain('ELETRICISTA');
    expect(chipsDoPainel(el)).toEqual([]);
  });

  it('restaura o filtro da URL e "Limpar" volta ao padrão', async () => {
    const { el, componente } = await abrir('/lista?status=false&busca=ele');
    expect(rh['listarTiposEquipe']).toHaveBeenLastCalledWith(expect.objectContaining({ ativo: false, buscaGlobal: 'ele' }));
    expect(chipsDoPainel(el)).toEqual(['Status: Inativo']);
    (componente as unknown as { lista: { reset(): void } }).lista.reset();
    expect(rh['listarTiposEquipe']).toHaveBeenLastCalledWith(expect.objectContaining({ ativo: null }));
  });

  it('"n" abre o cadastro; o tipo é gravado em maiúsculas com o status', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as {
      formularioAberto(): boolean;
      nome: { set(v: string): void };
      ativo: { set(v: boolean): void };
      salvar(): void;
    };
    expect(tela.formularioAberto()).toBe(true);
    tela.salvar();
    expect(rh['criarTipoEquipe']).not.toHaveBeenCalled();
    tela.nome.set('Encarregado');
    tela.ativo.set(false);
    tela.salvar();
    expect(rh['criarTipoEquipe']).toHaveBeenCalledWith({ nome: 'ENCARREGADO', ativo: false });
    expect(rh['desativarTipoEquipe']).not.toHaveBeenCalled();
  });

  it('a importação mostra a pré-visualização com erro por linha', async () => {
    const { componente, harness } = await abrir();
    const tela = componente as unknown as { importacao: { aberta: { set(v: boolean): void }; enviarArquivo(f: File): void } };
    tela.importacao.aberta.set(true);
    tela.importacao.enviarArquivo(new File(['x'], 'f.csv'));
    harness.detectChanges();
    await harness.fixture.whenStable();
    expect(document.body.textContent).toContain('Nome obrigatório');
  });
});
