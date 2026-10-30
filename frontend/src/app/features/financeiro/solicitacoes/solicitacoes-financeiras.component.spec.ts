import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { NotificationService } from '@ces/ui';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { SolicitacaoFinanceira } from '../../../core/models/financeiro.models';
import { AuthService } from '../../../core/services/auth.service';
import { FinanceiroService } from '../../../core/services/financeiro.service';
import { RhService } from '../../../core/services/rh.service';
import { abrirListagem, chipsDoPainel, paginaTeste } from '../../../shared/listagem/listagem-teste';
import { SolicitacoesFinanceirasComponent } from './solicitacoes-financeiras.component';

const cadastrada = {
  id: 'sol-001',
  numero: 1042,
  status: 'SOLICITADO',
  formulario: { id: 'f1', tipoSolicitacao: 'COMPRA', codigo: 'COMPRA_PADRAO', titulo: 'Solicitação de Compra', padrao: true, ativo: true },
  valores: { descricao: { valor: 'Papel A4 e canetas', exibicao: 'Papel A4 e canetas' } },
  valor: 580.5,
  fornecedor: { id: 'forn-001', nome: 'Papelaria Central', razaoSocial: 'Papelaria Central Comércio Ltda.', cadastrado: true },
  usuarioSolicitanteId: 'user-001',
  usuarioSolicitante: 'joao.silva',
  nomeColaboradorSolicitante: 'João Silva',
  desfecho: {},
  documentos: [],
  acoes: { podeEditar: true, podeSubmeter: false, podeDecidir: true, podeCancelar: true, podeDesfazerDecisao: false },
  ativo: true,
  dataCriacao: '2026-08-20T09:00:00Z'
} as unknown as SolicitacaoFinanceira;

const livre = {
  ...cadastrada,
  id: 'sol-002',
  numero: 1043,
  status: 'APROVADO',
  formulario: { ...cadastrada.formulario, tipoSolicitacao: 'PAGAMENTO' },
  fornecedor: { id: null, nome: 'Chaveiro do Zé', razaoSocial: null, cadastrado: false },
  acoes: { podeEditar: false, podeSubmeter: false, podeDecidir: false, podeCancelar: false, podeDesfazerDecisao: false }
} as unknown as SolicitacaoFinanceira;

const csv = (nome: string) =>
  of(new HttpResponse({ body: new Blob(['csv']), headers: new HttpHeaders({ 'content-disposition': `attachment; filename="${nome}"` }) }));

describe('SolicitacoesFinanceirasComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  let permissoes: string[];
  const abrir = (url = '/lista', data: Record<string, unknown> = { tipo: 'COMPRA', modo: 'minhas' }) =>
    abrirListagem(
      SolicitacoesFinanceirasComponent,
      'lista',
      url,
      [
        { provide: FinanceiroService, useValue: servico },
        { provide: AuthService, useValue: { currentUser: () => ({ permissoes }) } },
        { provide: LookupStore, useValue: { searchCollaborators: vi.fn(() => of({ items: [], nextCursor: null, hasMore: false })) } },
        { provide: RhService, useValue: { buscarColaboradorPorId: vi.fn(() => of({ id: 'c1', nomeCompleto: 'Maria Souza' })) } }
      ],
      data
    );
  const ultimaConsulta = () => servico['listarSolicitacoes'].mock.lastCall!;

  beforeEach(() => {
    TestBed.resetTestingModule();
    permissoes = ['financeiro:compra:read', 'financeiro:pagamento:read'];
    servico = {
      listarSolicitacoes: vi.fn(() => paginaTeste([cadastrada, livre])),
      buscarOpcoesReferencia: vi.fn(() => paginaTeste([])),
      exportarSolicitacoesResumo: vi.fn(() => csv('solicitacoes_compra_resumo.csv')),
      exportarSolicitacoesDetalhado: vi.fn(() => csv('solicitacoes_pagamento_detalhado.csv'))
    };
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('"minhas" abre já nos dados, só ativos, mais recentes primeiro, com o fornecedor', async () => {
    const { el } = await abrir();
    expect(ultimaConsulta()).toEqual(['COMPRA', { ativo: true, apenasMinhas: true, page: 0, size: 25, sort: 'dataCriacao,desc' }]);
    expect(el.textContent).toContain('Minhas solicitações – compras');
    expect(el.textContent).toContain('Papelaria Central Comércio Ltda.');
    expect(el.textContent).toContain('Chaveiro do Zé');
    expect(el.textContent).toContain('Não cadastrado');
    expect(chipsDoPainel(el)).toEqual(['Situação: Ativos']);
  });

  it('fila de aprovação fixa SOLICITADO, mais antigas primeiro, e não oferece status nem criação', async () => {
    const { el } = await abrir('/lista', { tipo: 'PAGAMENTO', modo: 'aprovacao' });
    expect(ultimaConsulta()).toEqual(['PAGAMENTO', expect.objectContaining({ status: 'SOLICITADO', sort: 'dataCriacao,asc' })]);
    expect(el.textContent).toContain('Fila de aprovação – pagamentos');
    expect(el.textContent).not.toContain('Novo pagamento');
  });

  it('links do dashboard (status, datas e tipo) aplicam o recorte com limites de data inclusivos', async () => {
    const { el } = await abrir('/lista?tipo=PAGAMENTO&status=APROVADO&dataCriacaoInicio=2026-08-01&vencimentoFim=2026-08-31', { tipo: 'AMBOS', modo: 'todas' });
    const [tipo, params] = ultimaConsulta();
    expect(tipo).toBe('PAGAMENTO');
    expect(params).toMatchObject({ status: 'APROVADO', vencimentoFim: '2026-08-31' });
    expect(params.dataCriacaoInicio).toMatch(/^2026-08-01T00:00:00(\.000)?[+-]\d{2}:\d{2}$/);
    expect(params).not.toHaveProperty('tipo');
    expect(el.textContent).toContain('Gestão de pagamentos');
    expect(chipsDoPainel(el)).toContain('Status: Aprovado');
  });

  it('status inválido na URL é ignorado; ordenação por fornecedor vai para o backend', async () => {
    await abrir('/lista?status=INEXISTENTE&ordem=fornecedor,asc', { tipo: 'COMPRA', modo: 'todas' });
    expect(ultimaConsulta()[1]).not.toHaveProperty('status');
    expect(ultimaConsulta()[1]).toMatchObject({ sort: 'fornecedor,asc' });
  });

  it('busca preserva PIX e linha digitável e ignora só espaços', async () => {
    await abrir('/lista?buscaGlobal=' + encodeURIComponent('chave+pix@x.com'));
    expect(ultimaConsulta()[1]).toMatchObject({ buscaGlobal: 'chave+pix@x.com' });
    TestBed.resetTestingModule();
    await abrir('/lista?buscaGlobal=%20%20');
    expect(ultimaConsulta()[1]).not.toHaveProperty('buscaGlobal');
  });

  it('erro de carga aparece no estado da tabela', async () => {
    servico['listarSolicitacoes'] = vi.fn(() => throwError(() => ({ status: 500, error: { detail: 'Falha no servidor' } })));
    const { el } = await abrir();
    expect(el.textContent).toContain('Não foi possível carregar as solicitações');
  });

  it('linha abre o detalhe, editar só quando permitido, novo vai para a criação', async () => {
    const { componente, el } = await abrir();
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const tela = componente as unknown as { abrir(s: unknown): void; editar(s: unknown): void; novo(): void };
    tela.abrir(livre);
    expect(navegar).toHaveBeenLastCalledWith(['/financeiro', 'pagamentos', 'sol-002']);
    tela.editar(cadastrada);
    expect(navegar).toHaveBeenLastCalledWith(['/financeiro', 'compras', 'sol-001', 'editar']);
    tela.novo();
    expect(navegar).toHaveBeenLastCalledWith(['/financeiro', 'compras', 'nova']);
    expect(el.querySelectorAll('[aria-label^="Editar solicitação"]').length).toBe(1);
  });

  it('exportação leva o recorte atual e só remove campos exclusivos de pagamento quando é compra', async () => {
    const { componente } = await abrir('/lista?valorMinimo=100&possuiCompraPrevia=true', { tipo: 'PAGAMENTO', modo: 'todas' });
    const notificacoes = TestBed.inject(NotificationService);
    const sucesso = vi.spyOn(notificacoes, 'success');
    const tela = componente as unknown as {
      abrirExportacao(): void;
      exportTipo: { (): string; set(v: string): void };
      exportFormato: { set(v: string): void };
      exportar(): void;
    };
    tela.abrirExportacao();
    expect(tela.exportTipo()).toBe('PAGAMENTO');
    tela.exportFormato.set('detalhado');
    tela.exportar();
    expect(servico['exportarSolicitacoesDetalhado']).toHaveBeenCalledWith('PAGAMENTO', expect.objectContaining({ valorMinimo: 100, possuiCompraPrevia: true }));
    expect(sucesso).toHaveBeenCalled();
    tela.exportTipo.set('COMPRA');
    tela.exportFormato.set('resumo');
    tela.exportar();
    const filtros = servico['exportarSolicitacoesResumo'].mock.lastCall![1];
    expect(filtros).toMatchObject({ valorMinimo: 100 });
    expect(filtros).not.toHaveProperty('possuiCompraPrevia');
    expect(filtros).not.toHaveProperty('page');
  });

  it('erro na exportação vira toast; sem permissão não há exportação', async () => {
    servico['exportarSolicitacoesResumo'] = vi.fn(() =>
      throwError(() => new Error('Falha de conexão com o servidor'))
    );
    const { componente } = await abrir();
    const erro = vi.spyOn(TestBed.inject(NotificationService), 'error');
    const tela = componente as unknown as { abrirExportacao(): void; exportar(): void };
    tela.abrirExportacao();
    tela.exportar();
    await vi.waitFor(() => expect(erro).toHaveBeenCalledWith('Falha de conexão com o servidor'));

    TestBed.resetTestingModule();
    permissoes = [];
    const { el } = await abrir();
    expect(el.textContent).not.toContain('Exportar CSV');
  });
});
