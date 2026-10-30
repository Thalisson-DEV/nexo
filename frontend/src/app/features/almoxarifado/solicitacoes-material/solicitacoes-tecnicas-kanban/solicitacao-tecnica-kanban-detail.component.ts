import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  model,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TooltipModule } from 'primeng/tooltip';
import {
  CesButtonComponent,
  CesCalloutComponent,
  CesDialogShellComponent,
  CesStatusComponent,
  CesTabItem,
  CesTabPanelDirective,
  CesTabsComponent,
  CesTone,
  NotificationService,
} from '@ces/ui';
import {
  AcaoPermitidaSolicitacao,
  ItemSolicitacaoTecnicaResponse,
  OperacaoSolicitacaoTecnicaResponse,
  SolicitacaoMaterialDetalheResponse,
  SolicitacaoMaterialSummaryResponse,
} from '../../../../core/models/solicitacao-material-tecnica.models';
import {
  EstoqueResponse,
  MotivoPendenciaResponse,
} from '../../../../core/models/almoxarifado.models';
import { ObraResponse } from '../../../../core/models/obras.models';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { ObrasService } from '../../../../core/services/obras.service';
import { RhService } from '../../../../core/services/rh.service';
import { SolicitacaoMaterialTecnicaService } from '../../../../core/services/solicitacao-material-tecnica.service';
import { UsuarioLookupService } from '../../../../core/services/usuario-lookup.service';
import { AnalisarComCopilotoComponent } from '../../../../shared/components/analisar-com-copiloto/analisar-com-copiloto.component';
import { SolicitacaoPendenciaModalComponent } from '../solicitacao-pendencia-modal.component';
import {
  separacaoLabel,
  separacaoTone,
  statusLabel,
  statusTone,
  tipoTecnicoDescricaoCompleta,
  tipoTecnicoEhAdicao,
  tipoTecnicoIcon,
  tipoTecnicoLabel,
} from '../solicitacao-tecnica-presentation';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { navegarSaindoDoDrawer } from '../../../../shared/utils/drawer-navegacao.utils';

