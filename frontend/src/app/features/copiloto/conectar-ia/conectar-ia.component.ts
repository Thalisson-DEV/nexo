import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import {
  CesButtonComponent,
  CesCalloutComponent,
  CesCellDirective,
  CesConfirmationService,
  CesContentState,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDrawerComponent,
  CesIconButtonComponent,
  CesPageHeaderComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTabItem,
  CesTabPanelDirective,
  CesTabsComponent,
  NotificationService
} from '@ces/ui';
import { ConexaoIa, ConexaoIaInfo } from '../../../core/models/conexoes-ia.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AuthService } from '../../../core/services/auth.service';
import { ConexoesIaService } from '../../../core/services/conexoes-ia.service';
import { ChaveAcessoDrawerComponent } from './chave-acesso-drawer.component';
import { GuiaConectarComponent } from './guia-conectar.component';
import { SITUACAO, comoConectou, dataHora, iconeConexao, rotuloPermissao, tempoRelativo } from './conectar-ia.util';

type Aba = 'minhas' | 'empresa';

const desligada = (c: ConexaoIa) => c.situacao === 'DESCONECTADA';

/** Ligadas primeiro; desconectadas só quando pedido. */
function visiveis(lista: ConexaoIa[], comDesconectadas: boolean): ConexaoIa[] {
  return comDesconectadas ? [...lista.filter((c) => !desligada(c)), ...lista.filter(desligada)] : lista.filter((c) => !desligada(c));
}

/**
 * "Conectar sua IA": as conexões do Nexo com o Claude, o ChatGPT e outros assistentes, o passo a
 * passo para conectar, as chaves de acesso e, para o administrador, as conexões da empresa.
 */
