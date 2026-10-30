import { DateMaskDirective } from '../../../shared/directives/date-mask.directive';
import { CommonModule, Location } from '@angular/common';
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
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { StepperModule } from 'primeng/stepper';
import { finalize, forkJoin, of } from 'rxjs';

import {
  AtualizarCorpoGovernancaRequest,
  CorpoGovernancaDetalhe,
  PerfilNormativoGovernanca,
  RegimeComposicaoGovernanca,
  TipoCorpoGovernanca
} from '../../../core/models/governanca-sst.models';
import { BaseOperacionalResponse } from '../../../core/models/rh.models';
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
import { GovernancaMembrosEtapaComponent } from './governanca-membros-etapa.component';
import { GovernancaContextoState, normalizarTipoGovernanca } from './governanca-state';

@Component({
  selector: 'app-governanca-composicao-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule, ReactiveFormsModule, BreadcrumbModule, ButtonModule, DatePickerModule,
    InputTextModule, MultiSelectModule, ProgressSpinnerModule, SelectModule, StepperModule,
    GovernancaMembrosEtapaComponent
  , DateMaskDirective],
  templateUrl: './governanca-composicao-form.component.html',
  styleUrl: './governanca-composicao-form.component.css'
})
export class GovernancaComposicaoFormComponent implements OnInit {
  private readonly api = inject(GovernancaSstService);
  private readonly rh = inject(RhService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly messages = inject(MessageService);
  private readonly contexto = inject(GovernancaContextoState);

  protected readonly idEdicao = this.route.snapshot.paramMap.get('id');
  protected readonly carregando = signal(true);
  protected readonly salvando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly activeStep = signal(1);
  protected readonly etapaTentada = signal(0);
  protected readonly bases = signal<BaseOperacionalResponse[]>([]);
  protected readonly perfisNormativos = signal<PerfilNormativoGovernanca[]>([]);
  protected readonly detalheCorpo = signal<CorpoGovernancaDetalhe | null>(null);

  protected readonly tipo: TipoCorpoGovernanca =
    normalizarTipoGovernanca(this.route.snapshot.queryParamMap.get('tipo')) ?? this.contexto.tipo();

  protected readonly form = new FormGroup({
    nome: new FormControl('', { nonNullable: true }),
    idBaseOperacional: new FormControl<string | null>(null),
    idsBasesAbrangidas: new FormControl<string[]>([], { nonNullable: true }),
    regimeComposicao: new FormControl<RegimeComposicaoGovernanca>('CIPA_DIMENSIONADA', { nonNullable: true }),
    unidadeGovernanca: new FormControl('', { nonNullable: true }),
    uf: new FormControl<string | null>(null),
    grauRisco: new FormControl<number | null>(null),
    idPerfilNormativo: new FormControl<string | null>(null),
    hipoteseRepresentanteNomeadoConfirmada: new FormControl(false, { nonNullable: true }),
    inicioPeriodo: new FormControl<Date | null>(null),
    fimPeriodo: new FormControl<Date | null>(null)
  });

  protected readonly passos = [
    { numero: 1, label: 'Identificação', icon: 'pi pi-id-card' },
    { numero: 2, label: 'Período', icon: 'pi pi-calendar' },
    { numero: 3, label: 'Revisão', icon: 'pi pi-check' },
    { numero: 4, label: 'Integrantes', icon: 'pi pi-users' }
  ];
  protected readonly regimeOptions = [
    { label: 'CIPA dimensionada', value: 'CIPA_DIMENSIONADA' },
    { label: 'Representante nomeado', value: 'REPRESENTANTE_NOMEADO' }
  ];
  protected readonly ufOptions = UNIDADES_FEDERATIVAS;
  protected readonly grauRiscoOptions = GRAUS_RISCO;
  protected readonly confirmacaoOptions = OPCOES_CONFIRMACAO;

  protected get breadcrumb(): MenuItem[] {
    return [
      { label: 'Segurança' },
      { label: this.tipo === 'CIPA' ? 'CIPA' : 'Brigada de Incêndio' },
      { label: this.titulo() }
    ];
  }

  ngOnInit(): void {
    this.contexto.selecionar(this.tipo);
    this.carregar();
  }

  protected titulo(): string {
    if (this.idEdicao) return this.tipo === 'CIPA' ? 'Editar composição da CIPA' : 'Editar equipe da Brigada';
    return this.tipo === 'CIPA' ? 'Nova composição da CIPA' : 'Nova equipe da Brigada';
  }

  protected subtitulo(): string {
    return this.idEdicao
      ? 'Revise os dados e mantenha os integrantes no mesmo fluxo.'
      : 'Cadastre os dados da formação e finalize incluindo os integrantes.';
  }

  protected continuar(): void {
    const etapa = this.activeStep();
    this.etapaTentada.set(Math.max(this.etapaTentada(), etapa));
    if (!this.etapaValida(etapa)) {
      this.messages.add({ severity: 'warn', summary: 'Revise esta etapa', detail: 'Preencha os campos indicados.' });
      return;
    }
    this.activeStep.update((atual) => Math.min(3, atual + 1));
  }

  protected voltarEtapa(): void { this.activeStep.update((atual) => Math.max(1, atual - 1)); }
  protected irParaEtapa(etapa: number): void {
    if (etapa < this.activeStep() || (this.detalheCorpo() && etapa <= 4)) this.activeStep.set(etapa);
  }
  protected cancelar(): void { this.irParaLista(); }
  protected concluir(): void { this.irParaLista(); }

  protected salvarEContinuar(): void {
    this.etapaTentada.set(3);
    if (!this.etapaValida(1) || !this.etapaValida(2)) {
      this.messages.add({ severity: 'warn', summary: 'Revise o cadastro', detail: 'Há campos obrigatórios pendentes.' });
      return;
    }
    const dados = montarCriacaoCorpo(this.tipo, this.form.getRawValue());
    const detalheAtual = this.detalheCorpo();
    const operacao$ = detalheAtual
      ? this.api.atualizarCorpo(detalheAtual.corpo.id, this.montarAtualizacao(detalheAtual, dados))
      : this.api.criarCorpo(dados);
    this.salvando.set(true);
    operacao$.pipe(finalize(() => this.salvando.set(false))).subscribe({
      next: (detalhe) => {
        const criado = !detalheAtual;
        this.detalheCorpo.set(detalhe);
        this.activeStep.set(4);
        this.messages.add({
          severity: 'success',
          summary: criado ? (this.tipo === 'CIPA' ? 'CIPA criada' : 'Equipe criada') : 'Formação atualizada',
          detail: criado ? 'Continue incluindo os integrantes nesta última etapa.' : 'Dados salvos. Revise os integrantes.'
        });
        if (criado) {
          const url = this.router.createUrlTree(
            ['/sst/governanca/composicoes', detalhe.corpo.id, 'editar'],
            { queryParams: { tipo: this.tipo } }
          ).toString();
          this.location.replaceState(url);
        }
      },
      error: (error) => this.messages.add({ severity: 'error', summary: 'Não foi possível salvar', detail: getErrorMessage(error), life: 7000 })
    });
  }

  protected atualizarDetalhe(detalhe: CorpoGovernancaDetalhe): void { this.detalheCorpo.set(detalhe); }
  protected basesAbrangiveis(): BaseOperacionalResponse[] {
    const principal = this.form.controls.idBaseOperacional.value;
    return this.bases().filter((base) => base.id !== principal);
  }
  protected perfisAplicaveis(): PerfilNormativoGovernanca[] {
    const valor = this.form.getRawValue();
    return this.perfisNormativos().filter((perfil) => {
      if (!perfilAplicaAoCadastro(perfil, this.tipo, valor.uf, valor.inicioPeriodo)) return false;
      return valor.regimeComposicao !== 'REPRESENTANTE_NOMEADO' || perfil.escopo === 'NACIONAL';
    });
  }
  protected perfilOptions() {
    return this.perfisAplicaveis().map((perfil) => ({ ...perfil, rotulo: `${perfil.codigo} · ${perfil.edicao}` }));
  }
  protected aoAlterarBase(): void {
    const principal = this.form.controls.idBaseOperacional.value;
    this.form.controls.idsBasesAbrangidas.setValue(this.form.controls.idsBasesAbrangidas.value.filter((id) => id !== principal));
  }
  protected aoAlterarRegime(): void {
    if (this.form.controls.regimeComposicao.value !== 'REPRESENTANTE_NOMEADO') {
      this.form.controls.hipoteseRepresentanteNomeadoConfirmada.setValue(false);
    }
    this.revalidarPerfil();
  }
  protected aoAlterarInicio(): void {
    const inicio = this.form.controls.inicioPeriodo.value;
    if (this.tipo === 'CIPA' && inicio) this.form.controls.fimPeriodo.setValue(fimMandatoAnual(inicio));
    this.revalidarPerfil();
  }
  protected revalidarPerfil(): void {
    const selecionado = this.form.controls.idPerfilNormativo.value;
    if (selecionado && !this.perfisAplicaveis().some((perfil) => perfil.id === selecionado)) {
      this.form.controls.idPerfilNormativo.setValue(null);
    }
  }
  protected nomeBase(id: string | null): string { return this.bases().find((base) => base.id === id)?.nome ?? 'Não informada'; }
  protected nomePerfil(id: string | null): string {
    const perfil = this.perfisNormativos().find((item) => item.id === id);
    return perfil ? `${perfil.codigo} · ${perfil.edicao}` : 'Sem perfil normativo vinculado';
  }
  protected campoInvalido(nome: keyof GovernancaComposicaoFormComponent['form']['controls']): boolean {
    if (this.etapaTentada() < this.activeStep()) return false;
    const valor = this.form.controls[nome].value;
    return valor === null || valor === undefined || (typeof valor === 'string' && !valor.trim());
  }
  protected periodoInvalido(): boolean {
    const { inicioPeriodo, fimPeriodo } = this.form.getRawValue();
    return !!inicioPeriodo && !!fimPeriodo && fimPeriodo < inicioPeriodo;
  }
  protected periodoCipaInvalido(): boolean {
    const { inicioPeriodo, fimPeriodo } = this.form.getRawValue();
    return this.tipo === 'CIPA' && !!inicioPeriodo && !!fimPeriodo && !periodoCipaAnual(inicioPeriodo, fimPeriodo);
  }

  protected carregar(): void {
    this.carregando.set(true);
    forkJoin({
      bases: this.rh.listarBasesOperacionaisAtivas(),
      perfis: this.api.listarPerfisNormativos({ tipoCorpo: this.tipo, confirmado: true, page: 0, size: 100 }),
      detalhe: this.idEdicao ? this.api.buscarCorpo(this.idEdicao) : of(null)
    }).pipe(finalize(() => this.carregando.set(false))).subscribe({
      next: ({ bases, perfis, detalhe }) => {
        this.bases.set(bases.content);
        this.perfisNormativos.set(perfis.content);
        if (detalhe) this.preencherEdicao(detalhe);
      },
      error: (error) => this.erro.set(getErrorMessage(error))
    });
  }

  private preencherEdicao(detalhe: CorpoGovernancaDetalhe): void {
    this.detalheCorpo.set(detalhe);
    const corpo = detalhe.corpo;
    this.form.patchValue({
      nome: corpo.nome, idBaseOperacional: corpo.idBaseOperacional,
      idsBasesAbrangidas: detalhe.basesAbrangidas.map((base) => base.idBaseOperacional),
      regimeComposicao: corpo.regimeComposicao ?? 'CIPA_DIMENSIONADA', unidadeGovernanca: corpo.unidadeGovernanca,
      uf: corpo.uf, grauRisco: corpo.grauRisco ?? null, idPerfilNormativo: corpo.idPerfilNormativo ?? null,
      hipoteseRepresentanteNomeadoConfirmada: corpo.hipoteseRepresentanteNomeadoConfirmada,
      inicioPeriodo: this.data(corpo.inicioPeriodo), fimPeriodo: this.data(corpo.fimPeriodo)
    });
    this.form.controls.idBaseOperacional.disable();
    this.form.controls.idsBasesAbrangidas.disable();
    this.form.controls.regimeComposicao.disable();
  }

  private montarAtualizacao(
    detalhe: CorpoGovernancaDetalhe,
    dados: ReturnType<typeof montarCriacaoCorpo>
  ): AtualizarCorpoGovernancaRequest {
    return {
      versao: detalhe.corpo.versao, nome: dados.nome, unidadeGovernanca: dados.unidadeGovernanca,
      uf: dados.uf, inicioPeriodo: dados.inicioPeriodo, fimPeriodo: dados.fimPeriodo,
      grauRisco: dados.grauRisco, idPerfilNormativo: dados.idPerfilNormativo,
      hipoteseRepresentanteNomeadoConfirmada: dados.hipoteseRepresentanteNomeadoConfirmada
    };
  }
  private etapaValida(etapa: number): boolean {
    const valor = this.form.getRawValue();
    if (etapa === 1) return !!valor.nome.trim() && !!valor.idBaseOperacional;
    if (etapa === 2) return !!valor.unidadeGovernanca.trim() && !!valor.uf && !!valor.grauRisco &&
      !!valor.inicioPeriodo && !!valor.fimPeriodo && !this.periodoInvalido() && !this.periodoCipaInvalido() &&
      (this.tipo !== 'CIPA' || valor.regimeComposicao !== 'REPRESENTANTE_NOMEADO' ||
        (valor.hipoteseRepresentanteNomeadoConfirmada && !!valor.idPerfilNormativo));
    return true;
  }
  private data(valor: string): Date { return new Date(`${valor}T12:00:00`); }
  private irParaLista(): void {
    this.router.navigate(['/sst/governanca/composicoes'], { queryParams: { tipo: this.tipo } });
  }
}
