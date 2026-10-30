import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { NotificacoesService } from '../../../../core/services/notificacoes.service';
import { abrirListagem, chipsDoPainel, tecla } from '../../../../shared/listagem/listagem-teste';
import { PoliticasRoteamentoComponent } from './politicas-roteamento.component';

const politica = (id: string, nome: string, ativo: boolean, chaveGatilho = 'chamado.aberto') => ({
  id,
  nome,
  descricao: null,
  chaveGatilho,
  ativo,
  rotas: [{ id: 'r1', padrao: true, ordem: 99, condicoes: [] }]
});

describe('PoliticasRoteamentoComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') => abrirListagem(PoliticasRoteamentoComponent, 'lista', url, [{ provide: NotificacoesService, useValue: servico }]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listarPoliticas: vi.fn(() => of([politica('p1', 'Supervisor da base', true), politica('p2', 'Antiga', false, 'sst.desvio')])),
      listarGatilhos: vi.fn(() => of([{ chave: 'chamado.aberto', nome: 'Chamado aberto', modo: 'POR_OCORRENCIA' }])),
      alternarPolitica: vi.fn((id: string, ativo: boolean) => of({ ...politica(id, 'Supervisor da base', ativo) }))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nas políticas ativas, com o nome do gatilho', async () => {
    const { el } = await abrir();
    expect(servico['listarPoliticas']).toHaveBeenCalledOnce();
    expect(el.textContent).toContain('Supervisor da base');
    expect(el.textContent).toContain('Chamado aberto');
    expect(el.textContent).not.toContain('Antiga');
    expect(chipsDoPainel(el)).toEqual(['Status: Ativas']);
  });

  it('restaura o recorte da URL', async () => {
    const { el } = await abrir('/lista?ativo=false');
    expect(el.textContent).toContain('Antiga');
    expect(el.textContent).not.toContain('Supervisor da base');
  });

  it('"n" leva ao formulário de nova política', async () => {
    await abrir();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    tecla('n');
    expect(navegar).toHaveBeenCalledWith(['/notificacoes/admin/politicas-roteamento/nova']);
  });
});
