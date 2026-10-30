import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { ButtonModule } from 'primeng/button';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { StepperModule } from 'primeng/stepper';
import {
  CesAsyncStateComponent,
  CesButtonComponent,
  CesNumberInputComponent,
  CesSelectComponent,
  CesStatusComponent,
  CesTextInputComponent,
  CesTextareaComponent,
  NotificationService
} from '@ces/ui';
import { catchError, firstValueFrom, forkJoin, of } from 'rxjs';
import { UsuarioResponse } from '../../../../core/models/iam.models';
import {
  GatilhoResponse,
  PoliticaRoteamentoResponse
} from '../../../../core/models/notificacao.models';
import { getErrorMessage, ProblemDetails } from '../../../../core/models/shared.models';
import { NotificacoesService } from '../../../../core/services/notificacoes.service';
import { UsuariosService } from '../../../../core/services/usuarios.service';
import { OPERADOR_OPTIONS } from '../../notificacoes-ui';
import {
  CondicaoRotaForm,
  montarPoliticaRequest,
  novaCondicao,
  novaRotaCondicional,
  politicaParaFormulario,
  PoliticaFieldErrors,
  RotaRoteamentoForm,
  rotasIniciais,
  rotuloUsuario,
  validarPolitica
} from './politica-roteamento-form';

