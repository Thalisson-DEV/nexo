import { DateMaskDirective } from '../../../shared/directives/date-mask.directive';
import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MenuItem, MessageService } from 'primeng/api';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { InputTextModule } from 'primeng/inputtext';
import { MultiSelectModule } from 'primeng/multiselect';
import { SelectModule } from 'primeng/select';
import { SkeletonModule } from 'primeng/skeleton';
import { StepperModule } from 'primeng/stepper';
import { TextareaModule } from 'primeng/textarea';
import { finalize, forkJoin, Observable } from 'rxjs';
import {
  CategoriaDocumentoGovernanca,
  CorpoGovernancaResumo,
  ModeloAtaGovernanca,
  PerfilNormativoGovernanca,
  RegimeComposicaoGovernanca,
  TipoCorpoGovernanca
} from '../../../core/models/governanca-sst.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { GovernancaSstService } from '../../../core/services/governanca-sst.service';
import { RhService } from '../../../core/services/rh.service';
import {
  fimMandatoAnual,
  GRAUS_RISCO,
  montarCriacaoCorpo,
  OPCOES_CONFIRMACAO,
  perfilAplicaAoCadastro,
  periodoCipaAnual,
  UNIDADES_FEDERATIVAS
} from './governanca-corpo-cadastro.utils';
import { GovernancaContextoState, normalizarTipoGovernanca } from './governanca-state';

type CadastroGovernanca = 'composicao' | 'reuniao' | 'documento';

interface PassoCadastro {
  numero: number;
  label: string;
  icon: string;
}

