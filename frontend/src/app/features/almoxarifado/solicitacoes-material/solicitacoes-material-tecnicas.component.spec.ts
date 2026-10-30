import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { SolicitacaoMaterialSummaryResponse } from '../../../core/models/solicitacao-material-tecnica.models';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { AuthService } from '../../../core/services/auth.service';
import { ConnectivityService } from '../../../core/services/connectivity.service';
import { ObrasService } from '../../../core/services/obras.service';
import { RhService } from '../../../core/services/rh.service';
import { SolicitacaoMaterialTecnicaService } from '../../../core/services/solicitacao-material-tecnica.service';
import { UsuarioLookupService } from '../../../core/services/usuario-lookup.service';
import { abrirListagem, chipsDoPainel, paginaTeste } from '../../../shared/listagem/listagem-teste';
import { SolicitacoesMaterialTecnicasComponent } from './solicitacoes-material-tecnicas.component';

const solicitacao = {
  id: 'abcdef12-0000-0000-0000-000000000000',
  idObra: 'obra-1',
  tipoObra: 'OBRA',
  tipo: 'ADICAO_COMUM',
  idEquipe: 'eq-1',
  dataSolicitacao: '2026-09-10T10:00:00Z',
  dataPrevista: '2026-09-20',
  statusSeparacao: 'PRONTO_PARA_SEPARACAO',
  status: 'APROVADO',
  numeroReserva: 'R-778',
  nomeAgenteReserva: 'Carlos',
  quantidadeItens: 3,
  atrasada: true
} as unknown as SolicitacaoMaterialSummaryResponse;

describe('SolicitacoesMaterialTecnicasComponent', () => {
  let listar: ReturnType<typeof vi.fn>;
  let online: ReturnType<typeof signal<boolean>>;
  const abrir = (url = '/lista', data: Record<string, unknown> = {}) =>
    abrirListagem(
      SolicitacoesMaterialTecnicasComponent,
      'lista',
      url,
      [
        { provide: SolicitacaoMaterialTecnicaService, useValue: { listar, obterKanban: vi.fn(() => of({ colunas: [] })) } },
        {
          provide: AlmoxarifadoService,
          useValue: { listarEstoquesAtivos: () => paginaTeste([{ id: 'alm-1', nomeIdentificador: 'ALMOX CENTRAL' }]), listarMateriais: () => paginaTeste([]) }
        },
        {
          provide: ObrasService,
          useValue: {
            listarObras: () => paginaTeste([
              { id: 'obra-1', numero: '1228694', titulo: 'REDE NOVA', ativo: true, processoNome: 'OBRAS' },
              { id: 'obra-2', numero: '9999999', titulo: 'COMERCIAL', ativo: true, processoNome: 'COMERCIAL' }
            ]),
            listarAgentesAtivos: () => of([])
          }
        },
        { provide: RhService, useValue: { listarEquipes: () => paginaTeste([{ id: 'eq-1', codigo: 'EQ-01' }]) } },
        { provide: AuthService, useValue: { currentUser: () => ({ permissoes: ['estoque:solicitacao:create', 'estoque:solicitacao:import'] }) } },
        { provide: ConnectivityService, useValue: { isOnline: online } },
        { provide: UsuarioLookupService, useValue: { resolverNome: () => 'Usuário' } }
      ],
      data
    );
  const ultimaConsulta = () => listar.mock.lastCall![0];

  beforeEach(() => {
    TestBed.resetTestingModule();
    listar = vi.fn(() => paginaTeste([solicitacao]));
    online = signal(true);
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados do contexto técnico, mais recentes primeiro, com obra e etapas', async () => {
    const { el } = await abrir();
    expect(ultimaConsulta()).toMatchObject({ contexto: 'TECNICO', page: 0, size: 25, sort: 'dataSolicitacao,desc', statuses: [] });
    expect(el.textContent).toContain('1228694 · REDE NOVA');
    expect(el.textContent).toContain('Planejada');
    expect(el.textContent).toContain('Atrasada');
    expect(el.textContent).toContain('R-778');
    expect(el.textContent).toContain('#abcdef12');
  });

  it('filtros da URL viram a consulta e chips nomeados; nº de série não vai para a URL', async () => {
    const { el, componente } = await abrir('/lista?obra=obra-1&status=PENDENTE,APROVADO&periodo=2026-09-01~2026-09-30&ordem=planejamento,asc');
    expect(ultimaConsulta()).toMatchObject({
      idObra: 'obra-1',
      statuses: ['PENDENTE', 'APROVADO'],
      dataInicio: '2026-09-01',
      dataFim: '2026-09-30',
      sort: 'dataPrevistaRetirada,asc'
    });
    expect(chipsDoPainel(el)).toEqual(expect.arrayContaining(['Obra: 1228694 · REDE NOVA', 'Status: 2 selecionados']));
    const tela = componente as unknown as { lista: { set(k: string, v: unknown): void } };
    tela.lista.set('numSerie', 'SN-1');
    await new Promise((r) => setTimeout(r));
    expect(ultimaConsulta()).toMatchObject({ numSerie: 'SN-1' });
    expect(TestBed.inject(Router).url).not.toContain('SN-1');
  });

  it('só obras do processo OBRAS entram nas opções', async () => {
    const { componente } = await abrir();
    const tela = componente as unknown as { obraOptions(): { value: string }[] };
    expect(tela.obraOptions().map((o) => o.value)).toEqual(['obra-1']);
  });

  it('modo kanban mostra o quadro no lugar da tabela; alternar navega entre as rotas', async () => {
    const { el, componente } = await abrir('/lista', { modo: 'kanban' });
    expect(el.querySelector('app-solicitacoes-tecnicas-kanban')).not.toBeNull();
    expect(el.querySelector('ces-data-table')).toBeNull();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    (componente as unknown as { alternarModoExibicao(): void }).alternarModoExibicao();
    expect(navegar).toHaveBeenCalledWith(['/almoxarifado/solicitacoes-tecnicas']);
  });

  it('sem conexão mostra o aviso; linha abre o detalhe', async () => {
    online.set(false);
    const { el, componente } = await abrir();
    expect(el.textContent).toContain('Sem conexão');
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    (componente as unknown as { abrirDetalhe(s: unknown): void }).abrirDetalhe(solicitacao);
    expect(navegar).toHaveBeenCalledWith(['/almoxarifado/solicitacoes-tecnicas', solicitacao.id]);
  });
});
