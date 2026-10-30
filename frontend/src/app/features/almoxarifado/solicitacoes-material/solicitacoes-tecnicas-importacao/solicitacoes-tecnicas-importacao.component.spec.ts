import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { CesConfirmationService, NotificationService } from '@ces/ui';
import { ConfirmationService } from 'primeng/api';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { SolicitacaoMaterialTecnicaService } from '../../../../core/services/solicitacao-material-tecnica.service';
import { SolicitacoesTecnicasImportacaoComponent } from './solicitacoes-tecnicas-importacao.component';

/* eslint-disable @typescript-eslint/no-explicit-any */
describe('SolicitacoesTecnicasImportacaoComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  let tela: any;
  let notificacoes: { error: ReturnType<typeof vi.fn>; warning: ReturnType<typeof vi.fn>; success: ReturnType<typeof vi.fn> };
  const linha = (extra: Record<string, unknown> = {}): any => ({
    linha: 2,
    numeroObra: 'OBRA-01',
    almoxarifado: 'ALMOX-01',
    codigoEquipe: 'EQ-01',
    tipoMovimentacao: '221',
    codigoMaterial: 'MAT-01',
    quantidade: 10,
    status: 'AGUARDANDO_PROCESSAMENTO',
    ...extra
  });
  const previa = (extra: Record<string, unknown> = {}) => ({
    token: 'tok-1',
    hashArquivo: 'h',
    expiraEm: '2026-12-31T23:59:59Z',
    totalLinhas: 1,
    totalConfirmaveis: 1,
    totalErros: 0,
    totalAvisos: 0,
    errosPorLinha: [],
    paresDisponiveis: [{ chave: 'OBRA-01', tipo: '221' }],
    ...extra
  });

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      validarImportacao: vi.fn(() => of(previa())),
      confirmarImportacao: vi.fn(),
      baixarModelo: vi.fn()
    };
    notificacoes = { error: vi.fn(), warning: vi.fn(), success: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideNoopAnimations(),
        ConfirmationService,
        { provide: SolicitacaoMaterialTecnicaService, useValue: servico },
        { provide: NotificationService, useValue: notificacoes }
      ]
    });
    const fixture = TestBed.createComponent(SolicitacoesTecnicasImportacaoComponent);
    fixture.componentRef.setInput('obras', []);
    fixture.detectChanges();
    tela = fixture.componentInstance;
  });

  it('valida o arquivo e monta a prévia com todas as combinações selecionadas', async () => {
    const arquivo = new File(['Numero Obra;Ocorrencia\nOBRA-01;;ALMOX-01;EQ-01;;;221;MAT-01;10'], 'tecnicas.csv', { type: 'text/csv' });
    await tela.onFileSelected(arquivo);
    expect(servico['validarImportacao']).toHaveBeenCalledWith(arquivo);
    expect(tela.previewData()).toEqual(previa());
    expect(tela.selectedPares()).toEqual([{ chave: 'OBRA-01', tipo: '221' }]);
  });

  it('linha inválida sem alteração continua com erro', () => {
    const row = linha({ status: 'COM_ERRO', mensagemErro: 'Material não encontrado no cadastro' });
    tela.registrosImport.set([row]);
    tela.startImportEdit(row);
    tela.finishImportEdit(row);
    expect(row).toMatchObject({ editing: false, status: 'COM_ERRO', mensagemErro: 'Material não encontrado no cadastro' });
  });

  it('corrigir a linha revalida no servidor e recalcula os pares', () => {
    const row = linha({ codigoMaterial: 'MAT-INVALIDO', status: 'COM_ERRO', mensagemErro: 'Material inválido' });
    tela.registrosImport.set([row]);
    tela.previewData.set(previa({ totalConfirmaveis: 0, totalErros: 1, paresDisponiveis: [] }));
    tela.startImportEdit(row);
    row.codigoMaterial = 'MAT-01';
    tela.finishImportEdit(row);
    expect(servico['validarImportacao']).toHaveBeenCalledWith(expect.any(File));
    expect(tela.registrosImport()[0].status).toBe('AGUARDANDO_PROCESSAMENTO');
    expect(tela.previewData()).toMatchObject({ totalConfirmaveis: 1, totalErros: 0, paresDisponiveis: [{ chave: 'OBRA-01', tipo: '221' }] });
  });

  it('obrigatórios em branco marcam erro', () => {
    const row = linha({ numeroObra: '', quantidade: 0, editing: true });
    tela.registrosImport.set([row]);
    tela.finishImportEdit(row);
    expect(row.status).toBe('COM_ERRO');
    expect(row.mensagemErro).toContain('obrigatório');
  });

  it('remover uma linha revalida o restante; filtro mostra só as com erro', () => {
    const ok = linha();
    const erro = linha({ linha: 3, numeroObra: 'OBRA-02', status: 'COM_ERRO', mensagemErro: 'Material inválido' });
    tela.registrosImport.set([ok, erro]);
    tela.filtroApenasErros.set(true);
    expect(tela.registrosExibidosImport()).toEqual([erro]);
    tela.removeImportRecord(erro);
    expect(tela.registrosImport()).toHaveLength(1);
    expect(servico['validarImportacao']).toHaveBeenCalled();
  });

  it('trocas de cadastro na correção preenchem os ids', () => {
    const fixture = TestBed.createComponent(SolicitacoesTecnicasImportacaoComponent);
    fixture.componentRef.setInput('obras', [{ id: 'obra-id-1', numero: 'OB-100', titulo: 'Obra 100' }]);
    fixture.componentRef.setInput('almoxarifados', [{ id: 'almox-id-1', nomeIdentificador: 'ALM-A' }]);
    fixture.componentRef.setInput('equipes', [{ id: 'eq-id-1', codigo: 'EQ-ALPHA' }]);
    fixture.componentRef.setInput('materiaisList', [{ id: 'mat-id-1', codigo: 'M-001', descricao: 'Cabo de Cobre' }]);
    fixture.componentRef.setInput('agentesList', [{ id: 'ag-id-1', nome: 'Agente Carlos' }]);
    const c = fixture.componentInstance as any;
    const row: any = { linha: 2 };
    c.onObraChange(row, 'OB-100');
    c.onAlmoxarifadoChange(row, 'ALM-A');
    c.onEquipeChange(row, 'EQ-ALPHA');
    c.onMaterialChange(row, 'M-001');
    c.onAgenteChange(row, 'Agente Carlos');
    expect(row).toMatchObject({
      idObra: 'obra-id-1', idAlmoxarifado: 'almox-id-1', idEquipe: 'eq-id-1',
      idMaterial: 'mat-id-1', nomeMaterial: 'Cabo de Cobre', idAgenteResponsavel: 'ag-id-1'
    });
  });

  it('campo com erro e nome amigável', () => {
    const row: any = { status: 'COM_ERRO', campoErro: 'codigoMaterial', mensagemErro: 'Material: material não encontrado.' };
    expect(tela.temErroNoCampo(row, 'codigoMaterial')).toBe(true);
    expect(tela.temErroNoCampo(row, 'obra')).toBe(false);
    expect(tela.obterNomeCampoAmigavel(row.campoErro, row.mensagemErro)).toBe('Material');
  });

  it('avisos exigem reconhecimento antes de processar', async () => {
    const confirmar = vi.spyOn(TestBed.inject(CesConfirmationService), 'confirm').mockResolvedValue(true);
    tela.previewData.set(previa({ totalAvisos: 1 }));
    tela.selectedPares.set([{ chave: 'OBRA-01', tipo: '221' }]);
    await tela.onProcessImport();
    expect(confirmar).not.toHaveBeenCalled();
    expect(notificacoes.warning).toHaveBeenCalled();
  });

  it('falha numa combinação mantém a prévia e marca a linha; sucesso total fecha e avisa a lista', async () => {
    vi.spyOn(TestBed.inject(CesConfirmationService), 'confirm').mockResolvedValue(true);
    const processado = vi.fn();
    tela.processado.subscribe(processado);
    tela.visible.set(true);
    tela.previewData.set(previa());
    tela.selectedPares.set([{ chave: '1228694', tipo: '221' }]);
    tela.registrosImport.set([linha({ chave: '1228694', numeroObra: '1228694' })]);
    servico['confirmarImportacao'].mockReturnValue(of({ resultados: [{ chave: '1228694', tipo: '221', solicitacoes: [], erro: 'chaves: A chave 1228694 nao pertence a previa informada.' }] }));
    await tela.onProcessImport();
    expect(tela.registrosImport()[0]).toMatchObject({ status: 'COM_ERRO', campoErro: 'numeroObra' });
    expect(notificacoes.error).toHaveBeenCalledWith(expect.stringContaining('nao pertence'), expect.any(Object));
    expect(processado).not.toHaveBeenCalled();

    tela.selectedPares.set([{ chave: '1228694', tipo: '221' }]);
    servico['confirmarImportacao'].mockReturnValue(of({ resultados: [{ chave: '1228694', tipo: '221', solicitacoes: [{}] }] }));
    await tela.onProcessImport();
    expect(tela.visible()).toBe(false);
    expect(tela.registrosImport()).toEqual([]);
    expect(processado).toHaveBeenCalled();
  });
});
