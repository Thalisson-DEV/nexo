import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, HostListener, OnDestroy, OnInit, inject, signal, ChangeDetectorRef } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { FileUploadModule } from 'primeng/fileupload';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { SkeletonModule } from 'primeng/skeleton';
import { StepperModule } from 'primeng/stepper';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { finalize, forkJoin } from 'rxjs';
import {
  EvidenciaGovernanca,
  NormalizacaoAtaGovernanca,
  ParticipacaoReuniaoGovernanca,
  ReuniaoGovernancaResumo,
  VersaoAtaGovernanca
} from '../../../core/models/governanca-sst.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { GovernancaSstService } from '../../../core/services/governanca-sst.service';
import { FileDownloadUtil } from '../../../core/utils/file-download.util';

type Transcricao = { id: string; numero: number; origem: string; conteudo: string; confirmada: boolean; dataCriacao: string };

@Component({
  selector: 'app-reuniao-governanca',
  standalone: true,
  imports: [CommonModule, FormsModule, BreadcrumbModule, ButtonModule, FileUploadModule, InputTextModule, SelectModule,
    SkeletonModule, StepperModule, TableModule, TagModule, TextareaModule, ToastModule, TooltipModule],
  providers: [MessageService],
  templateUrl: './reuniao-governanca.component.html',
  styleUrls: ['./reuniao-governanca.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReuniaoGovernancaComponent implements OnInit, OnDestroy {
  private readonly api = inject(GovernancaSstService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly cdr = inject(ChangeDetectorRef);
  readonly id = this.route.snapshot.paramMap.get('id')!;
  readonly carregando = signal(true);
  readonly processando = signal<string | null>(null);
  readonly expandedNormalizacao = signal<string | null>(null);
  readonly erro = signal<string | null>(null);
  readonly reuniao = signal<ReuniaoGovernancaResumo | null>(null);
  readonly participantes = signal<ParticipacaoReuniaoGovernanca[]>([]);
  readonly fotos = signal<EvidenciaGovernanca[]>([]);
  readonly transcricoes = signal<Transcricao[]>([]);
  readonly normalizacoes = signal<NormalizacaoAtaGovernanca[]>([]);
  readonly atas = signal<VersaoAtaGovernanca[]>([]);
  readonly capabilities = signal<any>({ geracaoIaDisponivel: false, normalizacaoIaDisponivel: false, geracaoManualDisponivel: true });
  readonly gravando = signal(false);
  readonly activeStep = signal(1);
  transcricao = '';
  instrucaoCorrecao = '';
  conteudoManual = '';
  legendaFoto = '';
  ataSelecionada?: string;
  secretariaSelecionada?: string;
  private reconhecimento: any;
  private sujo = false;
  private usouDitado = false;
  readonly presencas = [
    { label: 'Presente', value: 'PRESENTE' },
    { label: 'Ausência justificada', value: 'AUSENTE_JUSTIFICADA' },
    { label: 'Ausência não justificada', value: 'AUSENTE_NAO_JUSTIFICADA' }
  ];

  ngOnInit(): void { this.restaurarRascunho(); this.carregar(); }
  ngOnDestroy(): void { this.reconhecimento?.stop?.(); }

  podeEditar(): boolean {
    const s = this.reuniao()?.status;
    return s === 'AGENDADA' || s === 'EM_REALIZACAO' || s === 'AGUARDANDO_ATA';
  }

  @HostListener('window:beforeunload', ['$event'])
  protegerSaida(event: BeforeUnloadEvent): void { if (this.sujo) event.preventDefault(); }

  carregar(): void {
    this.carregando.set(true); this.erro.set(null);
    forkJoin({ reuniao: this.api.buscarReuniao(this.id), participantes: this.api.listarParticipacoes(this.id),
      fotos: this.api.listarFotosReuniao(this.id), transcricoes: this.api.listarTranscricoes(this.id),
      normalizacoes: this.api.listarNormalizacoes(this.id), atas: this.api.listarVersoesAta(this.id), capabilities: this.api.buscarCapabilitiesReuniao() })
      .pipe(finalize(() => this.carregando.set(false))).subscribe({
        next: data => {
          this.reuniao.set(data.reuniao); this.participantes.set(data.participantes); this.fotos.set(data.fotos);
          this.transcricoes.set(data.transcricoes as Transcricao[]); this.normalizacoes.set(data.normalizacoes);
          this.atas.set(data.atas); this.capabilities.set(data.capabilities); 
          const aprovada = data.atas.find(a => a.status === 'APROVADA');
          if (aprovada) {
            this.ataSelecionada = aprovada.id;
          } else if (!this.ataSelecionada || !data.atas.some(a => a.id === this.ataSelecionada)) {
            this.ataSelecionada = data.atas[0]?.id;
          }
          this.aoSelecionarAta();
          this.activeStep.set(this.etapaAtual());
        }, error: error => this.erro.set(getErrorMessage(error))
      });
  }

  voltar(): void {
    const tipo = this.reuniao()?.tipoCorpo ?? this.route.snapshot.data['tipoCorpo'] ?? 'CIPA';
    this.router.navigate(['/sst/governanca/reunioes'], { queryParams: { tipo } });
  }
  iniciar(): void { this.comando('inicio', () => this.api.executarComandoReuniao(this.id, 'inicio', {})); }

  salvarPresenca(item: ParticipacaoReuniaoGovernanca): void {
    if (item.presenca.includes('AUSENTE') && !item.justificativaAusencia?.trim()) {
      this.messages.add({ severity: 'warn', summary: 'Informe o motivo da ausência', detail: `Explique por que ${item.nome} não participou antes de salvar.` });
      return;
    }
    this.processando.set(`presenca-${item.id}`);
    this.api.registrarPresenca(this.id, item.id, { presenca: item.presenca, justificativa: item.justificativaAusencia })
      .pipe(finalize(() => this.processando.set(null))).subscribe({ next: atualizado => {
        this.participantes.update(lista => lista.map(p => p.id === atualizado.id ? atualizado : p));
      }, error: error => this.aviso(error) });
  }

  concluirPresencas(): void {
    const pendentes = this.participantes().filter(item => item.presenca === 'NAO_REGISTRADA');
    if (pendentes.length) {
      this.messages.add({ severity: 'warn', summary: 'Presenças incompletas', detail: `Ainda falta registrar ${pendentes.length} participante(s).` });
      return;
    }
    this.comando('presencas', () => this.api.concluirPresencas(this.id));
  }

  designarSecretaria(item: ParticipacaoReuniaoGovernanca): void {
    this.secretariaSelecionada = item.id;
    this.comando('secretaria', () => this.api.designarSecretario(this.id, 'PARTICIPANTE', item.id), false);
  }

  enviarFoto(event: any): void {
    const arquivo: File | undefined = event.files?.[0]; if (!arquivo) return;
    this.processando.set('foto');
    this.api.anexarFotoReuniao(this.id, arquivo, this.legendaFoto).pipe(finalize(() => this.processando.set(null)))
      .subscribe({ next: foto => { this.fotos.update(lista => [...lista, foto]); this.legendaFoto = ''; this.sucesso('Foto anexada'); }, error: e => this.aviso(e) });
  }

  baixarFoto(foto: EvidenciaGovernanca): void {
    this.api.baixarFotoReuniao(this.id, foto.id).subscribe({ next: blob => FileDownloadUtil.downloadBlob(blob, foto.nomeArquivo), error: e => this.aviso(e) });
  }

  removerFoto(foto: EvidenciaGovernanca): void {
    this.api.removerFotoReuniao(this.id, foto.id).subscribe({ next: () => this.fotos.update(lista => lista.filter(x => x.id !== foto.id)), error: e => this.aviso(e) });
  }

  alterarTranscricao(): void { this.sujo = true; localStorage.setItem(this.chaveRascunho(), this.transcricao); }

  ditar(): void {
    if (this.gravando()) { this.reconhecimento?.stop?.(); return; }
    const Ctor = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!Ctor) { this.messages.add({ severity: 'warn', summary: 'Ditado indisponível', detail: 'Este navegador não oferece reconhecimento de voz. Você ainda pode colar ou digitar a transcrição.' }); return; }
    this.reconhecimento = new Ctor(); this.reconhecimento.lang = 'pt-BR'; this.reconhecimento.continuous = true; this.reconhecimento.interimResults = true;
    
    let baseText = this.transcricao;
    this.reconhecimento.onstart = () => { baseText = this.transcricao; };
    this.reconhecimento.onresult = (event: any) => { 
      let interim = ''; let final = '';
      for (let i = event.resultIndex; i < event.results.length; i++) {
        if (event.results[i].isFinal) final += `${event.results[i][0].transcript} `;
        else interim += `${event.results[i][0].transcript} `;
      }
      if (final) baseText += final;
      this.transcricao = baseText + interim; 
      this.alterarTranscricao();
      this.cdr.markForCheck();
    };
    this.reconhecimento.onend = () => { this.transcricao = baseText; this.gravando.set(false); this.cdr.markForCheck(); }; 
    this.reconhecimento.onerror = () => { this.transcricao = baseText; this.gravando.set(false); this.cdr.markForCheck(); };
    this.reconhecimento.start(); this.usouDitado = true; this.gravando.set(true);
  }

  salvarTranscricao(): void {
    if (!this.transcricao.trim()) return;
    this.processando.set('transcricao');
    this.api.salvarTranscricao(this.id, { origem: this.usouDitado ? 'DITADO_NAVEGADOR' : 'DIGITADO', conteudo: this.transcricao.trim() })
      .subscribe({ next: (registro: any) => {
        this.transcricoes.update(lista => [...lista, registro]); 
        this.sucesso('Transcrição salva como nova revisão'); 
        this.sujo = false; localStorage.removeItem(this.chaveRascunho());
        this.confirmarTranscricao(registro);
      }, error: e => { this.processando.set(null); this.aviso(e); } });
  }

  confirmarTranscricao(item: Transcricao): void {
    this.processando.set('confirmar-transcricao'); 
    this.api.confirmarTranscricao(this.id, item.id)
      .subscribe({
        next: (atualizada: any) => { 
          this.sucesso('Transcrição confirmada'); 
          this.transcricoes.update(lista => lista.map(t => t.id === atualizada.id ? atualizada : t));
          this.carregarParaGeracao(atualizada);
        }, 
        error: (e: unknown) => { this.processando.set(null); this.aviso(e); }
      });
  }

  private carregarParaGeracao(transcricao: Transcricao): void {
    if (this.capabilities().geracaoIaDisponivel) {
      this.activeStep.set(4);
      this.gerarAta();
    } else {
      this.processando.set(null);
      this.activeStep.set(4);
    }
  }

  normalizar(): void {
    const revisao = [...this.transcricoes()].reverse().find(item => item.confirmada);
    if (!revisao) { this.messages.add({ severity: 'warn', summary: 'Falta a transcrição', detail: 'Salve e confirme uma revisão antes de aprimorar o texto.' }); return; }
    this.processando.set('normalizar');
    this.api.normalizarTranscricao(this.id, { idRevisaoTranscricao: revisao.id, clientRequestId: crypto.randomUUID() })
      .pipe(finalize(() => this.processando.set(null))).subscribe({ next: (registro: any) => { 
        if (registro.status === 'FALHA') {
           this.messages.add({ severity: 'warn', summary: 'Não foi possível normalizar', detail: this.rotuloFalha(registro.codigoFalha) });
           return;
        }
        this.normalizacoes.update(lista => [...lista, registro]); this.sucesso('Texto normalizado para sua conferência'); 
      }, error: e => this.aviso(e) });
  }

  confirmarNormalizacao(item: NormalizacaoAtaGovernanca): void {
    this.comando('confirmar-normalizacao', () => this.api.confirmarNormalizacao(this.id, item.id));
  }

  toggleNormalizacao(id: string): void {
    this.expandedNormalizacao.update(atual => atual === id ? null : id);
  }

  aoSelecionarAta(): void {
    this.conteudoManual = this.selecionada()?.conteudo || '';
  }

  gerarAta(): void {
    this.processando.set('gerar-ata');
    this.api.gerarAta(this.id, { clientRequestId: crypto.randomUUID(), idVersaoBase: this.ataSelecionada,
      instrucaoCorrecao: this.instrucaoCorrecao.trim() || undefined })
      .pipe(finalize(() => this.processando.set(null))).subscribe({ next: (tentativa: any) => {
        if (tentativa.status === 'FALHA' || !tentativa.versao) {
          this.messages.add({ severity: 'warn', summary: 'A ata não foi gerada', detail: this.rotuloFalha(tentativa.codigoFalha) });
          return;
        }
        this.atas.update(lista => [...lista, tentativa.versao]); 
        this.ataSelecionada = tentativa.versao.id;
        this.aoSelecionarAta();
        this.instrucaoCorrecao = ''; this.sucesso('Nova versão da ata gerada pela IA');
      }, error: e => this.aviso(e) });
  }

  gerarAtaManual(): void {
    if (!this.conteudoManual.trim()) return;
    this.processando.set('gerar-ata-manual');
    this.api.criarAtaManual(this.id, { conteudo: this.conteudoManual.trim() })
      .pipe(finalize(() => this.processando.set(null))).subscribe({ next: versao => {
        this.atas.update(lista => [...lista, versao]); 
        this.ataSelecionada = versao.id;
        this.aoSelecionarAta();
        this.sucesso('Alterações manuais salvas como nova versão');
      }, error: e => this.aviso(e) });
  }

  aprovarAta(): void { if (this.ataSelecionada) this.comando('aprovar-ata', () => this.api.aprovarAta(this.id, this.ataSelecionada!)); }

  gerarEBaixarDocx(): void {
    if (!this.ataSelecionada) return; this.processando.set('docx');
    this.api.gerarDocxAta(this.id, this.ataSelecionada).pipe(finalize(() => this.processando.set(null))).subscribe({ next: arquivo => {
      const idArquivo = String(arquivo['id']); const nome = String(arquivo['nomeArquivo'] ?? 'ata-reuniao.docx');
      this.api.baixarDocxAta(this.id, idArquivo).subscribe({ next: blob => FileDownloadUtil.downloadBlob(blob, nome), error: e => this.aviso(e) });
    }, error: e => this.aviso(e) });
  }

  enviarAssinada(event: any): void {
    const arquivo: File | undefined = event.files?.[0]; if (!arquivo || !this.ataSelecionada) return;
    this.comando('assinada', () => this.api.anexarCopiaAssinada(this.id, this.ataSelecionada!, arquivo));
  }

  finalizar(): void { this.comando('finalizar', () => this.api.finalizarReuniao(this.id)); }

  etapaAtual(): number {
    const status = this.reuniao()?.status;
    return status === 'AGENDADA' ? 1 : status === 'EM_REALIZACAO' ? 2 : status === 'AGUARDANDO_ATA' ? 3 : status === 'AGUARDANDO_ASSINATURAS' ? 4 : 5;
  }

  irParaEtapa(etapa: number): void { if (etapa <= this.etapaAtual()) this.activeStep.set(etapa); }

  selecionada(): VersaoAtaGovernanca | undefined { return this.atas().find(item => item.id === this.ataSelecionada); }

  rotulo(valor?: string): string {
    if (!valor) return 'Não informado';
    const rotulos: Record<string, string> = {
      AGENDADA: 'Agendada', EM_REALIZACAO: 'Em andamento', AGUARDANDO_ATA: 'Ata pendente',
      AGUARDANDO_ASSINATURAS: 'Aguardando assinaturas', FINALIZADA: 'Finalizada', CANCELADA: 'Cancelada',
      RASCUNHO: 'Rascunho', APROVADA: 'Aprovada', IA: 'Gerada com IA', MANUAL: 'Manual',
      EM_PROCESSAMENTO: 'Em processamento', CONFIRMADA: 'Conferida', FALHA: 'Falha'
    };
    return rotulos[valor] ?? valor.replaceAll('_', ' ').toLocaleLowerCase('pt-BR').replace(/^./, c => c.toLocaleUpperCase('pt-BR'));
  }

  rotuloFalha(codigo?: string): string {
    if (codigo === 'PROVEDOR_DESABILITADO') return 'A geração por IA está desabilitada no sistema.';
    if (codigo === 'NORMALIZADOR_DESABILITADO') return 'A normalização por IA está desabilitada no sistema.';
    return codigo || 'Falha desconhecida no processamento';
  }

  private comando(nome: string, operacao: () => any, recarregar = true): void {
    this.processando.set(nome); operacao().pipe(finalize(() => this.processando.set(null))).subscribe({
      next: () => { this.sucesso('Alteração salva'); if (recarregar) this.carregar(); }, error: (e: unknown) => this.aviso(e)
    });
  }
  private restaurarRascunho(): void { this.transcricao = localStorage.getItem(this.chaveRascunho()) ?? ''; this.sujo = !!this.transcricao; }
  private chaveRascunho(): string { return `sst-governanca-reuniao-${this.id}-transcricao`; }
  private sucesso(detail: string): void { this.messages.add({ severity: 'success', summary: 'Tudo certo', detail }); }
  private aviso(error: unknown): void { this.messages.add({ severity: 'error', summary: 'Não foi possível concluir', detail: getErrorMessage(error), life: 7000 }); }
}
