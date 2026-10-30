import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { RhService } from '../../../core/services/rh.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { CesConfirmationService } from '@ces/ui';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { UsuariosComponent } from './usuarios.component';

const usuario = {
  id: 'u1',
  login: 'USR123456',
  email: 'ana@exemplo.com.br',
  tipo: 'CAMPO',
  perfil: { id: 'p1', nome: 'ELETRICISTA' },
  colaborador: { id: 'c1', nomeCompleto: 'ANA LIMA', funcao: 'ELETRICISTA', baseOperacional: 'JUAZEIRO', ativo: false },
  tema: 'light',
  corPrimaria: 'primary',
  animacoesAtivadas: true,
  ativo: true
};

type Sinal<T> = { (): T; set(v: T): void };

describe('UsuariosComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  let rh: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = async (url = '/lista') => {
    const r = await abrirListagem(UsuariosComponent, 'lista', url, [
      { provide: UsuariosService, useValue: servico },
      { provide: RhService, useValue: rh },
      { provide: LookupStore, useValue: { searchCollaborators: vi.fn(() => of({ items: [], hasMore: false })) } },
      { provide: ImportacaoService, useValue: { baixarModeloCsv: vi.fn() } }
    ]);
    vi.spyOn(TestBed.inject(CesConfirmationService), 'confirm').mockResolvedValue(true);
    return { ...r, tela: r.componente as unknown as Record<string, Sinal<unknown>> & { salvar(): void; editar(u: unknown): void } };
  };

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listarUsuarios: vi.fn(() => paginaTeste([usuario])),
      listarPerfisAtivos: vi.fn(() => of([{ id: 'p1', nome: 'ELETRICISTA', ativo: true }])),
      criarUsuario: vi.fn(() => of(usuario)),
      atualizarUsuario: vi.fn(() => of(usuario)),
      ativarUsuario: vi.fn(() => of(null)),
      desativarUsuario: vi.fn(() => of(null)),
      validarImportacao: vi.fn(() =>
        of({ registros: [{ linha: 2, login: 'USR000001', senha: 'x', status: 'AGUARDANDO_PROCESSAMENTO' }], totalProcessados: 1, totalSucesso: 1, totalFalha: 0 })
      ),
      confirmarImportacao: vi.fn(() =>
        of({ registros: [{ linha: 2, login: 'USR000001', senha: 'x', status: 'PROCESSADO' }], totalProcessados: 1, totalSucesso: 1, totalFalha: 0 })
      )
    };
    rh = {
      listarBasesOperacionaisAtivas: vi.fn(() => paginaTeste([{ id: 'b1', nome: 'JUAZEIRO' }])),
      listarProcessosAtivos: vi.fn(() => paginaTeste([{ id: 'pr1', nome: 'MANUTENÇÃO' }])),
      listarFuncoesAtivas: vi.fn(() => paginaTeste([{ id: 'f1', nome: 'ELETRICISTA' }])),
      criarColaborador: vi.fn(() => of({ id: 'novo-colab' })),
      gerarAcesso: vi.fn(() => of({}))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, mostra o vínculo e avisa o colaborador desligado', async () => {
    const { el } = await abrir();
    expect(servico['listarUsuarios']).toHaveBeenCalledWith(expect.objectContaining({ ativo: null, page: 0, size: 25, sort: 'login,asc' }));
    expect(el.textContent).toContain('USR123456');
    expect(el.textContent).toContain('ANA LIMA');
    expect(el.textContent).toContain('Colaborador desligado');
  });

  it('restaura vínculo e perfil da URL', async () => {
    const { el } = await abrir('/lista?vinculo=false&perfil=p1');
    expect(servico['listarUsuarios']).toHaveBeenLastCalledWith(expect.objectContaining({ vinculadoAColaborador: false, idPerfil: 'p1' }));
    expect(chipsDoPainel(el)).toEqual(['Vínculo: Sem colaborador', 'Perfil: ELETRICISTA']);
  });

  it('cadastra colaborador novo e gera o acesso vinculado', async () => {
    const { tela } = await abrir();
    tecla('n');
    tela['login'].set('USR999999');
    tela['tipo'].set('CAMPO');
    tela['idPerfil'].set('p1');
    tela['senha'].set('segredo');
    tela['vinculo'].set('NOVO');
    tela.salvar();
    expect(rh['criarColaborador']).not.toHaveBeenCalled();
    tela['primeiroNome'].set('João');
    tela['ultimoNome'].set('Lima');
    tela['cpf'].set('987.654.321-00');
    tela['dataContratacao'].set(new Date(2025, 2, 3));
    tela['idBase'].set('b1');
    tela['idProcesso'].set('pr1');
    tela['idFuncao'].set('f1');
    tela.salvar();
    expect(rh['criarColaborador']).toHaveBeenCalledWith(expect.objectContaining({ nomeCompleto: 'João Lima', cpf: '98765432100', dataContratacao: '2025-03-03' }));
    expect(rh['gerarAcesso']).toHaveBeenCalledWith({ colaboradorId: 'novo-colab', usuario: expect.objectContaining({ login: 'USR999999', senha: 'segredo', idPerfil: 'p1' }) });
    expect(servico['criarUsuario']).not.toHaveBeenCalled();
  });

  it('editar sem senha mantém a atual e desativa quando pedido', async () => {
    const { tela } = await abrir();
    tela.editar(usuario);
    tela['ativo'].set(false);
    tela.salvar();
    const [, request] = servico['atualizarUsuario'].mock.calls[0];
    expect(request).not.toHaveProperty('senha');
    expect(request).toMatchObject({ login: 'USR123456', idPerfil: 'p1', tipo: 'CAMPO' });
    expect(servico['desativarUsuario']).toHaveBeenCalledWith('u1');
  });

  it('a importação descarta as linhas (com senha) depois de processar sem erro', async () => {
    const { tela } = await abrir();
    const importacao = tela['importacao'] as unknown as { enviarArquivo(f: File): void; processar(): Promise<void>; linhas(): unknown[] };
    importacao.enviarArquivo(new File(['x'], 'u.csv'));
    expect(importacao.linhas()).toHaveLength(1);
    await importacao.processar();
    expect(servico['confirmarImportacao']).toHaveBeenCalled();
    expect(importacao.linhas()).toEqual([]);
  });
});
