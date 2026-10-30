import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { KitConstrutivoResponse } from '../../../core/models/obras-kit.models';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { AuthService } from '../../../core/services/auth.service';
import { ContratoService } from '../../../core/services/contrato.service';
import { ObrasKitService } from '../../../core/services/obras-kit.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { KitsConstrutivosComponent } from './kits-construtivos.component';

const kit: KitConstrutivoResponse = {
  id: 'k1',
  idContrato: 'c1',
  numeroContrato: 'CT-01',
  codigo: 'LMI-0688',
  descricao: 'ESTRUTURA BT',
  grupo: 'ESTRUTURA',
  classesTensao: ['MT_15'],
  quantidadeItens: 2,
  quantidadeServicos: 1,
  quantidadeMateriais: 1,
  valor: 350,
  servicosSemValor: 0,
  dataReferenciaValor: '2026-10-30',
  itens: [],
  ativo: true
};

const kitDetalhado: KitConstrutivoResponse = {
  ...kit,
  itens: [
    { id: 'i1', tipo: 'SERVICO', idReferencia: 'a1', codigoReferencia: 'A1', descricaoReferencia: 'INSTALAR ESTRUTURA', operacao: 'INSTALAR', unidade: 'UN', quantidade: 1, valorUnitario: 350, valorTotal: 350 },
    { id: 'i2', tipo: 'MATERIAL', idReferencia: 'm1', codigoReferencia: 'M1', descricaoReferencia: 'CRUZETA', operacao: 'INSTALAR', unidade: 'UN', quantidade: 2 }
  ]
};

type TelaKits = {
  formularioAberto(): boolean;
  itens(): { idReferencia: string; quantidade: number }[];
  codigo: { set(v: string): void };
  descricao: { set(v: string): void };
  idContrato: { set(v: string | null): void };
  novaReferencia: { set(v: string | null): void };
  novaQuantidade: { set(v: number | null): void };
  erroItem(): string | undefined;
  adicionarItem(): void;
  abrir(k: KitConstrutivoResponse): void;
  salvar(): void;
};

describe('KitsConstrutivosComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  let permissoes: string[];
  const abrir = (url = '/lista') =>
    abrirListagem(KitsConstrutivosComponent, 'lista', url, [
      { provide: ObrasKitService, useValue: servico },
      { provide: ContratoService, useValue: { listarContratosAtivos: () => paginaTeste([{ id: 'c1', numeroContrato: 'CT-01', cliente: 'Cliente', ativo: true }]), listarAcoes: vi.fn(() => paginaTeste([{ id: 'a2', codigo: 'A2', nome: 'RETIRAR POSTE', ativo: true }])) } },
      { provide: AlmoxarifadoService, useValue: { listarMateriais: vi.fn(() => paginaTeste([])) } },
      { provide: AuthService, useValue: { currentUser: () => ({ permissoes }) } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    permissoes = ['obras:kit:read', 'obras:kit:write', 'obras:kit:import'];
    servico = {
      listarKits: vi.fn(() => paginaTeste([kit])),
      listarGrupos: vi.fn(() => of(['ESTRUTURA', 'POSTE'])),
      buscarKit: vi.fn(() => of(kitDetalhado)),
      criarKit: vi.fn(() => of(kit)),
      atualizarKit: vi.fn(() => of(kit)),
      desativarKit: vi.fn(() => of(undefined)),
      listarImportacoes: vi.fn(() => paginaTeste([]))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre nos kits ativos, com valor e tensões', async () => {
    const { el } = await abrir();
    expect(servico['listarKits']).toHaveBeenCalledWith(expect.objectContaining({ ativo: true, page: 0, size: 25, sort: 'codigo,asc' }));
    expect(el.textContent).toContain('LMI-0688');
    expect(el.textContent).toContain('15 kV');
    expect(el.textContent).toContain('350,00');
    expect(chipsDoPainel(el)).toEqual(['Status: Ativo']);
  });

  it('restaura contrato e tensão da URL', async () => {
    await abrir('/lista?contrato=c1&tensao=MT_15');
    expect(servico['listarKits']).toHaveBeenLastCalledWith(expect.objectContaining({ idContrato: 'c1', classeTensao: 'MT_15' }));
  });

  it('valida o cadastro e envia os itens adicionados', async () => {
    const { componente } = await abrir();
    tecla('n');
    const tela = componente as unknown as TelaKits;
    expect(tela.formularioAberto()).toBe(true);
    tela.salvar();
    expect(servico['criarKit']).not.toHaveBeenCalled();

    tela.adicionarItem();
    expect(tela.erroItem()).toContain('ação do contrato');

    tela.idContrato.set('c1');
    tela.codigo.set('KIT-NOVO');
    tela.descricao.set('Retirada de poste');
    tela.novaReferencia.set('a2');
    tela.novaQuantidade.set(1.5);
    tela.adicionarItem();
    expect(tela.itens()).toEqual([expect.objectContaining({ idReferencia: 'a2', quantidade: 1.5 })]);

    tela.salvar();
    expect(servico['criarKit']).toHaveBeenCalledWith(
      expect.objectContaining({
        idContrato: 'c1',
        codigo: 'KIT-NOVO',
        itens: [expect.objectContaining({ tipo: 'SERVICO', idReferencia: 'a2', operacao: 'INSTALAR', quantidade: 1.5 })]
      })
    );
  });

  it('não deixa repetir o mesmo item com a mesma ação', async () => {
    const { componente } = await abrir();
    const tela = componente as unknown as TelaKits;
    tela.abrir(kit);
    expect(servico['buscarKit']).toHaveBeenCalledWith('k1');
    expect(tela.itens()).toHaveLength(2);
    tela.novaReferencia.set('a1');
    tela.adicionarItem();
    expect(tela.erroItem()).toContain('já está no kit');
    expect(tela.itens()).toHaveLength(2);
  });

  it('sem permissão de escrita só consulta', async () => {
    permissoes = ['obras:kit:read'];
    const { el } = await abrir();
    expect(el.textContent).not.toContain('Novo kit');
    expect(el.textContent).not.toContain('Importar');
    expect(el.querySelector('button[aria-label^="Desativar"]')).toBeNull();
  });
});
