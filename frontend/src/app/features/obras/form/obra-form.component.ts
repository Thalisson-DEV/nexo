import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MenuItem, MessageService } from 'primeng/api';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';

import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MultiSelectModule } from 'primeng/multiselect';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { SelectModule } from 'primeng/select';
import { StepperModule } from 'primeng/stepper';
import { TextareaModule } from 'primeng/textarea';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TooltipModule } from 'primeng/tooltip';
import {
  AgenteResponsavelResponse,
  ObraRequest,
  ObraResponse,
  ObraResumoResponse,
  StatusObraResponse,
  TipoVinculoSubprojeto
} from '../../../core/models/obras.models';
import {
  BaseOperacionalResponse,
  CentroFaturamentoResponse,
  CoordenadorMinResponse,
  EquipeResponse,
  MunicipioResponse,
  ProcessoNegocioResponse,
  SupervisorMinResponse
} from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { ObrasService } from '../../../core/services/obras.service';
import { RhService } from '../../../core/services/rh.service';
import { LocationPickerDialogComponent, LocalSelecionado } from '../../../shared/components/location-picker-dialog/location-picker-dialog.component';
import {
  TIPO_VINCULO_OPTIONS,
  formatarDataParaApi,
  formatarMoeda,
  parseDataBrParaDate,
  sanitizarNumeroObra,
  sanitizarTituloObra,
  statusClass,
  statusIcon
} from '../obras-ui';

