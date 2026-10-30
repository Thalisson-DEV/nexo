import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MenuItem, MessageService } from 'primeng/api';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MultiSelectModule } from 'primeng/multiselect';
import { SelectModule } from 'primeng/select';
import { SkeletonModule } from 'primeng/skeleton';
import { StepperModule } from 'primeng/stepper';
import { TextareaModule } from 'primeng/textarea';
import { ToastModule } from 'primeng/toast';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TooltipModule } from 'primeng/tooltip';

import { getErrorMessage } from '../../../core/models/shared.models';
import {
  EnvolvidoEventoRequest,
  EventoSstRequest,
  EventoSstResponse,
  PAPEL_ENVOLVIDO_LABELS,
  PapelEnvolvido,
  REGISTRO_EVIDENCIA_LABELS,
  RegistroEvidencia,
  TIPO_CAT_LABELS,
  TIPO_ENVOLVIDO_LABELS,
  TIPO_EVENTO_IMPLICA_LESAO,
  TIPO_EVENTO_LABELS,
  TipoCat,
  TipoEnvolvido,
  TipoEventoSst,
  opcoesDe,
} from '../../../core/models/sst-evento.models';
import { SstService } from '../../../core/services/sst.service';
import { EventosLookups, EventosLookupsService } from './eventos-lookups.service';
import { RhService } from '../../../core/services/rh.service';
import { EquipeResponse, VeiculoResponse } from '../../../core/models/rh.models';
import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { RemoteLookup as LookupRemoteLookup } from '../../../core/lookup/store/remote-lookup';
import { RemoteLookup } from '../../../core/utils/remote-lookup.util';

import { CesAutocompleteComponent } from '@ces/ui';

interface EnvolvidoForm {
  tipo: TipoEnvolvido;
  papel: PapelEnvolvido;
  idColaborador: string | null;
  nome: string;
  funcao: string;
  empresa: string;
}

