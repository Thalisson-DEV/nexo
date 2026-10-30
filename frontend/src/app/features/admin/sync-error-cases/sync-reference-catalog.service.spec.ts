import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { RhService } from '../../../core/services/rh.service';
import { SstService } from '../../../core/services/sst.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { SyncReferenceCatalogService, syncReferenceKind } from './sync-reference-catalog.service';

describe('SyncReferenceCatalogService', () => {
  let service: SyncReferenceCatalogService;
  const lookups = {
    searchMaterials: vi.fn(() => of({ items: [{ value: 'mat-1', label: 'CABO · Cabo de cobre' }], nextCursor: null, hasMore: false })),
    searchStocks: vi.fn(() => of({ items: [], nextCursor: null, hasMore: false })),
    searchCollaborators: vi.fn(() => of({ items: [], nextCursor: null, hasMore: false }))
  };
  const stock = {
    buscarMaterialPorId: vi.fn(() => of({ id: 'mat-1', codigo: 'CABO', descricao: 'Cabo de cobre' })),
    buscarEstoquePorId: vi.fn(() => of({ id: 'stock-1', nomeIdentificador: 'Estoque Central' }))
  };
  const rh = {
    listarEquipes: vi.fn(() => of({ content: [{ id: 'team-1', codigo: 'FAF1234123' }] })),
    buscarEquipePorId: vi.fn(() => of({ id: 'team-1', codigo: 'FAF1234123' }))
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [
      SyncReferenceCatalogService,
      { provide: LookupStore, useValue: lookups },
      { provide: AlmoxarifadoService, useValue: stock },
      { provide: RhService, useValue: rh },
      { provide: SstService, useValue: {} },
      { provide: UsuariosService, useValue: {} }
    ] });
    service = TestBed.inject(SyncReferenceCatalogService);
  });

  it('identifica referências dos payloads sem depender do tipo de operação', () => {
    expect(syncReferenceKind('idEquipe')).toBe('team');
    expect(syncReferenceKind('idEstoqueOrigem')).toBe('stock');
    expect(syncReferenceKind('idMaterial')).toBe('material');
    expect(syncReferenceKind('idColaboradorSaque')).toBe('collaborator');
    expect(syncReferenceKind('dataPrevista')).toBeNull();
  });

  it('pesquisa equipes pelo código e resolve o ID original em um nome legível', () => {
    service.search('team', 'FAF123', null).subscribe(result => {
      expect(result.items).toEqual([{ value: 'team-1', label: 'Equipe FAF1234123' }]);
    });
    expect(rh.listarEquipes).toHaveBeenCalledWith({ codEquipe: 'faf123', ativo: true, size: 50 });
    service.resolve('team', 'team-1').subscribe(option => {
      expect(option).toEqual({ value: 'team-1', label: 'Equipe FAF1234123' });
    });
  });

  it('usa a busca paginada existente para materiais', () => {
    service.search('material', 'cabo', null).subscribe(result => {
      expect(result.items[0].value).toBe('mat-1');
    });
    expect(lookups.searchMaterials).toHaveBeenCalledWith({ query: 'cabo', cursor: null, limit: 50 });
    service.resolve('material', 'mat-1').subscribe(option => {
      expect(option.label).toBe('CABO · Cabo de cobre');
    });
  });
});
