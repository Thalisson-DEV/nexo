import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthService } from '../../../core/services/auth.service';
import { GovernancaSstService } from '../../../core/services/governanca-sst.service';
import { RhService } from '../../../core/services/rh.service';
import { abrirListagem, chipsDoPainel, paginaTeste } from '../../../shared/listagem/listagem-teste';
import { GovernancaSstComponent, rotuloGovernanca, tomGovernanca } from './governanca.component';

describe('GovernancaSstComponent', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let permissoes: string[];
  const abrir = (secao: string, url = '/lista') =>
    abrirListagem(
      GovernancaSstComponent,
      'lista',
      url,
      [
        { provide: GovernancaSstService, useValue: api },
        { provide: RhService, useValue: { listarBasesOperacionaisAtivas: () => paginaTeste([{ id: 'b1', nome: 'BASE SALVADOR' }]) } },
        { provide: AuthService, useValue: { currentUser: () => ({ permissoes }) } }
      ],
      { secao, tipoCorpo: 'CIPA' }
    );

  beforeEach(() => {
    TestBed.resetTestingModule();
    permissoes = ['sst:governanca:read', 'sst:governanca:write'];
    api = {
      dashboard: vi.fn(() =>
        of({
          calculadoEm: '2026-09-10T10:00:00Z',
          indicadores: { corposAtivos: 2, reunioesPendentesAta: 1, documentosVencidos: 3, treinamentosIrregulares: 0, acoesVencidas: 4 },
          atencao: [{ idRegistro: 'd1', tipo: 'DOCUMENTO', titulo: 'Alvará vencido', severidade: 'VENCIDO', prazo: '2026-09-01' }]
        })
      ),
      listarCorpos: vi.fn(() =>
        paginaTeste([{ id: 'c1', nome: 'CIPA 2026', baseNome: 'BASE SALVADOR', unidadeGovernanca: 'SSA', uf: 'BA', inicioPeriodo: '2026-01-01', fimPeriodo: '2026-12-31', quantidadeMembrosAtivos: 5, status: 'ATIVO' }])
      ),
      listarDocumentos: vi.fn(() => paginaTeste([])),
      listarAcoes: vi.fn(() => paginaTeste([{ id: 'a1', titulo: 'Trocar extintor', prioridade: 'ALTA', status: 'ABERTA', prazo: '2026-10-01' }])),
      buscarAcao: vi.fn(() => of({ id: 'a1', titulo: 'Trocar extintor', tipo: 'CORRETIVA', prioridade: 'ALTA', status: 'ABERTA' }))
    };
  });

  afterEach(() => localStorage.clear());

  it('painel mostra indicadores e pendências; o indicador leva à seção', async () => {
    const { el } = await abrir('painel');
    expect(api['dashboard']).toHaveBeenCalledWith(expect.objectContaining({ tipo: ['CIPA'] }));
    expect(el.textContent).toContain('Documentos vencidos');
    expect(el.textContent).toContain('Alvará vencido');
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    (el.querySelector('.gov-metrica') as HTMLButtonElement).click();
    expect(navegar).toHaveBeenCalledWith('/sst/governanca/cipa/composicoes');
  });

  it('composições abrem já na tabela, só do grupo selecionado, com filtros da URL nomeados', async () => {
    const { el } = await abrir('composicoes', '/lista?bases=b1&status=ATIVO');
    expect(api['listarCorpos']).toHaveBeenLastCalledWith(expect.objectContaining({ tipo: ['CIPA'], idsBases: ['b1'], status: ['ATIVO'], page: 0, size: 25 }));
    expect(el.textContent).toContain('CIPA 2026');
    expect(el.textContent).toContain('01/01/2026 — 31/12/2026');
    expect(chipsDoPainel(el)).toEqual(expect.arrayContaining(['Base: BASE SALVADOR', 'Situação: Ativo']));
    expect(el.textContent).toContain('Nova composição');
  });

  it('documentos filtram a situação pela validade; sem permissão não há envio', async () => {
    permissoes = ['sst:governanca:read'];
    const { el } = await abrir('documentos', '/lista?status=VENCIDO');
    expect(api['listarDocumentos']).toHaveBeenLastCalledWith(expect.objectContaining({ statusValidade: ['VENCIDO'] }));
    expect(api['listarDocumentos'].mock.lastCall![0].status).toBeUndefined();
    expect(el.textContent).not.toContain('Enviar documento');
  });

  it('ação abre o detalhe no drawer', async () => {
    const { componente } = await abrir('acoes');
    (componente as unknown as { abrirLinha(l: unknown): void }).abrirLinha({ id: 'a1' });
    expect((componente as unknown as { detalheAcao(): unknown }).detalheAcao()).toMatchObject({ titulo: 'Trocar extintor' });
  });

  it('rótulos e tons das situações', () => {
    expect(rotuloGovernanca('AGUARDANDO_ATA')).toBe('Ata pendente');
    expect(rotuloGovernanca('NOVO_ESTADO')).toBe('Novo estado');
    expect(tomGovernanca('VENCIDO')).toBe('danger');
    expect(tomGovernanca('A_VENCER')).toBe('warning');
    expect(tomGovernanca('ATIVO')).toBe('success');
  });
});
