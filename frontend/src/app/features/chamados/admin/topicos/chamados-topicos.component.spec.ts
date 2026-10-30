import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ChamadosService } from '../../../../core/services/chamados.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../../shared/listagem/listagem-teste';
import { ChamadosTopicosComponent } from './chamados-topicos.component';

const topico = {
  id: 't1',
  codigo: 'SISTEMA_ERRO',
  nome: 'Erro em sistema',
  descricao: null,
  prioridadePadrao: 'NORMAL',
  equipePadrao: { id: 'e1', nome: 'Infraestrutura' },
  slaPrimeiraRespostaHoras: 8,
  slaResolucaoHoras: 24,
  pausarSlaAguardandoSolicitante: true,
  prazoReaberturaHoras: 72,
  ordemExibicao: 10,
  ativo: true
};

describe('ChamadosTopicosComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') => abrirListagem(ChamadosTopicosComponent, 'lista', url, [{ provide: ChamadosService, useValue: servico }]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listarTopicos: vi.fn(() => paginaTeste([topico])),
      listarEquipes: vi.fn(() => paginaTeste([{ id: 'e1', nome: 'Infraestrutura' }])),
      criarTopico: vi.fn(() => of(topico)),
      atualizarTopico: vi.fn(() => of(topico)),
      alternarTopico: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre nos tópicos ativos, na ordem de exibição', async () => {
    const { el } = await abrir();
    expect(servico['listarTopicos']).toHaveBeenCalledWith({
      buscaGlobal: undefined,
      codigo: undefined,
      equipePadraoId: undefined,
      ativo: true,
      page: 0,
      size: 25,
      sort: 'ordemExibicao,asc'
    });
    expect(el.textContent).toContain('Erro em sistema');
    expect(chipsDoPainel(el)).toEqual(['Status: Ativos']);
  });

  it('restaura a equipe da URL', async () => {
    await abrir('/lista?equipe=e1');
    expect(servico['listarTopicos']).toHaveBeenLastCalledWith(expect.objectContaining({ equipePadraoId: 'e1' }));
  });

  it('valida que a primeira resposta não passa do prazo de resolução', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as {
      formularioAberto(): boolean;
      codigo: { set(v: string): void };
      nome: { set(v: string): void };
      equipePadraoId: { set(v: string): void };
      slaPrimeiraResposta: { set(v: number): void };
      salvar(): void;
    };
    expect(tela.formularioAberto()).toBe(true);
    tela.codigo.set('acesso');
    tela.nome.set('Acesso');
    tela.equipePadraoId.set('e1');
    tela.slaPrimeiraResposta.set(48);
    tela.salvar();
    expect(servico['criarTopico']).not.toHaveBeenCalled();
    tela.slaPrimeiraResposta.set(4);
    tela.salvar();
    expect(servico['criarTopico']).toHaveBeenCalledWith(expect.objectContaining({ codigo: 'ACESSO', equipePadraoId: 'e1', slaPrimeiraRespostaHoras: 4, slaResolucaoHoras: 24 }));
  });
});
