import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { CesConfirmationService, NotificationService } from '@ces/ui';
import { ConfirmationService } from 'primeng/api';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { SolicitacaoComercialImportRow } from '../../../../core/models/almoxarifado.models';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { SolicitacoesComerciaisImportacaoComponent } from './solicitacoes-comerciais-importacao.component';

/* eslint-disable @typescript-eslint/no-explicit-any */
const estoques = [
  { id: 'est-1', nomeIdentificador: 'ESTOQUE CENTRAL', ativo: true, tipoEstoque: { nome: 'FISICO' } },
  { id: 'est-2', nomeIdentificador: 'ESTOQUE LOCAL', ativo: true, idEquipe: 'eq-1', tipoEstoque: { nome: 'CAMPO' } }
];
const materiais = [{ id: 'mat-1', codigo: 'M-99', descricao: 'Parafuso Aço' }];

describe('SolicitacoesComerciaisImportacaoComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  let tela: any;

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      validarImportacaoSolicitacoesComerciais: vi.fn(),
      confirmarImportacaoSolicitacoesComerciais: vi.fn(),
      baixarModeloCsvSolicitacoesComerciais: vi.fn()
    };
    TestBed.configureTestingModule({
      providers: [
        provideNoopAnimations(),
        ConfirmationService,
        { provide: AlmoxarifadoService, useValue: servico },
        { provide: NotificationService, useValue: { error: vi.fn(), warning: vi.fn(), success: vi.fn(), info: vi.fn() } }
      ]
    });
    const fixture = TestBed.createComponent(SolicitacoesComerciaisImportacaoComponent);
    fixture.componentRef.setInput('estoques', estoques);
    fixture.componentRef.setInput('materiais', materiais);
    tela = fixture.componentInstance;
    vi.spyOn(TestBed.inject(CesConfirmationService), 'confirm').mockResolvedValue(true);
  });

  const linha = (extra: Partial<SolicitacaoComercialImportRow> = {}): SolicitacaoComercialImportRow =>
    ({ linha: 2, nomeEstoqueOrigem: 'ESTOQUE CENTRAL', nomeEstoqueDestino: 'ESTOQUE LOCAL', codigoMaterial: 'M-99', quantidade: 10, dataPrevista: '2026-10-20', status: 'AGUARDANDO_PROCESSAMENTO', ...extra }) as SolicitacaoComercialImportRow;

  it('corrigir com dados válidos casa os cadastros e libera a linha', () => {
    const row = linha({ codigoMaterial: 'M-00', status: 'COM_ERRO', mensagemErro: 'Material inválido' });
    tela.registros.set([row]);
    tela.startImportEdit(row);
    row.codigoMaterial = 'm-99';
    tela.finishImportEdit(row);
    expect(row).toMatchObject({ status: 'AGUARDANDO_PROCESSAMENTO', idEstoqueOrigem: 'est-1', idEstoqueDestino: 'est-2', idMaterial: 'mat-1', codigoMaterial: 'M-99' });
    expect(row.mensagemErro).toBeUndefined();
  });

  it('linha inválida sem alteração continua com erro; destino precisa ser estoque de campo', () => {
    const intacta = linha({ status: 'COM_ERRO', mensagemErro: 'Erro do servidor' });
    tela.registros.set([intacta]);
    tela.startImportEdit(intacta);
    tela.finishImportEdit(intacta);
    expect(intacta.mensagemErro).toBe('Erro do servidor');

    const destino = linha({ nomeEstoqueDestino: 'ESTOQUE CENTRAL', editing: true });
    tela.finishImportEdit(destino);
    expect(destino.status).toBe('COM_ERRO');
    expect(destino.mensagemErro).toContain('estoque de campo');
  });

  it('processa só as válidas, sem a flag de edição; parcial mantém só as com erro', async () => {
    tela.registros.set([linha({ editing: true }), linha({ linha: 3, status: 'COM_ERRO', mensagemErro: 'x' })]);
    servico['confirmarImportacaoSolicitacoesComerciais'].mockReturnValue(
      of({
        registros: [linha({ status: 'EM_PROCESSAMENTO' as any }), linha({ linha: 3, codigoMaterial: 'M-2', status: 'COM_ERRO', mensagemErro: 'Material inválido' })],
        totalProcessados: 2,
        totalSucesso: 1,
        totalFalha: 1,
        processamento: { etapa: 'CONFIRMACAO', estado: 'PARCIAL', mensagem: '' }
      })
    );
    const processado = vi.fn();
    tela.processado.subscribe(processado);
    await tela.onProcessImport();
    const enviados = servico['confirmarImportacaoSolicitacoesComerciais'].mock.calls[0][0].registros;
    expect(enviados).toHaveLength(1);
    expect(enviados[0].editing).toBeUndefined();
    expect(tela.registros().map((r: SolicitacaoComercialImportRow) => r.codigoMaterial)).toEqual(['M-2']);
    expect(processado).toHaveBeenCalled();
  });
});
