import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { SolicitacaoMaterialSummaryResponse } from '../../../../core/models/solicitacao-material-tecnica.models';
import { SolicitacaoMaterialTecnicaService } from '../../../../core/services/solicitacao-material-tecnica.service';
import { LIMITE_CALENDARIO, SolicitacoesTecnicasCalendarioComponent } from './solicitacoes-tecnicas-calendario.component';

function iso(deslocamento = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + deslocamento);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

const solicitacao = (extra: Partial<SolicitacaoMaterialSummaryResponse>) =>
  ({ id: 'sol-12345678', idObra: 'obra-1', tipo: 'ADICAO_COMUM', status: 'APROVADO', quantidadeItens: 2, dataPrevista: iso(), ...extra }) as SolicitacaoMaterialSummaryResponse;

function abrir(listar: ReturnType<typeof vi.fn>) {
  TestBed.configureTestingModule({ providers: [{ provide: SolicitacaoMaterialTecnicaService, useValue: { listar } }] });
  const fixture = TestBed.createComponent(SolicitacoesTecnicasCalendarioComponent);
  fixture.componentRef.setInput('filtros', { contexto: 'TECNICO' });
  fixture.componentRef.setInput('rotuloObra', () => '7100003 · TRAFO');
  fixture.detectChanges();
  TestBed.tick();
  fixture.detectChanges();
  return fixture;
}

describe('SolicitacoesTecnicasCalendarioComponent', () => {
  it('consulta a grade do mês com o recorte da tela e ordena pela data prevista', () => {
    const listar = vi.fn(() => of({ content: [], page: { totalElements: 0 } }));
    abrir(listar);

    expect(listar).toHaveBeenCalledWith(
      expect.objectContaining({ contexto: 'TECNICO', previstaDe: expect.any(String), previstaAte: expect.any(String), size: LIMITE_CALENDARIO, sort: 'dataPrevistaRetirada,asc' })
    );
  });

  it('mostra a solicitação no dia previsto, com a obra, e abre o detalhe', () => {
    const item = solicitacao({});
    const fixture = abrir(vi.fn(() => of({ content: [item], page: { totalElements: 1 } })));
    const abertas: SolicitacaoMaterialSummaryResponse[] = [];
    fixture.componentInstance.abrir.subscribe((s) => abertas.push(s));

    const pilula = fixture.nativeElement.querySelector('.cal-pill') as HTMLButtonElement;
    expect(pilula.textContent).toContain('7100003 · TRAFO');
    expect(fixture.nativeElement.querySelector('.cal-agenda-item').textContent).toContain('221');

    pilula.click();
    expect(abertas).toEqual([item]);
  });

  it('solicitação sem data prevista não entra na grade', () => {
    const fixture = abrir(vi.fn(() => of({ content: [solicitacao({ dataPrevista: null })], page: { totalElements: 1 } })));

    expect(fixture.nativeElement.querySelector('.cal-pill')).toBeNull();
    expect(fixture.nativeElement.querySelector('.cal-legenda').textContent).toContain('sem data prevista');
  });

  it('avisa quando o mês passa do limite e quando a consulta falha', () => {
    const cheia = abrir(vi.fn(() => of({ content: [solicitacao({})], page: { totalElements: LIMITE_CALENDARIO + 1 } })));
    expect(cheia.nativeElement.textContent).toContain('Mês com muitas solicitações');
    TestBed.resetTestingModule();

    const falha = abrir(vi.fn(() => throwError(() => new Error('falhou'))));
    expect(falha.nativeElement.querySelector('.cal-shell')).toBeNull();
  });
});
