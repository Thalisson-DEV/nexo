import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmationService, MenuItem, MessageService } from 'primeng/api';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { ButtonModule } from 'primeng/button';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { SelectModule } from 'primeng/select';
import { StepperModule } from 'primeng/stepper';
import { TextareaModule } from 'primeng/textarea';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TooltipModule } from 'primeng/tooltip';
import { firstValueFrom } from 'rxjs';
import {
  PerfilAcessoResponse,
  PermissaoResponse,
  UsuarioResponse
} from '../../../../core/models/iam.models';
import {
  CategoriaNotificacao,
  GatilhoResponse,
  OperadorCondicao,
  PoliticaRoteamentoResponse,
  PrioridadeNotificacao,
  RegraNotificacaoRequest,
  RegraNotificacaoResponse,
  TipoDestinatarioRegra
} from '../../../../core/models/notificacao.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { NotificacoesService } from '../../../../core/services/notificacoes.service';
import { PermissoesService } from '../../../../core/services/permissoes.service';
import { UsuariosService } from '../../../../core/services/usuarios.service';
import {
  CATEGORIA_OPTIONS,
  OPERADOR_OPTIONS,
  PRIORIDADE_OPTIONS,
  TIPO_DESTINATARIO_OPTIONS,
  dicaVariavel,
  extrairPlaceholders,
  politicasCompativeisComRegra,
  rotuloPapel
} from '../../notificacoes-ui';
import { DESTINOS_DEEPLINK, GrupoDestinoDeeplink, rotaConhecida } from './destinos-deeplink';
import { RemoteLookup } from '../../../../core/utils/remote-lookup.util';

/** Sugestão de silêncio para gatilhos por limiar: a varredura repetiria o aviso a cada ciclo. */
const COOLDOWN_PADRAO_LIMIAR = 720;

const ATALHOS_COOLDOWN = [
  { label: 'Sem silêncio', valor: 0 },
  { label: '1 h', valor: 60 },
  { label: '6 h', valor: 360 },
  { label: '12 h', valor: 720 },
  { label: '24 h', valor: 1440 }
];

/**
 * Condições e destinatários são listas repetíveis cujo conteúdo decide *quais campos aparecem*
 * (o alvo de um destinatário muda conforme o tipo). Guardá-las em `FormArray` obrigava o template
 * a ler `control.value` — leitura não reativa — para escolher o campo, e num componente OnPush em
 * app zoneless essa leitura não reagia à troca. Por isso as duas listas vivem em signals.
 */
interface CondicaoLinha {
  campo: string;
  operador: OperadorCondicao;
  valor: string;
}

interface DestinatarioLinha {
  tipo: TipoDestinatarioRegra;
  idAlvo: string | null;
  valorAlvo: string | null;
}

interface OpcaoAlvo {
  rotulo: string;
  valor: string;
}

/** Rótulo do campo de alvo — o segundo select do destinatário muda de significado com o tipo. */
const ROTULO_ALVO: Record<TipoDestinatarioRegra, string> = {
  PERMISSAO: 'Permissão',
  PERFIL: 'Perfil de acesso',
  USUARIO: 'Usuário',
  PAPEL_DINAMICO: 'Papel no registro',
  POLITICA: 'Política de roteamento'
};

/** Tipos cujo alvo é o id de um registro; os demais gravam o próprio texto em `valorAlvo`. */
const TIPOS_POR_ID: TipoDestinatarioRegra[] = ['PERFIL', 'USUARIO', 'POLITICA'];

/** Única categoria que o backend recusa no canal de e-mail. */
const CATEGORIA_SEM_EMAIL: CategoriaNotificacao = 'MENCAO';

/**
 * Gatilho que dispara a cada mensagem trocada. Ligar o e-mail nele multiplica a conversa por dois
 * na caixa de entrada de cada lado — merece confirmação explícita.
 */
const GATILHO_RUIDOSO = 'chamado.mensagem_recebida';

