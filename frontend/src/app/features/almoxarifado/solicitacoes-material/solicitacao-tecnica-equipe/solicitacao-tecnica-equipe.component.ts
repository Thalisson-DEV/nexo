import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnInit,
  computed,
  inject,
  input,
  model,
  output,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TooltipModule } from 'primeng/tooltip';
import {
  CesDialogShellComponent,
  NotificationService,
} from '@ces/ui';
import { EquipeResponse } from '../../../../core/models/rh.models';
import { ObraResponse } from '../../../../core/models/obras.models';
import { ObrasService } from '../../../../core/services/obras.service';
import {
  DetalhesTecnicosSolicitacaoResponse,
  PlanejamentoSolicitacaoTecnicaResponse,
  PlanejarSolicitacaoTecnicaRequest,
  SolicitacaoMaterialDetalheResponse,
} from '../../../../core/models/solicitacao-material-tecnica.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { SolicitacaoMaterialTecnicaService } from '../../../../core/services/solicitacao-material-tecnica.service';
import { ehConflitoDeVersao } from '../../../../core/utils/solicitacao-tecnica-error.util';
import {
  possuiMovimentacaoAtendimento,
  separacaoLabel,
  separacaoTagClass,
  separacaoTone,
  statusLabel,
  statusTagClass,
  statusTone,
  tipoTecnicoEhAdicao,
  tipoTecnicoIcon,
  tipoTecnicoLabel,
} from '../solicitacao-tecnica-presentation';

@Component({
  selector: 'app-solicitacao-tecnica-equipe',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    SelectModule,
    TooltipModule,
    CesDialogShellComponent,
  ],
  templateUrl: './solicitacao-tecnica-equipe.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SolicitacaoTecnicaEquipeComponent implements OnInit {
  private readonly service = inject(SolicitacaoMaterialTecnicaService);
  private readonly obrasService = inject(ObrasService, { optional: true });
  private readonly notifications = inject(NotificationService);
  private readonly cdr = inject(ChangeDetectorRef);

  readonly visible = model(false);
  readonly detail = input<SolicitacaoMaterialDetalheResponse | null>(null);
  readonly teams = input<EquipeResponse[]>([]);
  readonly completed = output<PlanejamentoSolicitacaoTecnicaResponse>();
  readonly conflict = output<{ intent: string; currentVersion?: number }>();

  protected readonly teamId = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly obraInfo = signal<ObraResponse | null>(null);

  protected readonly technical = computed<DetalhesTecnicosSolicitacaoResponse>(
    () => this.detail()?.tecnico ?? ({} as any),
  );

  protected readonly fixedType = computed(() => this.technical().tipoTecnico);
  protected readonly fixedTypeLabel = computed(() => tipoTecnicoLabel(this.fixedType()));
  protected readonly fixedTypeIcon = computed(() => tipoTecnicoIcon(this.fixedType()));
  protected readonly isAddition = computed(() => tipoTecnicoEhAdicao(this.fixedType()));
  protected readonly hasDeliveryMovement = computed(() =>
    possuiMovimentacaoAtendimento(this.detail()),
  );

  protected readonly obraDisplay = computed(() => {
    const obra = this.obraInfo();
    if (obra) return `Obra ${obra.numero} — ${obra.titulo}`;
    return this.technical()?.tipoObra ? `Obra (${this.technical().tipoObra})` : 'Obra vinculada';
  });

  protected readonly teamOptions = computed(() =>
    (this.teams() ?? []).map((team) => ({
      label: team.tipoEquipe ? `${team.codigo} · ${team.tipoEquipe.nome}` : team.codigo,
      value: team.id,
    })),
  );

  protected readonly currentTeamName = computed(() => {
    const id = this.technical()?.idEquipe;
    if (!id) return 'Nenhuma equipe atribuída';
    const team = (this.teams() ?? []).find((t) => t.id === id);
    if (!team) return 'Equipe vinculada';
    return team.tipoEquipe ? `${team.codigo} · ${team.tipoEquipe.nome}` : team.codigo;
  });

  protected readonly selectedTeamName = computed(() => {
    const id = this.teamId();
    if (!id) return 'Nenhuma selecionada';
    const team = (this.teams() ?? []).find((t) => t.id === id);
    if (!team) return 'Equipe selecionada';
    return team.tipoEquipe ? `${team.codigo} · ${team.tipoEquipe.nome}` : team.codigo;
  });

  protected readonly unchanged = computed(
    () => this.teamId() === (this.detail()?.tecnico?.idEquipe ?? null),
  );

  protected readonly statusLabel = statusLabel;
  protected readonly statusTone = statusTone;
  protected readonly statusTagClass = statusTagClass;
  protected readonly separacaoLabel = separacaoLabel;
  protected readonly separacaoTone = separacaoTone;
  protected readonly separacaoTagClass = separacaoTagClass;
  protected readonly tipoTecnicoIcon = tipoTecnicoIcon;
  protected readonly tipoTecnicoLabel = tipoTecnicoLabel;
  protected readonly tipoTecnicoEhAdicao = tipoTecnicoEhAdicao;

  ngOnInit(): void {
    const detail = this.detail();
    if (!detail) return;
    this.teamId.set(detail.tecnico?.idEquipe ?? null);
    const idObra = detail.tecnico?.idObra;
    if (idObra && typeof this.obrasService?.buscarObraPorId === 'function') {
      this.obrasService.buscarObraPorId(idObra).subscribe({
        next: (obra: ObraResponse) => {
          this.obraInfo.set(obra);
          this.cdr.markForCheck();
        },
        error: () => {},
      });
    }
  }

  protected submit(): void {
    if (this.hasDeliveryMovement()) {
      this.notifications.warning('Não é permitido alterar a equipe após a realização de entregas.');
      return;
    }
    const detail = this.detail();
    if (!detail) return;
    const technical = detail.tecnico;
    const idEquipe = this.teamId();
    if (!technical || !idEquipe || this.unchanged() || this.submitting()) return;

    const request: PlanejarSolicitacaoTecnicaRequest = {
      versaoEsperada: technical.versao,
      idObra: technical.idObra,
      ocorrencia: technical.ocorrencia?.codigo ?? null,
      idAlmoxarifadoAtendimento: technical.idAlmoxarifadoAtendimento,
      idEquipe,
      dataPrevista: technical.dataPrevista,
      observacao: technical.observacao ?? null,
      itens: detail.itens.map((item) => ({
        idItem: item.id,
        idMaterial: item.material.id,
        tipoMovimentacao: item.tipoMovimentacao,
        quantidade: item.quantidade,
      })),
    };

    this.submitting.set(true);
    this.service.substituirPlanejamento(detail.id, request).subscribe({
      next: (response) => {
        this.submitting.set(false);
        this.visible.set(false);
        this.notifications.success('Equipe responsável atualizada com sucesso.');
        this.completed.emit(response);
        this.cdr.markForCheck();
      },
      error: (error) => {
        this.submitting.set(false);
        if (ehConflitoDeVersao(error)) {
          this.visible.set(false);
          this.conflict.emit({
            intent: 'Definir equipe responsável',
            currentVersion: error?.error?.versaoAtual,
          });
          this.cdr.markForCheck();
          return;
        }
        this.notifications.error(getErrorMessage(error), { title: 'Equipe não atualizada' });
        this.cdr.markForCheck();
      },
    });
  }

  protected close(): void {
    if (!this.submitting()) this.visible.set(false);
  }
}
