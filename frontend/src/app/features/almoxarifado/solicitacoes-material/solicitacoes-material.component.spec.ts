import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { CesConfirmationService } from '@ces/ui';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { SolicitacaoSummaryResponse } from '../../../core/models/almoxarifado.models';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { RhService } from '../../../core/services/rh.service';
import { UsuarioLookupService } from '../../../core/services/usuario-lookup.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { CopilotoPainelService } from '../../copiloto/painel';
import { abrirListagem, chipsDoPainel, paginaTeste } from '../../../shared/listagem/listagem-teste';
import { SolicitacoesMaterialComponent } from './solicitacoes-material.component';

const solicitacao = {
  id: 'abcdef12-0000',
  tipoTecnico: 'ADICAO_COMUM',
  estoqueOrigemNome: 'ESTOQUE CENTRAL',
  estoqueDestinoNome: 'ESTOQUE LOCAL',
  status: 'ENTREGUE',
  dataSolicitacao: '2026-09-10T10:00:00Z',
  dataPrevista: '2026-09-12',
  foraPrazoSaque: true,
  totalItens: 2
} as unknown as SolicitacaoSummaryResponse;

describe('SolicitacoesMaterialComponent', () => {
  let almox: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista', data: Record<string, unknown> = {}) =>
    abrirListagem(
      SolicitacoesMaterialComponent,
      'lista',
      url,
      [
        { provide: AlmoxarifadoService, useValue: almox },
        { provide: RhService, useValue: { listarBasesOperacionais: () => paginaTeste([]), listarColaboradores: () => paginaTeste([]), listarMembrosAtivos: () => of([]) } },
        { provide: UsuariosService, useValue: { listarUsuarios: () => paginaTeste([]) } },
        { provide: UsuarioLookupService, useValue: { resolverNome: () => 'Usuário' } }
      ],
      data
    );
  /** Última consulta da tabela (as contagens dos atalhos usam `size: 1`). */
  const ultimaConsulta = () => [...almox['listarSolicitacoes'].mock.calls].reverse().find(([p]) => p.size !== 1)![0];

  beforeEach(() => {
    TestBed.resetTestingModule();
    almox = {
      listarSolicitacoes: vi.fn(() => paginaTeste([solicitacao])),
      listarEstoquesAtivos: vi.fn(() => paginaTeste([{ id: 'est-1', nomeIdentificador: 'ESTOQUE CENTRAL' }])),
      listarMateriais: vi.fn(() => paginaTeste([])),
      buscarConsumoAnterior: vi.fn(() => of({ idSolicitacao: 's', idEstoque: 'e', consultadoAte: '2026-10-01T00:00:00Z', itens: [] })),
      buscarSolicitacaoPorId: vi.fn(() =>
        of({
          id: solicitacao.id,
          status: 'ENTREGUE',
          dataSolicitacao: '2026-09-10T10:00:00Z',
          idRequisitante: 'u1',
          estoqueOrigem: { id: 'est-1', nomeIdentificador: 'ESTOQUE CENTRAL', tipoEstoque: { nome: 'FÍSICO' } },
          estoqueDestino: { id: 'est-2', nomeIdentificador: 'ESTOQUE LOCAL', tipoEstoque: { nome: 'CAMPO' } },
          itens: [
            { id: 'i1', quantidadeEntregue: 5, material: { codigo: 'M-1', descricao: 'Cabo' } },
            { id: 'i2', quantidadeEntregue: 0, material: { codigo: 'M-2', descricao: 'Poste' } }
          ]
        })
      ),
      estornarParcial: vi.fn(() => of(undefined)),
      listarMotivosPendencia: vi.fn(() => of([])),
      listarMotivosRejeicaoAtivos: vi.fn(() => paginaTeste([])),
      listarSaldosSeriais: vi.fn(() => paginaTeste([])),
      obterKanban: vi.fn(() => of({ colunas: [] }))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados do contexto comercial, mais recentes primeiro, com prazo de saque', async () => {
    const { el } = await abrir();
    expect(ultimaConsulta()).toEqual({ contexto: 'COMERCIAL', page: 0, size: 25, sort: 'dataSolicitacao,desc' });
    expect(el.textContent).toContain('#abcdef12');
    expect(el.textContent).toContain('Fora do prazo de 48h');
    expect(el.textContent).toContain('ESTOQUE CENTRAL');
  });

  it('filtros da URL viram a consulta com chips nomeados; ordenação por origem vai para o backend', async () => {
    const { el } = await abrir('/lista?estoqueOrigem=est-1&status=ENTREGUE,ESTORNADO&ordem=origem,asc');
    expect(ultimaConsulta()).toMatchObject({ idEstoqueOrigem: 'est-1', statuses: ['ENTREGUE', 'ESTORNADO'], sort: 'estoqueOrigem.nomeIdentificador,asc' });
    expect(chipsDoPainel(el)).toEqual(expect.arrayContaining(['Origem: ESTOQUE CENTRAL', 'Status: 2 selecionados']));
  });

  it('modo kanban mostra o quadro no lugar da tabela', async () => {
    const { el } = await abrir('/lista', { modo: 'kanban' });
    expect(el.querySelector('app-solicitacoes-comerciais-kanban')).not.toBeNull();
    expect(el.querySelector('ces-data-table')).toBeNull();
  });

  it('estorno parcial: só itens entregues, todos selecionados; envia as quantidades após confirmar', async () => {
    const { componente } = await abrir();
    vi.spyOn(TestBed.inject(CesConfirmationService), 'confirm').mockResolvedValue(true);
    const tela = componente as unknown as {
      confirmarEstorno(s: unknown): void;
      estornoItens(): { id: string; quantidadeEstorno: number }[];
      estornoSelecionados(): unknown[];
      confirmarEstornoParcial(): Promise<void>;
      estornoId(): string | null;
    };
    tela.confirmarEstorno(solicitacao);
    expect(tela.estornoItens().map((i) => i.id)).toEqual(['i1']);
    expect(tela.estornoSelecionados()).toHaveLength(1);
    tela.estornoItens()[0].quantidadeEstorno = 2;
    await tela.confirmarEstornoParcial();
    expect(almox['estornarParcial']).toHaveBeenCalledWith(solicitacao.id, { itens: [{ idItemSolicitacao: 'i1', quantidade: 2 }] });
    expect(tela.estornoId()).toBeNull();
  });

  it('linha abre o detalhe rápido no painel lateral, sem sair da lista', async () => {
    const { componente, harness } = await abrir();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    (componente as unknown as { abrirDetalhe(s: unknown): void }).abrirDetalhe(solicitacao);
    harness.detectChanges();
    const tela = componente as unknown as { detalheAberto(): boolean; detalheId(): string | null };
    expect(tela.detalheAberto()).toBe(true);
    expect(tela.detalheId()).toBe(solicitacao.id);
    expect(navegar).not.toHaveBeenCalled();
  });

  it('atalhos mostram a contagem de cada status no recorte e somam status ao clicar', async () => {
    almox['listarSolicitacoes'].mockImplementation((p: { statuses?: string[]; size: number }) =>
      p.size === 1 ? paginaTeste([], p.statuses?.[0] === 'PENDENTE' ? 3 : 1) : paginaTeste([solicitacao])
    );
    const { el, harness } = await abrir();
    const atalhos = () => Array.from(el.querySelectorAll<HTMLButtonElement>('.sol-atalhos .ces-quick-filter'));
    expect(atalhos()[0].textContent).toContain('Todas');
    expect(atalhos()[0].textContent).toContain('8');
    expect(atalhos()[1].textContent).toContain('Em aberto');
    expect(atalhos()[1].textContent).toContain('5');

    atalhos().find((b) => b.textContent!.includes('Aguardando aprovação'))!.click();
    harness.detectChanges();
    atalhos().find((b) => b.textContent!.includes('Entregue parcial'))!.click();
    harness.detectChanges();
    await harness.fixture.whenStable();
    expect(ultimaConsulta()).toMatchObject({ statuses: ['PENDENTE', 'ENTREGUE_PARCIAL'] });
    // As contagens ignoram os atalhos: o número de cada status não some ao filtrar.
    const contagens = almox['listarSolicitacoes'].mock.calls.filter(([p]) => p.size === 1).map(([p]) => p.statuses);
    expect(contagens).toContainEqual(['ENTREGUE']);

    atalhos()[1].click();
    harness.detectChanges();
    expect(ultimaConsulta().statuses).toEqual(['PENDENTE', 'APROVADO', 'ENTREGUE_PARCIAL']);
    expect(atalhos()[1].getAttribute('aria-pressed')).toBe('true');
  });

  it('trocar de visualização leva o recorte junto na URL', async () => {
    const { componente } = await abrir('/lista?status=PENDENTE');
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    (componente as unknown as { trocarVisao(v: string): void }).trocarVisao('calendario');
    expect(navegar).toHaveBeenCalledWith(['/almoxarifado/solicitacoes/calendario'], { queryParams: expect.objectContaining({ status: 'PENDENTE' }) });
  });

  it('modo calendário mostra o calendário no lugar da tabela', async () => {
    const { el } = await abrir('/lista', { modo: 'calendario' });
    expect(el.querySelector('app-solicitacoes-comerciais-calendario')).not.toBeNull();
    expect(el.querySelector('ces-data-table')).toBeNull();
  });

  it('"Analisar" do quadro e do detalhe abre o Copiloto ao lado, com foco no que foi pedido', async () => {
    const { componente } = await abrir('/lista', { modo: 'kanban' });
    const perguntar = vi.spyOn(TestBed.inject(CopilotoPainelService), 'perguntarSobre').mockImplementation(() => undefined);
    const tela = componente as unknown as {
      analisarEtapa(e: unknown): void;
      analisarSolicitacao(p: unknown): void;
      detalheAberto: { set(v: boolean): void; (): boolean };
      contextoCopiloto(): { id: string; datasets?: string[] };
    };
    tela.analisarEtapa({ id: 'ENTREGA', titulo: 'Entrega', pergunta: 'Gargalos?' });
    expect(perguntar).toHaveBeenCalledWith({ tipo: 'secao', id: 'quadro.ENTREGA', rotulo: 'Etapa Entrega' }, 'Gargalos?');

    tela.detalheAberto.set(true);
    tela.analisarSolicitacao({ id: 'abc', rotulo: 'Solicitação #abc', pergunta: 'Status?' });
    expect(tela.detalheAberto()).toBe(false);
    expect(perguntar).toHaveBeenLastCalledWith({ tipo: 'secao', id: 'solicitacao.abc', rotulo: 'Solicitação #abc' }, 'Status?');
    expect(tela.contextoCopiloto()).toMatchObject({ id: 'almoxarifado.solicitacoes-comerciais', datasets: expect.arrayContaining(['solicitacoes']) });
  });
});
