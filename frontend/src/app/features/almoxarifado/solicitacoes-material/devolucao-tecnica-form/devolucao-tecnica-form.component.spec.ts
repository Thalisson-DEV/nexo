import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CesConfirmationService, NotificationService } from '@ces/ui';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LookupStore } from '../../../../core/lookup/store/lookup.store';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { ObrasService } from '../../../../core/services/obras.service';
import { RhService } from '../../../../core/services/rh.service';
import { DevolucaoTecnicaFormComponent } from './devolucao-tecnica-form.component';

describe('DevolucaoTecnicaFormComponent', () => {
  const registrar = vi.fn(() => of('ref-1'));
  const confirmar = vi.fn(() => Promise.resolve(true));
  const notificacoes = { success: vi.fn(), warning: vi.fn(), error: vi.fn() };
  const materiais: Record<string, unknown> = {
    'mat-1': { id: 'mat-1', codigo: 'MAT-1', descricao: 'Cabo', unidadeMedida: 'M', detalhaSerie: false },
    'mat-s': { id: 'mat-s', codigo: 'MAT-S', descricao: 'Trafo', unidadeMedida: 'UN', detalhaSerie: true },
  };

  function abrir(idObra: string | null = 'obra-1') {
    const fixture = TestBed.createComponent(DevolucaoTecnicaFormComponent);
    fixture.componentRef.setInput('idObra', idObra);
    fixture.detectChanges();
    return fixture.componentInstance as any;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.resetTestingModule();
    TestBed.overrideComponent(DevolucaoTecnicaFormComponent, { set: { imports: [], template: '', schemas: [CUSTOM_ELEMENTS_SCHEMA] } });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: NotificationService, useValue: notificacoes },
        { provide: CesConfirmationService, useValue: { confirm: confirmar } },
        { provide: LookupStore, useValue: { searchMaterials: () => of({ items: [], nextCursor: null, hasMore: false }) } },
        {
          provide: AlmoxarifadoService,
          useValue: {
            listarEstoquesAtivos: () =>
              of({
                content: [
                  { id: 'fis', nomeIdentificador: 'Central', ativo: true, tipoEstoque: { nome: 'FISICO' } },
                  { id: 'cam', nomeIdentificador: 'Campo', ativo: true, tipoEstoque: { nome: 'CAMPO' } },
                  { id: 'est-obra', nomeIdentificador: 'Obra', ativo: true, idObra: 'obra-1', tipoEstoque: { nome: 'OBRA' } },
                ],
              }),
            listarSaldos: () => of({ content: [{ material: { id: 'mat-1' }, quantidade: 3, condicao: 'NOVO' }] }),
            listarSeriais: () => of({ content: [{ numSerie: 'S-OBRA' }] }),
            buscarMaterialPorId: (id: string) => of(materiais[id]),
            registrarDevolucaoTecnica: registrar,
          },
        },
        {
          provide: ObrasService,
          useValue: {
            listarObras: vi.fn(() => of({ content: [{ id: 'obra-1', numero: '1', titulo: 'A' }] })),
            buscarObraPorId: () => of({ id: 'obra-1', numero: '1', titulo: 'A', tipoObra: 'OBRA' }),
          },
        },
        { provide: RhService, useValue: { listarEquipes: () => of({ content: [{ id: 'eq-1', codigo: 'EQ' }] }) } },
      ],
    });
  });

  function incluir(tela: any, idMaterial = 'mat-1', quantidade = 2): void {
    tela.aoEscolherMaterial(idMaterial);
    tela.quantidade.set(quantidade);
    tela.adicionarItem();
  }

  it('só oferece almoxarifado físico como destino e começa em 222', () => {
    const tela = abrir();

    expect(tela.destinoOpcoes().map((o: any) => o.value)).toEqual(['fis']);
    expect(tela.tipo()).toBe('DEVOLUCAO_COMUM');
    expect(tela.alterado()).toBe(false);
  });

  it('não envia sem equipe, destino e materiais', async () => {
    const tela = abrir();

    await tela.enviar();

    expect(confirmar).not.toHaveBeenCalled();
    expect(tela.erro('idEquipe')).toBeTruthy();
    expect(tela.erro('idEstoqueDestino')).toBeTruthy();
    expect(tela.erro('itens')).toBeTruthy();
  });

  it('aceita material fora do saldo da obra, avisa e envia com o tipo escolhido', async () => {
    const tela = abrir();
    tela.idEquipe.set('eq-1');
    tela.idEstoqueDestino.set('fis');
    tela.tipo.set('DEVOLUCAO_CONCRETO');
    incluir(tela, 'mat-1', 5);

    expect(tela.excede()).toBe(false); // a seleção já foi limpa ao incluir
    expect(tela.itensForaDoEstoque()).toHaveLength(1);

    await tela.enviar();

    expect(confirmar.mock.calls[0]).toBeDefined();
    expect(registrar).toHaveBeenCalledWith({
      idObra: 'obra-1',
      ocorrencia: undefined,
      idEquipe: 'eq-1',
      idEstoqueDestino: 'fis',
      itens: [{ idMaterial: 'mat-1', tipo: 'DEVOLUCAO_CONCRETO', condicao: 'NOVO', guardarNoEstoque: true, quantidade: 5 }],
    });
    expect(notificacoes.success.mock.calls[0][1].action.label).toBe('Ver movimentações');
    expect(tela.aberto()).toBe(false);
  });

  it('sucata sugere não guardar, e a escolha vai no item', () => {
    const tela = abrir();
    tela.aoMudarCondicao('SUCATA');
    expect(tela.guardar()).toBe(false);

    incluir(tela);
    expect(tela.itens()[0]).toMatchObject({ condicao: 'SUCATA', guardarNoEstoque: false, saldoNaObra: 0 });

    tela.alternarDestino(0);
    expect(tela.itens()[0].guardarNoEstoque).toBe(true);
  });

  it('material com série envia uma linha por série, inclusive a que não estava na obra', async () => {
    const tela = abrir();
    tela.idEquipe.set('eq-1');
    tela.idEstoqueDestino.set('fis');
    tela.aoEscolherMaterial('mat-s');
    expect(tela.seriesDaObra()).toEqual(['S-OBRA']);
    tela.adicionarSerie('S-OBRA');
    tela.seriesTexto.update((t: string) => `${t}\nS-NOVA\nS-NOVA`);
    tela.adicionarItem();

    await tela.enviar();

    const itens = (registrar.mock.calls as any)[0][0].itens;
    expect(itens.map((i: any) => [i.numSerie, i.quantidade])).toEqual([['S-OBRA', 1], ['S-NOVA', 1]]);
  });

  it('repetir material, tipo e condição não duplica a linha', () => {
    const tela = abrir();
    incluir(tela);
    incluir(tela);

    expect(tela.itens()).toHaveLength(1);
    expect(notificacoes.warning).toHaveBeenCalled();
  });

  it('cancelar a confirmação não registra; erro do servidor mantém o drawer aberto', async () => {
    const tela = abrir();
    tela.idEquipe.set('eq-1');
    tela.idEstoqueDestino.set('fis');
    incluir(tela);

    confirmar.mockResolvedValueOnce(false);
    await tela.enviar();
    expect(registrar).not.toHaveBeenCalled();

    registrar.mockReturnValueOnce(throwError(() => new Error('falhou')));
    await tela.enviar();
    expect(notificacoes.error).toHaveBeenCalled();
    expect(tela.aberto()).toBe(true);
    expect(tela.itens()).toHaveLength(1);
  });

  it('trocar de obra limpa a lista, porque o saldo informado era da obra anterior', () => {
    const tela = abrir(null);
    tela.selecionarObra('obra-1');
    incluir(tela);

    tela.selecionarObra('obra-2');

    expect(tela.itens()).toHaveLength(0);
  });
});