@Component({
  selector: 'app-solicitacao-tecnica-kanban-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    TooltipModule,
    CesDialogShellComponent,
    CesButtonComponent,
    CesStatusComponent,
    CesTabsComponent,
    CesTabPanelDirective,
    CesCalloutComponent,
    AnalisarComCopilotoComponent,
    SolicitacaoPendenciaModalComponent,
  ],
  template: `
    <ces-dialog-shell
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [title]="dialogTitle()"
      description="Pré-relatório operacional e conferência de materiais técnicos"
      size="xl"
      (closed)="onClose()"
    >
      @if (loading()) {
        <div class="flex flex-col items-center justify-center py-16 gap-3 text-[var(--ces-foreground-muted)]">
          <i class="pi pi-spin pi-spinner text-3xl text-[var(--ces-brand)]"></i>
          <span class="text-xs font-medium">Carregando dados da solicitação técnica...</span>
        </div>
      } @else if (detalhe(); as det) {
        <div class="flex flex-col gap-4 text-[var(--ces-foreground)]">
          <!-- CABEÇALHO OPERACIONAL: ROTA, TIPO TÉCNICO, STATUS E ASSISTENTE IA -->
          <div class="flex items-center justify-between gap-3 pb-3 border-b border-[var(--ces-border)] flex-wrap">
            <div class="flex items-center gap-2 min-w-0 flex-wrap">
              <span class="text-xs font-bold uppercase tracking-wider text-[var(--ces-foreground-muted)]">
                {{ tipoEhAdicao() ? 'Movimentação (Saque):' : 'Movimentação (Devolução):' }}
              </span>

              <span class="text-xs font-bold text-[var(--ces-foreground)] truncate" [title]="rotaOrigem()">
                {{ rotaOrigem() }}
              </span>
              <i class="pi pi-arrow-right text-[10px] text-[var(--ces-brand)] shrink-0" aria-hidden="true"></i>
              <span class="text-xs font-bold text-[var(--ces-foreground)] truncate" [title]="rotaDestino()">
                {{ rotaDestino() }}
              </span>

              @if (det.tecnico?.tipoTecnico) {
                <ces-status [tone]="tipoEhAdicao() ? 'success' : 'danger'" [icon]="tipoTecnicoIcon(det.tecnico?.tipoTecnico)">
                  {{ tipoTecnicoDescricaoCompleta(det.tecnico?.tipoTecnico) }}
                </ces-status>
              }

              <span class="text-xs font-medium text-[var(--ces-foreground-muted)]">
                · {{ obraDisplay() }}
              </span>
              @if (det.tecnico?.ocorrencia?.codigo; as oc) {
                <span class="text-xs font-medium text-[var(--ces-foreground-muted)]">
                  · OC {{ oc }}
                </span>
              }
            </div>

            <!-- Status Operacional e Copiloto -->
            <div class="flex items-center gap-2 shrink-0 flex-wrap">
              @if (det.tecnico?.atrasada) {
                <ces-status tone="danger" icon="pi pi-clock">Atrasada</ces-status>
              }
              @if (det.possuiPendencia) {
                <ces-status tone="warning" icon="pi pi-exclamation-triangle">Com Pendência</ces-status>
              }
              <ces-status [tone]="statusTone(det.status)">
                {{ statusLabel(det.status) }}
              </ces-status>
              @if (det.statusSeparacao) {
                <ces-status [tone]="separacaoTone(det.statusSeparacao)">
                  {{ separacaoLabel(det.statusSeparacao) }}
                </ces-status>
              }

              <app-analisar-com-copiloto
                origem="almoxarifado.kanban.detalhe"
                tipo="SOLICITACAO_TECNICA"
                [entidade]="det.id"
                [rotulo]="'Solicitação Técnica #' + idCurto()"
                [pergunta]="perguntaCopiloto()"
                label="Analisar"
                variant="ghost"
                size="sm"
              />
            </div>
          </div>

          <!-- BALANÇO OPERACIONAL EXECUTIVO (4 CARDS KPI) -->
          <div class="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
            <div class="flex flex-col gap-1 p-3 rounded-xl border border-[var(--ces-border)] bg-[var(--ces-surface)] shadow-2xs">
              <span class="text-[10px] font-bold uppercase tracking-wider text-[var(--ces-foreground-muted)]">Itens Distintos</span>
              <span class="text-xl font-bold text-[var(--ces-foreground)] tabular-nums">{{ totalItens() }}</span>
              <span class="text-[10px] text-[var(--ces-foreground-muted)]">
                {{ totalItens() === 1 ? '1 material técnico' : totalItens() + ' materiais técnicos' }}
              </span>
            </div>

            <div class="flex flex-col gap-1 p-3 rounded-xl border border-[var(--ces-border)] bg-[var(--ces-surface)] shadow-2xs">
              <span class="text-[10px] font-bold uppercase tracking-wider text-[var(--ces-foreground-muted)]">Volume Solicitado</span>
              <span class="text-xl font-bold text-[var(--ces-foreground)] tabular-nums">{{ totalSolicitado() | number: '1.0-3' }}</span>
              <span class="text-[10px] text-[var(--ces-foreground-muted)]">Demanda técnica total</span>
            </div>

            <div class="flex flex-col gap-1 p-3 rounded-xl border border-[var(--ces-border)] bg-[var(--ces-surface)] shadow-2xs">
              <span class="text-[10px] font-bold uppercase tracking-wider text-[var(--ces-foreground-muted)]">
                {{ tipoEhAdicao() ? 'Total Entregue' : 'Total Devolvido' }}
              </span>
              <span class="text-xl font-bold text-[var(--ces-success)] tabular-nums">{{ totalAtendido() | number: '1.0-3' }}</span>
              <span class="text-[10px] text-[var(--ces-foreground-muted)]">{{ percentualAtendido() }}% atendido</span>
            </div>

            <div class="flex flex-col gap-1 p-3 rounded-xl border border-[var(--ces-border)] bg-[var(--ces-surface)] shadow-2xs">
              <span class="text-[10px] font-bold uppercase tracking-wider text-[var(--ces-foreground-muted)]">Saldo Pendente</span>
              <span
                class="text-xl font-bold tabular-nums"
                [class.text-[var(--ces-warning)]]="totalPendente() > 0"
                [class.text-[var(--ces-foreground-muted)]]="totalPendente() === 0"
              >
                {{ totalPendente() | number: '1.0-3' }}
              </span>
              <span class="text-[10px] text-[var(--ces-foreground-muted)]">
                {{ totalPendente() === 0 ? 'Totalmente atendido' : (tipoEhAdicao() ? 'Pendente de entrega' : 'Pendente de devolução') }}
              </span>
            </div>
          </div>

          <!-- DADOS GERAIS ESTRUTURADOS (3 COLUNAS NÃO-REDUNDANTES) -->
          <div class="grid grid-cols-1 md:grid-cols-3 gap-3 p-3.5 rounded-xl border border-[var(--ces-border)] bg-[var(--ces-surface-subtle)]">
            <!-- Coluna 1: Cronograma & Prazos -->
            <div class="flex flex-col gap-2">
              <div class="flex items-center gap-1.5 text-xs font-bold text-[var(--ces-foreground)] uppercase tracking-wider border-b border-[var(--ces-border)] pb-1.5">
                <i class="pi pi-calendar text-[var(--ces-brand)] text-xs"></i>
                <span>Cronograma & Prazos</span>
              </div>
              <div class="flex flex-col gap-1.5 text-xs">
                <div class="flex items-center justify-between gap-2">
                  <span class="text-[var(--ces-foreground-muted)]">Solicitação:</span>
                  <span class="font-semibold text-[var(--ces-foreground)]">
                    {{ (det.dataSolicitacao | date: 'dd/MM/yyyy HH:mm') || '—' }}
                  </span>
                </div>
                <div class="flex items-center justify-between gap-2">
                  <span class="text-[var(--ces-foreground-muted)]">Saque Previsto:</span>
                  <span class="font-semibold text-[var(--ces-foreground)]" [class.text-[var(--ces-danger)]]="det.tecnico?.atrasada">
                    {{ (det.tecnico?.dataPrevista | date: 'dd/MM/yyyy') || 'Aguardando agendamento' }}
                  </span>
                </div>
                <div class="flex items-center justify-between gap-2">
                  <span class="text-[var(--ces-foreground-muted)]">Situação do Prazo:</span>
                  @if (det.tecnico?.atrasada) {
                    <ces-status tone="danger" size="sm">Atrasada</ces-status>
                  } @else if (det.tecnico?.dataPrevista) {
                    <span class="text-[var(--ces-success)] font-medium">No Prazo</span>
                  } @else {
                    <span class="text-[var(--ces-foreground-muted)] italic">A definir</span>
                  }
                </div>
                <div class="flex items-center justify-between gap-2">
                  <span class="text-[var(--ces-foreground-muted)]">Separação:</span>
                  <span class="font-semibold text-[var(--ces-foreground)]">
                    {{ separacaoLabel(det.statusSeparacao) }}
                  </span>
                </div>
              </div>
            </div>

            <!-- Coluna 2: Equipe & Envolvidos -->
            <div class="flex flex-col gap-2 md:border-x md:border-[var(--ces-border)] md:px-3">
              <div class="flex items-center gap-1.5 text-xs font-bold text-[var(--ces-foreground)] uppercase tracking-wider border-b border-[var(--ces-border)] pb-1.5">
                <i class="pi pi-users text-[var(--ces-brand)] text-xs"></i>
                <span>Equipe & Envolvidos</span>
              </div>
              <div class="flex flex-col gap-1.5 text-xs">
                <div class="flex items-center justify-between gap-2">
                  <span class="text-[var(--ces-foreground-muted)]">Equipe de Campo:</span>
                  <span class="font-semibold text-[var(--ces-foreground)] truncate" [title]="equipeNome()">
                    {{ equipeNome() }}
                  </span>
                </div>
                <div class="flex items-center justify-between gap-2">
                  <span class="text-[var(--ces-foreground-muted)]">Resp. Saque:</span>
                  <div class="flex items-center gap-1 min-w-0">
                    <span class="font-semibold text-[var(--ces-foreground)] truncate" [title]="responsavelSaqueNome()">
                      {{ responsavelSaqueNome() }}
                    </span>
                    @if (det.tecnico?.saqueForaEquipe) {
                      <span class="text-[9px] px-1 py-0.2 rounded bg-[var(--ces-surface)] text-[var(--ces-foreground-muted)] border border-[var(--ces-border)] shrink-0">
                        Fora
                      </span>
                    }
                  </div>
                </div>
                <div class="flex items-center justify-between gap-2">
                  <span class="text-[var(--ces-foreground-muted)]">Reserva SAP:</span>
                  <span class="font-semibold text-[var(--ces-foreground)] truncate">
                    {{ det.tecnico?.reserva?.numero ? ('#' + det.tecnico?.reserva?.numero) : 'Sem reserva' }}
                  </span>
                </div>
                <div class="flex items-center justify-between gap-2">
                  <span class="text-[var(--ces-foreground-muted)]">Requisitante:</span>
                  <span class="font-semibold text-[var(--ces-foreground)] truncate" [title]="requisitanteNome()">
                    {{ requisitanteNome() }}
                  </span>
                </div>
              </div>
            </div>

            <!-- Coluna 3: Obra & Almoxarifado -->
            <div class="flex flex-col gap-2">
              <div class="flex items-center gap-1.5 text-xs font-bold text-[var(--ces-foreground)] uppercase tracking-wider border-b border-[var(--ces-border)] pb-1.5">
                <i class="pi pi-building text-[var(--ces-brand)] text-xs"></i>
                <span>Obra & Estoque</span>
              </div>
              <div class="flex flex-col gap-1.5 text-xs">
                <div class="flex items-center justify-between gap-2">
                  <span class="text-[var(--ces-foreground-muted)]">Obra:</span>
                  <span class="font-semibold text-[var(--ces-foreground)] truncate" [title]="obraDisplay()">
                    {{ obraDisplay() }}
                  </span>
                </div>
                <div class="flex items-center justify-between gap-2">
                  <span class="text-[var(--ces-foreground-muted)]">Tipo de Obra:</span>
                  <span class="font-semibold text-[var(--ces-foreground)]">
                    {{ det.tecnico?.tipoObra || '—' }}
                  </span>
                </div>
                <div class="flex items-center justify-between gap-2">
                  <span class="text-[var(--ces-foreground-muted)]">Estoque Almox.:</span>
                  <span class="font-semibold text-[var(--ces-foreground)] truncate" [title]="almoxarifadoNome()">
                    {{ almoxarifadoNome() }}
                  </span>
                </div>
                <div class="flex items-center justify-between gap-2">
                  <span class="text-[var(--ces-foreground-muted)]">Etapa Operacional:</span>
                  <span class="font-semibold text-[var(--ces-foreground)]">
                    {{ etapaLabel(det.etapaOperacional || det.tecnico?.etapaOperacional) }}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <!-- OBSERVAÇÕES OPERACIONAIS (SE HOUVER) -->
          @if (det.tecnico?.observacao; as obs) {
            <div class="flex items-start gap-2.5 p-3 rounded-xl border border-[var(--ces-border)] bg-[var(--ces-surface-subtle)] text-xs">
              <i class="pi pi-info-circle text-[var(--ces-brand)] text-sm shrink-0 mt-0.5" aria-hidden="true"></i>
              <div class="flex flex-col gap-0.5 min-w-0">
                <span class="text-[10px] font-bold uppercase tracking-wide text-[var(--ces-foreground-muted)]">
                  Observações Operacionais do Almoxarifado
                </span>
                <p class="text-[var(--ces-foreground)] whitespace-pre-wrap leading-relaxed">{{ obs }}</p>
              </div>
            </div>
          }

          <!-- ALERTA DE PENDÊNCIA GERAL DA SOLICITAÇÃO (SE HOUVER) -->
          @if (det.motivoPendencia) {
            <ces-callout tone="warning" title="Pendência Registrada na Solicitação" icon="pi pi-exclamation-triangle">
              <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mt-1">
                <div class="text-xs">
                  <span class="font-semibold">{{ det.motivoPendencia.nome }}:</span>
                  <span class="ml-1 italic">{{ det.justificativaPendencia || 'Sem justificativa informada.' }}</span>
                </div>
                <ces-button
                  label="Resolver Pendência Geral"
                  icon="pi pi-check"
                  variant="ghost"
                  size="sm"
                  [loading]="resolvendoPendencia()"
                  (pressed)="resolverPendenciaGeral()"
                />
              </div>
            </ces-callout>
          }

          <!-- ALERTA DE MOTIVO DE REJEIÇÃO (SE REJEITADA) -->
          @if (det.status === 'REJEITADO' && det.motivoRejeicao) {
            <ces-callout tone="danger" title="Solicitação Rejeitada" icon="pi pi-times-circle">
              <div class="text-xs mt-1">
                <span class="font-semibold">{{ det.motivoRejeicao.nome }}:</span>
                <span class="ml-1">{{ det.justificativaRejeicao || 'Sem justificativa registrada.' }}</span>
              </div>
            </ces-callout>
          }

          <!-- NAVEGAÇÃO POR ABAS -->
          <ces-tabs [(value)]="abaAtiva" [tabs]="abas()">
            <!-- ABA 1: MATERIAIS & QUANTIDADES -->
            <ng-template cesTabPanel="geral">
              <div class="flex flex-col gap-3 pt-2">
                <div class="flex items-center justify-between gap-2 flex-wrap">
                  <span class="text-xs font-bold uppercase tracking-wider text-[var(--ces-foreground)] flex items-center gap-1.5">
                    <i class="pi pi-box text-[var(--ces-brand)]"></i>
                    Materiais da Solicitação ({{ totalItens() }})
                  </span>

                  @if (podeRegistrarPendencia() && !det.motivoPendencia) {
                    <ces-button
                      label="Apontar Pendência Geral"
                      icon="pi pi-flag"
                      variant="ghost"
                      size="sm"
                      (pressed)="abrirPendenciaGeral()"
                      pTooltip="Registrar pendência geral nesta solicitação técnica"
                      tooltipPosition="left"
                    />
                  }
                </div>

                <!-- TABELA LIMPA DE MATERIAIS (PADRÃO Nexo, TOTALMENTE COMPATÍVEL COM TEMA ESCURO) -->
                <div class="rounded-xl border border-[var(--ces-border)] overflow-hidden shadow-2xs bg-[var(--ces-surface)]">
                  <div class="overflow-x-auto max-h-[320px]">
                    <table class="w-full text-left text-xs border-collapse">
                      <thead class="sticky top-0 z-10 bg-[var(--ces-surface-muted)] border-b border-[var(--ces-border)]">
                        <tr class="text-[11px] font-bold uppercase tracking-wider text-[var(--ces-foreground-muted)]">
                          <th class="py-2.5 px-3">Código</th>
                          <th class="py-2.5 px-3">Material</th>
                          <th class="py-2.5 px-3 text-center">Unid.</th>
                          <th class="py-2.5 px-3 text-right">Solicitada</th>
                          <th class="py-2.5 px-3 text-right">
                            {{ tipoEhAdicao() ? 'Entregue' : 'Devolvida' }}
                          </th>
                          <th class="py-2.5 px-3 text-right">Pendente</th>
                          <th class="py-2.5 px-3 text-center">Situação</th>
                          <th class="py-2.5 px-3 text-center w-24">Pendência</th>
                        </tr>
                      </thead>
                      <tbody class="divide-y divide-[var(--ces-border)] text-xs">
                        @for (item of det.itens; track item.id) {
                          <tr class="hover:bg-[var(--ces-surface-muted)]/50 transition-colors">
                            <td class="py-2.5 px-3 whitespace-nowrap font-bold text-xs text-[var(--ces-foreground)]">
                              {{ item.material.codigo }}
                            </td>
                            <td class="py-2.5 px-3">
                              <div class="flex items-center gap-1.5 flex-wrap">
                                <span class="font-medium text-xs text-[var(--ces-foreground)]">
                                  {{ item.material.descricao }}
                                </span>
                                @if (item.material.detalhaSerie) {
                                  <span class="text-[9px] font-bold px-1.5 py-0.5 rounded bg-[var(--ces-surface-subtle)] text-[var(--ces-foreground-muted)] border border-[var(--ces-border)]">
                                    SÉRIE
                                  </span>
                                }
                              </div>
                            </td>
                            <td class="py-2.5 px-3 text-center text-[11px] text-[var(--ces-foreground-muted)] uppercase">
                              {{ item.material.unidadeMedida || 'UN' }}
                            </td>
                            <td class="py-2.5 px-3 text-right font-semibold tabular-nums text-xs text-[var(--ces-foreground)]">
                              {{ item.quantidade | number: '1.2-3' }}
                            </td>
                            <td class="py-2.5 px-3 text-right font-semibold tabular-nums text-xs text-[var(--ces-success)]">
                              {{ itemAtendido(item) | number: '1.2-3' }}
                            </td>
                            <td
                              class="py-2.5 px-3 text-right font-semibold tabular-nums text-xs"
                              [class.text-[var(--ces-warning)]]="itemPendente(item) > 0"
                              [class.text-[var(--ces-foreground-muted)]]="itemPendente(item) <= 0"
                            >
                              {{ itemPendente(item) | number: '1.2-3' }}
                            </td>
                            <td class="py-2.5 px-3 text-center">
                              @if (item.motivoPendencia) {
                                <ces-status tone="warning" emphasis="outline">Pendência</ces-status>
                              } @else if (itemAtendido(item) >= item.quantidade) {
                                <ces-status tone="success" emphasis="soft">Atendido</ces-status>
                              } @else if (itemAtendido(item) > 0) {
                                <ces-status tone="warning" emphasis="soft">Parcial</ces-status>
                              } @else {
                                <ces-status tone="neutral" emphasis="soft">Aguardando</ces-status>
                              }
                            </td>
                            <td class="py-2.5 px-3 text-center">
                              @if (podeRegistrarPendencia()) {
                                <button
                                  type="button"
                                  class="inline-flex items-center justify-center p-1.5 rounded-lg border border-[var(--ces-border)] bg-[var(--ces-surface)] text-[var(--ces-foreground-muted)] hover:text-[var(--ces-warning)] hover:border-[var(--ces-warning)] transition-colors cursor-pointer"
                                  (click)="abrirPendenciaItem(item)"
                                  [title]="item.motivoPendencia ? 'Ver/Resolver pendência do item' : 'Registrar pendência neste item'"
                                >
                                  <i class="pi" [ngClass]="item.motivoPendencia ? 'pi-exclamation-triangle text-[var(--ces-warning)]' : 'pi-flag'" aria-hidden="true"></i>
                                </button>
                              } @else {
                                <span class="text-[var(--ces-foreground-muted)] text-[11px]">—</span>
                              }
                            </td>
                          </tr>
                        }
                      </tbody>
                      <tfoot class="sticky bottom-0 bg-[var(--ces-surface-muted)] border-t border-[var(--ces-border)] font-semibold text-xs text-[var(--ces-foreground)]">
                        <tr>
                          <td colspan="3" class="py-2.5 px-3 font-bold uppercase tracking-wider text-[11px] text-[var(--ces-foreground-muted)]">
                            Total Consolidado
                          </td>
                          <td class="py-2.5 px-3 text-right tabular-nums font-bold">
                            {{ totalSolicitado() | number: '1.2-3' }}
                          </td>
                          <td class="py-2.5 px-3 text-right tabular-nums font-bold text-[var(--ces-success)]">
                            {{ totalAtendido() | number: '1.2-3' }}
                          </td>
                          <td
                            class="py-2.5 px-3 text-right tabular-nums font-bold"
                            [class.text-[var(--ces-warning)]]="totalPendente() > 0"
                            [class.text-[var(--ces-foreground-muted)]]="totalPendente() <= 0"
                          >
                            {{ totalPendente() | number: '1.2-3' }}
                          </td>
                          <td colspan="2" class="py-2.5 px-3 text-center text-[11px] text-[var(--ces-foreground-muted)]">
                            {{ percentualAtendido() }}% atendido
                          </td>
                        </tr>
                      </tfoot>
                    </table>
                  </div>
                </div>
              </div>
            </ng-template>

            <!-- ABA 2: HISTÓRICO DE MOVIMENTAÇÕES (SE HOUVER) -->
            @if (det.tecnico?.operacoes && det.tecnico!.operacoes.length > 0) {
              <ng-template cesTabPanel="historico">
                <div class="flex flex-col gap-3 pt-2">
                  <div class="flex items-center justify-between gap-2 pb-1">
                    <span class="text-xs text-[var(--ces-foreground-muted)]">
                      Movimentações físicas e registros de entrega/devolução já processados nesta solicitação:
                    </span>
                    <div class="flex items-center gap-2 shrink-0">
                      <button
                        type="button"
                        (click)="expandirTodasOperacoes()"
                        class="text-xs font-semibold text-[var(--ces-brand)] hover:underline cursor-pointer"
                      >
                        Expandir todas
                      </button>
                      <span class="text-[var(--ces-foreground-muted)]">·</span>
                      <button
                        type="button"
                        (click)="recolherTodasOperacoes()"
                        class="text-xs font-semibold text-[var(--ces-foreground-muted)] hover:underline cursor-pointer"
                      >
                        Recolher todas
                      </button>
                    </div>
                  </div>

                  <div class="flex flex-col gap-2.5">
                    @for (op of det.tecnico!.operacoes; track op.id) {
                      <div class="rounded-xl border border-[var(--ces-border)] bg-[var(--ces-surface)] shadow-2xs overflow-hidden">
                        <button
                          type="button"
                          (click)="alternarOperacao(op.id)"
                          class="w-full p-3 flex items-center justify-between gap-3 hover:bg-[var(--ces-surface-muted)]/50 transition-colors text-left cursor-pointer select-none"
                          [attr.aria-expanded]="isOperacaoExpandida(op.id)"
                        >
                          <div class="flex items-center gap-3 min-w-0">
                            <div
                              class="w-8 h-8 rounded-lg flex items-center justify-center shrink-0 border border-[var(--ces-border)] bg-[var(--ces-surface-subtle)]"
                              [ngClass]="operacaoTone(op.tipo) === 'success' ? 'text-[var(--ces-success)]' : (operacaoTone(op.tipo) === 'danger' ? 'text-[var(--ces-danger)]' : 'text-[var(--ces-brand)]')"
                            >
                              <i [class]="operacaoIcon(op.tipo)" class="text-sm"></i>
                            </div>

                            <div class="flex flex-col gap-0.5 min-w-0">
                              <div class="flex items-center gap-2 flex-wrap">
                                <span class="text-xs font-bold text-[var(--ces-foreground)]">{{ operacaoFriendlyTitle(op.tipo) }}</span>
                                <span class="text-[11px] text-[var(--ces-foreground-muted)] font-mono">#{{ op.id.slice(0, 8) }}</span>
                                @if (op.saqueForaEquipe) {
                                  <span class="text-[9px] px-1.5 py-0.2 rounded bg-[var(--ces-surface-subtle)] text-[var(--ces-foreground-muted)] border border-[var(--ces-border)]">
                                    Saque fora da equipe
                                  </span>
                                }
                              </div>
                              <div class="flex items-center gap-2 flex-wrap text-xs text-[var(--ces-foreground-muted)]">
                                <span class="inline-flex items-center gap-1">
                                  <i class="pi pi-calendar text-[10px]"></i>
                                  {{ op.realizadaEm | date: 'dd/MM/yyyy às HH:mm' }}
                                </span>
                                <span>·</span>
                                <span class="inline-flex items-center gap-1">
                                  <i class="pi pi-user text-[10px]"></i>
                                  Operador: <strong class="text-[var(--ces-foreground)]">{{ resolverNomeUsuario(op.idUsuarioOperador) }}</strong>
                                </span>
                                @if (op.idColaboradorSaque) {
                                  <span>·</span>
                                  <span class="inline-flex items-center gap-1">
                                    <i class="pi pi-id-card text-[10px]"></i>
                                    Saque: <strong class="text-[var(--ces-foreground)]">{{ resolverNomeUsuario(op.idColaboradorSaque) }}</strong>
                                  </span>
                                }
                              </div>
                            </div>
                          </div>

                          <div class="flex items-center gap-3 shrink-0">
                            <span class="text-xs font-bold px-2 py-0.5 rounded border border-[var(--ces-border)] bg-[var(--ces-surface-subtle)] text-[var(--ces-foreground)]">
                              {{ op.itens.length }} {{ op.itens.length === 1 ? 'item' : 'itens' }}
                            </span>
                            <i
                              class="pi text-xs text-[var(--ces-foreground-muted)] transition-transform duration-200"
                              [class.pi-chevron-up]="isOperacaoExpandida(op.id)"
                              [class.pi-chevron-down]="!isOperacaoExpandida(op.id)"
                            ></i>
                          </div>
                        </button>

                        <div [class.hidden]="!isOperacaoExpandida(op.id)" class="border-t border-[var(--ces-border)] p-3 bg-[var(--ces-surface-subtle)]">
                          @if (op.motivo) {
                            <div class="mb-2 text-xs text-[var(--ces-foreground-muted)]">
                              <span class="font-semibold text-[var(--ces-foreground)]">Motivo/Justificativa:</span> {{ op.motivo }}
                            </div>
                          }

                          <div class="rounded-lg border border-[var(--ces-border)] overflow-hidden bg-[var(--ces-surface)]">
                            <table class="w-full text-left text-xs border-collapse">
                              <thead>
                                <tr class="bg-[var(--ces-surface-muted)] border-b border-[var(--ces-border)] text-[11px] font-bold uppercase tracking-wider text-[var(--ces-foreground-muted)]">
                                  <th class="py-2 px-3">Código</th>
                                  <th class="py-2 px-3">Material</th>
                                  <th class="py-2 px-3 text-right">Quantidade</th>
                                  <th class="py-2 px-3 text-center">Condição</th>
                                  <th class="py-2 px-3">Nº Série</th>
                                </tr>
                              </thead>
                              <tbody class="divide-y divide-[var(--ces-border)]">
                                @for (opItem of op.itens; track opItem.id) {
                                  <tr class="hover:bg-[var(--ces-surface-muted)]/50 transition-colors">
                                    <td class="py-2 px-3 font-bold text-xs text-[var(--ces-foreground)] whitespace-nowrap">
                                      {{ opItem.codigoMaterial }}
                                    </td>
                                    <td class="py-2 px-3 text-xs text-[var(--ces-foreground)]">
                                      {{ opItem.descricaoMaterial }}
                                    </td>
                                    <td class="py-2 px-3 text-right font-semibold tabular-nums text-xs text-[var(--ces-foreground)]">
                                      {{ opItem.quantidade | number: '1.2-3' }}
                                    </td>
                                    <td class="py-2 px-3 text-center">
                                      <span
                                        class="text-[10px] font-bold px-1.5 py-0.5 rounded border border-[var(--ces-border)]"
                                        [ngClass]="opItem.condicao === 'NOVO' ? 'bg-[var(--ces-surface-subtle)] text-[var(--ces-success)]' : 'bg-[var(--ces-surface-subtle)] text-[var(--ces-warning)]'"
                                      >
                                        {{ opItem.condicao || 'NOVO' }}
                                      </span>
                                    </td>
                                    <td class="py-2 px-3 text-xs text-[var(--ces-foreground-muted)] font-mono">
                                      {{ opItem.numeroSerie || '—' }}
                                    </td>
                                  </tr>
                                }
                              </tbody>
                            </table>
                          </div>
                        </div>
                      </div>
                    }
                  </div>
                </div>
              </ng-template>
            }
          </ces-tabs>
        </div>
      }

      <!-- RODAPÉ DO DIALOG SHELL COM AÇÕES INTEGRADAS -->
      <div dialog-footer class="flex items-center justify-between w-full pt-3 border-t border-[var(--ces-border)]">
        <!-- Ações secundárias (lado esquerdo) -->
        <div class="flex items-center gap-1.5 flex-wrap">
          @if (detalhe(); as det) {
            <ces-button
              label="Ver Relatório Completo"
              icon="pi pi-external-link"
              variant="ghost"
              size="sm"
              (pressed)="abrirRelatorioCompleto(det.id)"
              pTooltip="Abrir página completa com detalhamento e fluxo de assinaturas"
              tooltipPosition="top"
            />
            <ces-button
              label="Imprimir"
              icon="pi pi-print"
              variant="ghost"
              size="sm"
              (pressed)="imprimir()"
              pTooltip="Visualizar impressão ou gerar PDF do pré-relatório"
              tooltipPosition="top"
            />
          }
        </div>

        <!-- Ações operacionais contextuais (lado direito) -->
        <div class="flex items-center gap-2 flex-wrap">
          @if (detalhe(); as det) {
            @if (podeRejeitar(det)) {
              <ces-button
                label="Rejeitar"
                icon="pi pi-times"
                variant="destructive"
                size="sm"
                (pressed)="executarAcao('REJEITAR')"
              />
            }

            @if (podeAprovar(det)) {
              <ces-button
                label="Aprovar / Planejar"
                icon="pi pi-calendar"
                variant="primary"
                size="sm"
                (pressed)="executarAcao('PLANEJAR')"
              />
            }

            @if (podeIniciarSeparacao(det)) {
              <ces-button
                label="Iniciar Separação"
                icon="pi pi-play"
                variant="primary"
                size="sm"
                (pressed)="executarAcao('INICIAR_SEPARACAO')"
              />
            }

            @if (podePularSeparacao(det)) {
              <ces-button
                label="Pular Separação"
                icon="pi pi-forward"
                variant="outline"
                size="sm"
                (pressed)="executarAcao('PULAR_SEPARACAO')"
              />
            }

            @if (podeConcluirSeparacao(det)) {
              <ces-button
                label="Concluir Separação"
                icon="pi pi-box"
                variant="primary"
                size="sm"
                (pressed)="executarAcao('CONCLUIR_SEPARACAO')"
              />
            }

            @if (podeEntregar(det)) {
              <ces-button
                label="Registrar Entrega"
                icon="pi pi-truck"
                variant="primary"
                size="sm"
                (pressed)="executarAcao('ENTREGAR')"
              />
            }

            @if (podeDevolver(det)) {
              <ces-button
                label="Confirmar Devolução"
                icon="pi pi-undo"
                variant="primary"
                size="sm"
                (pressed)="executarAcao('DEVOLVER')"
              />
            }

            @if (podeEncerrarSaldo(det)) {
              <ces-button
                label="Encerrar Saldo"
                icon="pi pi-lock"
                variant="outline"
                size="sm"
                (pressed)="executarAcao('ENCERRAR_SALDO')"
              />
            }
          }

          <ces-button
            label="Fechar"
            variant="ghost"
            size="sm"
            (pressed)="onClose()"
          />
        </div>
      </div>
    </ces-dialog-shell>

    <!-- MODAL DE PENDÊNCIA (GERAL OU POR ITEM) -->
    @if (detalhe(); as det) {
      <app-solicitacao-pendencia-modal
        [(visible)]="modalPendenciaVisivel"
        [idSolicitacao]="det.id"
        [idItem]="pendenciaItemId()"
        [itemLabel]="pendenciaItemLabel()"
        [motivoAtual]="pendenciaMotivoAtual()"
        [justificativaAtual]="pendenciaJustificativaAtual()"
        (pendenciaSalva)="onPendenciaSalva()"
        (pendenciaResolvida)="onPendenciaResolvida()"
      />
    }
  `,
})
export class SolicitacaoTecnicaKanbanDetailComponent {
  private readonly service = inject(SolicitacaoMaterialTecnicaService);
  private readonly almoxarifadoService = inject(AlmoxarifadoService);
  private readonly rh = inject(RhService);
  private readonly obrasService = inject(ObrasService);
  private readonly router = inject(Router);
  private readonly userLookup = inject(UsuarioLookupService);
  private readonly notifications = inject(NotificationService);

