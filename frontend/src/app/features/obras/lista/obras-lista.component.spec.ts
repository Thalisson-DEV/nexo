import { HttpResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { CesConfirmationService } from '@ces/ui';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ImportacaoStatus, ObraResumoResponse } from '../../../core/models/obras.models';
import { AuthService } from '../../../core/services/auth.service';
import { ObrasService } from '../../../core/services/obras.service';
import { RhService } from '../../../core/services/rh.service';
import { abrirListagem, chipsDoPainel, paginaTeste } from '../../../shared/listagem/listagem-teste';
import { ObraImportRow, ObrasListComponent } from './obras-lista.component';

const obra: ObraResumoResponse = {
  id: 'obra-1',
  numero: '1228694',
  titulo: 'SUBESTAÇÃO CENTRAL - REFORMA ALIMENTADOR 02',
  notaObra: 'NOT-2026-8812',
  valorProjeto: 185000,
  dataInicio: '01/03/2026',
  dataPrevisaoFim: '30/09/2026',
  statusId: 'st-1',
  statusNome: 'EM EXECUÇÃO',
  statusConcluida: false,
  processoNome: 'DISTRIBUIÇÃO MT/BT',
  baseOperacionalNome: 'BASE JUAZEIRO',
  municipioNome: 'JUAZEIRO - BA',
  obraPaiNumero: '1228000',
  totalEquipes: 2,
  totalSubprojetos: 1,
  totalAnexos: 3,
  ativo: true
} as ObraResumoResponse;

