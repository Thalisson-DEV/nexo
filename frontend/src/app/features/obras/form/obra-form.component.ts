import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  CES_FORM_KIT,
  CesBooleanControlComponent,
  CesButtonComponent,
  CesCalloutComponent,
  CesDatepickerComponent,
  CesMultiSelectComponent,
  CesNumberInputComponent,
  CesSelectComponent,
  CesTextInputComponent,
  CesTextareaComponent,
  NotificationService,
  cesFocusFirstInvalid
} from '@ces/ui';
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
import { saidaDoDrawer } from '../../../shared/utils/drawer-navegacao.utils';
import { LocationPickerDialogComponent, LocalSelecionado } from '../../../shared/components/location-picker-dialog/location-picker-dialog.component';
import {
  TIPO_VINCULO_OPTIONS,
  formatarDataParaApi,
  parseDataBrParaDate,
  sanitizarNumeroObra,
  sanitizarTituloObra
} from '../obras-ui';

/**
 * Cadastro e edição da obra em drawer. A tela que abre monta o componente só enquanto o drawer
 * está aberto, então cada abertura começa limpa e os catálogos só são pedidos quando precisa.
 */
@Component({
  selector: 'app-obra-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ...CES_FORM_KIT,
    CesBooleanControlComponent,
    CesButtonComponent,
    CesCalloutComponent,
    CesDatepickerComponent,
    CesMultiSelectComponent,
    CesNumberInputComponent,
    CesSelectComponent,
    CesTextInputComponent,
    CesTextareaComponent,
    LocationPickerDialogComponent
  ],
  templateUrl: './obra-form.component.html',
  styles: `
    @media (min-width: 40rem) {
      .obra-grade-2 {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }
  `
})
export class ObraFormComponent implements OnInit {
  private readonly service = inject(ObrasService);
  private readonly rhService = inject(RhService);
  private readonly notificacoes = inject(NotificationService);
  private readonly router = inject(Router);

  /** Obra em edição; sem ela, o drawer cadastra uma nova. */
  readonly idObra = input<string | null>(null);
  /** `null` quando a escrita ficou na fila para sincronizar. */
  readonly salva = output<ObraResponse | null>();
  /** Depois de fechar, por qualquer meio. */
  readonly fechado = output<void>();

  protected readonly aberto = signal(true);
  private readonly aoSair = saidaDoDrawer(() => this.fechado.emit());
  protected readonly carregando = signal(false);
  protected readonly salvando = signal(false);
  private readonly tentouSalvar = signal(false);
  protected readonly editando = computed(() => !!this.idObra());

  protected readonly numero = signal('');
  protected readonly titulo = signal('');
  protected readonly notaObra = signal('');
  protected readonly descricao = signal('');
  protected readonly idProcesso = signal<string | null>(null);
  protected readonly idBaseOperacional = signal<string | null>(null);
  protected readonly idMunicipio = signal<string | null>(null);
  protected readonly idCentroCusto = signal<string | null>(null);
  protected readonly idSupervisor = signal<string | null>(null);
  protected readonly idCoordenador = signal<string | null>(null);
  protected readonly idAgenteResponsavel = signal<string | null>(null);
  protected readonly idStatusObra = signal<string | null>(null);
  protected readonly valorProjeto = signal<number | null>(null);
  protected readonly dataInicio = signal<Date | null>(null);
  protected readonly dataPrevisaoFim = signal<Date | null>(null);
  protected readonly dataFimEfetiva = signal<Date | null>(null);
  protected readonly equipes = signal<string[]>([]);
  protected readonly ehSubprojeto = signal(false);
  protected readonly idObraPai = signal<string | null>(null);
  protected readonly tipoVinculoPai = signal<TipoVinculoSubprojeto | null>(TipoVinculoSubprojeto.KIT);
  protected readonly latitude = signal<number | null>(null);
  protected readonly longitude = signal<number | null>(null);