  readonly visible = model(false);
  readonly item = input<SolicitacaoMaterialSummaryResponse | null>(null);

  readonly actionRequested = output<{ id: string; action: string }>();

  protected readonly loading = signal(false);
  protected readonly detalhe = signal<SolicitacaoMaterialDetalheResponse | null>(null);
  protected readonly equipeNome = signal<string>('Sem equipe definida');
  protected readonly obraInfo = signal<ObraResponse | null>(null);
  protected readonly almoxarifadoNome = signal<string>('Almoxarifado Central');
  protected readonly responsavelSaqueNome = signal<string>('A definir');
  protected readonly requisitanteNome = signal<string>('Requisitante');
  protected readonly resolvendoPendencia = signal(false);

  protected readonly abaAtiva = signal<string>('geral');
  protected readonly operacoesExpandidas = signal<string[]>([]);

  // Estados do Modal de Pendência
  protected readonly modalPendenciaVisivel = signal(false);
  protected readonly pendenciaItemId = signal<string | null>(null);
  protected readonly pendenciaItemLabel = signal<string | null>(null);
  protected readonly pendenciaMotivoAtual = signal<MotivoPendenciaResponse | null>(null);
  protected readonly pendenciaJustificativaAtual = signal<string | null>(null);

  // Apresentação e Labels Reutilizados
  protected readonly statusLabel = statusLabel;
  protected readonly statusTone = statusTone;
  protected readonly separacaoLabel = separacaoLabel;
  protected readonly separacaoTone = separacaoTone;
  protected readonly tipoTecnicoLabel = tipoTecnicoLabel;
  protected readonly tipoTecnicoIcon = tipoTecnicoIcon;
  protected readonly tipoTecnicoDescricaoCompleta = tipoTecnicoDescricaoCompleta;
  protected readonly tipoTecnicoEhAdicao = tipoTecnicoEhAdicao;

