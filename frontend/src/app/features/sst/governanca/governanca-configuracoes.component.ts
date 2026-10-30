import { DateMaskDirective } from '../../../shared/directives/date-mask.directive';
import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DatePickerModule } from 'primeng/datepicker';
import { FileUploadModule } from 'primeng/fileupload';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { finalize, forkJoin, switchMap } from 'rxjs';
import { CategoriaDocumentoGovernanca, ModeloAtaGovernanca, ModeloChecklistGovernanca, SecaoModeloAta, TreinamentoGovernanca } from '../../../core/models/governanca-sst.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { GovernancaSstService } from '../../../core/services/governanca-sst.service';
import { RhService } from '../../../core/services/rh.service';
import { SstService } from '../../../core/services/sst.service';
import { GovernancaContextoComponent } from './governanca-contexto.component';
import { GovernancaContextoState, normalizarTipoGovernanca } from './governanca-state';

type SecaoConfiguracao = 'inicio' | 'modelos-ata' | 'checklists' | 'treinamentos' | 'regras-treinamento' | 'escolas' | 'documentos' | 'ativos';
type CadastroConfiguracao = Exclude<SecaoConfiguracao, 'inicio'>;

interface ItemConfiguracao { id: CadastroConfiguracao; titulo: string; descricao: string; acao: string; icon: string; compartilhado?: boolean; }