@Component({
  selector: 'app-politica-roteamento-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    BreadcrumbModule,
    ButtonModule,
    ProgressSpinnerModule,
    StepperModule,
    CesAsyncStateComponent,
    CesButtonComponent,
    CesNumberInputComponent,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    CesTextareaComponent
  ],
  templateUrl: './politica-roteamento-form.component.html',
  styleUrl: './politica-roteamento-form.component.css'
})
export class PoliticaRoteamentoFormComponent {
  private readonly service = inject(NotificacoesService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly notification = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly timers = new Map<string, ReturnType<typeof setTimeout>>();

  protected readonly original = signal<PoliticaRoteamentoResponse | null>(null);
  protected readonly gravada = signal<PoliticaRoteamentoResponse | null>(null);
  protected readonly activeStep = signal(1);
  protected readonly gatilhos = signal<GatilhoResponse[]>([]);
  protected readonly chaveGatilho = signal('');
  protected readonly nome = signal('');
  protected readonly descricao = signal('');
  protected readonly rotas = signal<RotaRoteamentoForm[]>(rotasIniciais());
  protected readonly carregando = signal(true);
  protected readonly loadError = signal('');
  protected readonly salvando = signal(false);
  protected readonly fieldErrors = signal<PoliticaFieldErrors>({});

  protected readonly edicao = computed(() => this.original() !== null);
  protected readonly gatilhoAtual = computed(
    () => this.gatilhos().find((gatilho) => gatilho.chave === this.chaveGatilho()) ?? null
  );
  protected readonly gatilhosPorOcorrencia = computed(() =>
    this.gatilhos().filter((gatilho) => gatilho.modo === 'POR_OCORRENCIA')
  );
  protected readonly variaveisOptions = computed(() =>
    (this.gatilhoAtual()?.variaveis ?? []).map((variavel) => ({
      label: variavel,
      value: variavel
    }))
  );
  protected readonly rotasExibidas = computed(() => [
    ...this.rotas()
      .filter((rota) => !rota.padrao)
      .sort((a, b) => a.ordem - b.ordem),
    ...this.rotas().filter((rota) => rota.padrao)
  ]);
  protected readonly totalCondicionais = computed(
    () => this.rotas().filter((rota) => !rota.padrao).length
  );
  protected readonly resumoErros = computed(() => [...new Set(Object.values(this.fieldErrors()))]);
  protected readonly operadorOptions = OPERADOR_OPTIONS;
  protected readonly breadcrumb: MenuItem[] = [
    { label: 'Administração' },
    { label: 'Comunicação' },
    { label: 'Políticas de roteamento', routerLink: '/notificacoes/admin/politicas-roteamento' },
    { label: this.route.snapshot.paramMap.has('id') ? 'Editar' : 'Nova' }
  ];

  constructor() {
    inject(DestroyRef).onDestroy(() => this.timers.forEach((timer) => clearTimeout(timer)));
    this.carregar();
  }

  protected carregar(): void {
    const id = this.route.snapshot.paramMap.get('id');
    this.carregando.set(true);
    this.loadError.set('');
    forkJoin({
      gatilhos: this.service.listarGatilhos(),
      politica: id ? this.service.buscarPolitica(id) : of(null)
    }).subscribe({
      next: ({ gatilhos, politica }) => {
        this.gatilhos.set(gatilhos);
        if (politica) this.aplicar(politica);
        this.carregando.set(false);
        this.rotas().forEach((rota) => this.consultarUsuarios(rota.localId, ''));
      },
      error: (erro) => {
        this.loadError.set(getErrorMessage(erro));
        this.carregando.set(false);
      }
    });
  }

  private aplicar(politica: PoliticaRoteamentoResponse): void {
    const valor = politicaParaFormulario(politica);
    this.original.set(politica);
    this.chaveGatilho.set(valor.chaveGatilho);
    this.nome.set(valor.nome);
    this.descricao.set(valor.descricao);
    this.rotas.set(valor.rotas);
    this.carregarUsuariosSelecionados(valor.rotas);
  }

  private carregarUsuariosSelecionados(rotas: readonly RotaRoteamentoForm[]): void {
    const ids = [...new Set(rotas.map((rota) => rota.usuarioId).filter((id): id is string => !!id))];
    if (!ids.length) return;
    forkJoin(
      ids.map((id) =>
        this.usuariosService.buscarUsuarioPorId(id).pipe(catchError(() => of(null)))
      )
    ).subscribe((usuarios) => {
      this.rotas.update((atuais) =>
        atuais.map((rota) => {
          const usuario = usuarios.find((item) => item?.id === rota.usuarioId) ?? null;
          return {
            ...rota,
            usuarioAtivo: usuario?.ativo === true,
            usuarios: usuario ? this.mesclarUsuarios(rota.usuarios, [usuario]) : rota.usuarios
          };
        })
      );
    });
  }

  protected definirGatilho(chave: string | null): void {
    if (this.edicao()) return;
    const valor = chave ?? '';
    this.chaveGatilho.set(valor);
    const primeiroCampo = this.gatilhos().find((item) => item.chave === valor)?.variaveis[0] ?? '';
    this.rotas.update((rotas) =>
      rotas.map((rota) =>
        rota.padrao ? rota : { ...rota, condicoes: [novaCondicao(primeiroCampo)] }
      )
    );
    this.limparErro('chaveGatilho');
  }

  protected adicionarRota(): void {
    const usadas = new Set(this.rotas().map((rota) => rota.ordem));
    let ordem = Math.max(0, ...this.rotas().filter((rota) => !rota.padrao).map((rota) => rota.ordem)) + 10;
    while (usadas.has(ordem)) ordem += 10;
    const rota = novaRotaCondicional(ordem, this.gatilhoAtual()?.variaveis[0] ?? '');
    this.rotas.update((rotas) => [...rotas, rota]);
    this.consultarUsuarios(rota.localId, '');
    this.fieldErrors.update((erros) => {
      const copia = { ...erros };
      delete copia['rotas'];
      return copia;
    });
  }

  protected removerRota(localId: string): void {
    if (this.totalCondicionais() <= 1) return;
    this.rotas.update((rotas) => rotas.filter((rota) => rota.localId !== localId));
  }

  protected atualizarOrdem(localId: string, ordem: number | null): void {
    this.atualizarRota(localId, (rota) => ({ ...rota, ordem: ordem ?? -1 }));
  }

  protected adicionarCondicao(localId: string): void {
    this.atualizarRota(localId, (rota) => ({
      ...rota,
      condicoes: [...rota.condicoes, novaCondicao(this.gatilhoAtual()?.variaveis[0] ?? '')]
    }));
  }

  protected removerCondicao(localId: string, condicaoId: string): void {
    this.atualizarRota(localId, (rota) => ({
      ...rota,
      condicoes: rota.condicoes.filter((condicao) => condicao.localId !== condicaoId)
    }));
  }

  protected atualizarCondicao(
    rotaId: string,
    condicaoId: string,
    mudanca: Partial<Pick<CondicaoRotaForm, 'campo' | 'operador' | 'valor'>>
  ): void {
    this.atualizarRota(rotaId, (rota) => ({
      ...rota,
      condicoes: rota.condicoes.map((condicao) =>
        condicao.localId === condicaoId
          ? {
              ...condicao,
              ...mudanca,
              ...(mudanca.operador && mudanca.operador !== condicao.operador
                ? { operadorRotulo: undefined }
                : {})
            }
          : condicao
      )
    }));
  }

  protected operadorOptionsPara(condicao: CondicaoRotaForm) {
    return this.operadorOptions.map((opcao) => ({
      ...opcao,
      label:
        opcao.value === condicao.operador && condicao.operadorRotulo
          ? condicao.operadorRotulo
          : opcao.label
    }));
  }

  protected rotuloOperador(condicao: CondicaoRotaForm): string {
    return this.operadorOptionsPara(condicao).find((opcao) => opcao.value === condicao.operador)?.label
      ?? condicao.operador;
  }

  protected pesquisarUsuarios(localId: string, busca: string): void {
    this.atualizarRota(localId, (rota) => ({ ...rota, usuarioBusca: busca }));
    const anterior = this.timers.get(localId);
    if (anterior) clearTimeout(anterior);
    this.timers.set(localId, setTimeout(() => this.consultarUsuarios(localId, busca), 300));
  }

  private consultarUsuarios(localId: string, busca: string): void {
    this.atualizarRota(localId, (rota) => ({ ...rota, usuariosLoading: true }));
    this.usuariosService
      .listarUsuarios({ buscaGlobal: busca.trim(), ativo: true, page: 0, size: 20, sort: 'login,asc' })
      .subscribe({
        next: (pagina) =>
          this.atualizarRota(localId, (rota) => ({
            ...rota,
            usuariosLoading: false,
            usuarios: this.mesclarUsuarios(
              rota.usuarios.filter((usuario) => usuario.id === rota.usuarioId),
              pagina.content
            )
          })),
        error: (erro) => {
          this.atualizarRota(localId, (rota) => ({ ...rota, usuariosLoading: false }));
          this.notification.error(getErrorMessage(erro), {
            title: 'Não foi possível pesquisar usuários'
          });
        }
      });
  }

  protected usuarioOptions(rota: RotaRoteamentoForm) {
    return rota.usuarios.map((usuario) => ({
      label: `${rotuloUsuario(usuario)}${usuario.ativo ? '' : ' (Inativo)'}`,
      value: usuario.id,
      disabled: !usuario.ativo
    }));
  }

  protected definirUsuario(localId: string, usuarioId: string | null): void {
    this.atualizarRota(localId, (rota) => {
      const usuario = rota.usuarios.find((item) => item.id === usuarioId);
      return { ...rota, usuarioId, usuarioAtivo: usuario?.ativo === true };
    });
  }

  protected nomeUsuario(rota: RotaRoteamentoForm): string {
    const usuario = rota.usuarios.find((item) => item.id === rota.usuarioId);
    return usuario ? rotuloUsuario(usuario) : 'Usuário não selecionado';
  }

  protected irPara(etapa: number): void {
    if (etapa <= this.activeStep()) {
      this.activeStep.set(etapa);
      return;
    }
    if (etapa > 1 && !this.validarIdentificacao()) return;
    if (etapa > 2 && !this.validarRotas()) return;
    this.activeStep.set(Math.min(etapa, 3));
  }

  protected proximaEtapa(): void {
    this.irPara(this.activeStep() + 1);
  }

  protected etapaAnterior(): void {
    this.activeStep.set(Math.max(1, this.activeStep() - 1));
  }

  protected erro(caminho: string): string | undefined {
    return this.fieldErrors()[caminho];
  }

  protected erroRota(rota: RotaRoteamentoForm, campo: string): string | undefined {
    return this.erro(`rotas[${this.indiceRota(rota)}].${campo}`);
  }

  protected erroCondicao(
    rota: RotaRoteamentoForm,
    condicao: CondicaoRotaForm,
    campo: string
  ): string | undefined {
    return this.erro(
      `rotas[${this.indiceRota(rota)}].condicoes[${rota.condicoes.indexOf(condicao)}].${campo}`
    );
  }

  protected async salvar(): Promise<void> {
    if (this.salvando()) return;
    const valor = {
      chaveGatilho: this.chaveGatilho(),
      nome: this.nome(),
      descricao: this.descricao(),
      rotas: this.rotas()
    };
    const erros = validarPolitica(valor, this.gatilhoAtual()?.variaveis ?? []);
    this.fieldErrors.set(erros);
    if (Object.keys(erros).length) {
      this.notification.warning('Revise os campos destacados antes de salvar.');
      return;
    }

    this.salvando.set(true);
    try {
      const request = montarPoliticaRequest(valor);
      const atual = this.original();
      const salva = atual
        ? this.service.atualizarPolitica(atual.id, request)
        : this.service.criarPolitica(request);
      const politica = await firstValueFrom(salva);
      this.gravada.set(politica);
      this.activeStep.set(4);
      this.notification.success(atual ? 'Política atualizada.' : 'Política criada.');
    } catch (erro) {
      this.aplicarErroServidor(erro);
      this.activeStep.set(this.temErroIdentificacao() ? 1 : 2);
      this.notification.error(getErrorMessage(erro));
    } finally {
      this.salvando.set(false);
    }
  }

  protected sair(): void {
    void this.router.navigate(['/notificacoes/admin/politicas-roteamento']);
  }

  protected alternarAtivacao(): void {
    const politica = this.gravada();
    if (!politica) return;
    this.service.alternarPolitica(politica.id, !politica.ativo).subscribe({
      next: (atualizada) => {
        this.gravada.set(atualizada);
        this.notification.success(atualizada.ativo ? 'Política ativada.' : 'Política desativada.');
      },
      error: (erro) => this.notification.error(getErrorMessage(erro))
    });
  }

  private aplicarErroServidor(erro: unknown): void {
    const problem = (erro as { error?: ProblemDetails })?.error;
    const campos: PoliticaFieldErrors = {};
    Object.entries(problem?.erros ?? {}).forEach(([campo, mensagem]) => (campos[campo] = mensagem));
    Object.entries(problem?.errors ?? {}).forEach(([campo, mensagens]) => {
      if (mensagens[0]) campos[campo] = mensagens[0];
    });
    if (Object.keys(campos).length) this.fieldErrors.set(campos);
  }

  private validarIdentificacao(): boolean {
    const erros = validarPolitica(
      {
        chaveGatilho: this.chaveGatilho(),
        nome: this.nome(),
        descricao: this.descricao(),
        rotas: this.rotas()
      },
      this.gatilhoAtual()?.variaveis ?? []
    );
    const identificacao = Object.fromEntries(
      Object.entries(erros).filter(([campo]) =>
        ['chaveGatilho', 'nome', 'descricao'].includes(campo)
      )
    );
    this.fieldErrors.set(identificacao);
    if (Object.keys(identificacao).length) {
      this.notification.warning('Informe o gatilho e a identificação da política.');
      return false;
    }
    return true;
  }

  private validarRotas(): boolean {
    const erros = validarPolitica(
      {
        chaveGatilho: this.chaveGatilho(),
        nome: this.nome(),
        descricao: this.descricao(),
        rotas: this.rotas()
      },
      this.gatilhoAtual()?.variaveis ?? []
    );
    this.fieldErrors.set(erros);
    if (Object.keys(erros).length) {
      this.notification.warning('Revise as rotas e condições destacadas.');
      return false;
    }
    return true;
  }

  private temErroIdentificacao(): boolean {
    return Object.keys(this.fieldErrors()).some((campo) =>
      ['chaveGatilho', 'nome', 'descricao'].includes(campo)
    );
  }

  private atualizarRota(
    localId: string,
    atualizar: (rota: RotaRoteamentoForm) => RotaRoteamentoForm
  ): void {
    this.rotas.update((rotas) =>
      rotas.map((rota) => (rota.localId === localId ? atualizar(rota) : rota))
    );
  }

  private indiceRota(rota: RotaRoteamentoForm): number {
    return this.rotas().findIndex((item) => item.localId === rota.localId);
  }

  private mesclarUsuarios(
    atuais: readonly UsuarioResponse[],
    novos: readonly UsuarioResponse[]
  ): UsuarioResponse[] {
    const mapa = new Map(atuais.map((usuario) => [usuario.id, usuario]));
    novos.forEach((usuario) => mapa.set(usuario.id, usuario));
    return [...mapa.values()];
  }

  private limparErro(caminho: string): void {
    this.fieldErrors.update((erros) => {
      const copia = { ...erros };
      delete copia[caminho];
      return copia;
    });
  }
}
