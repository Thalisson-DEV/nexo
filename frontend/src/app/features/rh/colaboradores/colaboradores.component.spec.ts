import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ImportacaoService } from '../../../core/services/importacao.service';
import { RhService } from '../../../core/services/rh.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { ColaboradoresComponent, formatarCpf } from './colaboradores.component';

const colaborador = {
  id: 'c1',
  primeiroNome: 'MARIA',
  ultimoNome: 'SOUZA',
  nomeCompleto: 'MARIA DE SOUZA',
  cpf: '12345678901',
  dataContratacao: '2024-03-01',
  baseOperacional: { id: 'b1', nome: 'JUAZEIRO' },
  processo: { id: 'p1', nome: 'MANUTENÇÃO' },
  funcao: { id: 'f1', nome: 'ELETRICISTA' },
  ativo: true
};

describe('ColaboradoresComponent', () => {
  let rh: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') =>
    abrirListagem(ColaboradoresComponent, 'lista', url, [
      { provide: RhService, useValue: rh },
      { provide: ImportacaoService, useValue: { baixarModeloCsv: vi.fn() } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    rh = {
      listarBasesOperacionaisAtivas: vi.fn(() => paginaTeste([{ id: 'b1', nome: 'JUAZEIRO' }])),
      listarProcessosAtivos: vi.fn(() => paginaTeste([{ id: 'p1', nome: 'MANUTENÇÃO' }])),
      listarFuncoesAtivas: vi.fn(() => paginaTeste([{ id: 'f1', nome: 'ELETRICISTA' }])),
      listarPerfisAcessoAtivos: vi.fn(() => of([{ id: 'perfil1', nome: 'CAMPO', ativo: true }])),
      listarColaboradores: vi.fn(() => paginaTeste([colaborador])),
      criarColaborador: vi.fn(() => of({ ...colaborador, id: 'novo' })),
      atualizarColaborador: vi.fn(() => of(colaborador)),
      ativarColaborador: vi.fn(() => of(null)),
      desativarColaborador: vi.fn(() => of(null)),
      gerarAcesso: vi.fn(() => of(colaborador))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, ordenado pelo nome no servidor, com o CPF formatado', async () => {
    const { el } = await abrir();
    expect(rh['listarColaboradores']).toHaveBeenCalledWith(
      expect.objectContaining({ nomeCompleto: undefined, cpf: undefined, idBaseOperacional: null, ativo: null, page: 0, size: 25, sort: 'nomeCompleto,asc' })
    );
    expect(el.textContent).toContain('MARIA DE SOUZA');
    expect(el.textContent).toContain('123.456.789-01');
    expect(chipsDoPainel(el)).toEqual([]);
  });

  it('restaura base, função e período de contratação da URL', async () => {
    const { el } = await abrir('/lista?base=b1&funcao=f1&contratacao=2024-01-01~2024-06-30');
    expect(rh['listarColaboradores']).toHaveBeenLastCalledWith(
      expect.objectContaining({ idBaseOperacional: 'b1', idFuncao: 'f1', dataContratacao: '2024-01-01', dataContratacaoFim: '2024-06-30' })
    );
    expect(chipsDoPainel(el)).toEqual(['Base: JUAZEIRO', 'Função: ELETRICISTA', 'Contratação: 01/01/2024 a 30/06/2024']);
  });

  it('o CPF filtra só com dígitos e não vai para a URL', async () => {
    const { componente } = await abrir();
    const router = TestBed.inject(Router);
    const tela = componente as unknown as { lista: { filters: { cpf: { set(v: string): void } }; apply(): void } };
    tela.lista.filters.cpf.set('123.456.789-01');
    tela.lista.apply();
    await new Promise((r) => setTimeout(r));
    expect(rh['listarColaboradores']).toHaveBeenLastCalledWith(expect.objectContaining({ cpf: '12345678901' }));
    expect(router.url).not.toContain('cpf');
    expect(router.url).not.toContain('123');
  });

  it('"n" cadastra o colaborador e depois gera o acesso', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as Record<string, { set(v: unknown): void }> & { formularioAberto(): boolean; salvar(): void };
    expect(tela.formularioAberto()).toBe(true);
    tela['primeiroNome'].set('João');
    tela['ultimoNome'].set('Lima');
    tela['nomeCompleto'].set('João Lima');
    tela['cpf'].set(formatarCpf('98765432100'));
    tela['dataContratacao'].set(new Date(2025, 0, 15));
    tela['idBase'].set('b1');
    tela['idProcesso'].set('p1');
    tela['idFuncao'].set('f1');
    tela['gerarAcesso'].set(true);
    tela['login'].set('usr123');
    tela.salvar();
    expect(rh['criarColaborador']).not.toHaveBeenCalled();

    tela['login'].set('usr123456');
    tela['senha'].set('segredo');
    tela['idPerfil'].set('perfil1');
    tela.salvar();
    expect(rh['criarColaborador']).toHaveBeenCalledWith(expect.objectContaining({ cpf: '98765432100', dataContratacao: '2025-01-15', idBaseOperacional: 'b1' }));
    expect(rh['gerarAcesso']).toHaveBeenCalledWith({ colaboradorId: 'novo', usuario: { login: 'USR123456', senha: 'segredo', idPerfil: 'perfil1', tipo: 'CAMPO' } });
  });
});
