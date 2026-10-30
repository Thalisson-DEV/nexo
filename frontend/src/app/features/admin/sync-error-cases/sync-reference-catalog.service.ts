import { Injectable, inject } from '@angular/core';
import { Observable, map, of } from 'rxjs';
import { LookupOption } from '../../../core/lookup/models/lookup-option';
import { LookupPage } from '../../../core/lookup/models/lookup-page';
import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { ContratoService } from '../../../core/services/contrato.service';
import { ExecucaoService } from '../../../core/services/execucao.service';
import { RhService } from '../../../core/services/rh.service';
import { SstService } from '../../../core/services/sst.service';
import { UsuariosService } from '../../../core/services/usuarios.service';

export type SyncReferenceKind =
  | 'material' | 'stock' | 'collaborator' | 'team' | 'vehicle'
  | 'municipality' | 'base' | 'process' | 'inspectionType' | 'form' | 'user'
  | 'serviceType' | 'action';

const FIELD_KINDS: Record<string, SyncReferenceKind> = {
  idMaterial: 'material',
  idEstoqueOrigem: 'stock',
  idEstoqueDestino: 'stock',
  idAlmoxarifado: 'stock',
  idOrigem: 'stock',
  idDestino: 'stock',
  idColaboradorSaque: 'collaborator',
  idColaborador: 'collaborator',
  idRequisitante: 'user',
  idUsuario: 'user',
  idEquipe: 'team',
  idVeiculo: 'vehicle',
  idMunicipio: 'municipality',
  idBaseOperacional: 'base',
  idProcesso: 'process',
  idTipoInspecao: 'inspectionType',
  idFormulario: 'form',
  idTipoServico: 'serviceType',
  acoes: 'action'
};

export function syncReferenceKind(key: string): SyncReferenceKind | null {
  return FIELD_KINDS[key] ?? null;
}

const page = (items: LookupOption<string>[]): LookupPage<LookupOption<string>> => ({
  items, nextCursor: null, hasMore: false
});

@Injectable({ providedIn: 'root' })
export class SyncReferenceCatalogService {
  private readonly lookups = inject(LookupStore);
  private readonly stock = inject(AlmoxarifadoService);
  private readonly rh = inject(RhService);
  private readonly sst = inject(SstService);
  private readonly users = inject(UsuariosService);
  private readonly contratos = inject(ContratoService);
  private readonly execucao = inject(ExecucaoService);

  search(kind: SyncReferenceKind, query: string, cursor: string | null): Observable<LookupPage<LookupOption<string>>> {
    const request = { query, cursor, limit: 50 };
    if (kind === 'material') return this.lookups.searchMaterials(request);
    if (kind === 'stock') return this.lookups.searchStocks(request);
    if (kind === 'collaborator') return this.lookups.searchCollaborators(request);

    const q = query.trim().toLocaleLowerCase('pt-BR');
    const filter = (options: LookupOption<string>[]) => page(options.filter(option => option.label.toLocaleLowerCase('pt-BR').includes(q)).slice(0, 50));
    switch (kind) {
      case 'team': return this.rh.listarEquipes({ codEquipe: q, ativo: true, size: 50 }).pipe(map(result => filter(result.content.map(item => ({ value: item.id, label: `Equipe ${item.codigo}` })))));
      case 'vehicle': return this.rh.listarVeiculos({ buscaGlobal: q, ativo: true, size: 50 }).pipe(map(result => filter(result.content.map(item => ({ value: item.id, label: `${item.placa} · ${item.modelo}` })))));
      case 'municipality': return this.rh.listarMunicipios({ buscaGlobal: q, ativo: true, size: 50 }).pipe(map(result => filter(result.content.map(item => ({ value: item.id, label: item.nome })))));
      case 'base': return this.rh.listarBasesOperacionais({ buscaGlobal: q, ativo: true, size: 50 }).pipe(map(result => filter(result.content.map(item => ({ value: item.id, label: item.nome })))));
      case 'process': return this.rh.listarProcessos({ buscaGlobal: q, ativo: true, size: 50 }).pipe(map(result => filter(result.content.map(item => ({ value: item.id, label: item.nome })))));
      case 'inspectionType': return this.sst.listarOpcoesTiposInspecao().pipe(map(result => filter(result.map(item => ({ value: item.id, label: item.nome })))));
      case 'form': return this.sst.listarFormulariosAtivos().pipe(map(result => filter(result.content.map(item => ({ value: item.id, label: item.titulo })))));
      case 'user': return this.users.listarUsuarios({ buscaGlobal: q, ativo: true, size: 50 }).pipe(map(result => filter(result.content.map(item => ({ value: item.id, label: item.login })))));
      case 'serviceType': return this.execucao.listarOpcoesTiposServico().pipe(map(result => filter(result.map(item => ({ value: item.id, label: `${item.codigo} · ${item.descricao}` })))));
      case 'action': return this.contratos.listarAcoes({ buscaGlobal: q, ativo: true, size: 50 }).pipe(map(result => filter(result.content.map(item => ({ value: item.id, label: `${item.codigo} · ${item.nome}` })))));
      default: return of(page([]));
    }
  }

  resolve(kind: SyncReferenceKind, id: string): Observable<LookupOption<string>> {
    const option = (label: string) => ({ value: id, label });
    switch (kind) {
      case 'material': return this.stock.buscarMaterialPorId(id).pipe(map(item => option(`${item.codigo} · ${item.descricao}`)));
      case 'stock': return this.stock.buscarEstoquePorId(id).pipe(map(item => option(item.nomeIdentificador)));
      case 'collaborator': return this.rh.buscarColaboradorPorId(id).pipe(map(item => option(item.nomeCompleto)));
      case 'team': return this.rh.buscarEquipePorId(id).pipe(map(item => option(`Equipe ${item.codigo}`)));
      case 'vehicle': return this.rh.buscarVeiculoPorId(id).pipe(map(item => option(`${item.placa} · ${item.modelo}`)));
      case 'municipality': return this.rh.buscarMunicipioPorId(id).pipe(map(item => option(item.nome)));
      case 'base': return this.rh.buscarBaseOperacionalPorId(id).pipe(map(item => option(item.nome)));
      case 'process': return this.rh.buscarProcessoPorId(id).pipe(map(item => option(item.nome)));
      case 'inspectionType': return this.sst.buscarTipoInspecaoPorId(id).pipe(map(item => option(item.nome)));
      case 'form': return this.sst.buscarFormularioPorId(id).pipe(map(item => option(item.titulo)));
      case 'user': return this.users.buscarUsuarioPorId(id).pipe(map(item => option(item.login)));
      case 'serviceType': return this.execucao.listarTiposServico({ size: 500 }).pipe(map(result => {
        const tipo = result.content.find(item => item.id === id);
        if (!tipo) throw new Error('Tipo de serviço não encontrado');
        return option(`${tipo.codigo} · ${tipo.descricao}`);
      }));
      case 'action': return this.contratos.buscarAcaoPorId(id).pipe(map(item => option(`${item.codigo} · ${item.nome}`)));
    }
  }
}