@Component({
  selector: 'app-sst-evento-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    BreadcrumbModule,
    ButtonModule,
    DatePickerModule,
    InputNumberModule,
    InputTextModule,
    MultiSelectModule,
    SelectModule,
    SkeletonModule,
    StepperModule,
    TextareaModule,
    ToastModule,
    ToggleSwitchModule,
    TooltipModule,
    CesAutocompleteComponent,
  ],
  providers: [MessageService],
  templateUrl: './evento-form.component.html',
  styleUrl: './evento-form.component.css',
})
export class EventoFormComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly sst = inject(SstService);
  private readonly messages = inject(MessageService);
  private readonly lookupsService = inject(EventosLookupsService);
  private readonly rh = inject(RhService);
  private readonly lookupStore = inject(LookupStore);
  private readonly destroyRef = inject(DestroyRef);
  private readonly equipeLookup = new RemoteLookup<EquipeResponse>(
    {
      identify: (equipe) => equipe.id,
      selectedKeys: () => (this.idEquipe?.() ? [this.idEquipe()!] : []),
      load: ({ query, page, size }) =>
        this.rh.listarEquipes({ ativo: true, buscaGlobal: query, page, size, sort: 'codigo,asc' }),
    },
    this.destroyRef,
  );
  private readonly veiculoLookup = new RemoteLookup<VeiculoResponse>(
    {
      identify: (veiculo) => veiculo.id,
      selectedKeys: () => (this.idVeiculo?.() ? [this.idVeiculo()!] : []),
      load: ({ query, page, size }) =>
        this.rh.listarVeiculos({ ativo: true, buscaGlobal: query, page, size, sort: 'placa,asc' }),
    },
    this.destroyRef,
  );
  private readonly colaboradorLookup = new LookupRemoteLookup<string>({
    loader: ({ query, cursor }) => this.lookupStore.searchCollaborators({ query, cursor }),
    selectedValues: () => [
      ...(this.idGestor?.() ? [this.idGestor()!] : []),
      ...(this.idsTecnicos?.() ?? []),
      ...(this.envolvidos?.() ?? [])
        .map((envolvido) => envolvido.idColaborador)
        .filter((id): id is string => typeof id === 'string' && !!id),
    ],
    destroyRef: this.destroyRef,
  });

  protected readonly lookups = signal<EventosLookups | null>(null);
  protected readonly equipes = this.equipeLookup.items;
  protected readonly equipesLoading = this.equipeLookup.loading;
  protected readonly veiculos = this.veiculoLookup.items;
  protected readonly veiculosLoading = this.veiculoLookup.loading;
  protected readonly colaboradores = this.colaboradorLookup.items;
  protected readonly colaboradoresLoading = this.colaboradorLookup.loading;
  protected readonly colaboradoresStatus = this.colaboradorLookup.status;
  protected readonly colaboradoresError = computed(() => {
    const err = this.colaboradorLookup.error();
    return err ? getErrorMessage(err) : undefined;
  });
  protected readonly evento = signal<EventoSstResponse | null>(null);
  protected readonly carregando = signal(true);
  protected readonly salvando = signal(false);
  protected readonly activeStep = signal(1);
  protected readonly etapasTentadas = signal<ReadonlySet<number>>(new Set());
  protected readonly agora = new Date();

  protected readonly tipo = signal<TipoEventoSst | null>(null);
  protected readonly dataOcorrencia = signal<Date | null>(null);
  protected readonly dataComunicacao = signal<Date | null>(new Date());
  protected readonly idAtividade = signal<string | null>(null);
  protected readonly idGestor = signal<string | null>(null);
  protected readonly idProcesso = signal<string | null>(null);
  protected readonly idBaseOperacional = signal<string | null>(null);
  protected readonly idEquipe = signal<string | null>(null);
  protected readonly idVeiculo = signal<string | null>(null);
  protected readonly idClassificacao = signal<string | null>(null);
  protected readonly telemetria = signal<RegistroEvidencia>('NAO_APLICAVEL');
  protected readonly filmagem = signal<RegistroEvidencia>('NAO_APLICAVEL');
  protected readonly localOcorrencia = signal('');
  protected readonly descricao = signal('');
  protected readonly idLocalLesao = signal<string | null>(null);
  protected readonly idNaturezaLesao = signal<string | null>(null);
  protected readonly idAgenteCausador = signal<string | null>(null);
  protected readonly diasPerdidos = signal<number | null>(null);
  protected readonly possuiCat = signal(false);
  protected readonly tipoCat = signal<TipoCat | null>(null);
  protected readonly numeroCat = signal('');
  protected readonly dataEmissaoCat = signal<Date | null>(null);
  protected readonly idsTecnicos = signal<string[]>([]);
  protected readonly envolvidos = signal<EnvolvidoForm[]>([]);

  protected readonly tipoOptions = opcoesDe(TIPO_EVENTO_LABELS);
  protected readonly tipoCatOptions = opcoesDe(TIPO_CAT_LABELS);
  protected readonly papelOptions = opcoesDe(PAPEL_ENVOLVIDO_LABELS);
  protected readonly evidenciaOptions = opcoesDe(REGISTRO_EVIDENCIA_LABELS);

  private idEvento: string | null = null;

  protected readonly editando = computed(() => this.idEvento !== null);
  protected readonly breadcrumb = computed<MenuItem[]>(() => [
    { label: 'Segurança (SST)' },
    { label: 'Eventos de segurança', routerLink: '/sst/eventos' },
    { label: this.editando() ? 'Correção' : 'Novo registro' },
  ]);
  protected readonly implicaLesao = computed(() => {
    const tipo = this.tipo();
    return tipo ? TIPO_EVENTO_IMPLICA_LESAO[tipo] : false;
  });
  protected readonly prazoHoras = computed(() => (this.implicaLesao() ? 24 : 72));
  protected readonly atividadeSelecionada = computed(
    () =>
      this.lookups()?.atividades.find((atividade) => atividade.id === this.idAtividade()) ?? null,
  );
  protected readonly temAcidentado = computed(() =>
    this.envolvidos().some((envolvido) => envolvido.papel === 'ACIDENTADO'),
  );
  protected readonly atrasoHoras = computed(() => {
    const ocorrencia = this.dataOcorrencia();
    const comunicacao = this.dataComunicacao();
    if (!ocorrencia || !comunicacao) return null;
    const horas = Math.floor((comunicacao.getTime() - ocorrencia.getTime()) / 3_600_000);
    return horas < 0 ? null : horas;
  });
  protected readonly comunicacaoAtrasada = computed(() => {
    const horas = this.atrasoHoras();
    return horas !== null && horas > this.prazoHoras();
  });

  ngOnInit(): void {
    this.idEvento = this.route.snapshot.paramMap.get('id');
    this.lookupsService.carregar().subscribe((lookups) => {
      this.lookups.set(lookups);
      if (!this.idEvento) this.carregando.set(false);
    });

    if (this.idEvento) {
      this.sst.buscarEventoPorId(this.idEvento).subscribe({
        next: (evento) => {
          this.evento.set(evento);
          this.preencher(evento);
          this.carregando.set(false);
        },
        error: (error) => {
          this.carregando.set(false);
          this.messages.add({
            severity: 'error',
            summary: 'Não foi possível abrir',
            detail: getErrorMessage(error),
          });
        },
      });
    }
  }

  protected etapaTentada(etapa: number): boolean {
    return this.etapasTentadas().has(etapa);
  }

  protected avancar(): void {
    const etapa = this.activeStep();
    const problemas = this.problemasEtapa(etapa);
    this.marcarEtapaTentada(etapa);
    if (problemas.length) {
      this.messages.add({
        severity: 'warn',
        summary: `Revise a etapa ${etapa}`,
        detail: problemas[0],
      });
      return;
    }
    this.activeStep.set(Math.min(4, etapa + 1));
  }

  protected voltarEtapa(): void {
    this.activeStep.update((etapa) => Math.max(1, etapa - 1));
  }

  protected irParaEtapa(etapa: number): void {
    if (etapa < this.activeStep()) {
      this.activeStep.set(etapa);
    } else if (etapa > this.activeStep()) {
      for (let e = this.activeStep(); e < etapa; e++) {
        this.marcarEtapaTentada(e);
        const problemas = this.problemasEtapa(e);
        if (problemas.length) {
          this.messages.add({
            severity: 'warn',
            summary: `Revise a etapa ${e}`,
            detail: problemas[0],
          });
          return;
        }
      }
      this.activeStep.set(etapa);
    }
  }

  protected gestorLabel(): string {
    const id = this.idGestor();
    if (!id) return 'Não informado';
    const opt = this.colaboradores().find((item) => item.value === id);
    if (opt) return opt.label;
    const ev = this.evento();
    if (ev?.idGestor === id && ev.gestorNome) return ev.gestorNome;
    return 'Gestor selecionado';
  }

  protected tipoLabel(): string {
    const tipo = this.tipo();
    return tipo ? TIPO_EVENTO_LABELS[tipo] : 'Não informado';
  }

  protected classificacaoLabel(): string {
    return (
      this.lookups()?.classificacoes.find((item) => item.id === this.idClassificacao())?.nome ??
      'Não informada'
    );
  }

  protected baseLabel(): string {
    return (
      this.lookups()?.bases.find((item) => item.id === this.idBaseOperacional())?.nome ??
      'Não informada'
    );
  }

  protected processoLabel(): string {
    return (
      this.lookups()?.processos.find((item) => item.id === this.idProcesso())?.nome ??
      'Não informado'
    );
  }

  protected equipeLabel(): string {
    return (
      this.lookups()?.equipes.find((item) => item.id === this.idEquipe())?.codigo ?? 'Não informada'
    );
  }

  protected veiculoLabel(): string {
    return this.veiculos().find((item) => item.id === this.idVeiculo())?.placa ?? 'Não informado';
  }

  protected localLesaoLabel(): string {
    return (
      this.lookups()?.locaisLesao.find((item) => item.id === this.idLocalLesao())?.nome ??
      'Não informado'
    );
  }

  protected naturezaLesaoLabel(): string {
    return (
      this.lookups()?.naturezasLesao.find((item) => item.id === this.idNaturezaLesao())?.nome ??
      'Não informada'
    );
  }

  protected agenteCausadorLabel(): string {
    return (
      this.lookups()?.agentesCausadores.find((item) => item.id === this.idAgenteCausador())?.nome ??
      'Não informado'
    );
  }

  protected evidenciaLabel(valor: RegistroEvidencia): string {
    return REGISTRO_EVIDENCIA_LABELS[valor];
  }

  protected tipoEnvolvidoLabel(tipo: TipoEnvolvido): string {
    return TIPO_ENVOLVIDO_LABELS[tipo];
  }

  protected papelLabel(papel: PapelEnvolvido): string {
    return PAPEL_ENVOLVIDO_LABELS[papel] ?? papel;
  }

  protected nomeEnvolvido(envolvido: EnvolvidoForm): string {
    if (envolvido.tipo === 'TERCEIRO') return envolvido.nome || 'Terceiro sem nome';
    return (
      this.colaboradores().find((item) => item.value === envolvido.idColaborador)?.label ??
      'Colaborador não selecionado'
    );
  }

  protected nomesTecnicos(): string {
    const ids = new Set(this.idsTecnicos());
    return (
      this.colaboradores()
        .filter((item) => ids.has(item.value))
        .map((item) => item.label)
        .join(', ') || 'Não informados'
    );
  }

  protected onTipoAlterado(): void {
    if (!this.implicaLesao()) {
      this.idLocalLesao.set(null);
      this.idNaturezaLesao.set(null);
      this.diasPerdidos.set(null);
    }
  }

  protected onAtividadeAlterada(): void {
    const atividade = this.atividadeSelecionada();
    if (atividade?.idProcesso && !this.idProcesso()) this.idProcesso.set(atividade.idProcesso);
  }

  protected onCatAlterada(): void {
    if (!this.possuiCat()) {
      this.tipoCat.set(null);
      this.numeroCat.set('');
      this.dataEmissaoCat.set(null);
    }
  }

  protected adicionarEnvolvido(tipo: TipoEnvolvido): void {
    this.envolvidos.update((lista) => [
      ...lista,
      {
        tipo,
        papel: this.implicaLesao() && !this.temAcidentado() ? 'ACIDENTADO' : 'TESTEMUNHA',
        idColaborador: null,
        nome: '',
        funcao: '',
        empresa: '',
      },
    ]);
  }

  protected removerEnvolvido(indice: number): void {
    this.envolvidos.update((lista) => lista.filter((_, posicao) => posicao !== indice));
  }

  protected tocarEnvolvidos(): void {
    this.envolvidos.update((lista) => [...lista]);
  }

  protected voltar(): void {
    this.router.navigate(this.idEvento ? ['/sst/eventos', this.idEvento] : ['/sst/eventos']);
  }

  protected salvar(): void {
    [1, 2, 3].forEach((etapa) => this.marcarEtapaTentada(etapa));
    const problemas = this.validar();
    if (problemas.length) {
      const primeiraEtapa = [1, 2, 3].find((etapa) => this.problemasEtapa(etapa).length) ?? 1;
      this.activeStep.set(primeiraEtapa);
      this.messages.add({ severity: 'warn', summary: 'Revise o registro', detail: problemas[0] });
      return;
    }
    if (this.salvando()) return;

    this.salvando.set(true);
    const operacao = this.idEvento
      ? this.sst.atualizarEvento(this.idEvento, this.montarRequest())
      : this.sst.registrarEvento(this.montarRequest());
    operacao.subscribe({
      next: (evento) => {
        this.salvando.set(false);
        this.messages.add({
          severity: 'success',
          summary: this.idEvento ? 'Evento atualizado' : 'Evento registrado',
          detail: `Registro ${evento.numeroRegistro}.`,
        });
        this.router.navigate(['/sst/eventos', evento.id]);
      },
      error: (error) => {
        this.salvando.set(false);
        this.messages.add({
          severity: 'error',
          summary: 'Não foi possível salvar',
          detail: getErrorMessage(error),
        });
      },
    });
  }

  private marcarEtapaTentada(etapa: number): void {
    this.etapasTentadas.update((etapas) => new Set([...etapas, etapa]));
  }

  private problemasEtapa(etapa: number): string[] {
    const problemas: string[] = [];
    const ocorrencia = this.dataOcorrencia();
    const comunicacao = this.dataComunicacao();

    if (etapa === 1) {
      if (!this.tipo()) problemas.push('Informe o tipo da ocorrência.');
      if (!ocorrencia) problemas.push('Informe a data e hora da ocorrência.');
      if (!comunicacao) problemas.push('Informe a data e hora da comunicação.');
      if (ocorrencia && comunicacao && comunicacao < ocorrencia)
        problemas.push('A comunicação não pode ser anterior à ocorrência.');
      if (ocorrencia && ocorrencia > new Date())
        problemas.push('A ocorrência não pode estar no futuro.');
      if (comunicacao && comunicacao > new Date())
        problemas.push('A comunicação não pode estar no futuro.');
      if (!this.idClassificacao()) problemas.push('Informe a classificação do evento.');
      if (!this.descricao().trim()) problemas.push('Descreva o que aconteceu.');
    }

    if (etapa === 2) {
      if (!this.idAtividade()) problemas.push('Informe a atividade em execução.');
      if (!this.idGestor()) problemas.push('Informe o gestor responsável.');
      if (!this.idBaseOperacional()) problemas.push('Informe a base operacional.');
    }

    if (etapa === 3) {
      if (this.implicaLesao() && !this.idLocalLesao()) problemas.push('Informe o local da lesão.');
      if (this.implicaLesao() && !this.temAcidentado())
        problemas.push('Informe ao menos uma pessoa no papel de acidentado.');
      if (this.possuiCat() && !this.tipoCat()) problemas.push('Informe o tipo da CAT.');
      if (!this.idsTecnicos().length)
        problemas.push('Informe ao menos um técnico de segurança responsável.');
      const colaboradores = this.envolvidos().filter(
        (envolvido) => envolvido.tipo === 'COLABORADOR',
      );
      if (colaboradores.some((envolvido) => !envolvido.idColaborador))
        problemas.push('Selecione o colaborador de cada envolvido.');
      const ids = colaboradores.map((envolvido) => envolvido.idColaborador);
      if (new Set(ids).size !== ids.length)
        problemas.push('O mesmo colaborador foi informado mais de uma vez.');
      if (
        this.envolvidos().some(
          (envolvido) =>
            envolvido.tipo === 'TERCEIRO' && (!envolvido.nome.trim() || !envolvido.empresa.trim()),
        )
      ) {
        problemas.push('Informe nome e empresa de cada terceiro.');
      }
    }
    return problemas;
  }

  private validar(): string[] {
    return [1, 2, 3].flatMap((etapa) => this.problemasEtapa(etapa));
  }

  private montarRequest(): EventoSstRequest {
    const envolvidos: EnvolvidoEventoRequest[] = this.envolvidos().map((envolvido) =>
      envolvido.tipo === 'COLABORADOR'
        ? { tipo: 'COLABORADOR', papel: envolvido.papel, idColaborador: envolvido.idColaborador }
        : {
            tipo: 'TERCEIRO',
            papel: envolvido.papel,
            nome: envolvido.nome.trim(),
            funcao: envolvido.funcao.trim() || null,
            empresa: envolvido.empresa.trim(),
          },
    );

    return {
      tipo: this.tipo()!,
      dataOcorrencia: this.dataOcorrencia()!.toISOString(),
      dataComunicacao: this.dataComunicacao()!.toISOString(),
      idAtividade: this.idAtividade()!,
      idGestor: this.idGestor(),
      idBaseOperacional: this.idBaseOperacional()!,
      idEquipe: this.idEquipe(),
      idVeiculo: this.idVeiculo(),
      idClassificacao: this.idClassificacao()!,
      idProcesso: this.idProcesso(),
      telemetria: this.telemetria(),
      filmagem: this.filmagem(),
      localOcorrencia: this.localOcorrencia().trim() || null,
      latitude: null,
      longitude: null,
      descricao: this.descricao().trim(),
      idLocalLesao: this.implicaLesao() ? this.idLocalLesao() : null,
      idNaturezaLesao: this.implicaLesao() ? this.idNaturezaLesao() : null,
      idAgenteCausador: this.idAgenteCausador(),
      diasPerdidos: this.implicaLesao() ? this.diasPerdidos() : null,
      possuiCat: this.possuiCat(),
      tipoCat: this.possuiCat() ? this.tipoCat() : null,
      numeroCat: this.possuiCat() ? this.numeroCat().trim() || null : null,
      dataEmissaoCat: this.possuiCat() ? this.formatarData(this.dataEmissaoCat()) : null,
      idsTecnicos: this.idsTecnicos(),
      envolvidos,
    };
  }

  private preencher(evento: EventoSstResponse): void {
    this.tipo.set(evento.tipo);
    this.dataOcorrencia.set(new Date(evento.dataOcorrencia));
    this.dataComunicacao.set(new Date(evento.dataComunicacao));
    this.idAtividade.set(evento.idAtividade);
    this.idGestor.set(evento.idGestor);
    this.idProcesso.set(evento.idProcesso);
    this.idBaseOperacional.set(evento.idBaseOperacional);
    this.idEquipe.set(evento.idEquipe);
    this.idVeiculo.set(evento.idVeiculo);
    if (evento.idEquipe) {
      this.rh.buscarEquipePorId(evento.idEquipe).subscribe({
        next: (equipe) => this.equipeLookup.setSeed([equipe]),
        error: () => {},
      });
    }
    if (evento.idVeiculo) {
      this.rh.buscarVeiculoPorId(evento.idVeiculo).subscribe({
        next: (veiculo) => this.veiculoLookup.setSeed([veiculo]),
        error: () => {},
      });
    }
    this.colaboradorLookup.seedSelected([
      ...(evento.idGestor && evento.gestorNome
        ? [{ value: evento.idGestor, label: evento.gestorNome }]
        : []),
      ...evento.tecnicos
        .filter((tecnico) => !!tecnico.idColaborador && !!tecnico.nome)
        .map((tecnico) => ({ value: tecnico.idColaborador, label: tecnico.nome })),
      ...evento.envolvidos
        .filter(
          (envolvido) =>
            envolvido.tipo === 'COLABORADOR' && !!envolvido.idColaborador && !!envolvido.nome,
        )
        .map((envolvido) => ({ value: envolvido.idColaborador!, label: envolvido.nome })),
    ]);
    this.idClassificacao.set(evento.idClassificacao);
    this.telemetria.set(evento.telemetria);
    this.filmagem.set(evento.filmagem);
    this.localOcorrencia.set(evento.localOcorrencia ?? '');
    this.descricao.set(evento.descricao);
    this.idLocalLesao.set(evento.idLocalLesao);
    this.idNaturezaLesao.set(evento.idNaturezaLesao);
    this.idAgenteCausador.set(evento.idAgenteCausador);
    this.diasPerdidos.set(evento.diasPerdidos);
    this.possuiCat.set(evento.possuiCat);
    this.tipoCat.set(evento.tipoCat);
    this.numeroCat.set(evento.numeroCat ?? '');
    this.dataEmissaoCat.set(
      evento.dataEmissaoCat ? new Date(`${evento.dataEmissaoCat}T00:00:00`) : null,
    );
    this.idsTecnicos.set(evento.tecnicos.map((tecnico) => tecnico.idColaborador));
    this.envolvidos.set(
      evento.envolvidos.map((envolvido) => ({
        tipo: envolvido.tipo,
        papel: envolvido.papel,
        idColaborador: envolvido.idColaborador,
        nome: envolvido.nome ?? '',
        funcao: envolvido.funcao ?? '',
        empresa: envolvido.empresa ?? '',
      })),
    );
  }

  protected openEquipes(): void {
    this.equipeLookup.open();
  }
  protected searchEquipes(event: { filter?: string }): void {
    this.equipeLookup.search(event.filter ?? '');
  }
  protected loadMoreEquipes(): void {
    this.equipeLookup.loadNext();
  }
  protected openVeiculos(): void {
    this.veiculoLookup.open();
  }
  protected searchVeiculos(event: { filter?: string }): void {
    this.veiculoLookup.search(event.filter ?? '');
  }
  protected loadMoreVeiculos(): void {
    this.veiculoLookup.loadNext();
  }
  protected searchColaboradores(event: { query?: string; filter?: string } | string): void {
    const query = typeof event === 'string' ? event : (event.query ?? event.filter ?? '');
    this.colaboradorLookup.search(query);
  }
  protected loadMoreColaboradores(): void {
    this.colaboradorLookup.loadNext();
  }
  protected retryColaboradores(): void {
    this.colaboradorLookup.retry();
  }

  private formatarData(data: Date | null): string | null {
    if (!data) return null;
    const ano = data.getFullYear();
    const mes = String(data.getMonth() + 1).padStart(2, '0');
    const dia = String(data.getDate()).padStart(2, '0');
    return `${ano}-${mes}-${dia}`;
  }
}