@Component({
  selector: 'app-governanca-configuracoes',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    BreadcrumbModule,
    ButtonModule,
    CheckboxModule,
    DatePickerModule,
    FileUploadModule,
    InputTextModule,
    SelectModule,
    TableModule,
    TagModule,
    TextareaModule,
    ToastModule,
    TooltipModule,
    GovernancaContextoComponent
  , DateMaskDirective],
  providers: [MessageService],
  templateUrl: './governanca-configuracoes.component.html',
  styleUrls: ['./governanca-configuracoes.component.css', './governanca-configuracoes-form.component.css',
    './governanca-configuracoes-table.component.css', './governanca-table.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class GovernancaConfiguracoesComponent implements OnInit {
  private readonly api = inject(GovernancaSstService);
  private readonly rh = inject(RhService);
  private readonly sst = inject(SstService);
  private readonly route = inject(ActivatedRoute);
  readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly contexto = inject(GovernancaContextoState);

  readonly secao = signal<SecaoConfiguracao>('inicio');
  readonly cadastro = signal<CadastroConfiguracao | null>(null);
  readonly carregando = signal(true);
  readonly processando = signal<string | null>(null);
  readonly tipoModelo = signal<'CIPA' | 'BRIGADA'>('CIPA');

  readonly modelos = signal<ModeloAtaGovernanca[]>([]);
  readonly modelosChecklist = signal<ModeloChecklistGovernanca[]>([]);
  readonly formularios = signal<any[]>([]);
  readonly escolas = signal<any[]>([]);
  readonly categorias = signal<CategoriaDocumentoGovernanca[]>([]);
  readonly treinamentos = signal<TreinamentoGovernanca[]>([]);
  readonly exigencias = signal<any[]>([]);
  readonly ativos = signal<any[]>([]);
  readonly bases = signal<any[]>([]);
  readonly funcoes = signal<any[]>([]);
  readonly selecionado = signal<ModeloAtaGovernanca | null>(null);

  arquivoDocx?: File;
  idBaseAtivos?: string;
  modeloForm: any = this.novoModelo();
  treinamentoForm: any = this.novoTreinamento();
  exigenciaForm: any = this.novaExigencia();
  escolaForm: any = {};
  categoriaForm: any = this.novaCategoria();
  ativoForm: any = { tipo: 'OUTRO' };
  checklistForm: any = this.novoChecklist();

  readonly itensConfiguracao: ItemConfiguracao[] = [
    { id: 'modelos-ata', titulo: 'Modelos de ata', descricao: 'Defina o texto e as seções usadas nas reuniões.', acao: 'Gerenciar modelos', icon: 'pi pi-file-word' },
    { id: 'checklists', titulo: 'Checklists de inspeção', descricao: 'Vincule formulários às inspeções do grupo.', acao: 'Gerenciar checklists', icon: 'pi pi-list-check' },
    { id: 'treinamentos', titulo: 'Catálogo de treinamentos', descricao: 'Cadastre as capacitações disponíveis para o grupo.', acao: 'Gerenciar treinamentos', icon: 'pi pi-graduation-cap' },
    { id: 'regras-treinamento', titulo: 'Regras de treinamento', descricao: 'Informe quem precisa fazer cada capacitação.', acao: 'Gerenciar regras', icon: 'pi pi-sitemap' },
    { id: 'escolas', titulo: 'Instituições de treinamento', descricao: 'Cadastre escolas e acompanhe suas homologações.', acao: 'Gerenciar instituições', icon: 'pi pi-building-columns', compartilhado: true },
    { id: 'documentos', titulo: 'Tipos de documento', descricao: 'Defina validade, alertas e local de uso dos arquivos.', acao: 'Gerenciar tipos', icon: 'pi pi-folder' },
    { id: 'ativos', titulo: 'Itens para inspecionar', descricao: 'Cadastre extintores, hidrantes e pontos da base.', acao: 'Gerenciar itens', icon: 'pi pi-box' }
  ];

  readonly secaoAtual = computed(() => this.itensConfiguracao.find((item) => item.id === this.secao()));
  readonly modelosVisiveis = computed(() => this.modelos().filter((modelo) => modelo.tipoCorpo === this.tipoModelo()));
  readonly checklistsVisiveis = computed(() => this.modelosChecklist().filter((modelo) => modelo.tipoCorpo === this.tipoModelo()));
  readonly contextoDocumentoOptions = computed(() => this.tipoModelo() === 'CIPA'
    ? [{ label: 'Somente CIPA', value: 'CIPA' }, { label: 'CIPA e Brigada', value: 'AMBOS' }]
    : [{ label: 'Somente Brigada', value: 'BRIGADA' }, { label: 'CIPA e Brigada', value: 'AMBOS' }]);
  readonly naturezaDocumentoOptions = computed(() => this.tipoModelo() === 'CIPA'
    ? [
        { label: 'Registro da CIPA', value: 'REGISTRO_CIPA' },
        { label: 'Certificado de treinamento', value: 'CERTIFICADO_TREINAMENTO' }
      ]
    : [
        { label: 'Registro da Brigada', value: 'REGISTRO_BRIGADA' },
        { label: 'Licença ou ato do Corpo de Bombeiros', value: 'LICENCA_CORPO_BOMBEIROS' },
        { label: 'Atestado da Brigada', value: 'ATESTADO_BRIGADA' },
        { label: 'Alvará municipal', value: 'ALVARA_MUNICIPAL' },
        { label: 'Certificado de treinamento', value: 'CERTIFICADO_TREINAMENTO' }
      ]);
  readonly papelOptions = computed(() => this.tipoModelo() === 'CIPA'
    ? [
        { label: 'Presidente', value: 'PRESIDENTE' },
        { label: 'Vice-presidente', value: 'VICE_PRESIDENTE' },
        { label: 'Membro', value: 'MEMBRO' },
        { label: 'Representante nomeado', value: 'REPRESENTANTE_NOMEADO' }
      ]
    : [
        { label: 'Coordenador-geral', value: 'COORDENADOR_GERAL' },
        { label: 'Chefe da Brigada', value: 'CHEFE' },
        { label: 'Líder', value: 'LIDER' },
        { label: 'Brigadista', value: 'BRIGADISTA' }
      ]);
  readonly escopoDocumentoOptions = [
    { label: 'Base ou edificação', value: 'BASE' },
    { label: 'Comissão ou equipe', value: 'CORPO' },
    { label: 'Reunião', value: 'REUNIAO' },
    { label: 'Membro', value: 'MEMBRO' },
    { label: 'Colaborador', value: 'COLABORADOR' }
  ];
  readonly modoAplicabilidadeOptions = [
    { label: 'Cargo', value: 'FUNCAO' },
    { label: 'Papel na CIPA ou Brigada', value: 'PAPEL_CORPO' },
    { label: 'Cargo e papel', value: 'FUNCAO_E_PAPEL' }
  ];
  readonly ativoTipoOptions = [
    { label: 'Extintor', value: 'EXTINTOR' },
    { label: 'Luz de emergência', value: 'LUZ_EMERGENCIA' },
    { label: 'Hidrante', value: 'HIDRANTE' },
    { label: 'Saída de emergência', value: 'SAIDA_EMERGENCIA' },
    { label: 'Alarme', value: 'ALARME' },
    { label: 'Outro', value: 'OUTRO' }
  ];

  ngOnInit(): void {
    const tipo = normalizarTipoGovernanca(this.route.snapshot.queryParamMap.get('tipo')) ?? this.contexto.tipo();
    const secao = this.route.snapshot.queryParamMap.get('secao') as SecaoConfiguracao | null;
    this.contexto.selecionar(tipo);
    this.tipoModelo.set(tipo);
    if (secao && (secao === 'inicio' || this.itensConfiguracao.some((item) => item.id === secao))) this.secao.set(secao);
    this.reiniciarFormularios();
    this.carregar();
  }

  voltar(): void {
    if (this.cadastro()) {
      this.cadastro.set(null);
      return;
    }
    if (this.secao() !== 'inicio') {
      this.abrirSecao('inicio');
      return;
    }
    this.router.navigate(['/sst/governanca/painel'], { queryParams: { tipo: this.tipoModelo() } });
  }

  abrirSecao(secao: SecaoConfiguracao): void {
    this.cadastro.set(null);
    this.secao.set(secao);
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { secao: secao === 'inicio' ? null : secao, tipo: this.tipoModelo() },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
    if (secao === 'ativos' && this.idBaseAtivos) this.carregarAtivos();
  }

  abrirCadastro(secao: CadastroConfiguracao = this.secao() as CadastroConfiguracao): void {
    this.reiniciarFormulario(secao);
    this.cadastro.set(secao);
  }

  editarModelo(modelo: ModeloAtaGovernanca): void {
    this.selecionado.set(modelo);
    this.modeloForm = { ...modelo };
    this.arquivoDocx = undefined;
    this.cadastro.set('modelos-ata');
  }

  trocarTipoModelo(tipo: 'CIPA' | 'BRIGADA'): void {
    if (tipo === this.tipoModelo()) return;
    this.contexto.selecionar(tipo);
    this.tipoModelo.set(tipo);
    this.secao.set('inicio');
    this.cadastro.set(null);
    this.router.navigate([], { relativeTo: this.route, queryParams: { tipo, secao: null }, queryParamsHandling: 'merge', replaceUrl: true });
    this.reiniciarFormularios();
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    forkJoin({
      modelos: this.api.listarModelosAta({}),
      checklists: this.api.listarModelosChecklist({ page: 0, size: 200 }),
      formularios: this.sst.listarFormularios({ page: 0, size: 500, ativo: true, sort: 'titulo,asc' }),
      escolas: this.api.listarInstituicoes(),
      categorias: this.api.listarCategoriasDocumento({ page: 0, size: 200 }),
      treinamentos: this.api.listarTreinamentos({ page: 0, size: 500 }),
      exigencias: this.api.listarExigencias({ page: 0, size: 500 }),
      bases: this.rh.listarBasesOperacionaisAtivas(),
      funcoes: this.rh.listarFuncoesAtivas()
    }).pipe(finalize(() => this.carregando.set(false))).subscribe({
      next: (dados) => {
        const tipo = this.tipoModelo();
        this.modelos.set(dados.modelos);
        this.modelosChecklist.set(dados.checklists.content);
        this.formularios.set(dados.formularios.content);
        this.escolas.set(dados.escolas);
        this.categorias.set(dados.categorias.content.filter((categoria) => categoria.contexto === tipo || categoria.contexto === 'AMBOS'));
        this.treinamentos.set(dados.treinamentos.content.filter((treinamento) => tipo === 'CIPA' ? treinamento.aplicaCipa : treinamento.aplicaBrigada));
        this.exigencias.set(dados.exigencias.content.filter((exigencia: any) => exigencia.tipoCorpo === tipo));
        this.bases.set(dados.bases.content);
        this.funcoes.set(dados.funcoes.content);
      },
      error: (error) => this.erro(error)
    });
  }

  quantidade(secao: CadastroConfiguracao): number {
    const totais: Record<CadastroConfiguracao, number> = {
      'modelos-ata': this.modelosVisiveis().length,
      checklists: this.checklistsVisiveis().length,
      treinamentos: this.treinamentos().length,
      'regras-treinamento': this.exigencias().length,
      escolas: this.escolas().length,
      documentos: this.categorias().length,
      ativos: this.ativos().length
    };
    return totais[secao];
  }

  tituloCadastro(): string {
    const titulos: Record<CadastroConfiguracao, string> = {
      'modelos-ata': this.selecionado() ? 'Editar modelo de ata' : 'Cadastrar modelo de ata',
      checklists: 'Vincular checklist de inspeção',
      treinamentos: 'Cadastrar treinamento',
      'regras-treinamento': 'Cadastrar regra de treinamento',
      escolas: 'Cadastrar instituição de treinamento',
      documentos: 'Cadastrar tipo de documento',
      ativos: 'Cadastrar item para inspeção'
    };
    return titulos[this.cadastro()!];
  }

  podeSalvar(): boolean {
    switch (this.cadastro()) {
      case 'modelos-ata': return !!this.modeloForm.nome?.trim();
      case 'checklists': return !!this.checklistForm.idFormulario && !!this.checklistForm.nome?.trim();
      case 'treinamentos': return !!this.treinamentoForm.codigo?.trim() && !!this.treinamentoForm.nome?.trim();
      case 'regras-treinamento': {
        const form = this.exigenciaForm;
        return !!form.idTreinamento && !!form.identificadorFonte?.trim()
          && (form.modoAplicabilidade === 'PAPEL_CORPO' || !!form.idFuncao)
          && (form.modoAplicabilidade === 'FUNCAO' || !!form.papelCorpo);
      }
      case 'escolas': return !!this.escolaForm.nome?.trim();
      case 'documentos': return !!this.categoriaForm.codigo?.trim() && !!this.categoriaForm.nome?.trim();
      case 'ativos': return !!this.idBaseAtivos && !!this.ativoForm.identificadorLocal?.trim() && !!this.ativoForm.localizacao?.trim();
      default: return false;
    }
  }

  salvarCadastro(): void {
    if (!this.podeSalvar()) return;
    switch (this.cadastro()) {
      case 'modelos-ata': this.salvarModelo(); break;
      case 'checklists': this.salvarChecklist(); break;
      case 'treinamentos': this.salvarTreinamento(); break;
      case 'regras-treinamento': this.salvarExigencia(); break;
      case 'escolas': this.salvarEscola(); break;
      case 'documentos': this.salvarCategoria(); break;
      case 'ativos': this.salvarAtivo(); break;
    }
  }



  selecionarDocx(event: any): void { this.arquivoDocx = event.files?.[0]; }
  enviarDocx(): void {
    const modelo = this.selecionado();
    if (!modelo || !this.arquivoDocx) return;

    this.processando.set('docx');
    this.api.enviarBaseModeloAta(modelo.id, this.arquivoDocx).pipe(
      switchMap(() => this.api.buscarModeloAta(modelo.id)),
      finalize(() => this.processando.set(null))
    ).subscribe({
      next: atualizado => {
        this.editarModelo(atualizado);
        this.modelos.update(lista =>
          lista.map(item => item.id === atualizado.id ? atualizado : item)
        );
        this.sucesso('Arquivo DOCX enviado');
      },
      error: error => this.erro(error)
    });
  }
  comandoModelo(comando: 'duplicacao' | 'nova-revisao' | 'publicacao' | 'retirada'): void {
    if (!this.selecionado()) return;
    this.executar(comando, this.api.comandoModeloAta(this.selecionado()!.id, comando), (modelo: any) => {
      this.editarModelo(modelo);
      this.carregar();
    });
  }

  importar(tipo: 'matriz-treinamentos' | 'escolas' | 'ativos-inspecionaveis'): void {
    this.executar(`import-${tipo}`, this.api.importarReferencia(tipo, tipo === 'ativos-inspecionaveis' ? this.idBaseAtivos : undefined), (retorno: any) => {
      this.sucesso(`${retorno.quantidade} registros processados`);
      this.carregar();
      if (tipo === 'ativos-inspecionaveis') this.carregarAtivos();
    });
  }

  editarEstruturaChecklist(modelo: ModeloChecklistGovernanca): void { this.router.navigate(['/sst/formularios', modelo.idFormulario]); }
  carregarAtivos(): void {
    if (!this.idBaseAtivos) { this.ativos.set([]); return; }
    this.api.listarAtivos(this.idBaseAtivos).subscribe({ next: (ativos) => this.ativos.set(ativos), error: (error) => this.erro(error) });
  }

  rotulo(valor?: string): string {
    if (!valor) return 'Não informado';
    const opcoes = [
      ...this.contextoDocumentoOptions(), ...this.naturezaDocumentoOptions(), ...this.escopoDocumentoOptions,
      ...this.ativoTipoOptions, ...this.papelOptions(), ...this.modoAplicabilidadeOptions,
      { label: 'Confirmado', value: 'CONFIRMADO' }, { label: 'Pendente de conferência', value: 'PENDENTE_CONFERENCIA' }
    ];
    return opcoes.find((opcao) => opcao.value === valor)?.label
      ?? valor.replaceAll('_', ' ').toLocaleLowerCase('pt-BR').replace(/^./, (letra) => letra.toLocaleUpperCase('pt-BR'));
  }

  podePublicarModelo(): boolean {
    const modelo = this.selecionado();
    return !!modelo
      && modelo.status === 'RASCUNHO'
      && !!modelo.idArquivoDocx
      && !!modelo.hashArquivoDocx;
  }

  motivoBloqueioPublicacao(): string {
    const modelo = this.selecionado();
    if (!modelo) return '';
    if (!modelo.idArquivoDocx || !modelo.hashArquivoDocx) return 'Envie um arquivo DOCX antes de publicar.';
    return '';
  }

  isLeitura(): boolean {
    const modelo = this.selecionado();
    return !!modelo && modelo.status !== 'RASCUNHO';
  }

  private salvarModelo(): void {
    const form = this.modeloForm;
    const request = {
      nome: form.nome,
      tipoCorpo: this.tipoModelo(),
      idBaseAplicavel: form.idBaseAplicavel || null,
      orientacoesGerais: form.orientacoesGerais
    };
    this.executar('salvar', this.api.salvarModeloAta(request, this.selecionado()?.id), (modelo) => {
      this.editarModelo(modelo);
      this.carregar();
    });
  }

  private salvarChecklist(): void {
    const form = this.checklistForm;
    this.executar('salvar', this.api.salvarModeloChecklist({
      idFormulario: form.idFormulario, tipoCorpo: this.tipoModelo(), nome: form.nome,
      orientacao: form.orientacao || null, referenciaNormativa: form.referenciaNormativa || null
    }), () => this.finalizarCadastro());
  }

  private salvarTreinamento(): void {
    this.executar('salvar', this.api.salvarTreinamento(this.treinamentoForm), () => this.finalizarCadastro());
  }

  private salvarExigencia(): void {
    const form = this.exigenciaForm;
    this.executar('salvar', this.api.salvarExigencia({
      idTreinamento: form.idTreinamento,
      tipoCorpo: this.tipoModelo(),
      modoAplicabilidade: form.modoAplicabilidade,
      idFuncao: form.modoAplicabilidade !== 'PAPEL_CORPO' ? form.idFuncao : null,
      papelCorpo: form.modoAplicabilidade !== 'FUNCAO' ? form.papelCorpo : null,
      idBase: form.idBase || null,
      cargaHorariaMinutos: Number(form.cargaHorariaHoras) * 60,
      politicaInstituicao: 'QUALQUER',
      instituicoesPermitidas: [],
      modalidade: null,
      recorrenciaMeses: form.recorrenciaMeses || null,
      antecedenciaAlertaDias: 30,
      vigenciaInicio: this.data(form.vigenciaInicio),
      tipoFonte: 'INTERNA',
      identificadorFonte: form.identificadorFonte,
      fonteConfirmada: false
    }), () => this.finalizarCadastro());
  }

  private salvarEscola(): void {
    this.executar('salvar', this.api.salvarInstituicao({ ...this.escolaForm, possuiDadoSuspeito: false }), () => this.finalizarCadastro());
  }

  private salvarCategoria(): void {
    const form = this.categoriaForm;
    this.executar('salvar', this.api.salvarCategoriaDocumento({
      ...form,
      faixasAlertaDias: String(form.faixasAlertaDias).split(',').map(Number).filter(Boolean),
      confirmada: false
    }), () => this.finalizarCadastro());
  }

  private salvarAtivo(): void {
    this.executar('salvar', this.api.salvarAtivo({
      ...this.ativoForm,
      idBaseOperacional: this.idBaseAtivos,
      atributos: null,
      possuiDadoSuspeito: false
    }), () => {
      this.cadastro.set(null);
      this.carregarAtivos();
    });
  }

  private finalizarCadastro(): void {
    this.cadastro.set(null);
    this.reiniciarFormulario(this.secao() as CadastroConfiguracao);
    this.carregar();
  }

  private reiniciarFormularios(): void {
    this.modeloForm = this.novoModelo();
    this.treinamentoForm = this.novoTreinamento();
    this.exigenciaForm = this.novaExigencia();
    this.escolaForm = {};
    this.categoriaForm = this.novaCategoria();
    this.ativoForm = { tipo: 'OUTRO' };
    this.checklistForm = this.novoChecklist();
    this.selecionado.set(null);
  }

  private reiniciarFormulario(secao: CadastroConfiguracao): void {
    if (secao === 'modelos-ata') { this.modeloForm = this.novoModelo(); this.selecionado.set(null); }
    if (secao === 'checklists') this.checklistForm = this.novoChecklist();
    if (secao === 'treinamentos') this.treinamentoForm = this.novoTreinamento();
    if (secao === 'regras-treinamento') this.exigenciaForm = this.novaExigencia();
    if (secao === 'escolas') this.escolaForm = {};
    if (secao === 'documentos') this.categoriaForm = this.novaCategoria();
    if (secao === 'ativos') this.ativoForm = { tipo: 'OUTRO' };
  }

  private novoModelo(): any {
    return {
      nome: '', tipoCorpo: this.tipoModelo(), orientacoesGerais: '', idBaseAplicavel: null
    };
  }
  private novoTreinamento(): any { return { codigo: '', nome: '', descricao: '', contextos: [this.tipoModelo()] }; }
  private novaExigencia(): any { return { tipoCorpo: this.tipoModelo(), modoAplicabilidade: 'FUNCAO', cargaHorariaHoras: 8, recorrenciaMeses: 12, identificadorFonte: 'Matriz interna', vigenciaInicio: new Date() }; }
  private novoChecklist(): any { return { tipoCorpo: this.tipoModelo(), nome: '', orientacao: '', referenciaNormativa: '' }; }
  private novaCategoria(): any {
    const cipa = this.tipoModelo() === 'CIPA';
    return {
      contexto: this.tipoModelo(), natureza: cipa ? 'REGISTRO_CIPA' : 'LICENCA_CORPO_BOMBEIROS', escopoTitular: 'BASE',
      exigeNumero: true, exigeEmissor: true, exigeDataEmissao: true, exigeDataValidade: true, semValidade: false,
      obrigatoria: false, retencaoMinimaAnos: 5, faixasAlertaDias: '90,60,30'
    };
  }

  private data(valor?: Date | string): string | undefined {
    if (!valor) return undefined;
    if (typeof valor === 'string') return valor;
    return `${valor.getFullYear()}-${String(valor.getMonth() + 1).padStart(2, '0')}-${String(valor.getDate()).padStart(2, '0')}`;
  }
  private executar(nome: string, operacao: any, proximo: (retorno: any) => void): void {
    this.processando.set(nome);
    operacao.pipe(finalize(() => this.processando.set(null))).subscribe({
      next: (retorno: any) => { proximo(retorno); this.sucesso('Alteração salva'); },
      error: (error: unknown) => this.erro(error)
    });
  }
  private sucesso(detalhe: string): void { this.messages.add({ severity: 'success', summary: 'Tudo certo', detail: detalhe }); }
  private erro(error: unknown): void { this.messages.add({ severity: 'error', summary: 'Não foi possível concluir', detail: getErrorMessage(error), life: 7000 }); }
}
