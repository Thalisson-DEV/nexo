import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { SolicitacaoSummaryResponse } from '../../../../core/models/almoxarifado.models';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { paginaTeste } from '../../../../shared/listagem/listagem-teste';
import { SolicitacoesComerciaisCalendarioComponent } from './solicitacoes-comerciais-calendario.component';

const sol = (id: string, dataPrevista: string, status = 'APROVADO', destino = 'EQUIPE ' + id) =>
  ({ id: id.padEnd(12, '0'), dataPrevista, status, estoqueOrigemNome: 'CENTRAL', estoqueDestinoNome: destino, totalItens: 2 }) as unknown as SolicitacaoSummaryResponse;

describe('SolicitacoesComerciaisCalendarioComponent', () => {
  let listar: ReturnType<typeof vi.fn>;

  async function abrir(itens: SolicitacaoSummaryResponse[], total = itens.length) {
    listar = vi.fn(() => paginaTeste(itens, total));
    TestBed.configureTestingModule({ providers: [provideNoopAnimations(), { provide: AlmoxarifadoService, useValue: { listarSolicitacoes: listar } }] });
    const fixture = TestBed.createComponent(SolicitacoesComerciaisCalendarioComponent);
    fixture.componentRef.setInput('filtros', { contexto: 'COMERCIAL', statuses: ['APROVADO'] });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 12, 10, 0));
  });

  afterEach(() => {
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  it('consulta a grade de 6 semanas pelo saque previsto, mantendo o recorte da tela', async () => {
    await abrir([]);
    expect(listar).toHaveBeenCalledWith(
      expect.objectContaining({ contexto: 'COMERCIAL', statuses: ['APROVADO'], previstaDe: '2026-09-27', previstaAte: '2026-11-07', sort: 'dataPrevistaRetirada,asc' })
    );
  });

  it('põe cada solicitação no dia do saque, resume o mês e lista a agenda do dia selecionado', async () => {
    const { el } = await abrir([sol('a1', '2026-10-12'), sol('b2', '2026-10-05'), sol('c3', '2026-10-20', 'PENDENTE')]);
    expect(el.querySelector('.cal-title')?.textContent).toContain('Outubro de 2026');
    expect(el.querySelectorAll('.cal-pill')).toHaveLength(3);
    expect(el.querySelector('.cal-resumo')?.textContent).toContain('3 saques previstos no mês');
    // A que venceu dia 5 ainda está aprovada: conta como atrasada.
    expect(el.querySelector('.cal-resumo')?.textContent).toContain('1 atrasada');
    expect(el.querySelector('.cal-agenda')?.textContent).toContain('#a1000000');
  });

  it('dia com mais solicitações do que cabem mostra "+N mais" e o clique seleciona o dia', async () => {
    const dia = '2026-10-15';
    const { fixture, el } = await abrir([sol('a1', dia), sol('b2', dia), sol('c3', dia), sol('d4', dia), sol('e5', dia)]);
    const mais = el.querySelector<HTMLButtonElement>('.cal-mais')!;
    expect(mais.textContent).toContain('+2 mais');
    mais.click();
    fixture.detectChanges();
    expect(el.querySelectorAll('.cal-agenda-item')).toHaveLength(5);
  });

  it('avisa quando o mês passa do limite exibido', async () => {
    const { el } = await abrir([sol('a1', '2026-10-12')], 900);
    expect(el.textContent).toContain('Mostrando 500 de 900');
  });

  it('clicar numa solicitação emite a abertura do detalhe', async () => {
    const { fixture, el } = await abrir([sol('a1', '2026-10-12')]);
    const abriu = vi.fn();
    fixture.componentInstance.abrir.subscribe(abriu);
    el.querySelector<HTMLButtonElement>('.cal-pill')!.click();
    expect(abriu).toHaveBeenCalledWith(expect.objectContaining({ id: 'a10000000000' }));
  });

  it('navega entre meses e volta para hoje', async () => {
    const { fixture, el } = await abrir([]);
    el.querySelector<HTMLElement>('[aria-label="Próximo mês"] button, ces-icon-button[label="Próximo mês"] button')?.click();
    fixture.detectChanges();
    expect(el.querySelector('.cal-title')?.textContent).toContain('Novembro de 2026');
    expect(listar).toHaveBeenLastCalledWith(expect.objectContaining({ previstaDe: '2026-11-01' }));
  });
});
