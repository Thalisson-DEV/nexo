import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { NotificationService } from '@ces/ui';
import { ConfirmationService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { SolicitacaoResponse } from '../../../core/models/almoxarifado.models';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { AuthService } from '../../../core/services/auth.service';
import { RhService } from '../../../core/services/rh.service';
import { UsuarioLookupService } from '../../../core/services/usuario-lookup.service';
import { paginaTeste } from '../../../shared/listagem/listagem-teste';
import { ItemEntregaDraft, SolicitacaoComercialDetalheComponent } from './solicitacao-comercial-detalhe.component';

const detalhe = (extra: Partial<SolicitacaoResponse> = {}): SolicitacaoResponse =>
  ({
    id: 'sol-12345678-abcd',
    status: 'APROVADO',
    dataSolicitacao: '2026-10-01T10:00:00Z',
    dataVerificacao: '2026-10-01T12:00:00Z',
    dataPrevista: '2026-10-20',
    idRequisitante: 'u1',
    idAprovador: 'u2',
    idEquipe: 'eq-1',
    estoqueOrigem: { id: 'origem', nomeIdentificador: 'CENTRAL', tipoEstoque: { nome: 'FÍSICO' } },
    estoqueDestino: { id: 'destino', nomeIdentificador: 'EQUIPE 7', tipoEstoque: { nome: 'CAMPO' } },
    acoesPermitidas: ['ENTREGAR'],
    itens: [
      { id: 'i-cabo', quantidade: 10, quantidadeEntregue: 4, quantidadePendente: 6, material: { id: 'm-cabo', codigo: 'CABO', descricao: 'Cabo', unidadeMedida: 'MT', detalhaSerie: false } },
      { id: 'i-trafo', quantidade: 3, quantidadeEntregue: 0, quantidadePendente: 3, seriesReservadas: ['TR-002'], material: { id: 'm-trafo', codigo: 'TRAFO', descricao: 'Transformador', unidadeMedida: 'UN', detalhaSerie: true } }
    ],
    ...extra
  }) as unknown as SolicitacaoResponse;

type Tela = {
  detalhe(): SolicitacaoResponse | null;
  itensEntrega(): ItemEntregaDraft[];
  podeAprovar(): boolean;
  podeEntregar(): boolean;
  fluxo(): { rotulo: string; estado: string }[];
  responsavelId: { set(v: string): void };
  definirSeries(i: number, v: string[]): void;
  podeDefinirSeries(item: unknown): boolean;
  alternarReserva(item: unknown): void;
  reservaAberta(): string | null;
  reservaRascunho: { (): string[]; set(v: string[]): void };
  salvarReserva(item: unknown): void;
  preencherTudo(): void;
  zerarTudo(): void;
  itensMarcados(): number;
  confirmarEntrega(): void;
  aba(): string;
  trocarAssinatura(t: 'FISICA' | 'DIGITAL'): void;
  assinaturaAlmox: { set(v: string): void };
  assinaturaEquipe: { set(v: string): void };
  pedirAnalise(): void;
  carregarOrigens(): void;
  opcoesOrigem(): { label: string; value: string }[];
  trocarOrigem(id: string): void;
  podeTrocarOrigem(): boolean;
};

describe('SolicitacaoComercialDetalheComponent', () => {
  let almox: Record<string, ReturnType<typeof vi.fn>>;
  let avisos: Record<string, ReturnType<typeof vi.fn>>;

  async function abrir(det: SolicitacaoResponse, aba: 'itens' | 'entrega' = 'itens') {
    almox['buscarSolicitacaoPorId'].mockReturnValue(of(det));
    TestBed.configureTestingModule({
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        ConfirmationService,
        { provide: AlmoxarifadoService, useValue: almox },
        { provide: RhService, useValue: { listarMembrosAtivos: () => of([{ idColaborador: 'c1', nomeColaborador: 'Ana' }]), listarColaboradores: () => paginaTeste([]) } },
        { provide: UsuarioLookupService, useValue: { resolverNome: (id: string) => (id === 'u1' ? 'Requisitante' : 'Aprovador') } },
        { provide: NotificationService, useValue: avisos },
        { provide: AuthService, useValue: { currentUser: () => ({ permissoes: ['estoque:movimentacao:write'] }) } }
      ]
    });
    const fixture = TestBed.createComponent(SolicitacaoComercialDetalheComponent);
    fixture.componentRef.setInput('solicitacaoId', det.id);
    fixture.componentRef.setInput('abaInicial', aba);
    fixture.componentInstance.visible.set(true);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, tela: fixture.componentInstance as unknown as Tela };
  }

  beforeEach(() => {
    // As abas observam o próprio tamanho; o jsdom não tem ResizeObserver.
    (globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
      observe(): void {}
      unobserve(): void {}
      disconnect(): void {}
    };
    avisos = { success: vi.fn(), warning: vi.fn(), error: vi.fn() };
    almox = {
      buscarSolicitacaoPorId: vi.fn(),
      buscarConsumoAnterior: vi.fn(() =>
        of({
          idSolicitacao: 'sol-12345678-abcd',
          idEstoque: 'destino',
          consultadoAte: '2026-10-02T10:00:00Z',
          itens: [
            {
              idMaterial: 'm-cabo',
              ultimaSolicitacao: { id: 'sol-ant', dataSolicitacao: '2026-09-01T10:00:00Z', dataEntrega: '2026-09-02T10:00:00Z', quantidadeSolicitada: 60, quantidadeRecebida: 50 },
              quantidadeAplicada: 12,
              quantidadeDevolvida: 0,
              saldoAtual: 38
            },
            { idMaterial: 'm-trafo', ultimaSolicitacao: null, quantidadeAplicada: null, quantidadeDevolvida: null, saldoAtual: 0 }
          ]
        })
      ),
      listarSaldosSeriais: vi.fn(() => paginaTeste([{ numSerie: 'TR-001' }, { numSerie: 'TR-002' }])),
      verificarSeries: vi.fn(() => of([])),
      definirSeriesReservadas: vi.fn((_id: string, _item: string, series: string[]) => of(series)),
      listarMotivosPendencia: vi.fn(() => of([])),
      listarMotivosRejeicaoAtivos: vi.fn(() => paginaTeste([])),
      aprovarSolicitacao: vi.fn(() => of(undefined)),
      registrarEntrega: vi.fn(() => of(undefined)),
      listarEstoquesAtivos: vi.fn(() =>
        paginaTeste([
          { id: 'origem', nomeIdentificador: 'CENTRAL', ativo: true, tipoEstoque: { nome: 'FISICO' } },
          { id: 'norte', nomeIdentificador: 'ALMOX NORTE', ativo: true, tipoEstoque: { nome: 'FISICO' } },
          { id: 'destino', nomeIdentificador: 'EQUIPE 7', ativo: true, tipoEstoque: { nome: 'CAMPO' } }
        ])
      )
    };
  });

  afterEach(() => TestBed.resetTestingModule());

  it('as ações seguem a política do backend, inclusive com saldo encerrado', async () => {
    const { tela } = await abrir(detalhe({ status: 'ENTREGUE_PARCIAL', encerrada: true, acoesPermitidas: ['ENTREGAR'] }));
    expect(tela.podeEntregar()).toBe(true);
    expect(tela.podeAprovar()).toBe(false);
  });

  it('a linha do tempo diz o que falta: aprovada e aguardando retirada', async () => {
    const { tela } = await abrir(detalhe());
    expect(tela.fluxo().map((e) => [e.rotulo, e.estado])).toEqual([
      ['Solicitada', 'feito'],
      ['Aprovada', 'feito'],
      ['Aguardando retirada', 'atual']
    ]);
  });

  it('pendente mostra "Aguardando aprovação" como etapa atual', async () => {
    const { tela } = await abrir(detalhe({ status: 'PENDENTE', dataVerificacao: undefined, acoesPermitidas: ['APROVAR', 'REJEITAR'] }));
    expect(tela.fluxo()[1]).toMatchObject({ rotulo: 'Aguardando aprovação', estado: 'atual' });
    expect(tela.podeAprovar()).toBe(true);
  });

  it('material com série: as séries definidas antes já vêm escolhidas e o item entra com quantas séries tiver', async () => {
    const { tela } = await abrir(detalhe(), 'entrega');
    expect(almox['listarSaldosSeriais']).toHaveBeenCalledWith(expect.objectContaining({ idEstoque: 'origem', idMaterial: 'm-trafo', condicao: 'NOVO' }));
    expect(tela.itensEntrega()[1]).toMatchObject({ series: ['TR-002'], quantidadeAEntregar: 1 });
    expect(tela.itensMarcados()).toBe(2);

    tela.definirSeries(1, ['TR-001', 'TR-002']);
    expect(tela.itensEntrega()[1].quantidadeAEntregar).toBe(2);

    tela.definirSeries(1, []);
    expect(tela.itensEntrega()[1].quantidadeAEntregar).toBe(0);
    expect(tela.itensMarcados()).toBe(1);
  });

  it('"entregar tudo" completa o material comum e mantém as séries; "zerar" limpa tudo', async () => {
    const { tela } = await abrir(detalhe(), 'entrega');
    tela.definirSeries(1, []);
    tela.preencherTudo();
    expect(tela.itensEntrega().map((i) => [i.quantidadeAEntregar, i.series])).toEqual([
      [6, []],
      [0, []]
    ]);
    tela.zerarTudo();
    expect(tela.itensMarcados()).toBe(0);
  });

  it('várias séries de um item saem desmembradas: uma unidade por série e o item fica com o que falta', async () => {
    const { tela, fixture } = await abrir(detalhe(), 'entrega');
    const alterado = vi.fn();
    fixture.componentInstance.alterado.subscribe(alterado);
    tela.responsavelId.set('c1');
    tela.definirSeries(1, ['TR-001', 'TR-002']);
    tela.confirmarEntrega();
    expect(almox['registrarEntrega']).toHaveBeenCalledWith('sol-12345678-abcd', {
      assinaturaAlmox: 'FISICA',
      assinaturaEquipe: 'FISICA',
      idColaboradorSaque: 'c1',
      saqueForaEquipe: false,
      itens: [
        { idItemSolicitacao: 'i-cabo', quantidade: 6, novaQuantidadeSolicitada: 10 },
        { idItemSolicitacao: 'i-trafo', quantidade: 0, novaQuantidadeSolicitada: 1 }
      ],
      itensAdicionados: [
        { idMaterial: 'm-trafo', quantidade: 1, quantidadeSolicitada: 1, numSerie: 'TR-001' },
        { idMaterial: 'm-trafo', quantidade: 1, quantidadeSolicitada: 1, numSerie: 'TR-002' }
      ]
    });
    expect(alterado).toHaveBeenCalled();
    expect(tela.aba()).toBe('itens');
  });

  it('quem entrega troca o almoxarifado de origem: as séries da origem anterior saem e a entrega leva a nova origem', async () => {
    const semSaida = detalhe({
      itens: [
        { id: 'i-cabo', quantidade: 10, quantidadeEntregue: 0, quantidadePendente: 10, material: { id: 'm-cabo', codigo: 'CABO', descricao: 'Cabo', unidadeMedida: 'MT', detalhaSerie: false } },
        { id: 'i-trafo', quantidade: 3, quantidadeEntregue: 0, quantidadePendente: 3, seriesReservadas: ['TR-002'], material: { id: 'm-trafo', codigo: 'TRAFO', descricao: 'Transformador', unidadeMedida: 'UN', detalhaSerie: true } }
      ] as never
    });
    const { tela } = await abrir(semSaida, 'entrega');
    expect(tela.opcoesOrigem()).toEqual([{ label: 'CENTRAL', value: 'origem' }]);
    expect(almox['listarEstoquesAtivos']).not.toHaveBeenCalled();

    tela.carregarOrigens();
    expect(tela.opcoesOrigem().map((o) => o.value)).toEqual(['origem', 'norte']);
    tela.trocarOrigem('norte');
    expect(tela.itensEntrega()[1]).toMatchObject({ series: [], quantidadeAEntregar: 0 });

    tela.responsavelId.set('c1');
    tela.confirmarEntrega();
    expect(almox['registrarEntrega']).toHaveBeenCalledWith('sol-12345678-abcd', expect.objectContaining({ idEstoqueOrigem: 'norte' }));
  });

  it('depois da primeira saída a origem fica travada e a entrega não manda origem', async () => {
    const { tela } = await abrir(detalhe(), 'entrega');
    expect(tela.podeTrocarOrigem()).toBe(false);
    tela.responsavelId.set('c1');
    tela.confirmarEntrega();
    expect(almox['registrarEntrega'].mock.calls[0][1].idEstoqueOrigem).toBeUndefined();
  });

  it('definir séries antes da entrega reserva as séries do item', async () => {
    const { tela, fixture } = await abrir(detalhe());
    const [cabo, trafo] = tela.detalhe()!.itens;
    expect(tela.podeDefinirSeries(cabo)).toBe(false);
    expect(tela.podeDefinirSeries(trafo)).toBe(true);
    expect(document.body.textContent).toContain('1 de 3 séries definidas');

    tela.alternarReserva(trafo);
    fixture.detectChanges();
    expect(tela.reservaAberta()).toBe('i-trafo');
    expect(tela.reservaRascunho()).toEqual(['TR-002']);
    expect(document.body.querySelector('#dd-reserva-i-trafo app-series-selecao')).not.toBeNull();

    tela.reservaRascunho.set(['TR-001', 'TR-002']);
    tela.salvarReserva(trafo);

    expect(almox['definirSeriesReservadas']).toHaveBeenCalledWith('sol-12345678-abcd', 'i-trafo', ['TR-001', 'TR-002']);
    expect(avisos['success']).toHaveBeenCalledWith('2 séries reservadas para TRAFO. Na entrega, é só confirmar.');
    expect(tela.reservaAberta()).toBeNull();
  });

  it('sem quem retira, não envia e avisa', async () => {
    const membros = { listarMembrosAtivos: () => of([]), listarColaboradores: () => paginaTeste([]) };
    TestBed.overrideProvider(RhService, { useValue: membros });
    const { tela } = await abrir(detalhe(), 'entrega');
    tela.confirmarEntrega();
    expect(almox['registrarEntrega']).not.toHaveBeenCalled();
    expect(avisos['warning']).toHaveBeenCalledWith('Escolha quem retira o material.');
  });

  it('assinatura digital abre as duas áreas de assinatura e só envia com ambas desenhadas', async () => {
    const { tela, fixture } = await abrir(detalhe(), 'entrega');
    tela.responsavelId.set('c1');
    tela.trocarAssinatura('DIGITAL');
    fixture.detectChanges();
    expect(document.querySelectorAll('app-solicitacao-tecnica-assinatura')).toHaveLength(2);

    tela.confirmarEntrega();
    expect(almox['registrarEntrega']).not.toHaveBeenCalled();
    expect(avisos['warning']).toHaveBeenCalledWith('Colete as duas assinaturas digitais: almoxarifado e quem retira.');

    tela.assinaturaAlmox.set('data:image/png;base64,AAA');
    tela.assinaturaEquipe.set('data:image/png;base64,BBB');
    tela.confirmarEntrega();
    expect(almox['registrarEntrega']).toHaveBeenCalledWith(
      'sol-12345678-abcd',
      expect.objectContaining({ assinaturaAlmox: 'data:image/png;base64,AAA', assinaturaEquipe: 'data:image/png;base64,BBB' })
    );
  });

  it('"Analisar" pede o Copiloto com o contexto da solicitação', async () => {
    const { tela, fixture } = await abrir(detalhe());
    const analisar = vi.fn();
    fixture.componentInstance.analisar.subscribe(analisar);
    tela.pedirAnalise();
    expect(analisar).toHaveBeenCalledWith(expect.objectContaining({ id: 'sol-12345678-abcd', rotulo: 'Solicitação #sol-1234' }));
  });

  it('cada material mostra o que a equipe pegou no último pedido, quanto gastou e quanto tem', async () => {
    await abrir(detalhe());
    expect(almox['buscarConsumoAnterior']).toHaveBeenCalledWith('sol-12345678-abcd');
    const texto = document.body.textContent?.replace(/\s+/g, ' ') ?? '';
    expect(texto).toMatch(/Pegou em 02\/09\s*50 MT/);
    expect(texto).toMatch(/Gastou\s*12/);
    expect(texto).toMatch(/Tem agora\s*38/);
    expect(texto).toContain('Usou 24% do que pegou e ainda tem saldo.');
    expect(texto).toContain('Primeiro pedido deste material para o estoque.');
  });

  it('ver operações abre o detalhamento do consumo abaixo do material', async () => {
    almox['buscarConsumoAnteriorDetalhe'] = vi.fn(() =>
      of({ idMaterial: 'm-cabo', inicio: '2026-09-02T10:00:00Z', fim: '2026-10-02T10:00:00Z', saldoInicial: 0, saldoFinal: 38, saldoAtual: 38, movimentacoes: [] })
    );
    const { fixture } = await abrir(detalhe());
    const botoes = Array.from(document.body.querySelectorAll<HTMLButtonElement>('.ca-ver')).filter((b) => b.textContent?.includes('Ver operações'));
    expect(botoes).toHaveLength(1);

    botoes[0].click();
    fixture.detectChanges();

    expect(almox['buscarConsumoAnteriorDetalhe']).toHaveBeenCalledWith('sol-12345678-abcd', 'm-cabo');
    expect(botoes[0].getAttribute('aria-expanded')).toBe('true');
    expect(document.body.querySelector('.dd-consumo-ops app-consumo-anterior-operacoes')).not.toBeNull();
  });

  it('sem o consumo anterior o detalhe segue normal', async () => {
    almox['buscarConsumoAnterior'] = vi.fn(() => throwError(() => new Error('falhou')));
    const { tela } = await abrir(detalhe());
    expect(tela.detalhe()?.id).toBe('sol-12345678-abcd');
    expect(document.body.textContent).not.toContain('Pegou em');
  });
});
