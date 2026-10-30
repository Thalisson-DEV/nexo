import '@angular/compiler';
import { TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { of } from 'rxjs';
import { describe, beforeEach, it, expect, vi } from 'vitest';
import { NotificationService } from '@ces/ui';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { SolicitacaoPendenciaModalComponent } from './solicitacao-pendencia-modal.component';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

describe('SolicitacaoPendenciaModalComponent', () => {
  const service = {
    listarMotivosPendencia: vi.fn(() =>
      of([
        { id: 'mot-1', nome: 'Falta de Estoque', descricao: 'Não há estoque suficiente', ativo: true },
        { id: 'mot-2', nome: 'Avaria', descricao: 'Material avariado', ativo: true },
        { id: 'mot-3', nome: 'Especificação Incorreta', descricao: 'Divergência técnica', ativo: true },
      ])
    ),
    registrarPendencia: vi.fn(() => of(undefined)),
    removerPendencia: vi.fn(() => of(undefined)),
    buscarSolicitacaoPorId: vi.fn(() => of({ id: 'sol-123', itens: [] } as any)),
  };

  const notifications = {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [SolicitacaoPendenciaModalComponent],
      providers: [
        { provide: AlmoxarifadoService, useValue: service },
        { provide: NotificationService, useValue: notifications },
      ],
    });
  });

  it('exibe botão de ação com o rótulo "Salvar"', () => {
    const fixture = TestBed.createComponent(SolicitacaoPendenciaModalComponent);
    fixture.componentRef.setInput('idSolicitacao', 'sol-123');
    fixture.componentInstance.visible.set(true);
    fixture.detectChanges();

    // O diálogo é aberto no overlay global (fora do host do componente).
    const saveButton = document.body.querySelector('ces-button[label="Salvar"]');
    expect(saveButton).toBeTruthy();
  });

  it('valida que motivo e descrição com no mínimo 5 caracteres são obrigatórios no escopo GERAL', () => {
    const fixture = TestBed.createComponent(SolicitacaoPendenciaModalComponent);
    fixture.componentRef.setInput('idSolicitacao', 'sol-123');
    fixture.componentInstance.visible.set(true);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    expect(component.formularioValido()).toBe(false);

    // Sem motivo mas com descrição válida
    component.descricao.set('Descrição válida com mais de 5 caracteres');
    expect(component.formularioValido()).toBe(false);

    // Com motivo mas com descrição com menos de 5 caracteres
    component.idMotivo.set('mot-1');
    component.descricao.set('Abc');
    expect(component.formularioValido()).toBe(false);
    component.touched.set(true);
    expect(component.descricaoErro()).toBe('A descrição deve ter no mínimo 5 caracteres.');

    // Descrição com apenas espaços em branco
    component.descricao.set('    ');
    expect(component.formularioValido()).toBe(false);

    // Válido
    component.descricao.set('Material não localizado na prateleira');
    expect(component.formularioValido()).toBe(true);
    expect(component.descricaoErro()).toBeUndefined();
  });

  it('registra pendência da solicitação no escopo GERAL com botão Salvar', () => {
    const fixture = TestBed.createComponent(SolicitacaoPendenciaModalComponent);
    fixture.componentRef.setInput('idSolicitacao', 'sol-123');
    fixture.componentInstance.visible.set(true);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    expect(component.escopo()).toBe('GERAL');
    expect(component.modalTitle()).toBe('Registrar Pendência da Solicitação');

    component.idMotivo.set('mot-1');
    component.descricao.set('Aguardando autorização da gerência');
    component.salvar();

    expect(service.registrarPendencia).toHaveBeenCalledWith('sol-123', {
      idMotivoPendencia: 'mot-1',
      justificativa: 'Aguardando autorização da gerência',
      idItem: undefined,
    });
    expect(notifications.success).toHaveBeenCalledWith('Pendência registrada com sucesso.');
    expect(component.visible()).toBe(false);
  });

  it('permite registrar 3 pendências diferentes para 3 itens diferentes em uma única chamada ao Salvar', () => {
    const fixture = TestBed.createComponent(SolicitacaoPendenciaModalComponent);
    fixture.componentRef.setInput('idSolicitacao', 'sol-123');
    fixture.componentRef.setInput('itens', [
      { id: 'item-1', material: { codigo: 'MAT-1', descricao: 'Parafuso Sextavado' } },
      { id: 'item-2', material: { codigo: 'MAT-2', descricao: 'Cabo Flexível 2.5mm' } },
      { id: 'item-3', material: { codigo: 'MAT-3', descricao: 'Disjuntor 20A' } },
    ]);
    fixture.componentInstance.visible.set(true);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    component.setEscopo('MATERIAL');

    // Seleciona os 3 itens
    component.toggleItem('item-1');
    component.toggleItem('item-2');
    component.toggleItem('item-3');

    // Configura uma pendência diferente para cada um dos 3 itens
    component.setItemMotivo('item-1', 'mot-1');
    component.setItemJustificativa('item-1', 'Falta total em estoque do almoxarifado central');

    component.setItemMotivo('item-2', 'mot-2');
    component.setItemJustificativa('item-2', 'Cabo veio avariado e sem isolamento');

    component.setItemMotivo('item-3', 'mot-3');
    component.setItemJustificativa('item-3', 'Amperagem incompatível com o projeto da obra');

    expect(component.formularioValido()).toBe(true);

    component.salvar();

    expect(service.registrarPendencia).toHaveBeenCalledWith('sol-123', {
      idMotivoPendencia: 'mot-1',
      justificativa: 'Falta total em estoque do almoxarifado central',
      idItem: undefined,
      idsItens: ['item-1', 'item-2', 'item-3'],
      itensPendencias: [
        {
          idItem: 'item-1',
          idMotivoPendencia: 'mot-1',
          justificativa: 'Falta total em estoque do almoxarifado central',
        },
        {
          idItem: 'item-2',
          idMotivoPendencia: 'mot-2',
          justificativa: 'Cabo veio avariado e sem isolamento',
        },
        {
          idItem: 'item-3',
          idMotivoPendencia: 'mot-3',
          justificativa: 'Amperagem incompatível com o projeto da obra',
        },
      ],
    });

    expect(notifications.success).toHaveBeenCalledWith('Pendência(s) salva(s) com sucesso.');
    expect(component.visible()).toBe(false);
  });

  it('permite preenchimento rápido em lote para itens selecionados', () => {
    const fixture = TestBed.createComponent(SolicitacaoPendenciaModalComponent);
    fixture.componentRef.setInput('idSolicitacao', 'sol-123');
    fixture.componentRef.setInput('itens', [
      { id: 'item-1', material: { codigo: 'MAT-1', descricao: 'Material 1' } },
      { id: 'item-2', material: { codigo: 'MAT-2', descricao: 'Material 2' } },
    ]);
    fixture.componentInstance.visible.set(true);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    component.setEscopo('MATERIAL');
    component.toggleTodos();

    component.idMotivoLote.set('mot-1');
    component.descricaoLote.set('Todos sem estoque no almoxarifado');
    component.aplicarLoteAosSelecionados();

    expect(component.getItemConfig('item-1').idMotivo).toBe('mot-1');
    expect(component.getItemConfig('item-1').justificativa).toBe('Todos sem estoque no almoxarifado');
    expect(component.getItemConfig('item-2').idMotivo).toBe('mot-1');
    expect(component.getItemConfig('item-2').justificativa).toBe('Todos sem estoque no almoxarifado');

    expect(component.formularioValido()).toBe(true);
  });

  it('permite alternar todos os itens via toggleTodos', () => {
    const fixture = TestBed.createComponent(SolicitacaoPendenciaModalComponent);
    fixture.componentRef.setInput('idSolicitacao', 'sol-123');
    fixture.componentRef.setInput('itens', [
      { id: 'item-1', material: { codigo: 'MAT-1', descricao: 'Material 1' } },
      { id: 'item-2', material: { codigo: 'MAT-2', descricao: 'Material 2' } },
    ]);
    fixture.componentInstance.visible.set(true);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    component.setEscopo('MATERIAL');

    expect(component.todosSelecionados()).toBe(false);
    component.toggleTodos();
    expect(component.todosSelecionados()).toBe(true);
    expect(component.itensSelecionados().size).toBe(2);

    component.toggleTodos();
    expect(component.todosSelecionados()).toBe(false);
    expect(component.itensSelecionados().size).toBe(0);
  });

  it('suporta modo de item único quando idItem é fornecido', () => {
    const fixture = TestBed.createComponent(SolicitacaoPendenciaModalComponent);
    fixture.componentRef.setInput('idSolicitacao', 'sol-123');
    fixture.componentRef.setInput('idItem', 'item-456');
    fixture.componentRef.setInput('itemLabel', 'CABO-01 — Cabo 10mm');
    fixture.componentInstance.visible.set(true);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    expect(component.escopo()).toBe('MATERIAL');
    expect(component.modalTitle()).toBe('Registrar Pendência do Material');

    component.setItemMotivo('item-456', 'mot-2');
    component.setItemJustificativa('item-456', 'Material danificado na embalagem');
    component.salvar();

    expect(service.registrarPendencia).toHaveBeenCalledWith('sol-123', {
      idMotivoPendencia: 'mot-2',
      justificativa: 'Material danificado na embalagem',
      idItem: 'item-456',
      idsItens: ['item-456'],
      itensPendencias: [
        {
          idItem: 'item-456',
          idMotivoPendencia: 'mot-2',
          justificativa: 'Material danificado na embalagem',
        },
      ],
    });
    expect(notifications.success).toHaveBeenCalledWith('Pendência(s) salva(s) com sucesso.');
    expect(component.visible()).toBe(false);
  });

  it('permite resolver pendência existente', () => {
    const fixture = TestBed.createComponent(SolicitacaoPendenciaModalComponent);
    fixture.componentRef.setInput('idSolicitacao', 'sol-123');
    fixture.componentRef.setInput('motivoAtual', { id: 'mot-1', nome: 'Falta', descricao: 'Falta', ativo: true });
    fixture.componentRef.setInput('justificativaAtual', 'Sem estoque no galpão');
    fixture.componentInstance.visible.set(true);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    component.resolver();

    // Pendência geral: sem item.
    expect(service.removerPendencia).toHaveBeenCalledWith('sol-123', undefined);
    expect(notifications.success).toHaveBeenCalledWith('Pendência geral resolvida com sucesso.');
    expect(component.visible()).toBe(false);
  });
});
