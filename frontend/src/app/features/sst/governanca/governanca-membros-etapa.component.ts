import { DateMaskDirective } from '../../../shared/directives/date-mask.directive';
import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, EventEmitter, Input, OnInit, Output, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DatePickerModule } from 'primeng/datepicker';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';
import { finalize, forkJoin, map, Observable, of, switchMap } from 'rxjs';

import {
  CorpoGovernancaDetalhe,
  MembroCorpoGovernanca,
  PapelMembroGovernanca,
  RepresentacaoCipa,
  TipoCorpoGovernanca,
  TitularidadeCipa
} from '../../../core/models/governanca-sst.models';
import { ColaboradorResponse } from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AuthService } from '../../../core/services/auth.service';
import { GovernancaSstService } from '../../../core/services/governanca-sst.service';
import { RhService } from '../../../core/services/rh.service';
import {
  FormularioMembroGovernanca,
  PAPEIS_BRIGADA,
  PAPEIS_CIPA,
  dataInicialSugerida,
  dataLocal,
  montarDadosMembro
} from './governanca-membros.utils';
import { RemoteLookup } from '../../../core/utils/remote-lookup.util';

type ModoManutencao = 'ADICIONAR' | 'SAIDA' | 'SUBSTITUIR' | null;

@Component({
  selector: 'app-governanca-membros-etapa',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule, ReactiveFormsModule, ButtonModule, CheckboxModule, DatePickerModule,
    InputTextModule, SelectModule, TableModule, TagModule, TextareaModule, TooltipModule
  , DateMaskDirective],
  templateUrl: './governanca-membros-etapa.component.html',
  styleUrls: ['./governanca-membros-etapa.component.css', './governanca-table.css']
})
export class GovernancaMembrosEtapaComponent implements OnInit {
  private readonly api = inject(GovernancaSstService);
  private readonly rh = inject(RhService);
  private readonly auth = inject(AuthService);
  private readonly messages = inject(MessageService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly colaboradorLookup = new RemoteLookup<ColaboradorResponse>(
    {
      identify: (colaborador) => colaborador.id,
      selectedKeys: () => {
        const id = this.formMembro.controls.idColaborador.value;
        return id ? [id] : [];
      },
      load: ({ query, page, size }) => {
        const detalhe = this.detalheAtual();
        if (!detalhe) {
          return of({ content: [], page: { size, number: page, totalElements: 0, totalPages: 0 } });
        }
        const idsBases = [...new Set([
          detalhe.corpo.idBaseOperacional,
          ...detalhe.basesAbrangidas.map((base) => base.idBaseOperacional)
        ])];
        return forkJoin(idsBases.map(idBaseOperacional => this.rh.listarColaboradoresParaSelecao({
          ativo: true,
          idBaseOperacional,
          buscaGlobal: query,
          page,
          size,
          sort: 'nomeCompleto,asc'
        }))).pipe(map(paginas => ({
          content: [...new Map(
            paginas.flatMap(pagina => pagina.content).map(colaborador => [colaborador.id, colaborador] as const)
          ).values()].sort((a, b) => a.nomeCompleto.localeCompare(b.nomeCompleto, 'pt-BR')),
          page: {
            size,
            number: page,
            totalElements: paginas.reduce((total, pagina) => total + pagina.page.totalElements, 0),
            totalPages: Math.max(0, ...paginas.map(pagina => pagina.page.totalPages))
          }
        })));
      }
    },
    this.destroyRef
  );

  @Input({ required: true }) tipo!: TipoCorpoGovernanca;
  @Input({ required: true }) set detalhe(valor: CorpoGovernancaDetalhe) { this.detalheAtual.set(valor); }
  @Output() readonly atualizado = new EventEmitter<CorpoGovernancaDetalhe>();

  protected readonly detalheAtual = signal<CorpoGovernancaDetalhe | null>(null);
  protected readonly colaboradores = this.colaboradorLookup.items;
  protected readonly carregandoColaboradores = this.colaboradorLookup.loading;
  protected readonly processando = signal(false);
  protected readonly modo = signal<ModoManutencao>(null);
  protected readonly membroSelecionado = signal<MembroCorpoGovernanca | null>(null);
  protected readonly formularioTentado = signal(false);
  protected readonly exibirHistorico = signal(false);

  protected readonly representacaoOptions = [
    { label: 'Empregador', value: 'EMPREGADOR' as RepresentacaoCipa },
    { label: 'Empregados', value: 'EMPREGADOS' as RepresentacaoCipa }
  ];
  protected readonly titularidadeOptions = [
    { label: 'Titular', value: 'TITULAR' as TitularidadeCipa },
    { label: 'Suplente', value: 'SUPLENTE' as TitularidadeCipa }
  ];

  protected readonly formMembro = new FormGroup({
    idColaborador: new FormControl<string | null>(null),
    papel: new FormControl<PapelMembroGovernanca | null>(null),
    representacao: new FormControl<RepresentacaoCipa | null>(null),
    titularidade: new FormControl<TitularidadeCipa | null>(null),
    cicloEleitoral: new FormControl('', { nonNullable: true }),
    classificacaoEleitoral: new FormControl<number | null>(null),
    atoDesignacao: new FormControl('', { nonNullable: true }),
    reeleito: new FormControl(false, { nonNullable: true }),
    inicioParticipacao: new FormControl<Date | null>(null),
    turnoDisponivel: new FormControl('', { nonNullable: true })
  });
  protected readonly formMovimentacao = new FormGroup({
    dataEfeito: new FormControl<Date | null>(null),
    motivo: new FormControl('', { nonNullable: true }),
    fundamento: new FormControl('', { nonNullable: true })
  });

  ngOnInit(): void {
    if (this.membrosAtivos().length === 0) this.prepararAdicao();
    this.colaboradorLookup.open();
  }

  protected corpo() { return this.detalheAtual()?.corpo; }
  protected membrosAtivos(): MembroCorpoGovernanca[] {
    return this.detalheAtual()?.membros.filter((membro) => !membro.fimParticipacao) ?? [];
  }
  protected membrosHistoricos(): MembroCorpoGovernanca[] {
    return this.detalheAtual()?.membros.filter((membro) => !!membro.fimParticipacao) ?? [];
  }
  protected membrosExibidos(): MembroCorpoGovernanca[] {
    const ativos = [...this.membrosAtivos()].sort((a, b) => a.nomeSnapshot.localeCompare(b.nomeSnapshot, 'pt-BR'));
    if (!this.exibirHistorico()) return ativos;
    return [...ativos, ...this.membrosHistoricos().sort((a, b) => a.nomeSnapshot.localeCompare(b.nomeSnapshot, 'pt-BR'))];
  }
  protected podeGerenciar(): boolean {
    return this.temPermissao('sst:governanca:write') && this.corpo()?.status !== 'ENCERRADO';
  }
  protected podeAtivar(): boolean {
    return this.temPermissao('sst:governanca:aprovar') && this.corpo()?.status === 'EM_PREPARACAO';
  }
  protected colaboradoresDisponiveis(): ColaboradorResponse[] {
    const ativos = new Set(this.membrosAtivos().map((membro) => membro.idColaborador));
    return this.colaboradores().filter((colaborador) => !ativos.has(colaborador.id));
  }
  protected papeisOptions() {
    if (this.corpo()?.regimeComposicao === 'REPRESENTANTE_NOMEADO') {
      return [{ label: 'Representante nomeado', value: 'REPRESENTANTE_NOMEADO' as PapelMembroGovernanca }];
    }
    return this.tipo === 'CIPA' ? PAPEIS_CIPA : PAPEIS_BRIGADA;
  }

  protected prepararAdicao(): void {
    this.modo.set('ADICIONAR');
    this.membroSelecionado.set(null);
    this.prepararFormularioMembro();
  }

  protected fecharEditor(): void {
    this.modo.set(null);
    this.membroSelecionado.set(null);
    this.formularioTentado.set(false);
  }

  protected prepararSaida(membro: MembroCorpoGovernanca): void {
    this.modo.set('SAIDA');
    this.membroSelecionado.set(membro);
    this.formMovimentacao.reset({ dataEfeito: this.dataMovimentacaoSugerida(membro), motivo: '', fundamento: '' });
    this.formularioTentado.set(false);
  }

  protected prepararSubstituicao(membro: MembroCorpoGovernanca): void {
    const dataEfeito = this.dataMovimentacaoSugerida(membro);
    this.modo.set('SUBSTITUIR');
    this.membroSelecionado.set(membro);
    this.prepararFormularioMembro(membro, dataEfeito);
    this.formMovimentacao.reset({ dataEfeito, motivo: '', fundamento: '' });
  }

  protected aoAlterarRepresentacao(): void {
    this.formMembro.patchValue({ cicloEleitoral: '', classificacaoEleitoral: null, atoDesignacao: '', reeleito: false });
  }

  protected aoAlterarDataEfeito(): void {
    if (this.modo() === 'SUBSTITUIR') {
      this.formMembro.controls.inicioParticipacao.setValue(this.formMovimentacao.controls.dataEfeito.value);
    }
  }

  protected pesquisarColaboradores(event: { filter?: string }): void {
    this.colaboradorLookup.search(event.filter ?? '');
  }

  protected carregarMaisColaboradores(): void {
    this.colaboradorLookup.loadNext();
  }

  protected salvarManutencao(): void {
    this.formularioTentado.set(true);
    if (this.modo() === 'SAIDA') { this.registrarSaida(); return; }
    if (!this.formularioMembroValido()) { this.avisoFormulario(); return; }
    if (this.modo() === 'SUBSTITUIR') this.substituirMembro();
    else this.adicionarMembro();
  }

  protected ativarCorpo(): void {
    const corpo = this.corpo();
    if (!corpo) return;
    this.processando.set(true);
    this.api.ativarCorpo(corpo.id, corpo.versao).pipe(finalize(() => this.processando.set(false))).subscribe({
      next: (detalhe) => {
        this.aplicarAtualizacao(detalhe);
        this.messages.add({ severity: 'success', summary: this.tipo === 'CIPA' ? 'CIPA ativada' : 'Equipe ativada' });
      },
      error: (error) => this.avisoErro(error)
    });
  }

  protected tituloFormulario(): string {
    if (this.modo() === 'SAIDA') return 'Retirar integrante';
    if (this.modo() === 'SUBSTITUIR') return 'Substituir integrante';
    if (this.modo() === 'ADICIONAR') return this.tipo === 'CIPA' ? 'Adicionar integrante' : 'Adicionar brigadista';
    return 'Integrantes da formação';
  }

  protected colaboradorSelecionado(): ColaboradorResponse | undefined {
    const id = this.formMembro.controls.idColaborador.value;
    return this.colaboradores().find((colaborador) => colaborador.id === id);
  }

  protected orientacaoVinculo(): string {
    if (this.tipo === 'BRIGADA') return 'Defina a responsabilidade e a disponibilidade do brigadista nesta equipe.';
    if (this.corpo()?.regimeComposicao === 'REPRESENTANTE_NOMEADO') {
      return 'O representante nomeado deve possuir o respectivo ato de designação.';
    }
    if (this.formMembro.controls.representacao.value === 'EMPREGADOS') {
      return 'Representantes dos empregados são eleitos; informe o ciclo e a classificação eleitoral.';
    }
    if (this.formMembro.controls.representacao.value === 'EMPREGADOR') {
      return 'Representantes do empregador são designados pela empresa; informe o ato de designação.';
    }
    return 'Escolha quem o integrante representa para exibir somente os dados aplicáveis ao vínculo.';
  }

  protected rotulo(valor?: string | null): string {
    if (!valor) return 'Não informado';
    const nomes: Record<string, string> = {
      PRESIDENTE: 'Presidente', VICE_PRESIDENTE: 'Vice-presidente', MEMBRO: 'Membro',
      REPRESENTANTE_NOMEADO: 'Representante nomeado', COORDENADOR_GERAL: 'Coordenador geral',
      CHEFE: 'Chefe', LIDER: 'Líder', BRIGADISTA: 'Brigadista', EMPREGADOR: 'Empregador',
      EMPREGADOS: 'Empregados', TITULAR: 'Titular', SUPLENTE: 'Suplente',
      DESIGNADO: 'Designado', ELEITO: 'Eleito'
    };
    return nomes[valor] ?? valor.replaceAll('_', ' ').toLocaleLowerCase('pt-BR');
  }

  protected iniciais(nome: string): string {
    return nome.split(/\s+/).filter(Boolean).slice(0, 2).map((parte) => parte[0]).join('').toUpperCase();
  }
  protected dataMinima(): Date | null {
    return this.corpo() ? new Date(`${this.corpo()!.inicioPeriodo}T12:00:00`) : null;
  }
  protected dataMaxima(): Date | null {
    return this.corpo() ? new Date(`${this.corpo()!.fimPeriodo}T12:00:00`) : null;
  }
  protected origemEleita(): boolean {
    return this.tipo === 'CIPA' && this.corpo()?.regimeComposicao !== 'REPRESENTANTE_NOMEADO' &&
      this.formMembro.controls.representacao.value === 'EMPREGADOS';
  }
  protected exigeAtoDesignacao(): boolean { return this.tipo === 'CIPA' && !this.origemEleita(); }
  protected campoInvalido(nome: keyof GovernancaMembrosEtapaComponent['formMembro']['controls']): boolean {
    if (!this.formularioTentado()) return false;
    const valor = this.formMembro.controls[nome].value;
    return valor === null || valor === undefined || (typeof valor === 'string' && !valor.trim());
  }
  protected movimentacaoInvalida(nome: keyof GovernancaMembrosEtapaComponent['formMovimentacao']['controls']): boolean {
    if (!this.formularioTentado()) return false;
    const valor = this.formMovimentacao.controls[nome].value;
    return valor === null || valor === undefined || (typeof valor === 'string' && !valor.trim());
  }

  private prepararFormularioMembro(
    membro?: MembroCorpoGovernanca,
    inicio = this.corpo() ? dataInicialSugerida(this.corpo()!) : null
  ): void {
    const representante = this.corpo()?.regimeComposicao === 'REPRESENTANTE_NOMEADO';
    this.formMembro.reset({
      idColaborador: null,
      papel: representante ? 'REPRESENTANTE_NOMEADO' : membro?.papel ?? null,
      representacao: representante ? 'EMPREGADOS' : membro?.representacao ?? null,
      titularidade: representante ? null : membro?.titularidade ?? null,
      cicloEleitoral: '', classificacaoEleitoral: null, atoDesignacao: '', reeleito: false,
      inicioParticipacao: inicio, turnoDisponivel: membro?.turnoSnapshot ?? ''
    });
    this.formularioTentado.set(false);
  }

  private adicionarMembro(): void {
    const corpo = this.corpo();
    if (!corpo) return;
    const membro = montarDadosMembro(this.tipo, corpo, this.formMembro.getRawValue() as FormularioMembroGovernanca);
    this.concluir(this.api.adicionarMembro(corpo.id, { versao: corpo.versao, membro }), 'Integrante adicionado');
  }

  private registrarSaida(): void {
    const corpo = this.corpo();
    const membro = this.membroSelecionado();
    const valor = this.formMovimentacao.getRawValue();
    if (!corpo || !membro || !valor.dataEfeito || !valor.motivo.trim()) { this.avisoFormulario(); return; }
    this.concluir(this.api.registrarSaidaMembro(corpo.id, membro.id, {
      versao: corpo.versao, dataEfeito: dataLocal(valor.dataEfeito), motivo: valor.motivo.trim()
    }), 'Integrante retirado da formação');
  }

  private substituirMembro(): void {
    const corpo = this.corpo();
    const membroAtual = this.membroSelecionado();
    const movimento = this.formMovimentacao.getRawValue();
    if (!corpo || !membroAtual || !movimento.dataEfeito || !movimento.motivo.trim() || !movimento.fundamento.trim()) {
      this.avisoFormulario(); return;
    }
    const sucessor = montarDadosMembro(this.tipo, corpo, this.formMembro.getRawValue() as FormularioMembroGovernanca);
    this.concluir(this.api.substituirMembro(corpo.id, membroAtual.id, {
      versao: corpo.versao, dataEfeito: dataLocal(movimento.dataEfeito),
      motivoSaida: movimento.motivo.trim(), fundamentoProcedimental: movimento.fundamento.trim(), sucessor
    }), 'Substituição registrada');
  }

  private concluir(operacao: Observable<unknown>, mensagem: string): void {
    const corpo = this.corpo();
    if (!corpo) return;
    this.processando.set(true);
    operacao.pipe(
      switchMap(() => this.api.buscarCorpo(corpo.id)), finalize(() => this.processando.set(false))
    ).subscribe({
      next: (detalhe) => {
        const continuarAdicionando = this.modo() === 'ADICIONAR';
        this.aplicarAtualizacao(detalhe);
        if (continuarAdicionando) this.prepararAdicao();
        else this.fecharEditor();
        this.colaboradorLookup.search('', true);
        this.messages.add({ severity: 'success', summary: mensagem });
      },
      error: (error) => this.avisoErro(error)
    });
  }

  private aplicarAtualizacao(detalhe: CorpoGovernancaDetalhe): void {
    this.detalheAtual.set(detalhe);
    this.atualizado.emit(detalhe);
  }
  protected formularioMembroValido(): boolean {
    const valor = this.formMembro.getRawValue();
    if (!valor.idColaborador || !valor.papel || !valor.inicioParticipacao) return false;
    if (this.tipo === 'BRIGADA') return true;
    if (this.corpo()?.regimeComposicao === 'REPRESENTANTE_NOMEADO') return !!valor.atoDesignacao.trim();
    if (!valor.representacao || !valor.titularidade) return false;
    return valor.representacao === 'EMPREGADOR'
      ? !!valor.atoDesignacao.trim()
      : !!valor.cicloEleitoral.trim() && Number(valor.classificacaoEleitoral) > 0;
  }
  private dataMovimentacaoSugerida(membro: MembroCorpoGovernanca): Date {
    const sugerida = this.corpo() ? dataInicialSugerida(this.corpo()!) : new Date();
    const entrada = new Date(`${membro.inicioParticipacao}T12:00:00`);
    return sugerida < entrada ? entrada : sugerida;
  }
  private temPermissao(permissao: string): boolean {
    return this.auth.currentUser()?.permissoes?.includes(permissao) ?? false;
  }
  private avisoFormulario(): void {
    this.messages.add({ severity: 'warn', summary: 'Revise os dados', detail: 'Preencha os campos obrigatórios.' });
  }
  private avisoErro(error: unknown, summary = 'Não foi possível atualizar a formação'): void {
    this.messages.add({ severity: 'error', summary, detail: getErrorMessage(error), life: 7000 });
  }
}