@Component({
  selector: 'app-obra-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    BreadcrumbModule,
    ButtonModule,
    DatePickerModule,
    InputNumberModule,
    InputTextModule,
    MultiSelectModule,
    ProgressSpinnerModule,
    SelectModule,
    StepperModule,
    TextareaModule,
    ToggleSwitchModule,
    TooltipModule,
    LocationPickerDialogComponent
  ],
  templateUrl: './obra-form.component.html',
  styleUrl: './obra-form.component.css'
})
export class ObraFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(ObrasService);
  private readonly rhService = inject(RhService);
  private readonly messages = inject(MessageService);

  protected readonly isEdit = signal(false);
  protected readonly obraId = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly activeStep = signal(1);

  // Lookups
  protected readonly statusList = signal<StatusObraResponse[]>([]);
  protected readonly basesList = signal<BaseOperacionalResponse[]>([]);
  protected readonly processosList = signal<ProcessoNegocioResponse[]>([]);
  protected readonly municipiosList = signal<MunicipioResponse[]>([]);
  protected readonly centrosCustoList = signal<CentroFaturamentoResponse[]>([]);
  protected readonly supervisoresList = signal<SupervisorMinResponse[]>([]);
  protected readonly coordenadoresList = signal<CoordenadorMinResponse[]>([]);
  protected readonly agentesList = signal<AgenteResponsavelResponse[]>([]);
  protected readonly equipesList = signal<EquipeResponse[]>([]);
  protected readonly obrasPrincipaisList = signal<ObraResumoResponse[]>([]);

  // Constantes e Helpers
  protected readonly tipoVinculoOptions = TIPO_VINCULO_OPTIONS;
  protected readonly formatarMoeda = formatarMoeda;
  protected readonly statusClass = statusClass;
  protected readonly statusIcon = statusIcon;

  // Breadcrumb
  protected breadcrumbItems: MenuItem[] = [
    { icon: 'pi pi-home', routerLink: '/home' },
    { label: 'Obras', routerLink: '/obras' },
    { label: 'Nova Obra' }
  ];

  // Formulário Reativo
  protected form!: FormGroup;

  ngOnInit(): void {
    this.initForm();
    this.carregarLookups();

    const id = this.route.snapshot.paramMap.get('id');
    if (id && id !== 'novo' && id !== 'nova') {
      this.isEdit.set(true);
      this.obraId.set(id);
      this.breadcrumbItems = [
        { icon: 'pi pi-home', routerLink: '/home' },
        { label: 'Obras', routerLink: '/obras' },
        { label: 'Editar Obra' }
      ];
      this.carregarObra(id);
    } else {
      this.isEdit.set(false);
      this.obraId.set(null);
    }
  }

  protected voltar(): void {
    if (this.obraId()) {
      this.router.navigate(['/obras', this.obraId()]);
    } else {
      this.router.navigate(['/obras']);
    }
  }

  protected onNumeroChange(): void {
    const raw = this.form.get('numero')?.value;
    const sanitizado = sanitizarNumeroObra(raw);
    if (raw !== sanitizado) {
      this.form.get('numero')?.setValue(sanitizado, { emitEvent: false });
    }
  }

  protected onTituloChange(): void {
    const raw = this.form.get('titulo')?.value;
    const sanitizado = sanitizarTituloObra(raw);
    if (raw !== sanitizado) {
      this.form.get('titulo')?.setValue(sanitizado, { emitEvent: false });
    }
  }

  protected isStep1Valid(): boolean {
    const num = this.form.get('numero');
    const tit = this.form.get('titulo');
    return (num?.valid ?? false) && (tit?.valid ?? false);
  }

  protected isStep2Valid(): boolean {
    const proc = this.form.get('idProcesso');
    const base = this.form.get('idBaseOperacional');
    const mun = this.form.get('idMunicipio');
    const cc = this.form.get('idCentroCusto');
    const sup = this.form.get('idSupervisor');
    const coord = this.form.get('idCoordenador');
    const st = this.form.get('idStatusObra');
    const dtIni = this.form.get('dataInicio');
    const dtFim = this.form.get('dataPrevisaoFim');
    return (
      (proc?.valid ?? false) &&
      (base?.valid ?? false) &&
      (mun?.valid ?? false) &&
      (cc?.valid ?? false) &&
      (sup?.valid ?? false) &&
      (coord?.valid ?? false) &&
      (st?.valid ?? false) &&
      (dtIni?.valid ?? false) &&
      (dtFim?.valid ?? false)
    );
  }

  protected isStep3Valid(): boolean {
    const ehSub = this.form.get('ehSubprojeto')?.value;
    if (ehSub) {
      const paiId = this.form.get('idObraPai')?.value;
      const tipo = this.form.get('tipoVinculoPai')?.value;
      return !!(paiId && tipo);
    }
    return true;
  }

  // -------------------------------------------------------------------------
  // Navegação entre etapas (padrão Nexo: irPara/proximaEtapa/etapaAnterior)
  // -------------------------------------------------------------------------

  protected irPara(etapa: number): void {
    // Voltar para uma etapa anterior: sempre permitido, sem re-validar.
    if (etapa <= this.activeStep()) {
      this.activeStep.set(etapa);
      return;
    }
    // Avançar: valida cada etapa intermediária.
    if (etapa > 1 && !this.validarAObra()) return;
    if (etapa > 2 && !this.validarPlanejamento()) return;
    if (etapa > 3 && !this.validarExecucao()) return;
    this.activeStep.set(etapa);
  }

  protected proximaEtapa(): void {
    this.irPara(this.activeStep() + 1);
  }

  protected etapaAnterior(): void {
    this.activeStep.set(Math.max(1, this.activeStep() - 1));
  }

  private validarAObra(): boolean {
    if (this.isStep1Valid()) return true;
    this.form.get('numero')?.markAsTouched();
    this.form.get('titulo')?.markAsTouched();
    this.messages.add({
      severity: 'warn',
      summary: 'Identificação incompleta',
      detail: 'Preencha o Número e o Título da obra.'
    });
    return false;
  }

  private validarPlanejamento(): boolean {
    if (this.isStep2Valid()) return true;
    this.form.get('idProcesso')?.markAsTouched();
    this.form.get('idBaseOperacional')?.markAsTouched();
    this.form.get('idMunicipio')?.markAsTouched();
    this.form.get('idCentroCusto')?.markAsTouched();
    this.form.get('idSupervisor')?.markAsTouched();
    this.form.get('idCoordenador')?.markAsTouched();
    this.form.get('idStatusObra')?.markAsTouched();
    this.form.get('dataInicio')?.markAsTouched();
    this.form.get('dataPrevisaoFim')?.markAsTouched();
    this.messages.add({
      severity: 'warn',
      summary: 'Planejamento incompleto',
      detail: 'Preencha Processo, Base, Município, Centro de Custo, Supervisor, Coordenador, Status e Datas.'
    });
    return false;
  }

  private validarExecucao(): boolean {
    if (this.isStep3Valid()) return true;
    this.messages.add({
      severity: 'warn',
      summary: 'Subprojeto incompleto',
      detail: 'Selecione o Projeto Principal e o Tipo de Vínculo do subprojeto.'
    });
    return false;
  }

  protected capturarLocalizacaoAtual(): void {
    if (!navigator.geolocation) {
      this.messages.add({
        severity: 'warn',
        summary: 'Geolocalização indisponível',
        detail: 'Seu navegador não suporta captura de GPS.'
      });
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        this.form.patchValue({
          latitude: parseFloat(pos.coords.latitude.toFixed(6)),
          longitude: parseFloat(pos.coords.longitude.toFixed(6))
        });
        this.messages.add({
          severity: 'info',
          summary: 'Coordenadas capturadas',
          detail: `Lat: ${pos.coords.latitude.toFixed(5)}, Lon: ${pos.coords.longitude.toFixed(5)}`
        });
      },
      (err) => {
        this.messages.add({
          severity: 'error',
          summary: 'Erro de GPS',
          detail: err.message || 'Não foi possível capturar sua localização.'
        });
      },
      { enableHighAccuracy: true, timeout: 10000 }
    );
  }

  protected onLocalConfirmado(local: LocalSelecionado): void {
    this.form.patchValue({
      latitude: parseFloat(local.latitude.toFixed(6)),
      longitude: parseFloat(local.longitude.toFixed(6))
    });
    this.messages.add({
      severity: 'info',
      summary: 'Localização selecionada',
      detail: `Lat: ${local.latitude.toFixed(5)}, Lon: ${local.longitude.toFixed(5)}`
    });
  }

  protected statusSelecionadoEhConcluido(): boolean {
    const statusId = this.form?.get('idStatusObra')?.value;
    if (!statusId) return false;
    const st = this.statusList().find(s => s.id === statusId);
    return st?.concluida ?? false;
  }

  // Label resolvers for Review Step
  protected getStatusObj(): StatusObraResponse | undefined {
    return this.statusList().find(s => s.id === this.form.get('idStatusObra')?.value);
  }

  protected getProcessoNome(): string {
    const id = this.form.get('idProcesso')?.value;
    return this.processosList().find(p => p.id === id)?.nome || '—';
  }

  protected getBaseNome(): string {
    const id = this.form.get('idBaseOperacional')?.value;
    return this.basesList().find(b => b.id === id)?.nome || '—';
  }

  protected getMunicipioNome(): string {
    const id = this.form.get('idMunicipio')?.value;
    return this.municipiosList().find(m => m.id === id)?.nome || '—';
  }

  protected getCentroCustoNome(): string {
    const id = this.form.get('idCentroCusto')?.value;
    return this.centrosCustoList().find(c => c.id === id)?.nome || '—';
  }

  protected getSupervisorNome(): string {
    const id = this.form.get('idSupervisor')?.value;
    return this.supervisoresList().find(s => s.id === id)?.nomeColaborador || '—';
  }

  protected getCoordenadorNome(): string {
    const id = this.form.get('idCoordenador')?.value;
    return this.coordenadoresList().find(c => c.id === id)?.nomeColaborador || '—';
  }

  protected getAgenteNome(): string {
    const id = this.form.get('idAgenteResponsavel')?.value;
    return this.agentesList().find(a => a.id === id)?.nome || '—';
  }

  protected getObraPaiDisplay(): string {
    const paiId = this.form.get('idObraPai')?.value;
    if (!paiId) return '—';
    const obra = this.obrasPrincipaisList().find(o => o.id === paiId);
    if (obra) {
      return `${obra.numero} — ${obra.titulo}`;
    }
    return paiId;
  }

  protected getEquipesSelecionadas(): EquipeResponse[] {
    const ids: string[] = this.form.get('equipes')?.value || [];
    return this.equipesList().filter(e => ids.includes(e.id));
  }

  protected salvar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.messages.add({
        severity: 'warn',
        summary: 'Formulário incompleto',
        detail: 'Por favor, revise as etapas e preencha os campos obrigatórios.'
      });
      return;
    }

    const val = this.form.value;

    const request: ObraRequest = {
      numero: sanitizarNumeroObra(val.numero),
      titulo: sanitizarTituloObra(val.titulo),
      descricao: val.descricao?.trim() || undefined,
      notaObra: val.notaObra?.trim() || undefined,
      valorProjeto: val.valorProjeto ?? undefined,
      dataInicio: formatarDataParaApi(val.dataInicio)!,
      dataPrevisaoFim: formatarDataParaApi(val.dataPrevisaoFim)!,
      dataFimEfetiva: formatarDataParaApi(val.dataFimEfetiva) || null,
      latitude: val.latitude ?? undefined,
      longitude: val.longitude ?? undefined,
      idProcesso: val.idProcesso,
      idBaseOperacional: val.idBaseOperacional,
      idMunicipio: val.idMunicipio,
      idCentroCusto: val.idCentroCusto,
      idSupervisor: val.idSupervisor,
      idCoordenador: val.idCoordenador,
      idStatusObra: val.idStatusObra,
      idAgenteResponsavel: val.idAgenteResponsavel || undefined,
      idObraPai: val.ehSubprojeto ? (val.idObraPai || null) : null,
      tipoVinculoPai: val.ehSubprojeto ? val.tipoVinculoPai : null,
      equipes: val.equipes?.length > 0 ? val.equipes : undefined
    };

    if (this.isEdit() && request.idObraPai === this.obraId()) {
      this.messages.add({
        severity: 'error',
        summary: 'Vínculo Inválido',
        detail: 'Uma obra não pode ser vinculada como subprojeto de si mesma.'
      });
      return;
    }

    this.saving.set(true);

    const operacao$ = this.isEdit()
      ? this.service.atualizarObra(this.obraId()!, request)
      : this.service.criarObra(request);

    operacao$.subscribe({
      next: (obraSalva) => {
        this.saving.set(false);
        const numero = obraSalva?.numero || val.numero;
        const idSalvo = obraSalva?.id || this.obraId();
        this.messages.add({
          severity: 'success',
          summary: 'Obra Salva com Sucesso',
          detail: `A obra ${numero} foi ${this.isEdit() ? 'atualizada' : 'cadastrada'} com sucesso.`
        });
        if (idSalvo) {
          this.router.navigate(['/obras', idSalvo]);
        } else {
          this.router.navigate(['/obras']);
        }
      },
      error: (error: any) => {
        this.saving.set(false);
        this.messages.add({
          severity: 'error',
          summary: 'Erro ao salvar obra',
          detail: getErrorMessage(error)
        });
      }
    });
  }

  private initForm(): void {
    this.form = this.fb.group({
      numero: ['', [Validators.required, Validators.maxLength(7)]],
      titulo: ['', [Validators.required, Validators.maxLength(300)]],
      notaObra: ['', [Validators.maxLength(50)]],
      descricao: ['', [Validators.maxLength(1000)]],
      idStatusObra: [null, [Validators.required]],
      valorProjeto: [null],
      dataInicio: [null, [Validators.required]],
      dataPrevisaoFim: [null, [Validators.required]],
      dataFimEfetiva: [null],
      idProcesso: [null, [Validators.required]],
      idBaseOperacional: [null, [Validators.required]],
      idMunicipio: [null, [Validators.required]],
      idCentroCusto: [null, [Validators.required]],
      idSupervisor: [null, [Validators.required]],
      idCoordenador: [null, [Validators.required]],
      idAgenteResponsavel: [null],
      equipes: [[]],
      ehSubprojeto: [false],
      idObraPai: [null],
      tipoVinculoPai: [TipoVinculoSubprojeto.KIT],
      latitude: [null],
      longitude: [null]
    });
  }

  private carregarObra(id: string): void {
    this.loading.set(true);
    this.service.buscarObraPorId(id).subscribe({
      next: (obra: ObraResponse) => {
        this.form.patchValue({
          numero: obra.numero,
          titulo: obra.titulo,
          notaObra: obra.notaObra || '',
          descricao: obra.descricao || '',
          idStatusObra: obra.statusId,
          valorProjeto: obra.valorProjeto ?? null,
          dataInicio: parseDataBrParaDate(obra.dataInicio),
          dataPrevisaoFim: parseDataBrParaDate(obra.dataPrevisaoFim),
          dataFimEfetiva: parseDataBrParaDate(obra.dataFimEfetiva),
          idProcesso: obra.processoId || null,
          idBaseOperacional: obra.baseOperacionalId || null,
          idMunicipio: obra.municipioId || null,
          idCentroCusto: obra.centroCustoId || null,
          idSupervisor: obra.supervisorId || null,
          idCoordenador: obra.coordenadorId || null,
          idAgenteResponsavel: obra.agenteResponsavelId || null,
          equipes: obra.equipes || [],
          ehSubprojeto: !!obra.obraPaiId,
          idObraPai: obra.obraPaiId || null,
          tipoVinculoPai: obra.tipoVinculoPai || TipoVinculoSubprojeto.KIT,
          latitude: obra.latitude ?? null,
          longitude: obra.longitude ?? null
        });

        if (obra.obraPaiId && obra.obraPaiNumero) {
          const jaExiste = this.obrasPrincipaisList().some(o => o.id === obra.obraPaiId);
          if (!jaExiste) {
            this.obrasPrincipaisList.update(list => [
              {
                id: obra.obraPaiId!,
                numero: obra.obraPaiNumero!,
                titulo: obra.obraPaiTitulo || '',
                ativo: true
              } as ObraResumoResponse,
              ...list
            ]);
          }
        }

        this.loading.set(false);
      },
      error: (error: any) => {
        this.loading.set(false);
        this.messages.add({
          severity: 'error',
          summary: 'Erro ao carregar obra',
          detail: getErrorMessage(error)
        });
        this.router.navigate(['/obras']);
      }
    });
  }

  private carregarLookups(): void {
    this.service.listarStatusAtivos().subscribe({
      next: (res: StatusObraResponse[]) => {
        this.statusList.set(res);
        if (!this.isEdit() && res.length > 0 && !this.form.get('idStatusObra')?.value) {
          this.form.patchValue({ idStatusObra: res[0].id });
        }
      }
    });

    this.service.listarAgentesAtivos().subscribe({
      next: (res: AgenteResponsavelResponse[]) => this.agentesList.set(res)
    });

    this.rhService.listarBasesOperacionaisAtivas().subscribe({
      next: (res: any) => this.basesList.set(res.content ?? [])
    });

    this.rhService.listarProcessosAtivos().subscribe({
      next: (res: any) => this.processosList.set(res.content ?? [])
    });

    this.rhService.listarMunicipios({ ativo: true, size: 500, sort: 'nome,asc' }).subscribe({
      next: (res: any) => this.municipiosList.set(res.content ?? [])
    });

    this.rhService.listarCentrosFaturamentoAtivos().subscribe({
      next: (res: any) => this.centrosCustoList.set(res.content ?? [])
    });

    this.rhService.listarSupervisoresAtivosMin().subscribe({
      next: (res) => this.supervisoresList.set(res)
    });

    this.rhService.listarCoordenadoresAtivosMin().subscribe({
      next: (res) => this.coordenadoresList.set(res)
    });

    this.rhService.listarEquipes({ ativo: true, size: 500 }).subscribe({
      next: (res: any) => this.equipesList.set(res.content ?? [])
    });

    this.service.listarObras({ apenasPrincipais: true, ativo: true, size: 500, sort: 'numero,asc' }).subscribe({
      next: (res) => {
        const filtradas = (res.content ?? []).filter(o => o.id !== this.obraId());
        this.obrasPrincipaisList.set(filtradas);
      }
    });
  }
}