  private readonly statusLista = signal<StatusObraResponse[]>([]);
  private readonly bases = signal<BaseOperacionalResponse[]>([]);
  private readonly processos = signal<ProcessoNegocioResponse[]>([]);
  private readonly municipios = signal<MunicipioResponse[]>([]);
  private readonly centrosCusto = signal<CentroFaturamentoResponse[]>([]);
  private readonly supervisores = signal<SupervisorMinResponse[]>([]);
  private readonly coordenadores = signal<CoordenadorMinResponse[]>([]);
  private readonly agentes = signal<AgenteResponsavelResponse[]>([]);
  private readonly equipesLista = signal<EquipeResponse[]>([]);
  private readonly obrasPrincipais = signal<ObraResumoResponse[]>([]);

  protected readonly tipoVinculoOpcoes = TIPO_VINCULO_OPTIONS;
  protected readonly statusOpcoes = computed(() => this.statusLista().map((s) => ({ label: s.nome, value: s.id })));
  protected readonly basesOpcoes = computed(() => this.bases().map((b) => ({ label: b.nome, value: b.id })));
  protected readonly processosOpcoes = computed(() => this.processos().map((p) => ({ label: p.nome, value: p.id })));
  protected readonly municipiosOpcoes = computed(() => this.municipios().map((m) => ({ label: m.nome, value: m.id })));
  protected readonly centrosOpcoes = computed(() => this.centrosCusto().map((c) => ({ label: c.nome, value: c.id })));
  protected readonly supervisoresOpcoes = computed(() => this.supervisores().map((s) => ({ label: s.nomeColaborador, value: s.id })));
  protected readonly coordenadoresOpcoes = computed(() => this.coordenadores().map((c) => ({ label: c.nomeColaborador, value: c.id })));
  protected readonly agentesOpcoes = computed(() => this.agentes().map((a) => ({ label: a.nome, value: a.id })));
  protected readonly equipesOpcoes = computed(() => this.equipesLista().map((e) => ({ label: e.codigo, value: e.id })));
  protected readonly obrasPaiOpcoes = computed(() =>
    this.obrasPrincipais()
      .filter((o) => o.id !== this.idObra())
      .map((o) => ({ label: `${o.numero} — ${o.titulo}`, value: o.id }))
  );

