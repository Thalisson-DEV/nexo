import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import {
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MenuItem, MessageService } from 'primeng/api';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { InputTextModule } from 'primeng/inputtext';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { SelectModule } from 'primeng/select';
import { StepperModule } from 'primeng/stepper';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';
import { firstValueFrom } from 'rxjs';
import { PerfilAcessoResponse, UsuarioResponse } from '../../../../core/models/iam.models';
import {
  AnexoComunicadoResponse,
  ApresentacaoComunicado,
  ComunicadoMuralResponse,
  ComunicadoRequest,
  ComunicadoResponse,
  PrioridadeNotificacao,
  TipoAlvoSegmento,
  TipoComunicado
} from '../../../../core/models/notificacao.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { ComunicadosService } from '../../../../core/services/comunicados.service';
import { UsuariosService } from '../../../../core/services/usuarios.service';
import { FileDownloadUtil } from '../../../../core/utils/file-download.util';
import { RemoteLookup } from '../../../../core/utils/remote-lookup.util';
import { ComunicadoConteudoComponent } from '../../../../shared/components/comunicado-conteudo/comunicado-conteudo.component';
import {
  APRESENTACAO_OPTIONS,
  PRIORIDADE_OPTIONS,
  TIPO_ALVO_OPTIONS,
  TIPO_COMUNICADO_CLASSES,
  TIPO_COMUNICADO_ICONES,
  TIPO_COMUNICADO_OPTIONS,
  TIPO_USUARIO_OPTIONS,
  formatarTamanhoArquivo
} from '../../notificacoes-ui';