  protected readonly idCurto = computed(() => {
    const it = this.item() || this.detalhe();
    return it ? it.id.slice(0, 8) : '';
  });

  protected readonly tipoEhAdicao = computed(() => {
    const d = this.detalhe();
    return tipoTecnicoEhAdicao(d?.tecnico?.tipoTecnico);
  });

  protected readonly dialogTitle = computed(() => {
    const it = this.item();
    const d = this.detalhe();
    const id = it?.id || d?.id;
    if (!id) return 'Detalhes da Solicitação Técnica';
    const oc = d?.tecnico?.ocorrencia?.codigo || it?.ocorrencia?.codigo;
    const ocTexto = oc ? ` · OC ${oc}` : '';
    return `Solicitação Técnica #${id.slice(0, 8)}${ocTexto}`;
  });

  protected readonly obraDisplay = computed(() => {
    const obra = this.obraInfo();
    if (obra) return `${obra.numero} — ${obra.titulo}`;
    const d = this.detalhe();
    const it = this.item();
    const idObra = d?.tecnico?.idObra || it?.idObra;
    if (idObra) return `Obra #${idObra.slice(0, 8)}`;
    const tipo = d?.tecnico?.tipoObra || it?.tipoObra;
    if (tipo) return `Obra (${tipo})`;
    return 'Obra vinculada';
  });

