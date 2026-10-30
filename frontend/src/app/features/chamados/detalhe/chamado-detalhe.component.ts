import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmationService, MenuItem, MessageService } from 'primeng/api';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DialogModule } from 'primeng/dialog';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';
import { catchError, forkJoin, of } from 'rxjs';
import {
  AtualizarAtendimentoRequest,
  Chamado,
  ChamadoPrioridade,
  ChamadoStatus,
  EquipeChamado,
  EvidenciaChamado,
  InteracaoChamado
} from '../../../core/models/chamado.models';
import { UsuarioResponse } from '../../../core/models/iam.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { ChamadosService } from '../../../core/services/chamados.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { MencaoTextareaComponent } from '../../../shared/components/mencoes/mencao-textarea.component';
import {
  LIMITE_MARCACOES_POR_MENSAGEM,
  extrairMarcacoes
} from '../../../shared/components/mencoes/mencoes.util';
import { TextoMarcacoesComponent } from '../../../shared/components/mencoes/texto-marcacoes.component';
import {
  CHAMADO_PRIORIDADE_LABELS,
  CHAMADO_STATUS_LABELS,
  INTERACAO_LABELS,
  PRIORIDADE_OPTIONS,
  contagemSlaClass,
  contagemSlaLabel,
  formatarBytes,
  prioridadeClass
} from '../chamados-ui';
import { RemoteLookup } from '../../../core/utils/remote-lookup.util';

