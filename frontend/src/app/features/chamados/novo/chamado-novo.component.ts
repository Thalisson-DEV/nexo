import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { SelectModule } from 'primeng/select';
import { StepperModule } from 'primeng/stepper';
import { TextareaModule } from 'primeng/textarea';
import { firstValueFrom } from 'rxjs';
import {
  Chamado,
  ChamadoPrioridade,
  CriarChamadoRequest,
  TopicoChamado
} from '../../../core/models/chamado.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { ChamadosService } from '../../../core/services/chamados.service';
import { CHAMADO_PRIORIDADE_LABELS, PRIORIDADE_OPTIONS, formatarBytes } from '../chamados-ui';

type UploadStatus = 'pendente' | 'enviando' | 'concluido' | 'erro';
interface ArquivoPendente {
  file: File;
  status: UploadStatus;
  erro?: string;
}

@Component({
  selector: 'app-chamado-novo',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    BreadcrumbModule,
    ButtonModule,
    InputTextModule,
    ProgressSpinnerModule,
    SelectModule,
    StepperModule,
    TextareaModule
  ],
  templateUrl: './chamado-novo.component.html',
  styleUrl: './chamado-novo.component.css'
})
export class ChamadoNovoComponent implements OnInit {
  private readonly service = inject(ChamadosService);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);

  protected readonly breadcrumb = [
    { icon: 'pi pi-home', routerLink: '/home' },
    { label: 'Chamados', routerLink: '/chamados/meus' },
    { label: 'Novo chamado' }
  ];
  protected readonly topicos = signal<TopicoChamado[]>([]);
  protected readonly arquivos = signal<ArquivoPendente[]>([]);
  protected readonly loading = signal(false);
  protected readonly salvando = signal(false);
  protected readonly criado = signal<Chamado | null>(null);
  protected readonly activeStep = signal(1);
  protected readonly prioridadeOptions = [
    { label: 'Usar prioridade padrão do tópico', value: null },
    ...PRIORIDADE_OPTIONS
  ];
  protected readonly formatarBytes = formatarBytes;
  protected readonly form = new FormGroup({
    topicoId: new FormControl('', { nonNullable: true, validators: Validators.required }),
    assunto: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(150)]
    }),
    descricao: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(10), Validators.maxLength(5000)]
    }),
    prioridade: new FormControl<ChamadoPrioridade | null>(null)
  });

  ngOnInit(): void {
    this.loading.set(true);
    this.service.listarTopicosAtivos().subscribe({
      next: (topicos) => {
        this.topicos.set(topicos);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.messages.add({
          severity: 'error',
          summary: 'Não foi possível carregar os tópicos',
          detail: getErrorMessage(error)
        });
      }
    });
  }

  protected topicoSelecionado(): TopicoChamado | undefined {
    return this.topicos().find((topico) => topico.id === this.form.controls.topicoId.value);
  }

  protected prioridadePadrao(): string {
    const prioridade = this.topicoSelecionado()?.prioridadePadrao;
    return prioridade ? CHAMADO_PRIORIDADE_LABELS[prioridade] : '-';
  }

  protected prioridadeRevisao(): string {
    const prioridade = this.form.controls.prioridade.value;
    return prioridade ? CHAMADO_PRIORIDADE_LABELS[prioridade] : `Padrão do tópico: ${this.prioridadePadrao()}`;
  }

  protected selecionarArquivos(event: Event): void {
    const input = event.target as HTMLInputElement;
    const novos = Array.from(input.files ?? []);
    input.value = '';
    const atuais = this.arquivos();
    if (atuais.length + novos.length > 10) {
      this.aviso('Limite de anexos', 'Cada chamado aceita no máximo 10 arquivos.');
      return;
    }
    const validos: ArquivoPendente[] = [];
    for (const file of novos) {
      const erro = this.validarArquivo(file);
      if (erro) {
        this.aviso('Arquivo não aceito', `${file.name}: ${erro}`);
      } else if (!atuais.some((item) => item.file.name === file.name && item.file.size === file.size)) {
        validos.push({ file, status: 'pendente' });
      }
    }
    this.arquivos.set([...atuais, ...validos]);
  }

  protected removerArquivo(index: number): void {
    this.arquivos.update((arquivos) => arquivos.filter((_, position) => position !== index));
  }

  protected revisar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.activeStep.set(2);
  }

  protected voltar(): void {
    if (this.activeStep() === 2) {
      this.activeStep.set(1);
      return;
    }
    this.router.navigate(['/chamados/meus']);
  }

  protected async salvar(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.salvando() || this.criado()) return;
    this.salvando.set(true);
    const value = this.form.getRawValue();
    const request: CriarChamadoRequest = {
      topicoId: value.topicoId,
      assunto: value.assunto.trim(),
      descricao: value.descricao.trim(),
      ...(value.prioridade ? { prioridade: value.prioridade } : {})
    };
    try {
      const chamado = await firstValueFrom(this.service.criar(request));
      this.criado.set(chamado);
      this.form.disable();
      this.activeStep.set(3);
      await this.enviarPendentes(chamado.id);
      if (!this.temFalhas()) {
        this.messages.add({
          severity: 'success',
          summary: 'Chamado aberto',
          detail: `Protocolo ${chamado.protocolo} criado com sucesso.`
        });
      }
    } catch (error) {
      this.messages.add({
        severity: 'error',
        summary: 'Não foi possível abrir o chamado',
        detail: getErrorMessage(error)
      });
    } finally {
      this.salvando.set(false);
    }
  }

  protected async tentarNovamente(index: number): Promise<void> {
    const chamado = this.criado();
    const arquivo = this.arquivos()[index];
    if (!chamado || !arquivo || arquivo.status === 'enviando') return;
    await this.enviarArquivo(chamado.id, index);
  }

  protected irParaChamado(): void {
    const chamado = this.criado();
    if (chamado) this.router.navigate(['/chamados/meus', chamado.id]);
  }

  protected temFalhas(): boolean {
    return this.arquivos().some((arquivo) => arquivo.status === 'erro');
  }

  private async enviarPendentes(chamadoId: string): Promise<void> {
    for (let index = 0; index < this.arquivos().length; index += 1) {
      if (this.arquivos()[index].status === 'pendente') {
        await this.enviarArquivo(chamadoId, index);
      }
    }
  }

  private async enviarArquivo(chamadoId: string, index: number): Promise<void> {
    this.atualizarArquivo(index, { status: 'enviando', erro: undefined });
    const arquivo = this.arquivos()[index];
    try {
      await firstValueFrom(this.service.anexar(chamadoId, arquivo.file));
      this.atualizarArquivo(index, { status: 'concluido', erro: undefined });
    } catch (error) {
      const mensagem = getErrorMessage(error);
      this.atualizarArquivo(index, { status: 'erro', erro: mensagem });
      this.messages.add({
        severity: 'error',
        summary: `Falha ao enviar ${arquivo.file.name}`,
        detail: 'O chamado foi mantido. Você pode tentar enviar somente este arquivo novamente.'
      });
    }
  }

  private atualizarArquivo(index: number, changes: Partial<ArquivoPendente>): void {
    this.arquivos.update((arquivos) =>
      arquivos.map((arquivo, position) => (position === index ? { ...arquivo, ...changes } : arquivo))
    );
  }

  private validarArquivo(file: File): string | null {
    if (file.size > 15 * 1024 * 1024) return 'o tamanho máximo é 15 MB';
    const extensao = file.name.split('.').pop()?.toLowerCase();
    const aceitas = ['jpg', 'jpeg', 'png', 'webp', 'pdf', 'txt', 'log', 'doc', 'docx', 'xls', 'xlsx'];
    return extensao && aceitas.includes(extensao) ? null : 'formato não permitido';
  }

  private aviso(summary: string, detail: string): void {
    this.messages.add({ severity: 'warn', summary, detail });
  }
}