  protected readonly rotaOrigem = computed(() => {
    const d = this.detalhe();
    if (!d) return '—';
    if (this.tipoEhAdicao()) {
      return d.estoqueOrigem?.nomeIdentificador || this.almoxarifadoNome();
    }
    return d.estoqueOrigem?.nomeIdentificador || this.equipeNome();
  });

  protected readonly rotaDestino = computed(() => {
    const d = this.detalhe();
    if (!d) return '—';
    if (this.tipoEhAdicao()) {
      return d.estoqueDestino?.nomeIdentificador || this.equipeNome();
    }
    return d.estoqueDestino?.nomeIdentificador || this.almoxarifadoNome();
  });

  protected readonly totalItens = computed(() => this.detalhe()?.itens?.length ?? 0);

  protected readonly totalSolicitado = computed(() =>
    (this.detalhe()?.itens || []).reduce((acc, i) => acc + (Number(i.quantidade) || 0), 0)
  );

  protected readonly totalAtendido = computed(() =>
    (this.detalhe()?.itens || []).reduce((acc, i) => acc + this.itemAtendido(i), 0)
  );

  protected readonly totalPendente = computed(() =>
    (this.detalhe()?.itens || []).reduce((acc, i) => acc + this.itemPendente(i), 0)
  );

  protected readonly percentualAtendido = computed(() => {
    const sol = this.totalSolicitado();
    if (sol <= 0) return 0;
    const atendido = this.totalAtendido();
    return Math.min(100, Math.round((atendido / sol) * 100));
  });

