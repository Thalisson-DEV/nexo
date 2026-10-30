import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ContratoService } from '../../../core/services/contrato.service';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { AcoesValoresComponent } from './acoes-valores.component';

const acao = { id: 'a1', codigo: 'SERV01', nome: 'LIGAÇÃO NOVA', ativo: true };
const contrato = { id: 'k1', numeroContrato: 'CT-001', cliente: 'Concessionária', ativo: true };
const valor = { id: 'v1', acao, contrato, valorBase: 152.3, dataVigencia: '2026-01-01', ativo: true };

describe('AcoesValoresComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') =>
    abrirListagem(AcoesValoresComponent, 'lista', url, [
      { provide: ContratoService, useValue: servico },
      { provide: ImportacaoService, useValue: { baixarModeloCsv: vi.fn() } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listarAcoesAtivas: vi.fn(() => paginaTeste([acao])),
      listarContratosAtivos: vi.fn(() => paginaTeste([contrato])),
      listarAcoesValores: vi.fn(() => paginaTeste([valor])),
      criarAcaoValor: vi.fn(() => of(valor)),
      atualizarAcaoValor: vi.fn(() => of(valor)),
      ativarAcaoValor: vi.fn(() => of(null)),
      desativarAcaoValor: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, vigências mais recentes primeiro, com o valor em reais', async () => {
    const { el } = await abrir();
    expect(servico['listarAcoesValores']).toHaveBeenCalledWith(expect.objectContaining({ ativo: null, page: 0, size: 25, sort: 'dataVigencia,desc' }));
    expect(el.textContent).toContain('SERV01');
    expect(el.textContent).toMatch(/R\$\s152,30/);
  });

  it('restaura ação, contrato e vigência da URL com chips legíveis', async () => {
    const { el } = await abrir('/lista?acao=a1&contrato=k1&vigencia=2026-01-01');
    expect(servico['listarAcoesValores']).toHaveBeenLastCalledWith(expect.objectContaining({ idAcao: 'a1', idContrato: 'k1', dataVigencia: '2026-01-01' }));
    expect(chipsDoPainel(el)).toEqual(['Ação: SERV01', 'Contrato: CT-001', 'Vigência: 01/01/2026']);
  });

  it('"n" exige valor maior que zero e grava a vigência local', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as Record<string, { set(v: unknown): void }> & { salvar(): void };
    tela['idAcao'].set('a1');
    tela['idContrato'].set('k1');
    tela['vigencia'].set(new Date(2026, 4, 1));
    tela.salvar();
    expect(servico['criarAcaoValor']).not.toHaveBeenCalled();
    tela['valorBase'].set(99.9);
    tela.salvar();
    expect(servico['criarAcaoValor']).toHaveBeenCalledWith({ idAcao: 'a1', idContrato: 'k1', valorBase: 99.9, dataVigencia: '2026-05-01' });
  });
});
