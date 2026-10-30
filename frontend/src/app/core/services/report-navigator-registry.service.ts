import { Injectable, inject, Injector, Type } from '@angular/core';
import { CesReportNavigatorDataSource } from '../../design-system/patterns/report-navigator/report-navigator.models';
import { FinanceiroReportNavigatorDataSource } from '../../features/financeiro/solicitacoes/financeiro-report-navigator.data-source';
import { SolicitacaoMaterialReportNavigatorDataSource } from '../../features/almoxarifado/solicitacoes-material/solicitacao-material-report-navigator.data-source';
import { SolicitacaoTecnicaReportNavigatorDataSource } from '../../features/almoxarifado/solicitacoes-material/solicitacao-tecnica-report-navigator.data-source';
import { MovimentacaoEstoqueReportNavigatorDataSource } from '../../features/almoxarifado/movimentacoes-estoque/movimentacao-estoque-report-navigator.data-source';
import { SaldoEstoqueReportNavigatorDataSource } from '../../features/almoxarifado/saldos/saldo-estoque-report-navigator.data-source';
import { ExecucaoReportNavigatorDataSource } from '../../features/execucao/execucao-report-navigator.data-source';
import { TurnoReportNavigatorDataSource } from '../../features/execucao/turno-report-navigator.data-source';
import { DescontoReportNavigatorDataSource } from '../../features/dp/descontos/desconto-report-navigator.data-source';
import { InspecaoSstReportNavigatorDataSource } from '../../features/sst/inspecoes/inspecao-sst-report-navigator.data-source';
import { ObraReportNavigatorDataSource } from '../../features/obras/obra-report-navigator.data-source';

@Injectable({ providedIn: 'root' })
export class ReportNavigatorRegistryService {
  private readonly injector = inject(Injector);

  private readonly registry: Record<string, Type<CesReportNavigatorDataSource>> = {
    financeiro: FinanceiroReportNavigatorDataSource,
    'solicitacao-material': SolicitacaoMaterialReportNavigatorDataSource,
    'solicitacao-tecnica': SolicitacaoTecnicaReportNavigatorDataSource,
    movimentacao: MovimentacaoEstoqueReportNavigatorDataSource,
    saldo: SaldoEstoqueReportNavigatorDataSource,
    'execucao-comercial': ExecucaoReportNavigatorDataSource,
    turno: TurnoReportNavigatorDataSource,
    desconto: DescontoReportNavigatorDataSource,
    'inspecao-sst': InspecaoSstReportNavigatorDataSource,
    obra: ObraReportNavigatorDataSource
  };

  resolve(sourceKey: string): CesReportNavigatorDataSource | null {
    const token = this.registry[sourceKey];
    return token ? this.injector.get(token) : null;
  }
}