  protected readonly abas = computed<CesTabItem[]>(() => {
    const det = this.detalhe();
    const tabs: CesTabItem[] = [
      { value: 'geral', label: 'Materiais & Saldos', icon: 'pi pi-box' },
    ];
    if (det?.tecnico?.operacoes && det.tecnico.operacoes.length > 0) {
      tabs.push({
        value: 'historico',
        label: `Histórico (${det.tecnico.operacoes.length})`,
        icon: 'pi pi-history',
      });
    }
    return tabs;
  });

  protected readonly perguntaCopiloto = computed(() => {
    const it = this.item() || this.detalhe();
    if (!it) return 'Analise esta solicitação técnica.';
    const oc = this.detalhe()?.tecnico?.ocorrencia?.codigo || this.item()?.ocorrencia?.codigo || 'N/A';
    return `Faça uma análise operacional da solicitação técnica #${it.id.slice(0, 8)} (OC: ${oc}, status: ${it.status}), destacando prazos, divergências de materiais e os próximos passos para atendimento.`;
  });

  constructor() {
    effect(() => {
      const visible = this.visible();
      const item = this.item();
      if (visible && item) {
        this.abaAtiva.set('geral');
        this.carregarDetalhe(item.id);
      } else if (!visible) {
        this.detalhe.set(null);
        this.obraInfo.set(null);
      }
    });
  }