const ANEXO_TIPOS = ['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'application/pdf'];
const ANEXO_LIMITE_BYTES = 15 * 1024 * 1024;

type StatusUpload = 'pendente' | 'enviando' | 'concluido' | 'erro';

interface ArquivoPendente {
  file: File;
  status: StatusUpload;
  erro?: string;
}

type SegmentoForm = FormGroup<{
  tipoAlvo: FormControl<TipoAlvoSegmento>;
  idAlvo: FormControl<string | null>;
  valorAlvo: FormControl<string | null>;
}>;

/** Explicação de cada forma de apresentação, para o autor escolher sabendo o efeito. */
const APRESENTACAO_DESCRICAO: Record<ApresentacaoComunicado, string> = {
  MODAL: 'Abre sobre a tela assim que a pessoa entra no sistema. Use para o que não pode passar batido.',
  BANNER: 'Faixa fixa no topo, sem bloquear o uso. Boa para avisos que valem enquanto durarem.',
  CARD: 'Fica só na página do mural, para quem for procurar. O aviso mais discreto.'
};

const TIPO_DESCRICAO: Record<TipoComunicado, string> = {
  DICA: 'Orientação de uso do sistema.',
  CHANGELOG: 'O que mudou nesta versão.',
  AVISO: 'Comunicado geral da empresa.',
  MANUTENCAO: 'Parada programada ou indisponibilidade.',
  URGENTE: 'Assunto que exige atenção imediata.'
};

@Component({
  selector: 'app-comunicado-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    BreadcrumbModule,
    ButtonModule,
    CheckboxModule,
    ComunicadoConteudoComponent,
    InputTextModule,
    ProgressSpinnerModule,
    SelectModule,
    StepperModule,
    TextareaModule,
    TooltipModule
  ],
  templateUrl: './comunicado-form.component.html',
  styleUrl: './comunicado-form.component.css'
})
export class ComunicadoFormComponent {
  private readonly service = inject(ComunicadosService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly messages = inject(MessageService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly usuarioLookup = new RemoteLookup<UsuarioResponse>(
    {
      identify: (usuario) => usuario.id,
      selectedKeys: () =>
        this.segmentos?.controls
          .filter((grupo) => grupo.controls.tipoAlvo.value === 'USUARIO')
          .map((grupo) => grupo.controls.idAlvo.value)
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

  protected readonly breadcrumb: MenuItem[] = [
    { icon: 'pi pi-home', routerLink: '/home' },
    { label: 'Comunicados', routerLink: '/notificacoes/admin/comunicados' },
    { label: 'Formulário' }
  ];

  protected readonly tipoOptions = TIPO_COMUNICADO_OPTIONS;
  protected readonly apresentacaoOptions = APRESENTACAO_OPTIONS;
  protected readonly prioridadeOptions = PRIORIDADE_OPTIONS;
  protected readonly tipoAlvoOptions = TIPO_ALVO_OPTIONS;
  protected readonly tipoUsuarioOptions = TIPO_USUARIO_OPTIONS;
  protected readonly apresentacaoDescricao = APRESENTACAO_DESCRICAO;
  protected readonly tipoDescricao = TIPO_DESCRICAO;

  protected readonly activeStep = signal(1);
  protected readonly carregando = signal(false);
  protected readonly salvando = signal(false);
  protected readonly enviandoAnexo = signal(false);

  /** Comunicado carregado na edição. */
  protected readonly original = signal<ComunicadoResponse | null>(null);
  /** Comunicado já gravado nesta sessão do formulário — habilita o envio de anexos. */
  protected readonly gravado = signal<ComunicadoResponse | null>(null);
  protected readonly arquivos = signal<ArquivoPendente[]>([]);

  protected readonly perfis = signal<PerfilAcessoResponse[]>([]);
  protected readonly listaUsuarios = this.usuarioLookup.items;
  protected readonly usuariosLoading = this.usuarioLookup.loading;
  protected readonly usuariosIndisponiveis = computed(() => this.usuarioLookup.error() !== null);

  /**
   * Comunicado publicado só aceita correção de texto (`PATCH /texto`): mudar público-alvo ou
   * apresentação alteraria o que ele já significou para quem leu. O formulário encolhe para
   * as etapas que ainda fazem sentido.
   */
  protected readonly modoTexto = computed(() => this.original()?.status === 'PUBLICADO');
  protected readonly edicao = computed(() => !!this.original());

  protected readonly form = new FormGroup({
    titulo: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(150)]
    }),
    resumo: new FormControl('', { nonNullable: true, validators: Validators.maxLength(300) }),
    conteudo: new FormControl('', { nonNullable: true, validators: Validators.required }),
    tipo: new FormControl<TipoComunicado>('AVISO', {
      nonNullable: true,
      validators: Validators.required
    }),
    apresentacao: new FormControl<ApresentacaoComunicado>('CARD', {
      nonNullable: true,
      validators: Validators.required
    }),
    videoUrl: new FormControl('', { nonNullable: true, validators: Validators.maxLength(500) }),
    acaoRotulo: new FormControl('', { nonNullable: true, validators: Validators.maxLength(50) }),
    acaoDeeplink: new FormControl('', { nonNullable: true, validators: Validators.maxLength(500) }),
    fixado: new FormControl(false, { nonNullable: true }),
    exigeConfirmacao: new FormControl(false, { nonNullable: true }),
    // Os dois canais nascem ligados: desligar é decisão de quem publica, não um padrão silencioso.
    notificarNoSininho: new FormControl(true, { nonNullable: true }),
    notificarPorEmail: new FormControl(true, { nonNullable: true }),
    prioridadeNotificacao: new FormControl<PrioridadeNotificacao | null>(null),
    segmentos: new FormArray<SegmentoForm>([])
  });

  protected get segmentos(): FormArray<SegmentoForm> {
    return this.form.controls.segmentos;
  }

  /** Prévia com o mesmo componente que renderiza o mural de verdade. */
  protected readonly previa = computed<ComunicadoMuralResponse>(() => {
    const valor = this.form.getRawValue();
    this.versaoFormulario();
    return {
      id: this.gravado()?.id ?? this.original()?.id ?? 'previa',
      titulo: valor.titulo || 'Título do comunicado',
      resumo: valor.resumo || null,
      conteudo: valor.conteudo || '_Sem conteúdo._',
      tipo: valor.tipo,
      tipoRotulo: this.tipoOptions.find((item) => item.value === valor.tipo)?.label ?? valor.tipo,
      apresentacao: valor.apresentacao,
      videoUrl: valor.videoUrl || null,
      acaoRotulo: valor.acaoRotulo || null,
      acaoDeeplink: valor.acaoDeeplink || null,
      fixado: valor.fixado,
      exigeConfirmacao: valor.exigeConfirmacao,
      permiteAdiar: !valor.exigeConfirmacao,
      visualizado: false,
      confirmado: false,
      dataInicio: null,
      dataFim: null,
      // A prévia é do texto. Os anexos têm seção própria, com o download pelo endpoint de
      // administração — o do mural não enxerga um rascunho.
      anexos: []
    };
  });

  protected readonly anexosGravados = computed(
    () => this.gravado()?.anexos ?? this.original()?.anexos ?? []
  );

  /** Marcador para reavaliar a prévia quando o formulário muda (ngModel mexe em objetos). */
  private readonly versaoFormulario = signal(0);

  constructor() {
    this.carregarApoio();
    this.form.valueChanges.subscribe(() => this.versaoFormulario.update((valor) => valor + 1));

    const id = this.route.snapshot.paramMap.get('id');
    if (id) this.carregar(id);
    else this.iniciarNovo();
  }

  // -------------------------------------------------------------------------
  // Carregamento
  // -------------------------------------------------------------------------

  private iniciarNovo(): void {
    this.segmentos.push(this.novoSegmento('TODOS'));
  }

  private carregar(id: string): void {
    this.carregando.set(true);
    this.service.buscar(id).subscribe({
      next: (comunicado) => {
        this.original.set(comunicado);
        this.aplicar(comunicado);
        this.carregando.set(false);
      },
      error: (erro) => {
        this.carregando.set(false);
        this.messages.add({
          severity: 'error',
          summary: 'Não foi possível carregar o comunicado',
          detail: getErrorMessage(erro)
        });
        this.router.navigate(['/notificacoes/admin/comunicados']);
      }
    });
  }

  private aplicar(comunicado: ComunicadoResponse): void {
    this.segmentos.clear();
    this.form.patchValue({
      titulo: comunicado.titulo,
      resumo: comunicado.resumo ?? '',
      conteudo: comunicado.conteudo,
      tipo: comunicado.tipo,
      apresentacao: comunicado.apresentacao,
      videoUrl: comunicado.videoUrl ?? '',
      acaoRotulo: comunicado.acaoRotulo ?? '',
      acaoDeeplink: comunicado.acaoDeeplink ?? '',
      fixado: comunicado.fixado,
      exigeConfirmacao: comunicado.exigeConfirmacao,
      notificarNoSininho: comunicado.notificarNoSininho,
      notificarPorEmail: comunicado.notificarPorEmail,
      prioridadeNotificacao: comunicado.prioridadeNotificacao
    });
    comunicado.segmentos.forEach((segmento) =>
      this.segmentos.push(this.novoSegmento(segmento.tipoAlvo, segmento.idAlvo, segmento.valorAlvo))
    );
    for (const id of comunicado.segmentos
      .filter((segmento) => segmento.tipoAlvo === 'USUARIO' && !!segmento.idAlvo)
      .map((segmento) => segmento.idAlvo as string)) {
      this.usuariosService.buscarUsuarioPorId(id).subscribe({
        next: (usuario) => this.usuarioLookup.setSeed([usuario]),
        error: () => {}
      });
    }
    if (this.segmentos.length === 0) this.segmentos.push(this.novoSegmento('TODOS'));
  }

  private carregarApoio(): void {
    this.usuariosService.listarPerfisAtivos().subscribe({
      next: (perfis) => this.perfis.set(perfis),
      error: () => this.perfis.set([])
    });
  }

  protected openUsuarios(): void { this.usuarioLookup.open(); }

  protected searchUsuarios(event: { filter?: string }): void {
    this.usuarioLookup.search(event.filter ?? '');
  }

  protected loadMoreUsuarios(): void { this.usuarioLookup.loadNext(); }

  // -------------------------------------------------------------------------
  // Navegação entre etapas
  // -------------------------------------------------------------------------

  protected irPara(etapa: number): void {
    // Só deixa avançar; voltar é sempre livre.
    if (etapa <= this.activeStep()) {
      this.activeStep.set(etapa);
      return;
    }
    if (!this.validarAte(etapa)) return;
    this.activeStep.set(etapa);
  }

  private validarAte(destino: number): boolean {
    if (destino > 1 && !this.validarConteudo()) return false;
    if (this.modoTexto()) return true;
    if (destino > 2 && !this.validarApresentacao()) return false;
    if (destino > 3 && !this.validarPublico()) return false;
    return true;
  }

  protected proximaEtapa(): void {
    const atual = this.activeStep();
    // No modo de correção de texto as etapas 2 e 3 não existem.
    const proxima = this.modoTexto() && atual === 1 ? 4 : atual + 1;
    this.irPara(proxima);
  }

  protected etapaAnterior(): void {
    const atual = this.activeStep();
    const anterior = this.modoTexto() && atual === 4 ? 1 : atual - 1;
    this.activeStep.set(Math.max(1, anterior));
  }

  protected sair(): void {
    this.router.navigate(['/notificacoes/admin/comunicados']);
  }

  private validarConteudo(): boolean {
    this.form.controls.titulo.markAsTouched();
    this.form.controls.conteudo.markAsTouched();
    if (this.form.controls.titulo.invalid || this.form.controls.conteudo.invalid) {
      this.aviso('Conteúdo incompleto', 'Informe pelo menos o título e o corpo do comunicado.');
      return false;
    }
    return true;
  }

  private validarApresentacao(): boolean {
    const valor = this.form.getRawValue();
    const temRotulo = !!valor.acaoRotulo.trim();
    const temDestino = !!valor.acaoDeeplink.trim();
    if (temRotulo !== temDestino) {
      this.aviso(
        'Botão de ação incompleto',
        'O botão precisa do rótulo e do destino — ou de nenhum dos dois.'
      );
      return false;
    }
    return true;
  }

  private validarPublico(): boolean {
    return !!this.montarSegmentos();
  }

  // -------------------------------------------------------------------------
  // Público-alvo
  // -------------------------------------------------------------------------

  protected adicionarSegmento(): void {
    this.segmentos.push(this.novoSegmento('TODOS'));
  }

  protected removerSegmento(indice: number): void {
    this.segmentos.removeAt(indice);
  }

  protected aoTrocarTipoAlvo(grupo: SegmentoForm): void {
    grupo.controls.idAlvo.setValue(null);
    grupo.controls.valorAlvo.setValue(null);
  }

  protected descricaoAlvo(
    tipoAlvo: TipoAlvoSegmento,
    idAlvo: string | null,
    valorAlvo: string | null
  ): string {
    if (tipoAlvo === 'TODOS') return 'Todos os usuários';
    if (tipoAlvo === 'TIPO_USUARIO') {
      return (
        TIPO_USUARIO_OPTIONS.find((item) => item.value === valorAlvo)?.label ?? valorAlvo ?? '—'
      );
    }
    if (tipoAlvo === 'PERFIL') {
      return this.perfis().find((item) => item.id === idAlvo)?.nome ?? 'Perfil de acesso';
    }
    return this.listaUsuarios().find((item) => item.id === idAlvo)?.login ?? 'Usuário';
  }

  protected resumoPublico(): string[] {
    return this.segmentos.controls.map((grupo) => {
      const valor = grupo.getRawValue();
      return this.descricaoAlvo(valor.tipoAlvo, valor.idAlvo, valor.valorAlvo);
    });
  }

  // -------------------------------------------------------------------------
  // Gravação
  // -------------------------------------------------------------------------

  protected async salvar(): Promise<void> {
    if (this.salvando()) return;
    if (!this.validarConteudo()) return;

    const atual = this.original();

    // Publicado: só o texto muda.
    if (this.modoTexto() && atual) {
      this.salvando.set(true);
      try {
        const valor = this.form.getRawValue();
        const comunicado = await firstValueFrom(
          this.service.corrigirTexto(atual.id, {
            titulo: valor.titulo.trim(),
            resumo: valor.resumo.trim() || undefined,
            conteudo: valor.conteudo
          })
        );
        this.gravado.set(comunicado);
        this.activeStep.set(5);
        this.sucesso('Texto corrigido.');
      } catch (erro) {
        this.erro(erro);
      } finally {
        this.salvando.set(false);
      }
      return;
    }

    if (!this.validarApresentacao()) return;
    const segmentos = this.montarSegmentos();
    if (!segmentos) return;

    const valor = this.form.getRawValue();
    const request: ComunicadoRequest = {
      titulo: valor.titulo.trim(),
      resumo: valor.resumo.trim() || undefined,
      conteudo: valor.conteudo,
      tipo: valor.tipo,
      apresentacao: valor.apresentacao,
      videoUrl: valor.videoUrl.trim() || undefined,
      acaoRotulo: valor.acaoRotulo.trim() || undefined,
      acaoDeeplink: valor.acaoDeeplink.trim() || undefined,
      fixado: valor.fixado,
      exigeConfirmacao: valor.exigeConfirmacao,
      notificarNoSininho: valor.notificarNoSininho,
      notificarPorEmail: valor.notificarPorEmail,
      prioridadeNotificacao: valor.notificarNoSininho
        ? valor.prioridadeNotificacao ?? undefined
        : undefined,
      // A lista substitui a anterior por inteiro — sempre o conjunto completo da tela.
      segmentos
    };

    this.salvando.set(true);
    try {
      const comunicado = atual
        ? await firstValueFrom(this.service.atualizar(atual.id, request))
        : await firstValueFrom(this.service.criar(request));

      this.gravado.set(comunicado);
      this.activeStep.set(5);
      this.sucesso(atual ? 'Comunicado atualizado.' : 'Rascunho criado.');
      await this.enviarPendentes(comunicado.id);
    } catch (erro) {
      this.erro(erro);
    } finally {
      this.salvando.set(false);
    }
  }

  // -------------------------------------------------------------------------
  // Anexos — o endpoint exige o comunicado já criado
  // -------------------------------------------------------------------------

  protected selecionarArquivos(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const novos = Array.from(input.files ?? []);
    input.value = '';

    const validos: ArquivoPendente[] = [];
    for (const file of novos) {
      const erro = this.validarArquivo(file);
      if (erro) {
        this.aviso('Arquivo não aceito', `${file.name}: ${erro}`);
        continue;
      }
      const repetido = this.arquivos().some(
        (item) => item.file.name === file.name && item.file.size === file.size
      );
      if (!repetido) validos.push({ file, status: 'pendente' });
    }
    if (validos.length === 0) return;

    this.arquivos.update((atuais) => [...atuais, ...validos]);

    // Na edição o comunicado já existe: envia na hora, sem esperar o Salvar.
    const destino = this.gravado() ?? this.original();
    if (destino) void this.enviarPendentes(destino.id);
  }

  protected removerArquivo(indice: number): void {
    this.arquivos.update((arquivos) => arquivos.filter((_, posicao) => posicao !== indice));
  }

  protected async tentarNovamente(indice: number): Promise<void> {
    const destino = this.gravado() ?? this.original();
    const arquivo = this.arquivos()[indice];
    if (!destino || !arquivo || arquivo.status === 'enviando') return;
    await this.enviarArquivo(destino.id, indice);
  }

  protected baixarAnexoGravado(anexo: AnexoComunicadoResponse): void {
    const destino = this.gravado() ?? this.original();
    if (!destino) return;
    this.service.baixarAnexo(destino.id, anexo.id).subscribe({
      next: (blob) => FileDownloadUtil.downloadBlob(blob, anexo.nomeArquivo),
      error: (erro) => this.erro(erro)
    });
  }

  protected removerAnexoGravado(anexo: AnexoComunicadoResponse): void {
    const destino = this.gravado() ?? this.original();
    if (!destino) return;
    this.service.removerAnexo(destino.id, anexo.id).subscribe({
      next: () => {
        const semAnexo = (item: ComunicadoResponse | null) =>
          item ? { ...item, anexos: item.anexos.filter((a) => a.id !== anexo.id) } : item;
        this.gravado.update(semAnexo);
        this.original.update(semAnexo);
      },
      error: (erro) => this.erro(erro)
    });
  }

  protected temFalhaDeAnexo(): boolean {
    return this.arquivos().some((arquivo) => arquivo.status === 'erro');
  }

  private async enviarPendentes(idComunicado: string): Promise<void> {
    this.enviandoAnexo.set(true);
    for (let indice = 0; indice < this.arquivos().length; indice += 1) {
      if (this.arquivos()[indice].status === 'pendente') {
        await this.enviarArquivo(idComunicado, indice);
      }
    }
    this.enviandoAnexo.set(false);
  }

  private async enviarArquivo(idComunicado: string, indice: number): Promise<void> {
    this.atualizarArquivo(indice, { status: 'enviando', erro: undefined });
    const arquivo = this.arquivos()[indice];
    try {
      const anexo = await firstValueFrom(this.service.anexar(idComunicado, arquivo.file));
      this.atualizarArquivo(indice, { status: 'concluido', erro: undefined });
      const comAnexo = (item: ComunicadoResponse | null) =>
        item ? { ...item, anexos: [...item.anexos, anexo] } : item;
      this.gravado.update(comAnexo);
      this.original.update(comAnexo);
    } catch (erro) {
      this.atualizarArquivo(indice, { status: 'erro', erro: getErrorMessage(erro) });
      this.messages.add({
        severity: 'error',
        summary: `Falha ao enviar ${arquivo.file.name}`,
        detail: 'O comunicado foi mantido. Você pode reenviar somente este arquivo.'
      });
    }
  }

  private atualizarArquivo(indice: number, mudanca: Partial<ArquivoPendente>): void {
    this.arquivos.update((arquivos) =>
      arquivos.map((arquivo, posicao) =>
        posicao === indice ? { ...arquivo, ...mudanca } : arquivo
      )
    );
  }

  private validarArquivo(file: File): string | null {
    if (file.size > ANEXO_LIMITE_BYTES) return 'o tamanho máximo é 15 MB';
    if (!ANEXO_TIPOS.includes(file.type)) {
      return 'apenas imagens (JPEG, PNG, WEBP, GIF) ou PDF — vídeo entra pelo campo de link';
    }
    return null;
  }

  // -------------------------------------------------------------------------
  // Conclusão
  // -------------------------------------------------------------------------

  protected irParaPublicacao(): void {
    const comunicado = this.gravado();
    this.router.navigate(['/notificacoes/admin/comunicados'], {
      queryParams: comunicado ? { publicar: comunicado.id } : undefined
    });
  }

  protected tamanho(bytes: number): string {
    return formatarTamanhoArquivo(bytes);
  }

  protected iconeTipo(tipo: TipoComunicado): string {
    return TIPO_COMUNICADO_ICONES[tipo] ?? 'pi pi-megaphone';
  }

  protected classesTipo(tipo: TipoComunicado): string {
    return TIPO_COMUNICADO_CLASSES[tipo] ?? 'bg-slate-100 text-slate-600 border-slate-200';
  }

  protected iconeApresentacao(apresentacao: ApresentacaoComunicado): string {
    if (apresentacao === 'MODAL') return 'pi pi-window-maximize';
    if (apresentacao === 'BANNER') return 'pi pi-bars';
    return 'pi pi-clone';
  }

  // -------------------------------------------------------------------------
  // Apoio
  // -------------------------------------------------------------------------

  private novoSegmento(
    tipoAlvo: TipoAlvoSegmento,
    idAlvo: string | null = null,
    valorAlvo: string | null = null
  ): SegmentoForm {
    return new FormGroup({
      tipoAlvo: new FormControl<TipoAlvoSegmento>(tipoAlvo, {
        nonNullable: true,
        validators: Validators.required
      }),
      idAlvo: new FormControl<string | null>(idAlvo),
      valorAlvo: new FormControl<string | null>(valorAlvo)
    });
  }

  private montarSegmentos() {
    const segmentos = this.segmentos.controls.map((grupo) => grupo.getRawValue());

    if (segmentos.length === 0) {
      this.aviso('Público-alvo obrigatório', 'Informe ao menos um segmento para o comunicado.');
      return null;
    }

    const incompleto = segmentos.some(
      (segmento) =>
        ((segmento.tipoAlvo === 'PERFIL' || segmento.tipoAlvo === 'USUARIO') && !segmento.idAlvo) ||
        (segmento.tipoAlvo === 'TIPO_USUARIO' && !segmento.valorAlvo)
    );
    if (incompleto) {
      this.aviso(
        'Segmento incompleto',
        'Cada segmento precisa do alvo correspondente ao tipo escolhido.'
      );
      return null;
    }

    const chaves = segmentos.map(
      (segmento) => `${segmento.tipoAlvo}|${segmento.idAlvo ?? ''}|${segmento.valorAlvo ?? ''}`
    );
    if (new Set(chaves).size !== chaves.length) {
      this.aviso('Segmento duplicado', 'Remova as linhas repetidas do público-alvo.');
      return null;
    }

    return segmentos.map((segmento) => ({
      tipoAlvo: segmento.tipoAlvo,
      idAlvo: segmento.idAlvo ?? undefined,
      valorAlvo: segmento.valorAlvo ?? undefined
    }));
  }

  private aviso(summary: string, detail: string): void {
    this.messages.add({ severity: 'warn', summary, detail });
  }

  private sucesso(detail: string): void {
    this.messages.add({ severity: 'success', summary: 'Operação concluída', detail });
  }

  private erro(erro: unknown): void {
    this.messages.add({
      severity: 'error',
      summary: 'Não foi possível concluir',
      detail: getErrorMessage(erro)
    });
  }
}
