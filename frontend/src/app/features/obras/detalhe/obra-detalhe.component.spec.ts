import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { CesConfirmationService } from '@ces/ui';
import { ConfirmationService, MenuItem, MessageService } from 'primeng/api';
import { Observable, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ObraResponse } from '../../../core/models/obras.models';
import { AuthService } from '../../../core/services/auth.service';
import { ObrasProgramacaoService } from '../../../core/services/obras-programacao.service';
import { ContratoService } from '../../../core/services/contrato.service';
import { ObrasService } from '../../../core/services/obras.service';
import { RhService } from '../../../core/services/rh.service';
import { SolicitacaoMaterialTecnicaService } from '../../../core/services/solicitacao-material-tecnica.service';
import { ObraDetalheComponent } from './obra-detalhe.component';

const anexo = { id: 'a1', nomeArquivo: 'projeto.pdf', tipoAnexoNome: 'PROJETO', tamanhoBytes: 2048 };

const mockObra = {
  id: 'obra-1',
  numero: '1228694',
  titulo: 'REFORMA ALIMENTADOR 02',
  tipoObra: 'OBRA',
  valorProjeto: 185000,
  dataInicio: '01/03/2026',
  dataPrevisaoFim: '30/09/2099',
  statusId: 'st-1',
  statusNome: 'EM EXECUÇÃO',
  statusConcluida: false,
  processoNome: 'DISTRIBUIÇÃO',
  baseOperacionalNome: 'BASE JUAZEIRO',
  municipioNome: 'JUAZEIRO',
  supervisorNome: 'CARLOS SILVA',
  coordenadorNome: 'MARCOS OLIVEIRA',
  equipes: ['eq-1'],
  subprojetos: [{ id: 'sub-1', numero: '1228695', titulo: 'KIT 1', tipoVinculo: 'KIT', statusNome: 'PLANEJADA', dataInicio: '01/03/2026', dataPrevisaoFim: '30/04/2026', valorProjeto: 1000 }],
  anexos: [anexo],
  carteira: { idContrato: 'c1', classeTensao: 'MT_15', classeTensaoRotulo: '15 kV', prioridade: 'ALTA', lotePrevisto: 'LOTE-01', valorMaterialPrevisto: 60000, valorServicoPrevisto: 90000 },
  ativo: true,
  dataCriacao: '2026-01-01T10:00:00-03:00'
} as unknown as ObraResponse;