  protected readonly concluida = computed(() => this.statusLista().find((s) => s.id === this.idStatusObra())?.concluida ?? false);

  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    const obrigatorio = (campo: string, valor: unknown, mensagem: string) => {
      if (!valor) e[campo] = mensagem;
    };
    obrigatorio('numero', this.numero().trim(), 'Informe o número da obra.');
    obrigatorio('titulo', this.titulo().trim(), 'Informe o título da obra.');
    obrigatorio('idProcesso', this.idProcesso(), 'Selecione o processo.');
    obrigatorio('idBaseOperacional', this.idBaseOperacional(), 'Selecione a base.');
    obrigatorio('idMunicipio', this.idMunicipio(), 'Selecione o município.');
    obrigatorio('idCentroCusto', this.idCentroCusto(), 'Selecione o centro de custo.');
    obrigatorio('idSupervisor', this.idSupervisor(), 'Selecione o supervisor.');
    obrigatorio('idCoordenador', this.idCoordenador(), 'Selecione o coordenador.');
    obrigatorio('idStatusObra', this.idStatusObra(), 'Selecione o status.');
    obrigatorio('dataInicio', this.dataInicio(), 'Informe a data de início.');
    obrigatorio('dataPrevisaoFim', this.dataPrevisaoFim(), 'Informe a previsão de término.');
    if (this.ehSubprojeto()) {
      obrigatorio('idObraPai', this.idObraPai(), 'Selecione a obra principal.');
      obrigatorio('tipoVinculoPai', this.tipoVinculoPai(), 'Selecione o tipo de vínculo.');
    }
    return e;
  });

  private readonly retrato = computed(() =>
    JSON.stringify([
      this.numero(), this.titulo(), this.notaObra(), this.descricao(), this.idProcesso(), this.idBaseOperacional(),
      this.idMunicipio(), this.idCentroCusto(), this.idSupervisor(), this.idCoordenador(), this.idAgenteResponsavel(),
      this.idStatusObra(), this.valorProjeto(), this.dataInicio(), this.dataPrevisaoFim(), this.dataFimEfetiva(),
      this.equipes(), this.ehSubprojeto(), this.idObraPai(), this.tipoVinculoPai(), this.latitude(), this.longitude()
    ])
  );
  private readonly original = signal('');
  protected readonly alterado = computed(() => this.retrato() !== this.original());

  ngOnInit(): void {
    this.original.set(this.retrato());
    this.carregarCatalogos();
    const id = this.idObra();
    if (id) this.carregarObra(id);
  }

  protected fechar(): void {
    this.aberto.set(false);
    this.aoSair();
  }

  protected readonly numeroLimpo = () => sanitizarNumeroObra(this.numero());
  protected readonly tituloLimpo = () => sanitizarTituloObra(this.titulo());

  protected erro(campo: string): string | undefined {
    return this.tentouSalvar() ? this.erros()[campo] : undefined;
  }

  protected capturarGps(): void {
    if (!navigator.geolocation) {
      this.notificacoes.warning('Este navegador não informa a localização.');
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (posicao) => this.definirCoordenadas(posicao.coords.latitude, posicao.coords.longitude),
      (falha) => this.notificacoes.error(falha.message || 'Não foi possível capturar a localização.', { title: 'Erro de GPS' }),
      { enableHighAccuracy: true, timeout: 10000 }
    );
  }

  protected aoEscolherNoMapa(local: LocalSelecionado): void {
    this.definirCoordenadas(local.latitude, local.longitude);
  }

  private definirCoordenadas(latitude: number, longitude: number): void {
    this.latitude.set(parseFloat(latitude.toFixed(6)));
    this.longitude.set(parseFloat(longitude.toFixed(6)));
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) {
      this.notificacoes.warning('Preencha os campos destacados para salvar a obra.');
      setTimeout(() => cesFocusFirstInvalid());
      return;
    }

    const request: ObraRequest = {
      numero: sanitizarNumeroObra(this.numero()),
      titulo: sanitizarTituloObra(this.titulo()),
      descricao: this.descricao().trim() || undefined,
      notaObra: this.notaObra().trim() || undefined,
      valorProjeto: this.valorProjeto() ?? undefined,
      dataInicio: formatarDataParaApi(this.dataInicio())!,
      dataPrevisaoFim: formatarDataParaApi(this.dataPrevisaoFim())!,
      dataFimEfetiva: formatarDataParaApi(this.dataFimEfetiva()) || null,
      latitude: this.latitude() ?? undefined,
      longitude: this.longitude() ?? undefined,
      idProcesso: this.idProcesso()!,
      idBaseOperacional: this.idBaseOperacional()!,
      idMunicipio: this.idMunicipio()!,
      idCentroCusto: this.idCentroCusto()!,
      idSupervisor: this.idSupervisor()!,
      idCoordenador: this.idCoordenador()!,
      idStatusObra: this.idStatusObra()!,
      idAgenteResponsavel: this.idAgenteResponsavel() || undefined,
      idObraPai: this.ehSubprojeto() ? this.idObraPai() : null,
      tipoVinculoPai: this.ehSubprojeto() ? this.tipoVinculoPai() : null,
      equipes: this.equipes().length ? this.equipes() : undefined
    };

    const id = this.idObra();
    this.salvando.set(true);
    (id ? this.service.atualizarObra(id, request) : this.service.criarObra(request)).subscribe({
      next: (obra) => {
        this.salvando.set(false);
        this.notificacoes.success(`Obra ${obra?.numero || request.numero} ${id ? 'atualizada' : 'cadastrada'}.`, {
          action: !id && obra?.id ? { label: 'Abrir obra', run: () => void this.router.navigate(['/obras', obra.id]) } : undefined
        });
        this.salva.emit(obra);
        this.fechar();
      },
      error: (falha) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(falha), { title: 'Não foi possível salvar a obra' });
      }
    });
  }

  private carregarObra(id: string): void {
    this.carregando.set(true);
    this.service.buscarObraPorId(id).subscribe({
      next: (obra) => {
        this.numero.set(obra.numero);
        this.titulo.set(obra.titulo);
        this.notaObra.set(obra.notaObra || '');
        this.descricao.set(obra.descricao || '');
        this.idStatusObra.set(obra.statusId);
        this.valorProjeto.set(obra.valorProjeto ?? null);
        this.dataInicio.set(parseDataBrParaDate(obra.dataInicio));
        this.dataPrevisaoFim.set(parseDataBrParaDate(obra.dataPrevisaoFim));
        this.dataFimEfetiva.set(parseDataBrParaDate(obra.dataFimEfetiva));
        this.idProcesso.set(obra.processoId || null);
        this.idBaseOperacional.set(obra.baseOperacionalId || null);
        this.idMunicipio.set(obra.municipioId || null);
        this.idCentroCusto.set(obra.centroCustoId || null);
        this.idSupervisor.set(obra.supervisorId || null);
        this.idCoordenador.set(obra.coordenadorId || null);
        this.idAgenteResponsavel.set(obra.agenteResponsavelId || null);
        this.equipes.set(obra.equipes || []);
        this.ehSubprojeto.set(!!obra.obraPaiId);
        this.idObraPai.set(obra.obraPaiId || null);
        this.tipoVinculoPai.set(obra.tipoVinculoPai || TipoVinculoSubprojeto.KIT);
        this.latitude.set(obra.latitude ?? null);
        this.longitude.set(obra.longitude ?? null);
        this.garantirObraPai(obra);
        this.original.set(this.retrato());
        this.carregando.set(false);
      },
      error: (falha) => {
        this.carregando.set(false);
        this.notificacoes.error(getErrorMessage(falha), { title: 'Não foi possível abrir a obra' });
        this.fechar();
      }
    });
  }

  /** A obra principal pode estar fora da lista (inativa, por exemplo) e precisa aparecer no select. */
  private garantirObraPai(obra: ObraResponse): void {
    if (!obra.obraPaiId || !obra.obraPaiNumero) return;
    if (this.obrasPrincipais().some((o) => o.id === obra.obraPaiId)) return;
    this.obrasPrincipais.update((lista) => [
      { id: obra.obraPaiId!, numero: obra.obraPaiNumero!, titulo: obra.obraPaiTitulo || '', ativo: true } as ObraResumoResponse,
      ...lista
    ]);
  }

  private carregarCatalogos(): void {
    this.service.listarStatusAtivos().subscribe((lista) => {
      this.statusLista.set(lista);
      // O status inicial é sugestão: não conta como alteração ao fechar.
      if (!this.editando() && lista.length && !this.idStatusObra()) {
        this.idStatusObra.set(lista[0].id);
        this.original.set(this.retrato());
      }
    });
    this.service.listarAgentesAtivos().subscribe((lista) => this.agentes.set(lista));
    this.rhService.listarBasesOperacionaisAtivas().subscribe((r: any) => this.bases.set(r.content ?? []));
    this.rhService.listarProcessosAtivos().subscribe((r: any) => this.processos.set(r.content ?? []));
    this.rhService.listarMunicipios({ ativo: true, size: 500, sort: 'nome,asc' }).subscribe((r: any) => this.municipios.set(r.content ?? []));
    this.rhService.listarCentrosFaturamentoAtivos().subscribe((r: any) => this.centrosCusto.set(r.content ?? []));
    this.rhService.listarSupervisoresAtivosMin().subscribe((lista) => this.supervisores.set(lista));
    this.rhService.listarCoordenadoresAtivosMin().subscribe((lista) => this.coordenadores.set(lista));
    this.rhService.listarEquipes({ ativo: true, size: 500 }).subscribe((r: any) => this.equipesLista.set(r.content ?? []));
    this.service.listarObras({ apenasPrincipais: true, ativo: true, size: 500, sort: 'numero,asc' }).subscribe((r) => {
      const atuais = this.obrasPrincipais();
      const novas = (r.content ?? []).filter((o) => !atuais.some((a) => a.id === o.id));
      this.obrasPrincipais.set([...atuais, ...novas]);
    });
  }
}
