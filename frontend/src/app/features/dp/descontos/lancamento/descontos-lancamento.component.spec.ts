import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { LookupStore } from '../../../../core/lookup/store/lookup.store';
import { RhDescontosService } from '../../../../core/services/rh-descontos.service';
import { RhService } from '../../../../core/services/rh.service';
import { abrirListagem, chipsDoPainel, paginaTeste } from '../../../../shared/listagem/listagem-teste';
import { DIAS_PADRAO_DESCONTOS, DescontosLancamentoComponent } from './descontos-lancamento.component';

const desconto = {
  id: 'd1234567-aaaa',
  colaboradorId: 'c-1',
  motivo: { id: 'm1', descricao: 'AVARIA DE VEÍCULO' },
  quantidadeParcelas: 3,
  status: 'VALIDADO',
  dataOcorrencia: '2026-09-01',
  valorSugerido: 300,
  valorValidado: 250,
  valorBaixado: null,
  dataCriacao: '2026-09-02T10:00:00Z'
};

describe('DescontosLancamentoComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista', modo?: 'validar' | 'baixar') =>
    abrirListagem(
      DescontosLancamentoComponent,
      'lista',
      url,
      [
        { provide: RhDescontosService, useValue: servico },
        { provide: RhService, useValue: { buscarColaboradorPorId: vi.fn(() => of({ id: 'c-1', nomeCompleto: 'MARIA SOUZA' })) } },
        { provide: LookupStore, useValue: { searchCollaborators: vi.fn(() => of({ items: [], hasMore: false })) } }
      ],
      modo ? { modo } : {}
    );

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listarMotivos: vi.fn(() => paginaTeste([{ id: 'm1', descricao: 'AVARIA DE VEÍCULO', tetoParcelas: 6, ativo: true }])),
      buscarDescontos: vi.fn(() => paginaTeste([desconto])),
      exportarResumo: vi.fn(() => of(new Blob(['x']))),
      exportarDetalhado: vi.fn(() => of(new Blob(['x'])))
    };
  });

  afterEach(() => localStorage.clear());

  it('consulta abre nos últimos 90 dias, mais recentes primeiro, com o nome do colaborador e a divergência', async () => {
    const { el, harness } = await abrir();
    harness.detectChanges();
    const params = servico['buscarDescontos'].mock.calls[0][0];
    const esperado = new Date();
    esperado.setDate(esperado.getDate() - DIAS_PADRAO_DESCONTOS);
    expect(new Date(params.dataCriacaoInicio).toDateString()).toBe(esperado.toDateString());
    expect(params).toMatchObject({ page: 0, size: 20, sort: 'dataCriacao,desc' });
    expect(el.textContent).toContain('MARIA SOUZA');
    expect(el.textContent).toContain('Validado');
    expect(el.textContent).toMatch(/R\$\s?50[.,]00/);
  });

  it('link do painel (?status=&colaboradorId=) abre sem limite de data e com chips', async () => {
    const { el, harness } = await abrir('/lista?status=VALIDADO&colaboradorId=c-1');
    harness.detectChanges();
    const params = servico['buscarDescontos'].mock.lastCall![0];
    expect(params).toMatchObject({ status: 'VALIDADO', colaboradorId: 'c-1' });
    expect(params.dataCriacaoInicio).toBeUndefined();
    expect(chipsDoPainel(el)).toEqual(['Colaborador: MARIA SOUZA', 'Status: Validado']);
  });

  it('fila de validação usa o status da fila e o mais antigo primeiro; a busca fica fora da URL', async () => {
    const { el } = await abrir('/lista', 'validar');
    expect(servico['buscarDescontos']).toHaveBeenCalledWith(expect.objectContaining({ status: 'SOLICITADO', sort: 'dataCriacao,asc' }));
    expect(el.textContent).toContain('Fila de baixa');
    expect(el.querySelector('ces-multiselect')).toBeNull();
  });

  it('exportação leva o recorte atual no formato escolhido', async () => {
    const { componente } = await abrir('/lista?status=VALIDADO');
    const tela = componente as unknown as { formato: { set(v: string): void }; exportar(): void };
    tela.formato.set('detalhado');
    tela.exportar();
    expect(servico['exportarDetalhado']).toHaveBeenCalledWith(expect.objectContaining({ status: 'VALIDADO' }));
    expect(servico['exportarDetalhado'].mock.calls[0][0]).not.toHaveProperty('page');
  });
});