@Component({
  selector: 'app-regra-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [ConfirmationService],
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    BreadcrumbModule,
    ButtonModule,
    ConfirmDialogModule,
    InputNumberModule,
    InputTextModule,
    ProgressSpinnerModule,
    SelectModule,
    StepperModule,
    TextareaModule,
    ToggleSwitchModule,
    TooltipModule
  ],
  templateUrl: './regra-form.component.html',
  styleUrl: './regra-form.component.css'
})
export class RegraFormComponent {
  private readonly service = inject(NotificacoesService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly permissoesService = inject(PermissoesService);
  private readonly messages = inject(MessageService);
  private readonly confirmation = inject(ConfirmationService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly usuarioLookup = new RemoteLookup<UsuarioResponse>(
    {
      identify: (usuario) => usuario.id,
      selectedKeys: () =>
        this.destinatarios?.()
          .filter((destinatario) => destinatario.tipo === 'USUARIO')
          .map((destinatario) => destinatario.idAlvo)
          .filter((id): id is string => typeof id === 'string' && !!id) ?? [],
      load: ({ query, page, size }) =>
        this.usuariosService.listarUsuarios({
          ativo: true,
          buscaGlobal: query,
          page,
          size,
          sort: 'login,asc'
        })
    },
    this.destroyRef
  );
  private readonly permissaoLookup = new RemoteLookup<PermissaoResponse, string>(
    {
      identify: (permissao) => permissao.nome,
      selectedKeys: () =>
        this.destinatarios?.()
          .filter((destinatario) => destinatario.tipo === 'PERMISSAO')
          .map((destinatario) => destinatario.valorAlvo)
          .filter((nome): nome is string => typeof nome === 'string' && !!nome) ?? [],
      load: ({ query, page, size }) =>
        this.permissoesService.listar({ buscaGlobal: query, page, size, sort: 'nome,asc' })
    },
    this.destroyRef
  );

  protected readonly breadcrumb: MenuItem[] = [
    { icon: 'pi pi-home', routerLink: '/home' },
    { label: 'Regras de notificação', routerLink: '/notificacoes/admin/regras' },
    { label: 'Formulário' }
  ];

  protected readonly categoriaOptions = CATEGORIA_OPTIONS;
  protected readonly prioridadeOptions = PRIORIDADE_OPTIONS;
  protected readonly operadorOptions = OPERADOR_OPTIONS;
  protected readonly atalhosCooldown = ATALHOS_COOLDOWN;
  protected readonly atalhosLimiar = [1, 3, 5, 10, 20];

  protected readonly activeStep = signal(1);
  protected readonly carregando = signal(false);
  protected readonly salvando = signal(false);

  protected readonly gatilhos = signal<GatilhoResponse[]>([]);
  protected readonly politicas = signal<PoliticaRoteamentoResponse[]>([]);
  protected readonly perfis = signal<PerfilAcessoResponse[]>([]);
  protected readonly listaUsuarios = this.usuarioLookup.items;
  protected readonly usuariosLoading = this.usuarioLookup.loading;
  protected readonly listaPermissoes = this.permissaoLookup.items;
  protected readonly permissoesLoading = this.permissaoLookup.loading;

  protected readonly original = signal<RegraNotificacaoResponse | null>(null);
  protected readonly gravada = signal<RegraNotificacaoResponse | null>(null);
  protected readonly edicao = computed(() => !!this.original());

  private readonly chaveSelecionada = signal('');
  private readonly versaoFormulario = signal(0);

  protected readonly form = new FormGroup({
    chaveGatilho: new FormControl('', { nonNullable: true, validators: Validators.required }),
    nome: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(120)]
    }),
    descricao: new FormControl('', { nonNullable: true, validators: Validators.maxLength(500) }),
    categoria: new FormControl<CategoriaNotificacao | null>(null),
    prioridade: new FormControl<PrioridadeNotificacao | null>('NORMAL'),
    tituloTemplate: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(150)]
    }),
    corpoTemplate: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(1000)]
    }),
    deeplinkTemplate: new FormControl('', {
      nonNullable: true,
      validators: Validators.maxLength(500)
    }),
    limiar: new FormControl<number | null>(null),
    cooldownMinutos: new FormControl<number>(0, { nonNullable: true }),
    // O canal nasce ligado: desligar é ação deliberada de quem publica.
    enviarEmail: new FormControl<boolean>(true, { nonNullable: true })
  });

  protected readonly condicoes = signal<CondicaoLinha[]>([]);
  protected readonly destinatarios = signal<DestinatarioLinha[]>([this.novoDestinatario()]);

  protected readonly gatilhoAtual = computed(
    () => this.gatilhos().find((item) => item.chave === this.chaveSelecionada()) ?? null
  );

  protected readonly porLimiar = computed(() => this.gatilhoAtual()?.modo === 'POR_LIMIAR');
  protected readonly variaveis = computed(() => this.gatilhoAtual()?.variaveis ?? []);

  /** Valores lidos por signal para o exemplo da etapa "Disparo" acompanhar a digitação. */
  protected readonly limiarAtual = computed(() => {
    this.versaoFormulario();
    return this.form.controls.limiar.value ?? 1;
  });

  protected readonly cooldownAtual = computed(() => {
    this.versaoFormulario();
    return this.form.controls.cooldownMinutos.value ?? 0;
  });

  protected readonly variaveisOptions = computed(() =>
    this.variaveis().map((variavel) => ({ label: variavel, value: variavel }))
  );

  protected readonly papeisOptions = computed(() =>
    (this.gatilhoAtual()?.papeis ?? []).map((papel) => ({
      label: rotuloPapel(papel, this.chaveSelecionada()),
      value: papel
    }))
  );

  // -------------------------------------------------------------------------
  // Canal de e-mail
  // -------------------------------------------------------------------------

  /** Categoria que vale de fato: sem escolha explícita, a regra herda a padrão do gatilho. */
  protected readonly categoriaEfetiva = computed<CategoriaNotificacao | null>(() => {
    this.versaoFormulario();
    return this.categoriaEfetivaAgora();
  });

  /** `MENCAO` é veto de domínio: o backend recusa o canal mesmo se o payload pedir. */
  protected readonly permiteEmail = computed(
    () => this.categoriaEfetiva() !== CATEGORIA_SEM_EMAIL
  );

  protected readonly enviaEmail = computed(() => {
    this.versaoFormulario();
    return this.form.controls.enviarEmail.value;
  });

  // -------------------------------------------------------------------------
  // Destino ao clicar
  // -------------------------------------------------------------------------

  /** Só os destinos cujas variáveis este gatilho fornece — o resto seria recusado ao salvar. */
  protected readonly destinosDisponiveis = computed<GrupoDestinoDeeplink[]>(() => {
    const disponiveis = new Set(this.variaveis());
    return DESTINOS_DEEPLINK.map((grupo) => ({
      label: grupo.label,
      items: grupo.items.filter((destino) => !destino.requer || disponiveis.has(destino.requer))
    })).filter((grupo) => grupo.items.length > 0);
  });

  protected readonly deeplinkAtual = computed(() => {
    this.versaoFormulario();
    return this.form.controls.deeplinkTemplate.value;
  });

  /** Caminho fora do mapa de rotas: o Angular cairia no `**` e levaria o usuário para `home`. */
  protected readonly deeplinkDesconhecido = computed(() => !rotaConhecida(this.deeplinkAtual()));

  /**
   * Opções do campo de alvo por tipo. O destinatário usa um único `p-select` que só troca de
   * lista: criar e destruir selects a cada troca de tipo derrubava o overlay do PrimeNG no meio
   * da animação de fechamento e deixava o componente travado.
   */
  protected readonly opcoesAlvoPorTipo = computed<Record<TipoDestinatarioRegra, OpcaoAlvo[]>>(
    () => ({
      PERMISSAO: this.listaPermissoes().map((item) => ({ rotulo: item.nome, valor: item.nome })),
      PERFIL: this.perfis().map((item) => ({ rotulo: item.nome, valor: item.id })),
      USUARIO: this.listaUsuarios().map((item) => ({ rotulo: item.login, valor: item.id })),
      PAPEL_DINAMICO: (this.gatilhoAtual()?.papeis ?? []).map((papel) => ({
        rotulo: rotuloPapel(papel, this.chaveSelecionada()),
        valor: papel
      })),
      POLITICA: this.politicasCompativeis().map((politica) => ({
        rotulo: `${politica.nome}${politica.ativo ? '' : ' (Inativa)'}`,
        valor: politica.id
      }))
    })
  );

  protected readonly politicasAtivasCompativeis = computed(() =>
    this.politicas().filter(
      (politica) => politica.ativo && politica.chaveGatilho === this.chaveSelecionada()
    )
  );

  protected readonly politicasCompativeis = computed(() => {
    const selecionadas = new Set(
      this.destinatarios()
        .filter((destinatario) => destinatario.tipo === 'POLITICA' && !!destinatario.idAlvo)
        .map((destinatario) => destinatario.idAlvo)
    );
    return politicasCompativeisComRegra(
      this.politicas(),
      this.chaveSelecionada(),
      selecionadas
    );
  });

  /** PAPEL_DINAMICO só existe em gatilhos POR_OCORRENCIA — sem papéis, some da lista. */
  protected readonly tipoDestinatarioOptions = computed(() => {
    const temPapeis = (this.gatilhoAtual()?.papeis ?? []).length > 0;
    return TIPO_DESTINATARIO_OPTIONS.filter(
      (opcao) => opcao.value !== 'PAPEL_DINAMICO' || temPapeis
    );
  });

  /**
   * Prévia do texto com os placeholders destacados. Não inventamos valores de exemplo: o que a
   * tela mostra é exatamente onde cada variável entra.
   */
  protected readonly previaTitulo = computed(() => {
    this.versaoFormulario();
    return this.destacar(this.form.controls.tituloTemplate.value);
  });

  protected readonly previaCorpo = computed(() => {
    this.versaoFormulario();
    return this.destacar(this.form.controls.corpoTemplate.value);
  });

  /** Resumo da regra em uma frase, para a etapa de revisão. */
  protected readonly resumoNatural = computed(() => {
    this.versaoFormulario();
    const gatilho = this.gatilhoAtual();
    if (!gatilho) return '';

    const quando =
      gatilho.modo === 'POR_LIMIAR'
        ? `Quando houver ${this.form.controls.limiar.value ?? 1} ou mais pendências em "${gatilho.nome}"`
        : `Sempre que acontecer "${gatilho.nome}"`;

    const condicoes = this.condicoes()
      .map((linha) => {
        const operador = OPERADOR_OPTIONS.find((item) => item.value === linha.operador)?.label ?? '';
        return `${linha.campo} ${operador} ${linha.valor}`;
      })
      .filter((texto) => texto.trim().length > 2);

    const filtro = condicoes.length > 0 ? `, e somente se ${condicoes.join(' e ')}` : '';
    const quem = this.resumoDestinatarios().join(', ') || 'ninguém ainda';
    const silencio =
      this.form.controls.cooldownMinutos.value > 0
        ? ` Não repete o aviso por ${this.rotuloCooldown(this.form.controls.cooldownMinutos.value)}.`
        : '';

    return `${quando}${filtro}, avisar ${quem}.${silencio}`;
  });

  constructor() {
    this.carregarCatalogo();
    this.carregarPoliticas();
    this.carregarApoio();

    this.form.valueChanges.subscribe(() => this.versaoFormulario.update((valor) => valor + 1));
    this.form.controls.chaveGatilho.valueChanges.subscribe((chave) =>
      this.chaveSelecionada.set(chave ?? '')
    );
    // A categoria é escolhida no mesmo formulário: o canal precisa reagir à troca.
    this.form.controls.categoria.valueChanges.subscribe(() => this.sincronizarCanalEmail());

    const id = this.route.snapshot.paramMap.get('id');
    if (id) this.carregar(id);
  }

  // -------------------------------------------------------------------------
  // Carregamento
  // -------------------------------------------------------------------------

  private carregarCatalogo(): void {
    this.service.listarGatilhos().subscribe({
      next: (gatilhos) => this.gatilhos.set(gatilhos),
      error: (erro) => this.erro(erro)
    });
  }

  private carregarPoliticas(): void {
    this.service.listarPoliticas().subscribe({
      next: (politicas) => this.politicas.set(politicas),
      error: (erro) => this.erro(erro)
    });
  }

  private carregarApoio(): void {
    this.usuariosService.listarPerfisAtivos().subscribe({
      next: (perfis) => this.perfis.set(perfis),
      error: () => this.perfis.set([])
    });
  }

  private carregar(id: string): void {
    this.carregando.set(true);
    this.service.buscarRegra(id).subscribe({
      next: (regra) => {
        this.original.set(regra);
        this.aplicar(regra);
        this.carregando.set(false);
      },
      error: (erro) => {
        this.carregando.set(false);
        this.erro(erro);
        this.router.navigate(['/notificacoes/admin/regras']);
      }
    });
  }

  private aplicar(regra: RegraNotificacaoResponse): void {
    this.form.patchValue({
      chaveGatilho: regra.chaveGatilho,
      nome: regra.nome,
      descricao: regra.descricao ?? '',
      categoria: regra.categoria,
      prioridade: regra.prioridade,
      tituloTemplate: regra.tituloTemplate,
      corpoTemplate: regra.corpoTemplate,
      deeplinkTemplate: regra.deeplinkTemplate ?? '',
      limiar: regra.limiar,
      cooldownMinutos: regra.cooldownMinutos,
      enviarEmail: regra.enviarEmail
    });
    this.chaveSelecionada.set(regra.chaveGatilho);
    this.sincronizarCanalEmail();
    this.condicoes.set(
      regra.condicoes.map((condicao) => ({
        campo: condicao.campo,
        operador: condicao.operador,
        valor: condicao.valor
      }))
    );
    this.destinatarios.set(
      regra.destinatarios.length > 0
        ? regra.destinatarios.map((destinatario) => ({
            tipo: destinatario.tipo,
            idAlvo: destinatario.idAlvo ?? null,
            valorAlvo: destinatario.valorAlvo ?? null
          }))
        : [this.novoDestinatario()]
    );
    for (const id of regra.destinatarios
      .filter((destinatario) => destinatario.tipo === 'USUARIO' && !!destinatario.idAlvo)
      .map((destinatario) => destinatario.idAlvo as string)) {
      this.usuariosService.buscarUsuarioPorId(id).subscribe({
        next: (usuario) => this.usuarioLookup.setSeed([usuario]),
        error: () => {}
      });
    }
    this.permissaoLookup.setSeed(
      regra.destinatarios
        .filter((destinatario) => destinatario.tipo === 'PERMISSAO' && !!destinatario.valorAlvo)
        .map((destinatario) => ({ id: destinatario.valorAlvo!, nome: destinatario.valorAlvo!, ativo: true }))
    );
    // O gatilho é imutável: trocá-lo invalidaria condições, papéis e textos de uma vez.
    this.form.controls.chaveGatilho.disable();
  }

  // -------------------------------------------------------------------------
  // Etapa 1 — gatilho
  // -------------------------------------------------------------------------

  protected escolherGatilho(gatilho: GatilhoResponse): void {
    if (this.edicao()) return;
    if (this.form.controls.chaveGatilho.value === gatilho.chave) return;

    this.form.controls.chaveGatilho.setValue(gatilho.chave);
    this.condicoes.set([]);
    this.destinatarios.set([this.novoDestinatario()]);
    this.form.controls.categoria.setValue(gatilho.categoriaPadrao);
    this.form.controls.limiar.setValue(gatilho.modo === 'POR_LIMIAR' ? 1 : null);
    this.form.controls.cooldownMinutos.setValue(
      gatilho.modo === 'POR_LIMIAR' ? COOLDOWN_PADRAO_LIMIAR : 0
    );
  }

  // -------------------------------------------------------------------------
  // Canal de e-mail
  // -------------------------------------------------------------------------

  /**
   * Lê a categoria direto do controle, sem passar pelos signals: a troca de categoria emite o
   * `valueChanges` do próprio controle antes do formulário inteiro, e nesse instante
   * `versaoFormulario` ainda aponta para o estado anterior.
   */
  private categoriaEfetivaAgora(): CategoriaNotificacao | null {
    return this.form.controls.categoria.value ?? this.gatilhoAtual()?.categoriaPadrao ?? null;
  }

  /**
   * Mantém o interruptor coerente com a categoria: `MENCAO` desmarca e desabilita; sair dela
   * devolve o padrão do canal, que é ligado.
   */
  private sincronizarCanalEmail(): void {
    const controle = this.form.controls.enviarEmail;
    const permite = this.categoriaEfetivaAgora() !== CATEGORIA_SEM_EMAIL;

    if (!permite) {
      controle.setValue(false, { emitEvent: false });
      controle.disable({ emitEvent: false });
      return;
    }
    if (controle.disabled) {
      controle.enable({ emitEvent: false });
      controle.setValue(true, { emitEvent: false });
    }
  }

  /** Ligar o canal numa regra que já existia e estava desligada — ou numa regra nova. */
  private ligandoEmailAgora(): boolean {
    if (!this.form.getRawValue().enviarEmail) return false;
    const atual = this.original();
    return !atual || !atual.enviarEmail;
  }

  // -------------------------------------------------------------------------
  // Navegação
  // -------------------------------------------------------------------------

  protected irPara(etapa: number): void {
    if (etapa <= this.activeStep()) {
      this.activeStep.set(etapa);
      return;
    }
    if (etapa > 1 && !this.validarGatilho()) return;
    if (etapa > 2 && !this.validarMensagem()) return;
    if (etapa > 3 && !this.validarDestinatarios()) return;
    if (etapa > 4 && !this.validarDisparo()) return;
    this.activeStep.set(etapa);
  }

  protected proximaEtapa(): void {
    this.irPara(this.activeStep() + 1);
  }

  protected etapaAnterior(): void {
    this.activeStep.set(Math.max(1, this.activeStep() - 1));
  }

  protected sair(): void {
    this.router.navigate(['/notificacoes/admin/regras']);
  }

  private validarGatilho(): boolean {
    if (this.gatilhoAtual()) return true;
    this.aviso('Escolha o gatilho', 'Selecione o evento que dispara esta regra.');
    return false;
  }

  private validarMensagem(): boolean {
    this.form.controls.nome.markAsTouched();
    this.form.controls.tituloTemplate.markAsTouched();
    this.form.controls.corpoTemplate.markAsTouched();
    if (
      this.form.controls.nome.invalid ||
      this.form.controls.tituloTemplate.invalid ||
      this.form.controls.corpoTemplate.invalid
    ) {
      this.aviso('Mensagem incompleta', 'Informe o nome da regra, o título e o corpo do aviso.');
      return false;
    }
    return this.validarPlaceholders();
  }

  private validarDestinatarios(): boolean {
    const destinatarios = this.destinatarios();
    if (destinatarios.length === 0) {
      this.aviso('Destinatário obrigatório', 'Informe ao menos quem deve ser avisado.');
      return false;
    }
    const incompleto = destinatarios.some(
      (destinatario) =>
        ((destinatario.tipo === 'PERFIL' ||
          destinatario.tipo === 'USUARIO' ||
          destinatario.tipo === 'POLITICA') &&
          !destinatario.idAlvo) ||
        ((destinatario.tipo === 'PERMISSAO' || destinatario.tipo === 'PAPEL_DINAMICO') &&
          !destinatario.valorAlvo)
    );
    if (incompleto) {
      this.aviso(
        'Destinatário incompleto',
        'Cada destinatário precisa do alvo correspondente ao tipo escolhido.'
      );
      return false;
    }
    const politicaInvalida = destinatarios.find((destinatario) => {
      if (destinatario.tipo !== 'POLITICA' || !destinatario.idAlvo) return false;
      const politica = this.politicas().find((item) => item.id === destinatario.idAlvo);
      return !politica?.ativo || politica.chaveGatilho !== this.chaveSelecionada();
    });
    if (politicaInvalida) {
      this.aviso(
        'Política indisponível',
        'Reative a política atual ou escolha outra política ativa do mesmo gatilho antes de salvar.'
      );
      return false;
    }
    return true;
  }

  private validarDisparo(): boolean {
    if (this.porLimiar()) {
      const limiar = this.form.controls.limiar.value;
      if (!limiar || limiar < 1) {
        this.aviso('Limiar obrigatório', 'Gatilhos por limiar precisam de um valor a partir de 1.');
        return false;
      }
    }
    if (this.condicoes().some((condicao) => !condicao.campo || !condicao.valor.trim())) {
      this.aviso('Condição incompleta', 'Cada condição precisa de campo, operador e valor.');
      return false;
    }
    return true;
  }

  /** O backend recusa placeholder fora do catálogo do gatilho — antecipa para evitar 422. */
  private validarPlaceholders(): boolean {
    const permitidas = new Set(this.variaveis());
    const valor = this.form.getRawValue();
    const usadas = [
      ...extrairPlaceholders(valor.tituloTemplate),
      ...extrairPlaceholders(valor.corpoTemplate),
      ...extrairPlaceholders(valor.deeplinkTemplate)
    ];
    const invalidas = [...new Set(usadas.filter((item) => !permitidas.has(item)))];
    if (invalidas.length === 0) return true;

    this.aviso(
      'Variável inexistente',
      `Este gatilho não fornece: ${invalidas.map((item) => `{{${item}}}`).join(', ')}.`
    );
    return false;
  }

  // -------------------------------------------------------------------------
  // Listas repetíveis
  // -------------------------------------------------------------------------

  protected adicionarCondicao(): void {
    this.condicoes.update((linhas) => [
      ...linhas,
      { campo: this.variaveis()[0] ?? '', operador: 'IGUAL' as OperadorCondicao, valor: '' }
    ]);
  }

  protected removerCondicao(indice: number): void {
    this.condicoes.update((linhas) => linhas.filter((_, posicao) => posicao !== indice));
  }

  protected alterarCondicao(indice: number, mudanca: Partial<CondicaoLinha>): void {
    this.condicoes.update((linhas) =>
      linhas.map((linha, posicao) => (posicao === indice ? { ...linha, ...mudanca } : linha))
    );
  }

  protected adicionarDestinatario(): void {
    this.destinatarios.update((linhas) => [...linhas, this.novoDestinatario()]);
  }

  protected removerDestinatario(indice: number): void {
    this.destinatarios.update((linhas) => linhas.filter((_, posicao) => posicao !== indice));
  }

  /** Trocar o tipo zera o alvo: cada tipo aponta para uma lista diferente. */
  protected definirTipoDestinatario(indice: number, tipo: TipoDestinatarioRegra): void {
    if (tipo === 'POLITICA' && this.politicasAtivasCompativeis().length === 0) {
      this.aviso(
        'Nenhuma política compatível',
        'Crie ou reative uma política para este gatilho antes de associá-la à regra.'
      );
      return;
    }
    this.alterarDestinatario(indice, { tipo, idAlvo: null, valorAlvo: null });
  }

  /** Grava o alvo no campo que o tipo escolhido exige (`idAlvo` ou `valorAlvo`). */
  protected definirAlvoDestinatario(indice: number, valor: string | null): void {
    const tipo = this.destinatarios()[indice]?.tipo;
    if (!tipo) return;
    this.alterarDestinatario(
      indice,
      TIPOS_POR_ID.includes(tipo) ? { idAlvo: valor, valorAlvo: null } : { valorAlvo: valor, idAlvo: null }
    );
  }

  protected alvoDestinatario(linha: DestinatarioLinha): string | null {
    return TIPOS_POR_ID.includes(linha.tipo) ? linha.idAlvo : linha.valorAlvo;
  }

  protected politicaSelecionadaInvalida(linha: DestinatarioLinha): boolean {
    if (linha.tipo !== 'POLITICA' || !linha.idAlvo) return false;
    const politica = this.politicas().find((item) => item.id === linha.idAlvo);
    return !politica?.ativo || politica.chaveGatilho !== this.chaveSelecionada();
  }

  protected rotuloAlvo(tipo: TipoDestinatarioRegra): string {
    return ROTULO_ALVO[tipo];
  }

  protected openUsuarios(tipo: TipoDestinatarioRegra): void {
    if (tipo === 'USUARIO') this.usuarioLookup.open();
    if (tipo === 'PERMISSAO') this.permissaoLookup.open();
  }

  protected searchUsuarios(tipo: TipoDestinatarioRegra, event: { filter?: string }): void {
    if (tipo === 'USUARIO') this.usuarioLookup.search(event.filter ?? '');
    if (tipo === 'PERMISSAO') this.permissaoLookup.search(event.filter ?? '');
  }

  protected loadMoreUsuarios(tipo: TipoDestinatarioRegra): void {
    if (tipo === 'USUARIO') this.usuarioLookup.loadNext();
    if (tipo === 'PERMISSAO') this.permissaoLookup.loadNext();
  }

  protected loadingUsuarios(tipo: TipoDestinatarioRegra): boolean {
    return (tipo === 'USUARIO' && this.usuariosLoading()) ||
      (tipo === 'PERMISSAO' && this.permissoesLoading());
  }

  protected alterarDestinatario(indice: number, mudanca: Partial<DestinatarioLinha>): void {
    this.destinatarios.update((linhas) =>
      linhas.map((linha, posicao) => (posicao === indice ? { ...linha, ...mudanca } : linha))
    );
  }

  protected inserirVariavel(
    campo: 'tituloTemplate' | 'corpoTemplate' | 'deeplinkTemplate',
    variavel: string
  ): void {
    const controle = this.form.controls[campo];
    controle.setValue(`${controle.value ?? ''}{{${variavel}}}`);
    controle.markAsDirty();
  }

  /**
   * Escolha no seletor de destinos conhecidos. O seletor espelha o campo de texto: limpá-lo
   * devolve a regra ao estado "aviso não clicável".
   */
  protected aplicarDestino(caminho: string | null): void {
    this.form.controls.deeplinkTemplate.setValue(caminho ?? '');
    this.form.controls.deeplinkTemplate.markAsDirty();
  }

  protected aplicarCooldown(minutos: number): void {
    this.form.controls.cooldownMinutos.setValue(minutos);
  }

  protected aplicarLimiar(itens: number): void {
    this.form.controls.limiar.setValue(itens);
  }

  // -------------------------------------------------------------------------
  // Gravação
  // -------------------------------------------------------------------------

  protected salvar(): void {
    if (this.salvando()) return;
    if (!this.validarGatilho() || !this.validarMensagem()) return;
    if (!this.validarDestinatarios() || !this.validarDisparo()) return;

    // Um chamado com dez respostas viraria dez e-mails para cada lado: confirme antes de gravar.
    if (this.chaveSelecionada() === GATILHO_RUIDOSO && this.ligandoEmailAgora()) {
      this.confirmation.confirm({
        header: 'Ligar e-mail neste gatilho?',
        message:
          'Esta regra dispara a cada mensagem trocada no chamado. Com o e-mail ligado, um chamado ' +
          'com dez respostas gera dez e-mails para cada lado. Deseja continuar?',
        acceptLabel: 'Ligar mesmo assim',
        rejectLabel: 'Voltar e desmarcar',
        accept: () => void this.gravar()
      });
      return;
    }

    void this.gravar();
  }

  private async gravar(): Promise<void> {
    const valor = this.form.getRawValue();
    const gatilho = this.gatilhoAtual()!;

    const request: RegraNotificacaoRequest = {
      chaveGatilho: valor.chaveGatilho,
      nome: valor.nome.trim(),
      descricao: valor.descricao.trim() || undefined,
      categoria: valor.categoria ?? undefined,
      prioridade: valor.prioridade ?? undefined,
      tituloTemplate: valor.tituloTemplate.trim(),
      corpoTemplate: valor.corpoTemplate.trim(),
      deeplinkTemplate: valor.deeplinkTemplate.trim() || undefined,
      limiar: gatilho.modo === 'POR_LIMIAR' ? valor.limiar ?? undefined : undefined,
      cooldownMinutos: valor.cooldownMinutos ?? 0,
      enviarEmail: valor.enviarEmail,
      // Condições e destinatários substituem as listas anteriores por inteiro.
      condicoes: this.condicoes().map((condicao) => ({
        campo: condicao.campo,
        operador: condicao.operador,
        valor: condicao.valor.trim()
      })),
      destinatarios: this.destinatarios().map((destinatario) => ({
        tipo: destinatario.tipo,
        idAlvo: destinatario.idAlvo,
        valorAlvo: destinatario.valorAlvo
      }))
    };

    const atual = this.original();
    this.salvando.set(true);
    try {
      const regra = atual
        ? await firstValueFrom(this.service.atualizarRegra(atual.id, request))
        : await firstValueFrom(this.service.criarRegra(request));
      this.gravada.set(regra);
      this.activeStep.set(6);
      this.messages.add({
        severity: 'success',
        summary: 'Operação concluída',
        detail: atual ? 'Regra atualizada.' : 'Regra criada.'
      });
    } catch (erro) {
      this.erro(erro);
    } finally {
      this.salvando.set(false);
    }
  }

  protected alternarAtivacao(): void {
    const regra = this.gravada();
    if (!regra) return;
    this.service.alternarRegra(regra.id, !regra.ativo).subscribe({
      next: (atualizada) => {
        this.gravada.set(atualizada);
        this.messages.add({
          severity: 'success',
          summary: 'Operação concluída',
          detail: atualizada.ativo ? 'Regra ativada.' : 'Regra desativada.'
        });
      },
      error: (erro) => this.erro(erro)
    });
  }

  // -------------------------------------------------------------------------
  // Apresentação
  // -------------------------------------------------------------------------

  protected readonly resumoDestinatarios = computed(() =>
    this.destinatarios().map((linha) => {
      if (linha.tipo === 'PERMISSAO') return `quem tem "${linha.valorAlvo ?? '—'}"`;
      if (linha.tipo === 'PAPEL_DINAMICO') {
        if (!linha.valorAlvo) return 'o papel ainda não escolhido';
        // O rótulo já é uma frase ("Quem abriu o chamado") — entra minúsculo no meio do resumo.
        const rotulo = rotuloPapel(linha.valorAlvo, this.chaveSelecionada());
        return rotulo.charAt(0).toLowerCase() + rotulo.slice(1);
      }
      if (linha.tipo === 'PERFIL') {
        const perfil = this.perfis().find((item) => item.id === linha.idAlvo)?.nome;
        return `o perfil ${perfil ?? '—'}`;
      }
      if (linha.tipo === 'POLITICA') {
        const politica = this.politicas().find((item) => item.id === linha.idAlvo);
        return `a política ${politica?.nome ?? '—'}`;
      }
      const usuario = this.listaUsuarios().find((item) => item.id === linha.idAlvo)?.login;
      return `o usuário ${usuario ?? '—'}`;
    })
  );

  protected rotuloCooldown(minutos: number): string {
    if (!minutos) return 'sem silêncio';
    if (minutos % 60 !== 0) return `${minutos} min`;
    const horas = minutos / 60;
    return horas === 1 ? '1 hora' : `${horas} horas`;
  }

  /** Explicação amigável de uma variável; `null` quando não há dica cadastrada. */
  protected dica(variavel: string): string | null {
    return dicaVariavel(variavel);
  }

  /** Renderiza a variável no formato de template sem colidir com a interpolação do Angular. */
  protected placeholder(variavel: string): string {
    return `{{${variavel}}}`;
  }

  protected iconeGatilho(gatilho: GatilhoResponse): string {
    return gatilho.modo === 'POR_LIMIAR' ? 'pi pi-chart-bar' : 'pi pi-bolt';
  }

  /** Envolve cada `{{variavel}}` numa marca, para a prévia mostrar onde o valor entra. */
  private destacar(template: string): { texto: string; variavel: boolean }[] {
    const partes: { texto: string; variavel: boolean }[] = [];
    const padrao = /\{\{\s*([\w.]+)\s*\}\}/g;
    let ultimo = 0;
    let achado: RegExpExecArray | null;

    while ((achado = padrao.exec(template)) !== null) {
      if (achado.index > ultimo) {
        partes.push({ texto: template.slice(ultimo, achado.index), variavel: false });
      }
      partes.push({ texto: achado[1], variavel: true });
      ultimo = achado.index + achado[0].length;
    }
    if (ultimo < template.length) {
      partes.push({ texto: template.slice(ultimo), variavel: false });
    }
    return partes;
  }

  // -------------------------------------------------------------------------
  // Apoio
  // -------------------------------------------------------------------------

  private novoDestinatario(): DestinatarioLinha {
    return { tipo: 'PERMISSAO', idAlvo: null, valorAlvo: null };
  }

  private aviso(summary: string, detail: string): void {
    this.messages.add({ severity: 'warn', summary, detail });
  }

  private erro(erro: unknown): void {
    this.messages.add({
      severity: 'error',
      summary: 'Não foi possível concluir',
      detail: getErrorMessage(erro)
    });
  }
}
