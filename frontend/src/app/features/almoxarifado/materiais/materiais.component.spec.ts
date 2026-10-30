import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { MateriaisComponent } from './materiais.component';

const material = { id: 'm1', codigo: 'MAT001', descricao: 'CABO DE COBRE', unidadeMedida: 'MT', avaliacaoTecnica: { id: 'a1', nome: 'NOVO' }, detalhaSerie: true, ativo: true };

describe('MateriaisComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') =>
    abrirListagem(MateriaisComponent, 'lista', url, [
      { provide: AlmoxarifadoService, useValue: servico },
      { provide: ImportacaoService, useValue: { baixarModeloCsv: vi.fn() } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listarAvaliacoesAtivas: vi.fn(() => paginaTeste([{ id: 'a1', nome: 'NOVO' }])),
      listarCentrosInvestimentoAtivos: vi.fn(() => paginaTeste([{ id: 'c1', codigo: 'CI-01' }])),
      listarMateriais: vi.fn(() => paginaTeste([material])),
      criarMaterial: vi.fn(() => of({ ...material, id: 'novo' })),
      atualizarMaterial: vi.fn(() => of(material)),
      ativarMaterial: vi.fn(() => of(undefined)),
      desativarMaterial: vi.fn(() => of(undefined))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, ordenado pelo código, marcando quem controla nº de série', async () => {
    const { el } = await abrir();
    expect(servico['listarMateriais']).toHaveBeenCalledWith({ idAvaliacaoTecnica: undefined, detalhaSerie: undefined, ativo: undefined, buscaGlobal: undefined, page: 0, size: 25, sort: 'codigo,asc' });
    expect(el.textContent).toContain('CABO DE COBRE');
    expect(el.textContent).toContain('Controla');
    expect(chipsDoPainel(el)).toEqual([]);
  });

  it('restaura avaliação e nº de série da URL', async () => {
    const { el } = await abrir('/lista?avaliacao=a1&serie=true');
    expect(servico['listarMateriais']).toHaveBeenLastCalledWith(expect.objectContaining({ idAvaliacaoTecnica: 'a1', detalhaSerie: true }));
    expect(chipsDoPainel(el)).toEqual(['Avaliação: NOVO', 'Nº de série: Sim']);
  });

  it('"n" cadastra e editar envia só o que é do material', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as Record<string, { set(v: unknown): void }> & { salvar(): void; editar(m: unknown): void };
    tela['codigo'].set('MAT002');
    tela['descricao'].set('Conector');
    tela['idCentro'].set('c1');
    tela.salvar();
    expect(servico['criarMaterial']).toHaveBeenCalledWith(expect.objectContaining({ codigo: 'MAT002', descricao: 'Conector', idCentroInvestimento: 'c1', detalhaSerie: false }));
    tela.editar(material);
    tela['ativo'].set(false);
    tela.salvar();
    expect(servico['atualizarMaterial']).toHaveBeenCalledWith('m1', expect.objectContaining({ codigo: 'MAT001', idAvaliacaoTecnica: 'a1', detalhaSerie: true }));
    expect(servico['desativarMaterial']).toHaveBeenCalledWith('m1');
  });
});
