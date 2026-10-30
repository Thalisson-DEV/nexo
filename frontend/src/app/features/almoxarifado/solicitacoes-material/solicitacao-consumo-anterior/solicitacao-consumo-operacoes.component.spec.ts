import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NotificationService } from '@ces/ui';
import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ConsumoAnteriorDetalheResponse, ConsumoAnteriorMovimentacao, ItemSolicitacaoResponse } from '../../../../core/models/almoxarifado.models';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { agruparOperacoesConsumo } from './consumo-anterior.util';
import { ConsumoAnteriorTabelaComponent } from './solicitacao-consumo-anterior.component';
import { ConsumoAnteriorOperacoesComponent } from './solicitacao-consumo-operacoes.component';

const mov = (parcial: Partial<ConsumoAnteriorMovimentacao>): ConsumoAnteriorMovimentacao => ({
  id: Math.random().toString(36).slice(2),
  tipo: 'APLICACAO',
  dataHora: '2026-09-10T10:00:00Z',
  quantidade: -1,
  condicao: 'NOVO',
  ...parcial
});

const movimentacoes: ConsumoAnteriorMovimentacao[] = [
  mov({ tipo: 'ENTREGA', quantidade: 3, dataHora: '2026-09-02T10:00:00Z', idReferenciaOperacao: 'op-1', numSerie: 'MD-002',
    solicitacao: { id: 'sol-anterior-123', contexto: 'COMERCIAL' }, contraparte: { id: 'almox', nomeIdentificador: 'ALMOXARIFADO' } }),
  mov({ tipo: 'ENTREGA', quantidade: 1, dataHora: '2026-09-02T10:00:00Z', idReferenciaOperacao: 'op-1', numSerie: 'MD-001',
    solicitacao: { id: 'sol-anterior-123', contexto: 'COMERCIAL' }, contraparte: { id: 'almox', nomeIdentificador: 'ALMOXARIFADO' } }),
  mov({ tipo: 'APLICACAO', quantidade: -1, idReferenciaOperacao: 'exec-1', numSerie: 'MD-001',
    nota: { idExecucao: 'exec-1', referencia: 'NS-123', tipoServico: 'CL · LIGAÇÃO NOVA' } }),
  mov({ tipo: 'TRANSFERENCIA', quantidade: -1, idReferenciaOperacao: 'op-2', contraparte: { id: 'eq2', nomeIdentificador: 'EQUIPE 02' } }),
  mov({ tipo: 'RETIRADA', quantidade: 1, condicao: 'SUCATA', idReferenciaOperacao: 'exec-1',
    nota: { idExecucao: 'exec-1', referencia: 'NS-123', tipoServico: null } })
];

describe('agruparOperacoesConsumo', () => {
  it('separa por natureza, junta a mesma operação numa linha e reúne as séries', () => {
    const secoes = agruparOperacoesConsumo(movimentacoes);

    expect(secoes.map((s) => s.grupo)).toEqual(['recebido', 'aplicado', 'transferido', 'sucata']);
    const [recebido, aplicado, transferido, sucata] = secoes;
    expect(recebido.linhas).toHaveLength(1);
    expect(recebido.linhas[0]).toMatchObject({
      titulo: 'Entrega da solicitação #sol-ante',
      detalhe: 'Solicitação comercial · de ALMOXARIFADO',
      quantidade: 4,
      series: ['MD-001', 'MD-002'],
      link: ['/almoxarifado/solicitacoes', 'sol-anterior-123']
    });
    expect(aplicado.linhas[0]).toMatchObject({ titulo: 'Nota NS-123', detalhe: 'CL · LIGAÇÃO NOVA', link: ['/execucao/comercial', 'exec-1'] });
    expect(transferido.linhas[0]).toMatchObject({ titulo: 'Enviada para EQUIPE 02', link: ['/almoxarifado/movimentacoes', 'op-2'] });
    expect(sucata).toMatchObject({ foraDaConta: true, total: 1 });
    expect(recebido.foraDaConta).toBe(false);
  });

  it('soma sem resíduo de ponto flutuante', () => {
    const [aplicado] = agruparOperacoesConsumo([
      mov({ quantidade: -0.1, idReferenciaOperacao: 'a' }),
      mov({ quantidade: -0.2, idReferenciaOperacao: 'b' })
    ]);
    expect(aplicado.total).toBe(-0.3);
  });

  it('sem movimentações não há seções', () => {
    expect(agruparOperacoesConsumo(null)).toEqual([]);
  });
});

const detalhe: ConsumoAnteriorDetalheResponse = {
  idMaterial: 'm-medidor',
  inicio: '2026-09-02T10:00:00Z',
  fim: '2026-10-02T10:00:00Z',
  saldoInicial: 0,
  saldoFinal: 2,
  saldoAtual: 12,
  movimentacoes
};

describe('ConsumoAnteriorOperacoesComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  function abrir(servico: Partial<AlmoxarifadoService>) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AlmoxarifadoService, useValue: servico },
        { provide: NotificationService, useValue: { success: vi.fn() } }
      ]
    });
    const fixture = TestBed.createComponent(ConsumoAnteriorOperacoesComponent);
    fixture.componentRef.setInput('idSolicitacao', 'sol-atual');
    fixture.componentRef.setInput('idMaterial', 'm-medidor');
    fixture.componentRef.setInput('unidade', 'UN');
    fixture.detectChanges();
    return fixture;
  }

  it('mostra a conta do saldo e cada operação com o link para a origem', () => {
    const buscar = vi.fn(() => of(detalhe));
    const fixture = abrir({ buscarConsumoAnteriorDetalhe: buscar });
    const el = fixture.nativeElement as HTMLElement;
    const texto = el.textContent?.replace(/\s+/g, ' ') ?? '';

    expect(buscar).toHaveBeenCalledWith('sol-atual', 'm-medidor');
    expect(texto).toContain('Saldo antes de 02/09');
    expect(texto).toMatch(/Recebido em solicitações\s*\+4/);
    expect(texto).toMatch(/Aplicado em serviços\s*−1/);
    expect(texto).toMatch(/Saldo em 02\/10\s*2 UN/);
    expect(texto).toContain('Hoje o saldo é 12 UN');
    expect(texto).toContain('Fora da conta do saldo novo');
    const nota = Array.from(el.querySelectorAll('a')).find((a) => a.textContent?.includes('Nota NS-123'));
    expect(nota?.getAttribute('href')).toBe('/execucao/comercial/exec-1');
    expect(nota?.getAttribute('target')).toBe('_blank');
  });

  it('as séries da mesma operação ficam recolhidas atrás de um botão', () => {
    const fixture = abrir({ buscarConsumoAnteriorDetalhe: () => of(detalhe) });
    const el = fixture.nativeElement as HTMLElement;
    const botao = el.querySelector<HTMLButtonElement>('button[aria-expanded]');
    expect(botao?.textContent).toContain('2 séries');
    expect(el.querySelector('.co-linha__grade--aberta')).toBeNull();

    botao!.click();
    fixture.detectChanges();

    expect(botao?.getAttribute('aria-expanded')).toBe('true');
    expect(el.querySelector('.co-linha__grade--aberta')?.textContent).toContain('MD-002');
  });

  it('sem pedido anterior avisa que não há período para detalhar', () => {
    const fixture = abrir({ buscarConsumoAnteriorDetalhe: () => of({ ...detalhe, inicio: null, movimentacoes: [] }) });
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('não há período anterior para detalhar');
  });

  it('na falha oferece tentar de novo', () => {
    const buscar = vi.fn().mockReturnValueOnce(throwError(() => new Error('falhou'))).mockReturnValue(of(detalhe));
    const fixture = abrir({ buscarConsumoAnteriorDetalhe: buscar });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Não foi possível carregar as operações');

    el.querySelector<HTMLButtonElement>('ces-button button')!.click();
    fixture.detectChanges();

    expect(buscar).toHaveBeenCalledTimes(2);
    expect(el.textContent).toContain('Nota NS-123');
  });
});

describe('ConsumoAnteriorTabelaComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('cada material abre as operações na própria linha, como acordeão, uma linha por material', () => {
    const buscarDetalhe = vi.fn(() => of(detalhe));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: NotificationService, useValue: { success: vi.fn() } },
        {
          provide: AlmoxarifadoService,
          useValue: {
            buscarConsumoAnteriorDetalhe: buscarDetalhe,
            buscarConsumoAnterior: () =>
              of({
                idSolicitacao: 'sol-atual',
                idEstoque: 'eq',
                consultadoAte: '2026-10-02T10:00:00Z',
                itens: [
                  {
                    idMaterial: 'm-medidor',
                    ultimaSolicitacao: { id: 'sol-anterior-123', dataSolicitacao: '2026-09-01T10:00:00Z', dataEntrega: '2026-09-02T10:00:00Z', quantidadeSolicitada: 4, quantidadeRecebida: 4 },
                    quantidadeAplicada: 1,
                    quantidadeDevolvida: 0,
                    saldoAtual: 12
                  },
                  { idMaterial: 'm-cabo', ultimaSolicitacao: null, quantidadeAplicada: null, quantidadeDevolvida: null, saldoAtual: 0 }
                ]
              })
          }
        }
      ]
    });
    const fixture = TestBed.createComponent(ConsumoAnteriorTabelaComponent);
    fixture.componentRef.setInput('idSolicitacao', 'sol-atual');
    fixture.componentRef.setInput('itens', [
      { id: 'i1', quantidade: 4, material: { id: 'm-medidor', codigo: 'MED', descricao: 'Medidor', unidadeMedida: 'UN' } },
      // material com série desmembrado na entrega: continua uma linha só, somando o pedido
      { id: 'i3', quantidade: 1, numSerie: 'S1', material: { id: 'm-medidor', codigo: 'MED', descricao: 'Medidor', unidadeMedida: 'UN' } },
      { id: 'i2', quantidade: 50, material: { id: 'm-cabo', codigo: 'CABO', descricao: 'Cabo', unidadeMedida: 'MT' } }
    ] as unknown as ItemSolicitacaoResponse[]);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    const botoes = el.querySelectorAll<HTMLButtonElement>('.ca-material__botao');
    expect(botoes).toHaveLength(2);
    expect(el.querySelectorAll('.ca-tabela__linha:not(.ca-tabela__cab)')[0].textContent).toContain('5');
    expect(botoes[0].getAttribute('aria-expanded')).toBe('false');
    expect(buscarDetalhe).not.toHaveBeenCalled();

    el.querySelector<HTMLElement>('.ca-tabela__linha--expansivel')!.click();
    fixture.detectChanges();

    expect(botoes[0].getAttribute('aria-expanded')).toBe('true');
    expect(buscarDetalhe).toHaveBeenCalledWith('sol-atual', 'm-medidor');
    expect(el.querySelector('#ca-ops-m-medidor app-consumo-anterior-operacoes')).not.toBeNull();

    botoes[0].click();
    fixture.detectChanges();
    expect(el.querySelector('#ca-ops-m-medidor')).toBeNull();

    // primeiro pedido também abre: o detalhe mostra como o saldo chegou ao que é
    botoes[1].click();
    fixture.detectChanges();
    expect(buscarDetalhe).toHaveBeenCalledWith('sol-atual', 'm-cabo');
  });
});