describe('ObrasListComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  let permissoes: string[];
  const abrir = (url = '/lista') =>
    abrirListagem(ObrasListComponent, 'lista', url, [
      { provide: ObrasService, useValue: servico },
      { provide: AuthService, useValue: { currentUser: () => ({ permissoes }) } },
      {
        provide: RhService,
        useValue: {
          listarBasesOperacionaisAtivas: vi.fn(() => paginaTeste([{ id: 'b1', nome: 'BASE JUAZEIRO' }])),
          listarProcessosAtivos: vi.fn(() => paginaTeste([{ id: 'p1', nome: 'DISTRIBUIÇÃO MT/BT' }])),
          listarMunicipios: vi.fn(() => paginaTeste([])),
          listarCentrosFaturamentoAtivos: vi.fn(() => paginaTeste([])),
          listarSupervisoresAtivosMin: vi.fn(() => of([{ id: 'sup-1', nomeColaborador: 'CARLOS SILVA' }])),
          listarCoordenadoresAtivosMin: vi.fn(() => of([{ id: 'coord-1', nomeColaborador: 'MARCOS OLIVEIRA' }]))
        }
      }
    ]);
  const ultimaConsulta = () => servico['listarObras'].mock.lastCall![0];

  beforeEach(() => {
    TestBed.resetTestingModule();
    permissoes = ['obras:obra:read', 'obras:obra:write', 'obras:obra:import', 'obras:obra:export'];
    servico = {
      listarObras: vi.fn(() => paginaTeste([obra])),
      listarStatusAtivos: vi.fn(() => of([
        { id: 'st-1', nome: 'EM EXECUÇÃO', concluida: false, ativo: true },
        { id: 'st-2', nome: 'CONCLUÍDA', concluida: true, ativo: true }
      ])),
      listarAgentesAtivos: vi.fn(() => of([])),
      alterarStatus: vi.fn(() => of({ ...obra, statusNome: 'CONCLUÍDA' })),
      desativarObra: vi.fn(() => of(null)),
      validarImportacao: vi.fn(() => of({ totalProcessados: 1, totalSucesso: 0, totalFalha: 1, registros: [{ linha: 2, numero: 'b-1234567', titulo: '  reforma   de rede ', status: ImportacaoStatus.COM_ERRO, mensagemErro: 'Processo não informado' }] })),
      confirmarImportacao: vi.fn(() => of({ totalProcessados: 1, totalSucesso: 1, totalFalha: 0, registros: [] })),
      exportarResumo: vi.fn(() => of(new HttpResponse({ body: new Blob(['csv']) }))),
      exportarDetalhado: vi.fn(() => of(new HttpResponse({ body: new Blob(['csv']) }))),
      baixarArquivoBlob: vi.fn(),
      baixarModeloCsv: vi.fn(() => of(new Blob()))
    };
  });

  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('abre já nos dados (só ativas, início mais recente primeiro), com status e subprojeto', async () => {
    const { el } = await abrir();
    expect(ultimaConsulta()).toEqual({ ativo: true, apenasPrincipais: false, page: 0, size: 25, sort: 'dataInicio,desc' });
    expect(el.textContent).toContain('SUBESTAÇÃO CENTRAL');
    expect(el.textContent).toContain('EM EXECUÇÃO');
    expect(el.textContent).toContain('subprojeto de 1228000');
    expect(el.textContent).toContain('01/03/2026 → 30/09/2026');
  });

  it('filtros da URL viram parâmetros do backend (listas, datas dd/MM/yyyy e valores) e chips nomeados', async () => {
    const { el } = await abrir('/lista?status=st-1&base=b1&inicio=2026-03-01~2026-03-31&valorMin=1000&principais=true&ordem=valor,asc');
    expect(ultimaConsulta()).toMatchObject({
      statusIds: ['st-1'],
      baseOperacionalIds: ['b1'],
      dataInicioDe: '01/03/2026',
      dataInicioAte: '31/03/2026',
      valorProjetoMin: 1000,
      apenasPrincipais: true,
      sort: 'valorProjeto,asc'
    });
    expect(chipsDoPainel(el)).toEqual(expect.arrayContaining(['Status: EM EXECUÇÃO', 'Base: BASE JUAZEIRO', 'Início: 01/03/2026 a 31/03/2026']));
  });

  it('sem permissão de escrita não há criação nem ações de edição', async () => {
    permissoes = ['obras:obra:read'];
    const { el } = await abrir();
    expect(el.textContent).not.toContain('Nova obra');
    expect(el.querySelector('[aria-label^="Editar obra"]')).toBeNull();
    expect(el.querySelector('[aria-label^="Solicitar materiais"]')).not.toBeNull();
  });

  it('alterar status para um status concluído envia a data efetiva e recarrega', async () => {
    const { componente } = await abrir();
    const tela = componente as unknown as {
      abrirAlteracaoStatus(o: ObraResumoResponse): void;
      novoStatusId: { set(v: string): void };
      novaDataFimEfetiva: { set(v: Date): void };
      statusSelecionadoEhConcluido(): boolean;
      salvarAlteracaoStatus(): void;
      obraStatus(): unknown;
    };
    tela.abrirAlteracaoStatus(obra);
    expect(tela.statusSelecionadoEhConcluido()).toBe(false);
    tela.novoStatusId.set('st-2');
    expect(tela.statusSelecionadoEhConcluido()).toBe(true);
    tela.novaDataFimEfetiva.set(new Date(2026, 8, 15));
    const chamadas = servico['listarObras'].mock.calls.length;
    tela.salvarAlteracaoStatus();
    expect(servico['alterarStatus']).toHaveBeenCalledWith('obra-1', 'st-2', '15/09/2026');
    expect(tela.obraStatus()).toBeNull();
    await new Promise((r) => setTimeout(r));
    expect(servico['listarObras'].mock.calls.length).toBeGreaterThan(chamadas);
  });

  it('desativar pede confirmação; ações de navegação levam aos destinos certos', async () => {
    const { componente } = await abrir();
    const tela = componente as unknown as {
      desativarObra(o: ObraResumoResponse): Promise<void>;
      solicitarMateriais(o: ObraResumoResponse): void;
      editarObra(o: ObraResumoResponse): void;
      visualizarObra(o: ObraResumoResponse): void;
    };
    const confirmar = vi.spyOn(TestBed.inject(CesConfirmationService), 'confirm').mockResolvedValue(false);
    await tela.desativarObra(obra);
    expect(servico['desativarObra']).not.toHaveBeenCalled();
    confirmar.mockResolvedValue(true);
    await tela.desativarObra(obra);
    expect(servico['desativarObra']).toHaveBeenCalledWith('obra-1');

    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    tela.solicitarMateriais(obra);
    expect(navegar).toHaveBeenLastCalledWith(['/almoxarifado/solicitacoes-tecnicas/nova'], { queryParams: { idObra: 'obra-1' } });
    tela.editarObra(obra);
    expect(navegar).toHaveBeenLastCalledWith(['/obras', 'obra-1', 'editar']);
    const abrirAba = vi.spyOn(window, 'open').mockReturnValue(null);
    tela.visualizarObra(obra);
    expect(abrirAba).toHaveBeenCalledWith('/obras/obra-1', '_blank');
  });

  it('exportação leva o recorte atual no formato escolhido', async () => {
    const { componente } = await abrir('/lista?status=st-1&busca=rede');
    const tela = componente as unknown as { exportFormato: { set(v: string): void }; executarExportacao(): void };
    tela.exportFormato.set('detalhado');
    tela.executarExportacao();
    expect(servico['exportarDetalhado']).toHaveBeenCalledWith(expect.objectContaining({ statusIds: ['st-1'], ativo: true }));
    expect(servico['baixarArquivoBlob']).toHaveBeenCalledWith(expect.any(Blob), expect.stringMatching(/^obras_detalhado_\d{4}-\d{2}-\d{2}\.csv$/));

    servico['exportarResumo'] = vi.fn(() => throwError(() => new Error('falhou')));
    tela.exportFormato.set('resumo');
    tela.executarExportacao();
    expect(servico['baixarArquivoBlob']).toHaveBeenCalledTimes(1);
  });

  it('importação: correção normaliza número e título e libera a linha completa; processar confirma', async () => {
    const { componente } = await abrir();
    const tela = componente as unknown as {
      importacao: { enviarArquivo(f: File): void; linhas(): ObraImportRow[]; processar(): Promise<void> };
      finishImportEdit(l: ObraImportRow): void;
    };
    tela.importacao.enviarArquivo(new File(['x'], 'obras.csv'));
    const [linha] = tela.importacao.linhas();
    linha.editing = true;
    Object.assign(linha, {
      dataInicio: '01/01/2026', dataPrevisaoFim: '01/06/2026', nomeProcesso: 'P', nomeBaseOperacional: 'B',
      nomeMunicipio: 'M', nomeCentroCusto: 'C', nomeSupervisor: 'S', nomeCoordenador: 'K'
    });
    tela.finishImportEdit(linha);
    const corrigida = tela.importacao.linhas()[0];
    expect(corrigida).toMatchObject({ numero: '1234567', titulo: 'REFORMA DE REDE', status: ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, editing: false });
    expect(corrigida.mensagemErro).toBeUndefined();

    vi.spyOn(TestBed.inject(CesConfirmationService), 'confirm').mockResolvedValue(true);
    await tela.importacao.processar();
    expect(servico['confirmarImportacao']).toHaveBeenCalledWith({ registros: [expect.not.objectContaining({ editing: expect.anything() })] });
  });
});