  protected itemAtendido(item: ItemSolicitacaoTecnicaResponse): number {
    if (this.tipoEhAdicao()) {
      return item.quantidadeEntregaLiquida ?? item.quantidadeEntregue ?? 0;
    }
    return item.quantidadeDevolucaoLiquida ?? item.quantidadeDevolvidaBruta ?? 0;
  }

  protected itemPendente(item: ItemSolicitacaoTecnicaResponse): number {
    if (item.quantidadePendente !== undefined && item.quantidadePendente !== null) {
      return item.quantidadePendente;
    }
    return Math.max(0, item.quantidade - this.itemAtendido(item));
  }

  protected etapaLabel(etapa?: string | null): string {
    const map: Record<string, string> = {
      AGUARDANDO_PLANEJAMENTO: 'Aguardando Planejamento',
      PRONTO_PARA_SEPARACAO: 'Pronto para Separação',
      EM_SEPARACAO: 'Em Separação',
      PENDENTE_ENTREGA: 'Pendente Entrega',
      PENDENTE_DEVOLUCAO: 'Pendente Devolução',
      CONCLUIDA: 'Concluída',
    };
    return map[etapa ?? ''] || etapa || '—';
  }

  protected podeRegistrarPendencia(): boolean {
    const status = this.detalhe()?.status;
    return status === 'PENDENTE' || status === 'APROVADO';
  }

  protected podeAprovar(det: SolicitacaoMaterialDetalheResponse): boolean {
    return (
      det.acoesPermitidas?.includes('APROVAR') ||
      det.acoesPermitidas?.includes('EDITAR_PLANEJAMENTO')
    );
  }

  protected podeIniciarSeparacao(det: SolicitacaoMaterialDetalheResponse): boolean {
    return det.acoesPermitidas?.includes('INICIAR_SEPARACAO') ?? false;
  }

  protected podeConcluirSeparacao(det: SolicitacaoMaterialDetalheResponse): boolean {
    return det.acoesPermitidas?.includes('CONCLUIR_SEPARACAO') ?? false;
  }

  protected podePularSeparacao(det: SolicitacaoMaterialDetalheResponse): boolean {
    return det.acoesPermitidas?.includes('PULAR_SEPARACAO') ?? false;
  }

  protected podeEntregar(det: SolicitacaoMaterialDetalheResponse): boolean {
    return this.tipoEhAdicao() && (det.acoesPermitidas?.includes('ENTREGAR') ?? false);
  }

  protected podeDevolver(det: SolicitacaoMaterialDetalheResponse): boolean {
    return !this.tipoEhAdicao() && (det.acoesPermitidas?.includes('CONFIRMAR_DEVOLUCAO') ?? false);
  }

  protected podeEncerrarSaldo(det: SolicitacaoMaterialDetalheResponse): boolean {
    return det.acoesPermitidas?.includes('ENCERRAR_SALDO') ?? false;
  }

  protected podeRejeitar(det: SolicitacaoMaterialDetalheResponse): boolean {
    return det.acoesPermitidas?.includes('REJEITAR') ?? false;
  }

