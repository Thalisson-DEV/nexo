import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { EventoSolicitacao } from '../../../core/models/financeiro.models';
import { SolicitacaoFinanceiraHistoricoComponent } from './solicitacao-financeira-historico.component';

describe('SolicitacaoFinanceiraHistoricoComponent', () => {
  const renderizar = (eventos: EventoSolicitacao[]) => {
    const fixture = TestBed.createComponent(SolicitacaoFinanceiraHistoricoComponent);
    fixture.componentRef.setInput('eventos', eventos);
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  };

  it('renderiza a rejeição, que chega sem valor (o backend omite campos nulos)', () => {
    // Exatamente como vem da API: sem as chaves `valor` e `statusAnterior`.
    const rejeicao = {
      id: 'ev-1',
      tipo: 'REJEITADA',
      rotulo: 'Rejeitada',
      statusNovo: 'REJEITADO',
      usuarioId: 'u-1',
      usuario: 'Carla Diretora',
      texto: 'Fora do orçamento',
      ocorridoEm: '2026-10-23T14:48:16Z'
    } as unknown as EventoSolicitacao;

    const texto = renderizar([rejeicao]);

    expect(texto).toContain('Rejeitada');
    expect(texto).toContain('Motivo: Fora do orçamento');
    expect(texto).not.toContain('Valor');
  });

  it('mostra o valor que deixou de valer quando a aprovação é desfeita', () => {
    const texto = renderizar([
      {
        id: 'ev-2',
        tipo: 'DECISAO_DESFEITA',
        rotulo: 'Decisão desfeita',
        statusAnterior: 'APROVADO',
        statusNovo: 'SOLICITADO',
        usuarioId: 'u-1',
        usuario: 'Carla Diretora',
        valor: 100,
        texto: 'Boleto vencido',
        ocorridoEm: '2026-10-23T14:20:38Z'
      }
    ]);

    expect(texto).toContain('Era aprovação');
    expect(texto).toContain('Deixou de valer a autorização de R$');
  });
});