describe('ObraDetalheComponent', () => {
  let fixture: ComponentFixture<ObraDetalheComponent>;
  let tela: any;
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  let permissoes: string[];

  async function abrir(obra: ObraResponse = mockObra, busca = vi.fn(() => of(obra) as Observable<ObraResponse>)): Promise<HTMLElement> {
    servico['buscarObraPorId'] = busca;
    await TestBed.configureTestingModule({
      imports: [ObraDetalheComponent],
      providers: [
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        MessageService,
        ConfirmationService,
        { provide: ObrasService, useValue: servico },
        { provide: ContratoService, useValue: { listarContratosAtivos: () => of({ content: [{ id: 'c1', numeroContrato: 'CT-01', cliente: 'Cliente' }] }) } },
        { provide: RhService, useValue: { listarEquipes: vi.fn(() => of({ content: [{ id: 'eq-1', codigo: 'EQ-01', tipoEquipe: { nome: 'LINHA VIVA' }, baseOperacional: { nome: 'BASE JUAZEIRO' } }] })) } },
        { provide: AuthService, useValue: { currentUser: () => ({ permissoes }) } },
        { provide: ObrasProgramacaoService, useValue: { resumos: vi.fn(() => of([])), listarDaObra: vi.fn(() => of([])) } },
        { provide: SolicitacaoMaterialTecnicaService, useValue: { obterProjecaoObra: vi.fn(() => of({ idObra: 'obra-1', tipoObra: 'OBRA', ocorrencias: [], solicitacoes: [] })), obterSaldoObra: vi.fn(() => of({ idEstoque: null, itens: [] })) } },
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: 'obra-1' })) } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(ObraDetalheComponent);
    tela = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    permissoes = ['obras:obra:read', 'obras:obra:write', 'obras:anexos:write'];
    servico = {
      listarStatusAtivos: vi.fn(() => of([{ id: 'st-1', nome: 'EM EXECUÇÃO', concluida: false }, { id: 'st-2', nome: 'CONCLUÍDA', concluida: true }])),
      listarTiposAnexosAtivos: vi.fn(() => of([{ id: 'tipo-1', nome: 'PROJETO' }])),
      listarObras: vi.fn(() => of({ content: [] })),
      informarStatusOperacional: vi.fn(() => of({})),
      informarCarteira: vi.fn(() => of({})),
      listarHistoricoStatus: vi.fn(() => of([{ id: 'h1', statusNovoId: 'st-1', statusNovoNome: 'EM EXECUÇÃO', observacao: 'Equipe mobilizada', usuario: 'ANA', data: new Date().toISOString() }])),
      listarObrasProximas: vi.fn(() => of([])),
      buscarOrcamento: vi.fn(() => of({ idObra: 'obra-1', importado: false, totalServicosImportados: 0, itens: [] })),
      desvincularSubprojeto: vi.fn(() => of({})),
      removerAnexo: vi.fn(() => of(undefined)),
      anexarArquivo: vi.fn(() => of(anexo)),
      baixarAnexo: vi.fn(() => of({ body: new Blob(['x']) })),
      extrairNomeArquivoDeResponse: vi.fn(() => 'projeto.pdf'),
      baixarArquivoBlob: vi.fn()
    };
  });

  it('monta o relatório com identidade, prazo, indicadores, dados e seções', async () => {
    const el = await abrir();
    const texto = el.textContent!;
    expect(servico['buscarObraPorId']).toHaveBeenCalledWith('obra-1');
    expect(el.querySelector('h1')!.textContent).toContain('REFORMA ALIMENTADOR 02');
    expect(texto).toContain('15 kV');
    expect(texto).toContain('Prioridade alta');
    expect(texto).toContain('CT-01 · Cliente');
    expect(texto).toContain('Faltam');
    expect(texto).toContain('60.000,00');
    expect(texto).toContain('CARLOS SILVA');
    expect(texto).toContain('LOTE-01');
    expect(texto).toContain('Equipe mobilizada');
    expect(texto).toContain('EQ-01');
    expect(texto).toContain('projeto.pdf');
    expect(texto).toContain('1228695');
    expect(texto).toContain('Não há orçamento importado');
  });

  it('entrega solicitações e movimentações ao bloco de materiais', async () => {
    await abrir();
    const servicoSolicitacao = TestBed.inject(SolicitacaoMaterialTecnicaService) as unknown as { obterProjecaoObra: ReturnType<typeof vi.fn> };
    servicoSolicitacao.obterProjecaoObra.mockReturnValue(of({
      idObra: 'obra-1',
      tipoObra: 'OBRA',
      ocorrencias: [],
      solicitacoes: [{ id: 'sol-12345678', tipo: 'ADICAO_COMUM', status: 'APROVADO', itens: [] }],
      movimentacoes: [{ idReferencia: 'ref-1', tipo: 'DEVOLUCAO', dataHora: '2026-10-31T11:00:00-03:00', itens: [] }]
    }));

    (tela as unknown as { carregarSolicitacoes(id: string): void }).carregarSolicitacoes('obra-1');
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelectorAll('app-obra-materiais .mat-bloco').length).toBe(2);
  });

  it('mostra os pontos de atenção entre os indicadores e os dados, com a ação que resolve', async () => {
    const semTensao = { ...mockObra, anexos: [], carteira: { ...mockObra.carteira, classeTensao: null, classeTensaoRotulo: null } } as ObraResponse;
    const el = await abrir(semTensao);
    const secao = el.querySelector('.obra-atencao')!;
    expect(secao.textContent).toContain('Classe de tensão não informada');
    expect(el.querySelector('.obra-kpis')!.compareDocumentPosition(secao) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(secao.compareDocumentPosition(el.querySelector('.obra-corpo')!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

    const acao = Array.from(secao.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.includes('Informar tensão'))!;
    acao.click();
    expect(tela.carteiraAberta()).toBe(true);
  });

  it('sem permissão de escrita o insight não oferece a ação e não há menu de atualização', async () => {
    permissoes = ['obras:obra:read'];
    const semTensao = { ...mockObra, carteira: { ...mockObra.carteira, classeTensao: null } } as ObraResponse;
    const el = await abrir(semTensao);
    expect(el.querySelector('.obra-atencao')!.textContent).toContain('Classe de tensão não informada');
    expect(el.querySelector('.obra-atencao')!.textContent).not.toContain('Informar tensão');
    expect(el.querySelector('.obra-acoes')!.textContent).not.toContain('Atualizar obra');
    expect(el.querySelector('.obra-acoes')!.textContent).toContain('SOLICITAR MATERIAIS');
  });

  it('as ações ficam na barra flutuante e "Atualizar obra" reúne o que muda a obra', async () => {
    const el = await abrir();
    const barra = el.querySelector('.obra-acoes')!;
    expect(barra.textContent).toContain('VOLTAR');
    expect(barra.textContent).toContain('IMPRIMIR');
    expect(barra.textContent).toContain('Atualizar obra');
    const itens = tela.acoesAtualizar().map((i: MenuItem) => i.label);
    expect(itens).toEqual(['Registrar status', 'Editar carteira', 'Editar cadastro e equipes', 'Adicionar anexo', 'Vincular subprojeto']);
  });

  it('obra concluída não oferece solicitar materiais nem vincular subprojeto', async () => {
    const el = await abrir({ ...mockObra, statusConcluida: true, dataFimEfetiva: '01/06/2026' } as ObraResponse);
    expect(el.querySelector('.obra-acoes')!.textContent).not.toContain('SOLICITAR MATERIAIS');
    expect(tela.acoesAtualizar().map((i: MenuItem) => i.label)).not.toContain('Vincular subprojeto');
    expect(el.textContent).toContain('Concluída em 01/06/2026');
  });

  it('registra o status com a observação e recarrega sem esconder a página', async () => {
    await abrir();
    tela.abrirStatus();
    expect(tela.novoStatusId()).toBe('st-1');
    tela.observacaoStatus.set('  Aguardando desligamento  ');
    tela.salvarStatus();
    expect(servico['informarStatusOperacional']).toHaveBeenCalledWith('obra-1', { idStatusObra: 'st-1', observacao: 'Aguardando desligamento', dataFimEfetiva: null });
    expect(tela.statusAberto()).toBe(false);
    expect(tela.carregando()).toBe(false);
    expect(servico['buscarObraPorId']).toHaveBeenCalledTimes(2);
  });

  it('edita a carteira partindo dos dados atuais', async () => {
    await abrir();
    tela.abrirCarteira();
    expect(tela.carteiraAlterada()).toBe(false);
    tela.cLote.set('LOTE-02');
    expect(tela.carteiraAlterada()).toBe(true);
    tela.salvarCarteira();
    expect(servico['informarCarteira']).toHaveBeenCalledWith('obra-1', expect.objectContaining({ idContrato: 'c1', classeTensao: 'MT_15', lotePrevisto: 'LOTE-02', valorProjeto: 185000 }));
  });

  it('remover anexo e desvincular subprojeto pedem confirmação', async () => {
    await abrir();
    const confirmar = vi.spyOn(TestBed.inject(CesConfirmationService), 'confirm').mockResolvedValue(false);
    await tela.removerAnexo(anexo);
    await tela.desvincular(mockObra.subprojetos[0]);
    expect(confirmar).toHaveBeenCalledTimes(2);
    expect(servico['removerAnexo']).not.toHaveBeenCalled();
    expect(servico['desvincularSubprojeto']).not.toHaveBeenCalled();
  });

  it('pré-visualiza o anexo e libera a URL ao fechar', async () => {
    await abrir();
    const criar = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x');
    const revogar = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    tela.visualizarAnexo(anexo);
    expect(tela.previewAberto()).toBe(true);
    expect(tela.previewTipo()).toBe('pdf');
    expect(criar).toHaveBeenCalled();
    tela.fecharPreview();
    expect(revogar).toHaveBeenCalledWith('blob:x');
    expect(tela.previewUrl()).toBeNull();
  });

  it('erro ao carregar mostra o estado de erro em vez de sair da tela', async () => {
    const el = await abrir(mockObra, vi.fn(() => throwError(() => new Error('falhou'))));
    expect(el.textContent).toContain('Não foi possível abrir a obra');
  });
});