@Component({
  selector: 'app-governanca-cadastro',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    BreadcrumbModule,
    ButtonModule,
    DatePickerModule,
    InputTextModule,
    MultiSelectModule,
    SelectModule,
    SkeletonModule,
    StepperModule,
    TextareaModule
  , DateMaskDirective],
  templateUrl: './governanca-cadastro.component.html',
  styleUrl: './governanca-cadastro.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class GovernancaCadastroComponent implements OnInit {
  private readonly api = inject(GovernancaSstService);
  private readonly rh = inject(RhService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly contexto = inject(GovernancaContextoState);

  protected readonly cadastro = this.route.snapshot.data['cadastro'] as CadastroGovernanca;
  protected readonly carregando = signal(true);
  protected readonly salvando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly activeStep = signal(1);
  protected readonly etapaTentada = signal(0);
  protected readonly arquivo = signal<File | null>(null);
  protected readonly bases = signal<any[]>([]);
  protected readonly corpos = signal<CorpoGovernancaResumo[]>([]);
  protected readonly modelosAta = signal<ModeloAtaGovernanca[]>([]);
  protected readonly categorias = signal<CategoriaDocumentoGovernanca[]>([]);
  protected readonly perfisNormativos = signal<PerfilNormativoGovernanca[]>([]);

  protected tipo: TipoCorpoGovernanca =
    normalizarTipoGovernanca(this.route.snapshot.queryParamMap.get('tipo')) ??
    normalizarTipoGovernanca(this.route.snapshot.data['tipoCorpo']) ??
    this.contexto.tipo();

  protected readonly form = new FormGroup({
    nome: new FormControl('', { nonNullable: true }),
    idBaseOperacional: new FormControl<string | null>(null),
    idsBasesAbrangidas: new FormControl<string[]>([], { nonNullable: true }),
    regimeComposicao: new FormControl<RegimeComposicaoGovernanca>('CIPA_DIMENSIONADA', {
      nonNullable: true
    }),
    unidadeGovernanca: new FormControl('', { nonNullable: true }),
    uf: new FormControl<string | null>(null),
    grauRisco: new FormControl<number | null>(null),
    idPerfilNormativo: new FormControl<string | null>(null),
    hipoteseRepresentanteNomeadoConfirmada: new FormControl(false, { nonNullable: true }),
    inicioPeriodo: new FormControl<Date | null>(null),
    fimPeriodo: new FormControl<Date | null>(null),
    idCorpo: new FormControl<string | null>(null),
    tipoReuniao: new FormControl('ORDINARIA', { nonNullable: true }),
    idModeloAta: new FormControl<string | null>(null),
    dataHora: new FormControl<Date | null>(null),
    duracaoMinutos: new FormControl(60, { nonNullable: true }),
    localOuMeio: new FormControl('', { nonNullable: true }),
    pauta: new FormControl('', { nonNullable: true }),
    idCategoria: new FormControl<string | null>(null),
    idBaseDocumento: new FormControl<string | null>(null),
    numeroDocumento: new FormControl('', { nonNullable: true }),
    emissor: new FormControl('', { nonNullable: true }),
    dataEmissao: new FormControl<Date | null>(null),
    dataValidade: new FormControl<Date | null>(null),
    observacoes: new FormControl('', { nonNullable: true })
  });

  protected readonly regimeOptions = [
    { label: 'CIPA dimensionada', value: 'CIPA_DIMENSIONADA' },
    { label: 'Representante nomeado', value: 'REPRESENTANTE_NOMEADO' }
  ];
  protected readonly ufOptions = UNIDADES_FEDERATIVAS;
  protected readonly grauRiscoOptions = GRAUS_RISCO;
  protected readonly confirmacaoOptions = OPCOES_CONFIRMACAO;
  protected readonly tipoReuniaoOptions = [
    { label: 'Ordinária', value: 'ORDINARIA' },
    { label: 'Extraordinária', value: 'EXTRAORDINARIA' },
    { label: 'Simulado', value: 'SIMULADO' },
    { label: 'Outro', value: 'OUTRO' }
  ];

  protected get breadcrumb(): MenuItem[] {
    return [
      { label: 'Segurança' },
      { label: this.tipo === 'CIPA' ? 'CIPA' : 'Brigada de Incêndio' },
      { label: this.cadastro === 'documento' ? 'Documentos' : 'Reuniões' }
    ];
  }

  ngOnInit(): void {
    this.contexto.selecionar(this.tipo);
    this.carregar();
  }

  protected carregar(): void {
    this.carregando.set(true);
    this.erro.set(null);
    const bases$ = this.rh.listarBasesOperacionaisAtivas();
    let consulta$: Observable<any>;

    if (this.cadastro === 'reuniao') {
      consulta$ = forkJoin({
        bases: bases$,
        corpos: this.api.listarCorpos({ tipo: [this.tipo], status: ['ATIVO'], page: 0, size: 100 }),
        modelos: this.api.listarModelosAtaAplicaveis({ tipo: this.tipo })
      });
    } else if (this.cadastro === 'documento') {
      consulta$ = forkJoin({
        bases: bases$,
        categorias: this.api.listarCategoriasDocumento({
          contexto: [this.tipo, 'AMBOS'],
          confirmada: true,
          page: 0,
          size: 100
        })
      });
    } else {
      consulta$ = forkJoin({
        bases: bases$,
        perfis: this.api.listarPerfisNormativos({
          tipoCorpo: this.tipo,
          confirmado: true,
          page: 0,
          size: 100
        })
      });
    }

    consulta$.pipe(finalize(() => this.carregando.set(false))).subscribe({
      next: (dados) => {
        this.bases.set(dados.bases.content);
        if (dados.corpos) this.corpos.set(dados.corpos.content);
        if (dados.modelos) this.modelosAta.set(dados.modelos);
        if (dados.categorias) this.categorias.set(dados.categorias.content);
        if (dados.perfis) this.perfisNormativos.set(dados.perfis.content);
      },
      error: (error) => this.erro.set(getErrorMessage(error))
    });
  }

  protected titulo(): string {
    if (this.cadastro === 'composicao') {
      return this.tipo === 'CIPA' ? 'Nova composição da CIPA' : 'Nova equipe da Brigada';
    }
    if (this.cadastro === 'reuniao') return 'Agendar reunião';
    return 'Enviar documento';
  }

  protected subtitulo(): string {
    if (this.cadastro === 'composicao') {
      return 'Identifique o grupo, informe o período e confira os dados antes de criar.';
    }
    if (this.cadastro === 'reuniao') {
      return 'Escolha o grupo, organize data e pauta e revise o agendamento.';
    }
    return 'Selecione o arquivo, classifique o documento e informe sua validade.';
  }

  protected passos(): PassoCadastro[] {
    if (this.cadastro === 'composicao') {
      return [
        { numero: 1, label: 'Identificação', icon: 'pi pi-id-card' },
        { numero: 2, label: 'Período', icon: 'pi pi-calendar' },
        { numero: 3, label: 'Revisão', icon: 'pi pi-check' }
      ];
    }
    if (this.cadastro === 'reuniao') {
      return [
        { numero: 1, label: 'Grupo', icon: 'pi pi-users' },
        { numero: 2, label: 'Agenda e pauta', icon: 'pi pi-calendar' },
        { numero: 3, label: 'Revisão', icon: 'pi pi-check' }
      ];
    }
    return [
      { numero: 1, label: 'Arquivo', icon: 'pi pi-upload' },
      { numero: 2, label: 'Classificação', icon: 'pi pi-folder' },
      { numero: 3, label: 'Validade', icon: 'pi pi-calendar' },
      { numero: 4, label: 'Revisão', icon: 'pi pi-check' }
    ];
  }

  protected selecionarArquivo(event: Event): void {
    const input = event.target as HTMLInputElement;
    const arquivo = input.files?.[0] ?? null;
    if (!arquivo) return;
    if (arquivo.size > 15 * 1024 * 1024) {
      this.messages.add({
        severity: 'warn',
        summary: 'Arquivo muito grande',
        detail: 'Selecione um arquivo de até 15 MB.'
      });
      input.value = '';
      return;
    }
    this.arquivo.set(arquivo);
  }

  protected removerArquivo(): void {
    this.arquivo.set(null);
  }

  protected irParaEtapa(etapa: number): void {
    if (etapa < this.activeStep()) this.activeStep.set(etapa);
  }

  protected continuar(): void {
    const etapa = this.activeStep();
    this.etapaTentada.set(Math.max(this.etapaTentada(), etapa));
    if (!this.etapaValida(etapa)) {
      this.messages.add({
        severity: 'warn',
        summary: 'Revise esta etapa',
        detail: 'Preencha os campos indicados antes de continuar.'
      });
      return;
    }
    this.activeStep.update((atual) => Math.min(this.passos().length, atual + 1));
  }

  protected voltarEtapa(): void {
    this.activeStep.update((atual) => Math.max(1, atual - 1));
  }

  protected cancelar(): void {
    this.router.navigate(['/sst/governanca', this.secaoLista()], { queryParams: { tipo: this.tipo } });
  }

  protected irParaComposicoes(): void {
    this.router.navigate(['/sst/governanca/composicoes'], { queryParams: { tipo: this.tipo } });
  }

  protected salvar(): void {
    if (!this.etapaValida(this.activeStep())) return;
    this.salvando.set(true);
    const valor = this.form.getRawValue();
    let operacao$: Observable<any>;

    if (this.cadastro === 'composicao') {
      operacao$ = this.api.criarCorpo(montarCriacaoCorpo(this.tipo, valor));
    } else if (this.cadastro === 'reuniao') {
      operacao$ = this.api.criarReuniao({
        idCorpo: valor.idCorpo,
        tipo: valor.tipoReuniao,
        idModeloAta: valor.idModeloAta,
        dataHora: valor.dataHora?.toISOString(),
        duracaoMinutos: Number(valor.duracaoMinutos),
        localOuMeio: valor.localOuMeio.trim(),
        pauta: valor.pauta.trim(),
        registroHistorico: false
      });
    } else {
      operacao$ = this.api.enviarDocumento(this.arquivo()!, {
        contexto: this.tipo,
        escopoTitular: 'BASE',
        idCategoria: valor.idCategoria,
        idBase: valor.idBaseDocumento,
        idTitular: valor.idBaseDocumento,
        numeroDocumento: valor.numeroDocumento.trim() || undefined,
        emissor: valor.emissor.trim() || undefined,
        dataEmissao: this.data(valor.dataEmissao),
        dataValidade: this.data(valor.dataValidade),
        observacoes: valor.observacoes.trim() || undefined
      });
    }
    operacao$.pipe(finalize(() => this.salvando.set(false))).subscribe({
      next: (registro) => {
        this.messages.add({ severity: 'success', summary: 'Registro salvo' });
        if (this.cadastro === 'reuniao' && registro?.id) {
          this.router.navigate(['/sst/governanca/reunioes', registro.id], {
            queryParams: { tipo: this.tipo }
          });
          return;
        }
        this.cancelar();
      },
      error: (error) =>
        this.messages.add({
          severity: 'error',
          summary: 'Não foi possível salvar',
          detail: getErrorMessage(error),
          life: 7000
        })
    });
  }
  protected campoInvalido(nome: keyof GovernancaCadastroComponent['form']['controls']): boolean {
    if (this.etapaTentada() < this.activeStep()) return false;
    const valor = this.form.controls[nome].value;
    return valor === null || valor === undefined || (typeof valor === 'string' && !valor.trim());
  }

  protected periodoInvalido(): boolean {
    const inicio = this.form.controls.inicioPeriodo.value;
    const fim = this.form.controls.fimPeriodo.value;
    return !!inicio && !!fim && fim < inicio;
  }

  protected periodoCipaInvalido(): boolean {
    const inicio = this.form.controls.inicioPeriodo.value;
    const fim = this.form.controls.fimPeriodo.value;
    return this.tipo === 'CIPA' && !!inicio && !!fim && !periodoCipaAnual(inicio, fim);
  }

  protected basesAbrangiveis(): any[] {
    const principal = this.form.controls.idBaseOperacional.value;
    return this.bases().filter((base) => base.id !== principal);
  }

  protected perfisAplicaveis(): PerfilNormativoGovernanca[] {
    const exigePerfilNacional =
      this.tipo === 'CIPA' &&
      this.form.controls.regimeComposicao.value === 'REPRESENTANTE_NOMEADO';
    return this.perfisNormativos().filter(
      (perfil) =>
        (!exigePerfilNacional || perfil.escopo === 'NACIONAL') &&
        perfilAplicaAoCadastro(
        perfil,
        this.tipo,
        this.form.controls.uf.value,
        this.form.controls.inicioPeriodo.value
      )
    );
  }

  protected perfilOptions(): Array<PerfilNormativoGovernanca & { rotulo: string }> {
    return this.perfisAplicaveis().map((perfil) => ({
      ...perfil,
      rotulo: `${perfil.codigo} · ${perfil.edicao}`
    }));
  }

  protected aoAlterarBase(): void {
    const principal = this.form.controls.idBaseOperacional.value;
    this.form.controls.idsBasesAbrangidas.setValue(
      this.form.controls.idsBasesAbrangidas.value.filter((id) => id !== principal)
    );
  }

  protected aoAlterarRegime(): void {
    if (this.form.controls.regimeComposicao.value !== 'REPRESENTANTE_NOMEADO') {
      this.form.controls.hipoteseRepresentanteNomeadoConfirmada.setValue(false);
    }
    this.revalidarPerfil();
  }
  protected aoAlterarInicio(): void {
    const inicio = this.form.controls.inicioPeriodo.value;
    if (this.tipo === 'CIPA' && inicio) {
      this.form.controls.fimPeriodo.setValue(fimMandatoAnual(inicio));
    }
    this.revalidarPerfil();
  }

  protected revalidarPerfil(): void {
    const selecionado = this.form.controls.idPerfilNormativo.value;
    if (selecionado && !this.perfisAplicaveis().some((perfil) => perfil.id === selecionado)) {
      this.form.controls.idPerfilNormativo.setValue(null);
    }
  }

  protected nomePerfil(id: string | null): string {
    const perfil = this.perfisNormativos().find((item) => item.id === id);
    return perfil ? `${perfil.codigo} · ${perfil.edicao}` : 'Sem perfil normativo vinculado';
  }

  protected validadeInvalida(): boolean {
    const emissao = this.form.controls.dataEmissao.value;
    const validade = this.form.controls.dataValidade.value;
    return !!emissao && !!validade && validade < emissao;
  }

  protected nomeBase(id: string | null): string {
    return this.bases().find((base) => base.id === id)?.nome ?? 'Não informada';
  }

  protected nomeCorpo(id: string | null): string {
    return this.corpos().find((corpo) => corpo.id === id)?.nome ?? 'Não informado';
  }

  protected nomeModelo(id: string | null): string {
    return this.modelosAta().find((modelo) => modelo.id === id)?.nome ?? 'Não informado';
  }

  protected nomeTipoReuniao(tipo: string): string {
    return this.tipoReuniaoOptions.find((opcao) => opcao.value === tipo)?.label ?? tipo;
  }

  protected nomeCategoria(id: string | null): string {
    return this.categorias().find((categoria) => categoria.id === id)?.nome ?? 'Não informada';
  }

  private etapaValida(etapa: number): boolean {
    const valor = this.form.getRawValue();
    if (this.cadastro === 'composicao') {
      if (etapa === 1) return !!valor.nome.trim() && !!valor.idBaseOperacional;
      if (etapa === 2) {
        return (
          !!valor.unidadeGovernanca.trim() &&
          !!valor.uf &&
          !!valor.grauRisco &&
          !!valor.inicioPeriodo &&
          !!valor.fimPeriodo &&
          !this.periodoInvalido() &&
          !this.periodoCipaInvalido() &&
          (this.tipo !== 'CIPA' ||
            valor.regimeComposicao !== 'REPRESENTANTE_NOMEADO' ||
            (valor.hipoteseRepresentanteNomeadoConfirmada && !!valor.idPerfilNormativo))
        );
      }
      return true;
    }
    if (this.cadastro === 'reuniao') {
      if (etapa === 1) return !!valor.idCorpo && !!valor.idModeloAta;
      if (etapa === 2) {
        return (
          !!valor.dataHora &&
          Number(valor.duracaoMinutos) > 0 &&
          !!valor.localOuMeio.trim() &&
          !!valor.pauta.trim()
        );
      }
      return true;
    }
    if (etapa === 1) return !!this.arquivo();
    if (etapa === 2) return !!valor.idCategoria && !!valor.idBaseDocumento;
    if (etapa === 3) return !this.validadeInvalida();
    return true;
  }

  private secaoLista(): 'composicoes' | 'reunioes' | 'documentos' {
    return this.cadastro === 'composicao'
      ? 'composicoes'
      : this.cadastro === 'reuniao'
        ? 'reunioes'
        : 'documentos';
  }

  private data(valor: Date | null): string | undefined {
    if (!valor) return undefined;
    return `${valor.getFullYear()}-${String(valor.getMonth() + 1).padStart(2, '0')}-${String(valor.getDate()).padStart(2, '0')}`;
  }
}
