import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ChamadosService } from '../../../core/services/chamados.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { ChamadosListaComponent } from './chamados-lista.component';

const chamado = {
  id: 'c1',
  protocolo: 'CH-20261010-0001',
  topico: { id: 't1', nome: 'ACESSO' },
  equipeAtendimento: { id: 'q1', nome: 'TI' },
  assunto: 'Sem acesso ao sistema',
  prioridade: 'ALTA',
  status: 'EM_ATENDIMENTO',
  usuarioSolicitanteId: 'u1',
  usuarioResponsavelId: 'u2',
  dataLimiteResolucao: '2026-10-12T10:00:00Z',
  dataUltimaInteracao: '2026-10-10T10:00:00Z',
  slaResolucaoVencido: false,
  slaPausado: false
};

describe('ChamadosListaComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (modo: 'central' | 'meus', url = '/lista') =>
    abrirListagem(
      ChamadosListaComponent,
      'lista',
      url,
      [
        { provide: ChamadosService, useValue: servico },
        {
          provide: UsuariosService,
          useValue: {
            listarUsuarios: vi.fn(() => paginaTeste([])),
            buscarUsuarioPorId: vi.fn((id: string) => of({ id, login: id === 'u2' ? 'USR000002' : 'USR000001' }))
          }
        }
      ],
      modo === 'central' ? { modo: 'central' } : {}
    );

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listarTopicosAtivos: vi.fn(() => of([{ id: 't1', nome: 'ACESSO' }])),
      listarEquipes: vi.fn(() => paginaTeste([{ id: 'q1', nome: 'TI', codigo: 'TI' }])),
      listarFila: vi.fn(() => paginaTeste([chamado])),
      listarMeus: vi.fn(() => paginaTeste([chamado]))
    };
  });

  afterEach(() => localStorage.clear());

  it('fila: ordena pelo prazo de resolução e mostra o responsável', async () => {
    const { el, harness } = await abrir('central');
    harness.detectChanges();
    expect(servico['listarFila']).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 25, sort: 'dataLimiteResolucao,asc', ativo: true }));
    expect(el.textContent).toContain('CH-20261010-0001');
    expect(el.textContent).toContain('USR000002');
    expect(el.textContent).toContain('Em atendimento');
    expect(el.querySelector('button[aria-keyshortcuts="N"]')).toBeNull();
  });

  it('meus: ordena pela última interação e não envia filtros da central', async () => {
    await abrir('meus', '/lista?responsavel=u2');
    expect(servico['listarMeus']).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'dataUltimaInteracao,desc', usuarioResponsavelId: undefined }));
    expect(servico['listarFila']).not.toHaveBeenCalled();
  });

  it('restaura status, tópico e período de abertura da URL', async () => {
    const { el } = await abrir('central', '/lista?status=ABERTO&topico=t1&abertura=2026-10-01~2026-10-10');
    const ultima = servico['listarFila'].mock.lastCall![0];
    expect(ultima).toMatchObject({ status: 'ABERTO', topicoId: 't1' });
    expect(new Date(ultima.dataAberturaInicio).getDate()).toBe(1);
    expect(new Date(ultima.dataAberturaFim).getHours()).toBe(23);
    expect(chipsDoPainel(el)).toEqual(['Tópico: ACESSO', 'Status: Aberto', 'Abertura: 01/10/2026 a 10/10/2026']);
  });

  it('"n" em Meus chamados abre a tela de novo chamado', async () => {
    await abrir('meus');
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    tecla('n');
    expect(navegar).toHaveBeenCalledWith(['/chamados/novo']);
  });
});