  protected carregarDetalhe(id: string): void {
    this.loading.set(true);
    this.service.buscarPorId(id).subscribe({
      next: (res) => {
        this.detalhe.set(res);
        this.loading.set(false);

        // 1. Resolução do nome do requisitante
        if (res.idRequisitante) {
          this.requisitanteNome.set(this.userLookup.resolverNome(res.idRequisitante, 'Requisitante'));
        }

        // 2. Resolução do colaborador de saque
        if (res.tecnico?.idColaboradorSaque) {
          this.responsavelSaqueNome.set(
            this.userLookup.resolverNome(res.tecnico.idColaboradorSaque, 'Colaborador do Saque')
          );
        } else {
          this.responsavelSaqueNome.set('A definir');
        }

        // 3. Resolução da Equipe
        if (res.tecnico?.idEquipe) {
          this.rh.buscarEquipePorId(res.tecnico.idEquipe).subscribe({
            next: (eq) => {
              const label = eq.tipoEquipe ? `${eq.codigo} · ${eq.tipoEquipe.nome}` : eq.codigo;
              this.equipeNome.set(label);
            },
            error: () => this.equipeNome.set('Equipe Vinculada'),
          });
        } else {
          this.equipeNome.set('Sem equipe definida');
        }

        // 4. Resolução da Obra (se houver idObra)
        if (res.tecnico?.idObra) {
          this.obrasService.buscarObraPorId(res.tecnico.idObra).subscribe({
            next: (o) => this.obraInfo.set(o),
            error: () => this.obraInfo.set(null),
          });
        } else {
          this.obraInfo.set(null);
        }

        // 5. Resolução do Almoxarifado
        if (res.estoqueOrigem?.nomeIdentificador && tipoTecnicoEhAdicao(res.tecnico?.tipoTecnico)) {
          this.almoxarifadoNome.set(res.estoqueOrigem.nomeIdentificador);
        } else if (res.estoqueDestino?.nomeIdentificador && !tipoTecnicoEhAdicao(res.tecnico?.tipoTecnico)) {
          this.almoxarifadoNome.set(res.estoqueDestino.nomeIdentificador);
        } else if (res.tecnico?.idAlmoxarifadoAtendimento) {
          this.almoxarifadoService.listarEstoquesAtivos().subscribe({
            next: (paged) => {
              const found = (paged.content || []).find((w) => w.id === res.tecnico?.idAlmoxarifadoAtendimento);
              if (found) {
                this.almoxarifadoNome.set(found.nomeIdentificador);
              }
            },
          });
        }
      },
      error: () => {
        this.loading.set(false);
        this.notifications.error('Não foi possível carregar os detalhes da solicitação técnica.');
      },
    });
  }

  protected abrirRelatorioCompleto(id: string): void {
    this.visible.set(false);
    void navegarSaindoDoDrawer(() => this.router.navigate(['/almoxarifado/solicitacoes-tecnicas', id]));
  }

  protected imprimir(): void {
    window.print();
  }

  protected executarAcao(action: string): void {
    const d = this.detalhe();
    if (!d) return;
    this.actionRequested.emit({ id: d.id, action });
  }

  protected resolverNomeUsuario(id?: string | null): string {
    return this.userLookup.resolverNome(id, 'Usuário');
  }

  // --- Operações e Histórico ---
  protected alternarOperacao(opId: string): void {
    this.operacoesExpandidas.update((list) =>
      list.includes(opId) ? list.filter((id) => id !== opId) : [...list, opId]
    );
  }

  protected isOperacaoExpandida(opId: string): boolean {
    return this.operacoesExpandidas().includes(opId);
  }

  protected expandirTodasOperacoes(): void {
    const ops = this.detalhe()?.tecnico?.operacoes ?? [];
    this.operacoesExpandidas.set(ops.map((o) => o.id));
  }

  protected recolherTodasOperacoes(): void {
    this.operacoesExpandidas.set([]);
  }

  protected operacaoFriendlyTitle(tipo: string): string {
    const t = (tipo || '').toUpperCase();
    if (t === 'ENTREGA') return 'Entrega de Materiais';
    if (t === 'DEVOLUCAO') return 'Devolução ao Almoxarifado';
    if (t === 'ESTORNO') return 'Correção / Estorno de Movimentação';
    if (t.includes('ENCERRA')) return 'Encerramento de Saldo Remanescente';
    if (t.includes('CANCEL')) return 'Cancelamento da Solicitação';
    return tipo || 'Movimentação';
  }

  protected operacaoTone(tipo: string): CesTone {
    const t = (tipo || '').toUpperCase();
    if (t === 'ENTREGA' || t === 'DEVOLUCAO') return 'success';
    if (t === 'ESTORNO') return 'danger';
    if (t.includes('ENCERRA')) return 'neutral';
    return 'info';
  }

  protected operacaoIcon(tipo: string): string {
    const t = (tipo || '').toUpperCase();
    if (t === 'ENTREGA') return 'pi pi-truck';
    if (t === 'DEVOLUCAO') return 'pi pi-undo';
    if (t === 'ESTORNO') return 'pi pi-replay';
    if (t.includes('ENCERRA')) return 'pi pi-lock';
    return 'pi pi-box';
  }

  // --- Gestão de Pendências ---
  protected abrirPendenciaItem(item: ItemSolicitacaoTecnicaResponse): void {
    const det = this.detalhe();
    if (!det || !item?.id) return;
    this.pendenciaItemId.set(item.id);
    this.pendenciaItemLabel.set(`${item.material.codigo} — ${item.material.descricao}`);
    this.pendenciaMotivoAtual.set(item.motivoPendencia ?? null);
    this.pendenciaJustificativaAtual.set(item.justificativaPendencia ?? null);
    this.modalPendenciaVisivel.set(true);
  }

  protected abrirPendenciaGeral(): void {
    const det = this.detalhe();
    if (!det) return;
    this.pendenciaItemId.set(null);
    this.pendenciaItemLabel.set(null);
    this.pendenciaMotivoAtual.set(det.motivoPendencia ?? null);
    this.pendenciaJustificativaAtual.set(det.justificativaPendencia ?? null);
    this.modalPendenciaVisivel.set(true);
  }

  protected resolverPendenciaGeral(): void {
    const det = this.detalhe();
    if (!det) return;
    this.resolvendoPendencia.set(true);
    this.almoxarifadoService.removerPendencia(det.id).subscribe({
      next: () => {
        this.resolvendoPendencia.set(false);
        this.notifications.success('Pendência geral resolvida com sucesso.');
        this.carregarDetalhe(det.id);
      },
      error: (err) => {
        this.resolvendoPendencia.set(false);
        this.notifications.error(getErrorMessage(err) || 'Erro ao resolver pendência geral.');
      },
    });
  }

  protected onPendenciaSalva(): void {
    const det = this.detalhe();
    if (det) {
      this.carregarDetalhe(det.id);
    }
  }

  protected onPendenciaResolvida(): void {
    const det = this.detalhe();
    if (det) {
      this.carregarDetalhe(det.id);
    }
  }

  protected onClose(): void {
    this.visible.set(false);
  }
}