@Component({
  selector: 'app-conectar-ia',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NgTemplateOutlet,
    CesButtonComponent,
    CesCalloutComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesDrawerComponent,
    CesIconButtonComponent,
    CesPageHeaderComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTabPanelDirective,
    CesTabsComponent,
    ChaveAcessoDrawerComponent,
    GuiaConectarComponent
  ],
  styleUrl: './conectar-ia.component.css',
  templateUrl: './conectar-ia.component.html'
})
export class ConectarIaComponent {
  private readonly service = inject(ConexoesIaService);
  private readonly auth = inject(AuthService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly notificacao = inject(NotificationService);

  protected readonly admin = computed(() => !!this.auth.currentUser()?.permissoes?.includes('assistente:conexao:manage'));

  protected readonly info = signal<ConexaoIaInfo | null>(null);
  protected readonly erroInfo = signal(false);

  protected readonly minhas = signal<ConexaoIa[]>([]);
  protected readonly carregandoMinhas = signal(true);
  protected readonly erroMinhas = signal<string | null>(null);

  protected readonly todas = signal<ConexaoIa[]>([]);
  protected readonly carregandoTodas = signal(false);
  protected readonly erroTodas = signal<string | null>(null);
  private todasCarregadas = false;
  protected readonly pessoa = signal<string | null>(null);

  /** Conexões desconectadas ficam escondidas até a pessoa pedir para ver. */
  protected readonly verDesconectadas = signal(false);
  protected readonly desconectadasMinhas = computed(() => this.minhas().filter(desligada).length);
  protected readonly desconectadasTodas = computed(() => this.todas().filter(desligada).length);
  protected readonly minhasVisiveis = computed(() => visiveis(this.minhas(), this.verDesconectadas()));

  protected readonly aba = signal<Aba>('minhas');
  protected readonly guiaAberto = signal(false);
  protected readonly chaveAberta = signal(false);
  protected readonly detalheAberto = signal(false);
  protected readonly selecionada = signal<ConexaoIa | null>(null);
  protected readonly desconectando = signal(false);

  protected readonly endereco = computed(() => this.info()?.urlServidor ?? '');
  protected readonly podeEscrever = computed(() => !!this.info()?.podeEscrever);

  protected readonly abas = computed<CesTabItem[]>(() => [
    { value: 'minhas', label: 'Suas conexões', icon: 'pi pi-user', badge: this.carregandoMinhas() ? undefined : this.minhas().length - this.desconectadasMinhas() },
    { value: 'empresa', label: 'Da empresa', icon: 'pi pi-building' }
  ]);

  protected readonly estadoMinhas = computed<CesContentState>(() =>
    this.carregandoMinhas() ? 'loading' : this.erroMinhas() ? 'error' : this.minhasVisiveis().length ? 'ready' : 'empty'
  );

  protected readonly todasFiltradas = computed(() => {
    const p = this.pessoa();
    return visiveis(this.todas(), this.verDesconectadas()).filter((c) => !p || c.usuarioLogin === p);
  });
  protected readonly estadoTodas = computed<CesContentState>(() =>
    this.carregandoTodas() ? 'loading' : this.erroTodas() ? 'error' : this.todasFiltradas().length ? 'ready' : 'empty'
  );
  protected readonly opcoesPessoa = computed<CesSelectOption<string>[]>(() =>
    [...new Set(this.todas().map((c) => c.usuarioLogin).filter((l): l is string => !!l))]
      .sort()
      .map((l) => ({ label: l, value: l }))
  );

  protected readonly colunasMinhas: CesDataTableColumn<ConexaoIa>[] = [
    { key: 'nome', header: 'Aplicativo', priority: 1, width: '32%', display: (c) => `${c.nome} ${comoConectou(c)}` },
    { key: 'permiteEscrita', header: 'O que pode fazer', display: rotuloPermissao },
    { key: 'criadaEm', header: 'Conectou', value: (c) => c.criadaEm, display: (c) => tempoRelativo(c.criadaEm) },
    { key: 'ultimoUsoEm', header: 'Último uso', priority: 1, value: (c) => c.ultimoUsoEm ?? '', display: (c) => tempoRelativo(c.ultimoUsoEm) },
    { key: 'situacao', header: 'Situação', priority: 1, display: (c) => SITUACAO[c.situacao].rotulo }
  ];
  protected readonly colunasTodas: CesDataTableColumn<ConexaoIa>[] = [
    { key: 'usuarioLogin', header: 'Pessoa', priority: 1, display: (c) => c.usuarioLogin ?? '' },
    { ...this.colunasMinhas[0], priority: 2 },
    this.colunasMinhas[1],
    this.colunasMinhas[3],
    this.colunasMinhas[4]
  ];

  protected readonly chave = (c: ConexaoIa) => c.id;
  protected readonly rotulo = (c: ConexaoIa) => c.nome;
  protected readonly sit = (c: ConexaoIa) => SITUACAO[c.situacao];
  protected readonly icone = iconeConexao;
  protected readonly como = comoConectou;
  protected readonly permissao = rotuloPermissao;
  protected readonly relativo = tempoRelativo;
  protected readonly dataHora = dataHora;

  constructor() {
    this.service.info().subscribe({
      next: (info) => this.info.set(info),
      error: () => this.erroInfo.set(true)
    });
    this.carregarMinhas();
  }

  protected trocarAba(aba: Aba): void {
    this.aba.set(aba);
    if (aba === 'empresa' && !this.todasCarregadas) this.carregarTodas();
  }

  protected abrirGuia(): void {
    this.guiaAberto.set(true);
  }

  protected abrirChave(): void {
    this.guiaAberto.set(false);
    this.chaveAberta.set(true);
  }

  protected abrir(conexao: ConexaoIa): void {
    this.selecionada.set(conexao);
    this.detalheAberto.set(true);
  }

  protected recarregarTudo(): void {
    this.carregarMinhas();
    if (this.todasCarregadas) this.carregarTodas();
  }

  protected async desconectar(conexao: ConexaoIa): Promise<void> {
    const deOutraPessoa = conexao.usuarioId !== this.auth.currentUser()?.id && !!conexao.usuarioLogin;
    const ok = await this.confirmacao.confirm({
      title: `Desconectar ${conexao.nome}?`,
      message: deOutraPessoa
        ? `${conexao.nome} de ${conexao.usuarioLogin} para de acessar o Nexo na hora. Para voltar, a pessoa precisa conectar de novo.`
        : `${conexao.nome} para de acessar o Nexo na hora. Para voltar a usar, é só conectar de novo.`,
      confirmLabel: 'Desconectar',
      cancelLabel: 'Manter conectada',
      variant: 'destructive'
    });
    if (!ok) return;
    this.desconectando.set(true);
    this.service.desconectar(conexao.id).subscribe({
      next: () => {
        this.desconectando.set(false);
        this.detalheAberto.set(false);
        this.notificacao.success(`${conexao.nome} foi desconectada.`);
        this.recarregarTudo();
      },
      error: (e) => {
        this.desconectando.set(false);
        this.notificacao.error(getErrorMessage(e));
      }
    });
  }

  protected carregarMinhas(): void {
    this.carregandoMinhas.set(true);
    this.erroMinhas.set(null);
    this.service.listar().subscribe({
      next: (lista) => {
        this.minhas.set(lista);
        this.carregandoMinhas.set(false);
      },
      error: (e) => {
        this.erroMinhas.set(getErrorMessage(e));
        this.carregandoMinhas.set(false);
      }
    });
  }

  protected carregarTodas(): void {
    this.todasCarregadas = true;
    this.carregandoTodas.set(true);
    this.erroTodas.set(null);
    this.service.listarTodas().subscribe({
      next: (pagina) => {
        this.todas.set(pagina.content);
        this.carregandoTodas.set(false);
      },
      error: (e) => {
        this.erroTodas.set(getErrorMessage(e));
        this.carregandoTodas.set(false);
      }
    });
  }
}
