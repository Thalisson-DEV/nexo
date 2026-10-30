import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { NotificacoesStore } from '../../../core/services/notificacoes-store.service';
import { NotificacoesService } from '../../../core/services/notificacoes.service';
import { abrirListagem, chipsDoPainel, paginaTeste } from '../../../shared/listagem/listagem-teste';
import { MinhasNotificacoesComponent } from './minhas-notificacoes.component';

const nova = {
  id: 'n1',
  categoria: 'APROVACAO',
  categoriaRotulo: 'Aprovações pendentes',
  prioridade: 'ALTA',
  prioridadeRotulo: 'Alta',
  titulo: 'Desconto aguardando validação',
  corpo: 'O desconto 123 precisa da sua validação.',
  deeplink: '/dp/descontos/validacao',
  lida: false,
  dataCriacao: '2026-10-10T10:00:00Z'
};

describe('MinhasNotificacoesComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  let store: Record<string, unknown>;
  const abrir = (url = '/lista') =>
    abrirListagem(MinhasNotificacoesComponent, 'lista', url, [
      { provide: NotificacoesService, useValue: servico },
      { provide: NotificacoesStore, useValue: store }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listar: vi.fn(() => paginaTeste([nova])),
      marcarLida: vi.fn(() => of(undefined)),
      marcarNaoLida: vi.fn(() => of(undefined)),
      arquivar: vi.fn(() => of(undefined)),
      marcarTodasLidas: vi.fn(() => of({})),
      arquivarLidas: vi.fn(() => of({}))
    };
    store = {
      temNaoLidas: signal(true),
      naoLidasPorCategoria: signal({ APROVACAO: 2, SISTEMA: 0 }),
      carregarResumo: vi.fn(),
      aplicarResumo: vi.fn(),
      carregarRecentes: vi.fn(),
      atualizar: vi.fn()
    };
  });

  afterEach(() => localStorage.clear());

  it('abre a caixa de entrada mais recente primeiro, com a categoria não lida como filtro rápido', async () => {
    const { el } = await abrir();
    expect(servico['listar']).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 25, sort: 'dataCriacao,desc', apenasNaoLidas: undefined }));
    expect(el.textContent).toContain('Desconto aguardando validação');
    expect(el.textContent).toContain('Nova');
    const rapidos = Array.from(el.querySelectorAll('.ces-quick-filter')).map((b) => b.textContent!.trim());
    expect(rapidos).toEqual(['Aprovações pendentes · 2']);
  });

  it('o filtro rápido aplica a categoria e vira chip', async () => {
    const { el, harness } = await abrir();
    (el.querySelector('.ces-quick-filter') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(servico['listar']).toHaveBeenLastCalledWith(expect.objectContaining({ categoria: 'APROVACAO' }));
    expect(chipsDoPainel(el)).toEqual(['Categoria: Aprovações pendentes']);
  });

  it('abrir marca como lida e navega para o registro', async () => {
    const { componente } = await abrir('/lista?naoLidas=true');
    expect(servico['listar']).toHaveBeenLastCalledWith(expect.objectContaining({ apenasNaoLidas: true }));
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const tela = componente as unknown as { abrir(n: unknown): void; lida(n: unknown): boolean };
    tela.abrir(nova);
    expect(servico['marcarLida']).toHaveBeenCalledWith('n1');
    expect(tela.lida(nova)).toBe(true);
    expect(navegar).toHaveBeenCalledWith('/dp/descontos/validacao');
  });

  it('falha ao marcar desfaz a leitura na tela', async () => {
    servico['marcarLida'] = vi.fn(() => throwError(() => ({ status: 500 })));
    const { componente } = await abrir();
    const tela = componente as unknown as { alternarLeitura(n: unknown): void; lida(n: unknown): boolean };
    tela.alternarLeitura(nova);
    expect(tela.lida(nova)).toBe(false);
  });
});
