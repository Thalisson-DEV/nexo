import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { NotificacoesService } from '../../../../core/services/notificacoes.service';
import { UsuariosService } from '../../../../core/services/usuarios.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../../shared/listagem/listagem-teste';
import { RegrasNotificacaoComponent } from './regras-notificacao.component';

const regra = {
  id: 'r1',
  nome: 'Avisar supervisor',
  descricao: null,
  sistema: true,
  chaveGatilho: 'chamado.aberto',
  gatilhoNome: 'Chamado aberto',
  categoria: 'CHAMADO',
  prioridade: 'ALTA',
  enviarEmail: false,
  permiteEmail: true,
  destinatarios: [{ tipo: 'PERFIL', idAlvo: 'p1', descricaoAlvo: null, valorAlvo: null, tipoRotulo: 'Perfil' }],
  modo: 'POR_OCORRENCIA',
  limiar: null,
  cooldownMinutos: 60,
  condicoes: [],
  ativo: true
};

describe('RegrasNotificacaoComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') =>
    abrirListagem(RegrasNotificacaoComponent, 'lista', url, [
      { provide: NotificacoesService, useValue: servico },
      {
        provide: UsuariosService,
        useValue: { listarPerfisAtivos: vi.fn(() => of([{ id: 'p1', nome: 'SUPERVISOR' }])), buscarUsuarioPorId: vi.fn(() => of(null)) }
      }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listarRegras: vi.fn(() => paginaTeste([regra])),
      listarGatilhos: vi.fn(() => of([{ chave: 'chamado.aberto', nome: 'Chamado aberto' }])),
      alternarRegra: vi.fn(() => of(null)),
      removerRegra: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre nas regras ativas e descreve destinatários e silêncio', async () => {
    const { el } = await abrir();
    expect(servico['listarRegras']).toHaveBeenCalledWith({
      buscaGlobal: undefined,
      chaveGatilho: undefined,
      categoria: undefined,
      sistema: undefined,
      ativo: true,
      page: 0,
      size: 25,
      sort: 'nome,asc'
    });
    expect(el.textContent).toContain('SUPERVISOR');
    expect(el.textContent).toContain('1 hora de silêncio');
    expect(el.textContent).toContain('De fábrica');
    expect(el.querySelector('button[aria-label="Remover Avisar supervisor"]')).toBeNull();
    expect(chipsDoPainel(el)).toEqual(['Status: Ativas']);
  });

  it('restaura categoria e origem da URL', async () => {
    const { el } = await abrir('/lista?categoria=CHAMADO&sistema=false');
    expect(servico['listarRegras']).toHaveBeenLastCalledWith(expect.objectContaining({ categoria: 'CHAMADO', sistema: false }));
    expect(chipsDoPainel(el)).toEqual(['Categoria: Chamados', 'Origem: Criadas aqui', 'Status: Ativas']);
  });

  it('"n" leva ao formulário de nova regra', async () => {
    await abrir();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    tecla('n');
    expect(navegar).toHaveBeenCalledWith(['/notificacoes/admin/regras/nova']);
  });
});