@Component({
  selector: 'app-chamado-detalhe',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [ConfirmationService],
  imports: [
    CommonModule,
    FormsModule,
    BreadcrumbModule,
    ButtonModule,
    CheckboxModule,
    ConfirmDialogModule,
    DialogModule,
    MencaoTextareaComponent,
    ProgressSpinnerModule,
    SelectModule,
    TextareaModule,
    TextoMarcacoesComponent,
    TooltipModule
  ],
  templateUrl: './chamado-detalhe.component.html',
  styleUrl: './chamado-detalhe.component.css'
})
export class ChamadoDetalheComponent implements OnInit {
  private readonly service = inject(ChamadosService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly confirmation = inject(ConfirmationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly id = this.route.snapshot.paramMap.get('id') ?? '';
  private readonly equipeLookup = new RemoteLookup<EquipeChamado>(
    {
      identify: (equipe) => equipe.id,
      selectedKeys: () => (this.equipeAtendimentoId ? [this.equipeAtendimentoId] : []),
      load: ({ query, page, size }) =>
        this.service.listarEquipes({ ativo: true, buscaGlobal: query, page, size, sort: 'nome,asc' })
    },
    this.destroyRef
  );
  private readonly usuarioLookup = new RemoteLookup<UsuarioResponse>(
    {
      identify: (usuario) => usuario.id,
      selectedKeys: () => (this.responsavelAtendimentoId ? [this.responsavelAtendimentoId] : []),
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

  protected readonly modoCentral = this.route.snapshot.data['modo'] === 'central';
  protected readonly chamado = signal<Chamado | null>(null);
  protected readonly interacoes = signal<InteracaoChamado[]>([]);
  protected readonly equipes = this.equipeLookup.items;
  protected readonly equipesLoading = this.equipeLookup.loading;
  protected readonly usuarios = this.usuarioLookup.items;
  protected readonly usuariosLoading = this.usuarioLookup.loading;
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly uploading = signal(false);
  protected readonly prioridadeOptions = PRIORIDADE_OPTIONS;
  protected readonly mensagem = signal('');
  protected readonly interno = signal(false);
  /** `detail` do 422 de marcação — fica junto ao composer, sem limpar o que foi digitado. */
  protected readonly erroMarcacao = signal<string | null>(null);
  protected dialogAtendimento = false;
  protected dialogReabertura = false;
  protected statusAtendimento: ChamadoStatus | null = null;
  protected prioridadeAtendimento: ChamadoPrioridade | null = null;
  protected equipeAtendimentoId: string | null = null;
  protected responsavelAtendimentoId: string | null = null;
  protected solucao = '';
  protected motivoReabertura = '';
  protected readonly statusLabel = (value: ChamadoStatus) => CHAMADO_STATUS_LABELS[value];
  protected readonly prioridadeLabel = (value: ChamadoPrioridade) =>
    CHAMADO_PRIORIDADE_LABELS[value];
  protected readonly prioridadeClass = prioridadeClass;
  protected readonly formatarBytes = formatarBytes;
  /**
   * Comentário interno: texto de circulação restrita.
   *
   * Vale tanto para o envio quanto para o autocomplete de `@` — com ele ligado, só aparece quem
   * tem `chamado:manage` e conseguiria abrir o texto.
   */
  protected readonly modoInterno = computed(
    () => this.modoCentral && (this.chamado()?.status === 'RESOLVIDO' || this.interno())
  );
  protected readonly breadcrumb: MenuItem[] = [
    { icon: 'pi pi-home', routerLink: '/home' },
    { label: 'Chamados' },
    {
      label: this.modoCentral ? 'Fila da central' : 'Meus chamados',
      routerLink: this.modoCentral ? '/chamados/fila' : '/chamados/meus'
    },
    { label: 'Detalhe' }
  ];

  ngOnInit(): void {
    this.carregar();
    if (this.modoCentral) this.carregarLookups();
  }

  protected voltar(): void {
    this.router.navigate([this.modoCentral ? '/chamados/fila' : '/chamados/meus']);
  }

  protected podeResponder(): boolean {
    const status = this.chamado()?.status;
    if (!status) return false;
    if (this.modoCentral) return !['FECHADO', 'CANCELADO'].includes(status);
    return ['ABERTO', 'EM_ATENDIMENTO', 'AGUARDANDO_SOLICITANTE'].includes(status);
  }

  protected podeAnexar(): boolean {
    const status = this.chamado()?.status;
    return !!status && !['FECHADO', 'CANCELADO'].includes(status) && (this.chamado()?.anexos?.length ?? 0) < 10;
  }

  protected podeCancelarMeu(): boolean {
    const status = this.chamado()?.status;
    return !this.modoCentral && (status === 'ABERTO' || status === 'AGUARDANDO_SOLICITANTE');
  }

  protected podeReabrirMeu(): boolean {
    const item = this.chamado();
    if (this.modoCentral || item?.status !== 'RESOLVIDO' || !item.dataResolucao) return false;
    const limite = new Date(item.dataResolucao).getTime() + item.prazoReaberturaHoras * 3_600_000;
    return Date.now() <= limite;
  }

  protected enviarMensagem(): void {
    const texto = this.mensagem().trim();
    if (!texto || this.saving()) return;
    if (texto.length > 5000) {
      this.aviso('Mensagem muito longa', 'Use no máximo 5.000 caracteres.');
      return;
    }
    // O servidor recusa a mensagem inteira acima do teto; barrar aqui poupa a ida e volta.
    if (extrairMarcacoes(texto).length > LIMITE_MARCACOES_POR_MENSAGEM) {
      this.erroMarcacao.set(
        `Marque no máximo ${LIMITE_MARCACOES_POR_MENSAGEM} pessoas por mensagem.`
      );
      return;
    }
    const interno = this.modoInterno();
    this.erroMarcacao.set(null);
    this.saving.set(true);
    const request = this.modoCentral
      ? this.service.adicionarInteracao(this.id, { mensagem: texto, interno })
      : this.service.responderComoSolicitante(this.id, texto);
    request.subscribe({
      next: () => {
        this.mensagem.set('');
        this.interno.set(false);
        this.saving.set(false);
        this.sucesso(interno ? 'Comentário interno registrado.' : 'Mensagem enviada.');
        this.carregar();
      },
      error: (error) => {
        // 422 é a invariante de marcação (visibilidade restrita ou teto de 10). Nada foi gravado:
        // preserve o texto para a pessoa remover ou trocar o `@` e reenviar.
        if (error?.status === 422) {
          this.saving.set(false);
          this.erroMarcacao.set(getErrorMessage(error));
          return;
        }
        this.tratarErro(error, 'Não foi possível enviar a mensagem');
      }
    });
  }

  protected abrirAtendimento(status?: ChamadoStatus): void {
    const item = this.chamado();
    if (!item) return;
    this.statusAtendimento = status ?? null;
    this.prioridadeAtendimento = item.prioridade;
    this.equipeAtendimentoId = item.equipeAtendimento.id;
    this.responsavelAtendimentoId = item.usuarioResponsavelId ?? null;
    this.solucao = status === 'RESOLVIDO' ? item.solucao ?? '' : '';
    this.service.buscarEquipe(item.equipeAtendimento.id).subscribe({
      next: (equipe) => {
        this.equipeLookup.setSeed([equipe]);
        if (equipe.membros?.length) {
          this.carregarUsuariosPorIds(equipe.membros);
        }
      },
      error: () => {}
    });
    this.dialogAtendimento = true;
  }

  protected salvarAtendimento(): void {
    const item = this.chamado();
    if (!item || this.saving()) return;
    if (this.statusAtendimento === 'RESOLVIDO' && !this.solucao.trim()) {
      this.aviso('Solução obrigatória', 'Descreva a solução antes de resolver o chamado.');
      return;
    }
    const request: AtualizarAtendimentoRequest = {};
    if (this.statusAtendimento) request.status = this.statusAtendimento;
    if (this.prioridadeAtendimento && this.prioridadeAtendimento !== item.prioridade) {
      request.prioridade = this.prioridadeAtendimento;
    }
    if (this.equipeAtendimentoId && this.equipeAtendimentoId !== item.equipeAtendimento.id) {
      request.equipeId = this.equipeAtendimentoId;
    }
    if (this.responsavelAtendimentoId && this.responsavelAtendimentoId !== item.usuarioResponsavelId) {
      request.responsavelId = this.responsavelAtendimentoId;
    }
    if (this.statusAtendimento === 'RESOLVIDO') request.solucao = this.solucao.trim();
    if (!Object.keys(request).length) {
      this.aviso('Nenhuma alteração', 'Altere ao menos um dado do atendimento.');
      return;
    }
    this.saving.set(true);
    this.service.atualizarAtendimento(this.id, request).subscribe({
      next: () => {
        this.saving.set(false);
        this.dialogAtendimento = false;
        this.statusAtendimento = null;
        this.prioridadeAtendimento = null;
        this.equipeAtendimentoId = null;
        this.responsavelAtendimentoId = null;
        this.solucao = '';
        this.sucesso('Atendimento atualizado.');
        this.carregar();
      },
      error: (error) => this.tratarErro(error, 'Não foi possível atualizar o atendimento')
    });
  }

  protected statusDisponiveis(): { label: string; value: ChamadoStatus }[] {
    const status = this.chamado()?.status;
    const map: Partial<Record<ChamadoStatus, ChamadoStatus[]>> = {
      ABERTO: ['EM_ATENDIMENTO', 'CANCELADO'],
      EM_ATENDIMENTO: ['AGUARDANDO_SOLICITANTE', 'RESOLVIDO', 'CANCELADO'],
      AGUARDANDO_SOLICITANTE: ['EM_ATENDIMENTO', 'RESOLVIDO', 'CANCELADO'],
      RESOLVIDO: ['FECHADO', 'EM_ATENDIMENTO']
    };
    return (status ? map[status] ?? [] : []).map((value) => ({
      value,
      label: CHAMADO_STATUS_LABELS[value]
    }));
  }

  protected responsaveisDisponiveis(): UsuarioResponse[] {
    const equipe = this.equipes().find((item) => item.id === this.equipeAtendimentoId);
    if (!equipe?.membros?.length) return this.usuarios();
    return this.usuarios().filter((usuario) => equipe.membros?.includes(usuario.id) ?? true);
  }

  protected confirmarCancelamento(): void {
    this.confirmation.confirm({
      header: 'Cancelar chamado',
      message: 'O cancelamento é definitivo. Deseja continuar?',
      icon: 'pi pi-exclamation-triangle',
      rejectLabel: 'Voltar',
      acceptLabel: 'Cancelar chamado',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.cancelarMeu()
    });
  }

  protected reabrirMeu(): void {
    const motivo = this.motivoReabertura.trim();
    if (!motivo || this.saving()) return;
    this.saving.set(true);
    this.service.reabrirMeu(this.id, motivo).subscribe({
      next: () => {
        this.saving.set(false);
        this.dialogReabertura = false;
        this.motivoReabertura = '';
        this.sucesso('Chamado reaberto.');
        this.carregar();
      },
      error: (error) => this.tratarErro(error, 'Não foi possível reabrir o chamado')
    });
  }

  protected selecionarAnexo(event: Event): void {
    const input = event.target as HTMLInputElement;
    const arquivo = input.files?.[0];
    input.value = '';
    if (!arquivo) return;
    const erro = this.validarArquivo(arquivo);
    if (erro) {
      this.aviso('Arquivo não aceito', erro);
      return;
    }
    this.uploading.set(true);
    this.service.anexar(this.id, arquivo).subscribe({
      next: () => {
        this.uploading.set(false);
        this.sucesso('Evidência adicionada.');
        this.carregar();
      },
      error: (uploadError) => {
        this.uploading.set(false);
        this.tratarErro(uploadError, 'Não foi possível anexar a evidência');
      }
    });
  }

  protected baixar(anexo: EvidenciaChamado): void {
    this.service.baixarAnexo(this.id, anexo.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = anexo.nomeArquivo;
        anchor.click();
        URL.revokeObjectURL(url);
      },
      error: (error) => this.tratarErro(error, 'Não foi possível baixar a evidência')
    });
  }

  protected nomeUsuario(id?: string | null): string {
    if (!id) return 'Não informado';
    return this.usuarios().find((usuario) => usuario.id === id)?.login ?? id;
  }

  protected interacaoLabel(item: InteracaoChamado): string {
    return INTERACAO_LABELS[item.tipo];
  }

  protected interacaoIcone(item: InteracaoChamado): string {
    const map: Partial<Record<InteracaoChamado['tipo'], string>> = {
      ABERTURA: 'pi pi-plus-circle',
      MENSAGEM_PUBLICA: 'pi pi-comment',
      COMENTARIO_INTERNO: 'pi pi-lock',
      ALTERACAO_STATUS: 'pi pi-sync',
      ALTERACAO_PRIORIDADE: 'pi pi-flag',
      ATRIBUICAO: 'pi pi-user',
      ENCAMINHAMENTO: 'pi pi-send',
      REABERTURA: 'pi pi-replay',
      CANCELAMENTO: 'pi pi-times-circle',
      EVIDENCIA_ADICIONADA: 'pi pi-paperclip'
    };
    return map[item.tipo] ?? 'pi pi-circle';
  }

  protected statusClass(status: ChamadoStatus): string {
    const classes: Record<ChamadoStatus, string> = {
      ABERTO: 'bg-blue-50 text-blue-600 dark:bg-blue-950/50 dark:text-blue-300',
      EM_ATENDIMENTO: 'bg-amber-50 text-amber-600 dark:bg-amber-950/50 dark:text-amber-300',
      AGUARDANDO_SOLICITANTE: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300',
      RESOLVIDO: 'bg-green-50 text-green-600 dark:bg-green-950/50 dark:text-green-300',
      FECHADO: 'bg-slate-200 text-slate-600 dark:bg-slate-800 dark:text-slate-400',
      CANCELADO: 'bg-red-50 text-red-600 dark:bg-red-950/50 dark:text-red-300'
    };
    return classes[status];
  }

  protected contagemSlaLabel(chamado: Chamado): string {
    return contagemSlaLabel(chamado);
  }

  protected contagemSlaClass(chamado: Chamado): string {
    return contagemSlaClass(chamado);
  }

  private carregar(): void {
    this.loading.set(true);
    forkJoin({
      chamado: this.service.buscar(this.id),
      interacoes: this.service.listarInteracoes(this.id)
    }).subscribe({
      next: ({ chamado, interacoes }) => {
        this.chamado.set(chamado);
        this.interacoes.set(interacoes);
        const equipeExistente = this.equipes().find((e) => e.id === chamado.equipeAtendimento.id);
        if (!equipeExistente) {
          this.equipeLookup.setSeed([{
            id: chamado.equipeAtendimento.id,
            codigo: chamado.equipeAtendimento.codigo,
            nome: chamado.equipeAtendimento.nome,
            membros: [],
            ativo: true,
            dataCriacao: ''
          }]);
        }
        if (this.modoCentral) {
          this.service.buscarEquipe(chamado.equipeAtendimento.id).subscribe({
            next: (equipe) => {
              this.equipeLookup.setSeed([equipe]);
              if (equipe.membros?.length) {
                this.carregarUsuariosPorIds(equipe.membros);
              }
            },
            error: () => {}
          });
        }
        this.carregarUsuariosPorIds([
          chamado.usuarioSolicitanteId,
          chamado.usuarioResponsavelId ?? '',
          ...interacoes.flatMap((interacao) => [
            interacao.usuarioAutorId,
            interacao.responsavelAnteriorId ?? '',
            interacao.responsavelNovoId ?? ''
          ])
        ]);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.messages.add({ severity: 'error', summary: 'Não foi possível carregar o chamado', detail: getErrorMessage(error) });
      }
    });
  }

  private carregarLookups(): void {
    this.equipeLookup.open();
    this.usuarioLookup.open();
  }

  protected openEquipes(): void { this.equipeLookup.open(); }
  protected searchEquipes(event: { filter?: string }): void {
    this.equipeLookup.search(event.filter ?? '');
  }
  protected loadMoreEquipes(): void { this.equipeLookup.loadNext(); }

  protected onEquipeAtendimentoChange(): void {
    if (!this.equipeAtendimentoId) return;
    const equipe = this.equipes().find((item) => item.id === this.equipeAtendimentoId);
    if (equipe?.membros?.length) {
      this.carregarUsuariosPorIds(equipe.membros);
    } else {
      this.service.buscarEquipe(this.equipeAtendimentoId).subscribe({
        next: (equipeCompleta) => {
          this.equipeLookup.setSeed([equipeCompleta]);
          if (equipeCompleta.membros?.length) {
            this.carregarUsuariosPorIds(equipeCompleta.membros);
          }
        },
        error: () => {}
      });
    }
  }

  protected openUsuarios(): void { this.usuarioLookup.open(); }
  protected searchUsuarios(event: { filter?: string }): void {
    this.usuarioLookup.search(event.filter ?? '');
  }
  protected loadMoreUsuarios(): void { this.usuarioLookup.loadNext(); }

  private carregarUsuariosPorIds(ids: readonly string[]): void {
    const carregados = new Set(this.usuarios().map((usuario) => usuario.id));
    const faltantes = [...new Set(ids)].filter((id) => id && !carregados.has(id));
    if (!faltantes.length) return;
    forkJoin(
      faltantes.map((id) => this.usuariosService.buscarUsuarioPorId(id).pipe(catchError(() => of(null))))
    ).subscribe((usuarios) =>
      this.usuarioLookup.setSeed(
        usuarios.filter((usuario): usuario is UsuarioResponse => usuario !== null)
      )
    );
  }

  private cancelarMeu(): void {
    this.saving.set(true);
    this.service.cancelarMeu(this.id).subscribe({
      next: () => {
        this.saving.set(false);
        this.sucesso('Chamado cancelado.');
        this.carregar();
      },
      error: (error) => this.tratarErro(error, 'Não foi possível cancelar o chamado')
    });
  }

  private tratarErro(error: any, summary: string): void {
    this.saving.set(false);
    if (error?.status === 409) {
      this.dialogAtendimento = false;
      this.carregar();
      this.aviso('O chamado foi atualizado por outra pessoa', 'Os dados foram recarregados. Revise antes de tentar novamente.');
      return;
    }
    this.messages.add({ severity: 'error', summary, detail: getErrorMessage(error) });
  }

  private validarArquivo(file: File): string | null {
    if (file.size > 15 * 1024 * 1024) return 'O tamanho máximo por arquivo é 15 MB.';
    const extensao = file.name.split('.').pop()?.toLowerCase();
    const aceitas = ['jpg', 'jpeg', 'png', 'webp', 'pdf', 'txt', 'log', 'doc', 'docx', 'xls', 'xlsx'];
    return extensao && aceitas.includes(extensao) ? null : 'Este formato não é permitido.';
  }

  private sucesso(detail: string): void {
    this.messages.add({ severity: 'success', summary: 'Operação concluída', detail });
  }

  private aviso(summary: string, detail: string): void {
    this.messages.add({ severity: 'warn', summary, detail });
  }
}
