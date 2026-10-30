import { CommonModule } from '@angular/common';
import {
  AfterViewChecked,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  ViewChild,
  computed,
  inject,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { EMPTY, Subject, Subscription, catchError, switchMap, take, takeUntil, timer } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { AvatarModule } from 'primeng/avatar';
import { SkeletonModule } from 'primeng/skeleton';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import {
  AssistenteAcao,
  AssistenteConversa,
  AssistenteMensagem,
  MensagemPendente
} from '../../../core/models/assistente.models';
import { AssistenteService } from '../../../core/services/assistente.service';
import { AuthService } from '../../../core/services/auth.service';
import {
  formatAssistenteFieldName,
  formatAssistenteSource,
  formatAssistenteValue,
  getEvidencePeriod,
  getEvidenceRows,
  getRowEntries
} from '../../../core/utils/assistente-presentation.util';
import {
  CHAT_SUGGESTIONS,
  ChatSuggestion
} from './chatbot.config';
import {
  ResolvedChatNavigation,
  resolveChatNavigation
} from './chatbot-navigation.util';
import { ChatbotStorage } from './chatbot-storage';
import { AssistantRichContentComponent } from './assistant-rich-content.component';
@Component({
  selector: 'app-chatbot',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    AvatarModule,
    AssistantRichContentComponent,
    ButtonModule,
    ConfirmDialogModule,
    SkeletonModule,
    TextareaModule,
    TooltipModule
  ],
  providers: [ConfirmationService],
  templateUrl: './chatbot.component.html',
  styleUrls: [
    './chatbot.component.css',
    './chatbot-welcome.css',
    './chatbot-messages.css',
    './chatbot-conversation.css',
    './chatbot-controls.css',
    './chatbot-mobile.css'
  ]
})
export class ChatbotComponent implements AfterViewChecked, OnDestroy {
  @ViewChild('scrollContainer') private scrollContainer?: ElementRef<HTMLElement>;
  @ViewChild('messageInput') private messageInput?: ElementRef<HTMLTextAreaElement>;
  private readonly assistenteService = inject(AssistenteService);
  private readonly auth = inject(AuthService);
  private readonly storage = new ChatbotStorage(() => this.auth.currentUser()?.id);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly router = inject(Router);
  private readonly destroy$ = new Subject<void>();
  private readonly pollSubscriptions = new Map<string, Subscription>();
  private shouldScroll = false;
  protected readonly isOpen = signal(false);
  protected readonly isBootstrapping = signal(false);
  protected readonly isSending = signal(false);
  protected readonly isRestarting = signal(false);
  protected readonly conversation = signal<AssistenteConversa | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly transportError = signal<MensagemPendente | null>(null);
  protected readonly restartError = signal<string | null>(null);
  protected readonly liveAnnouncement = signal('');
  protected readonly newMessage = signal('');
  protected readonly messages = computed(() => this.conversation()?.mensagens ?? []);
  protected readonly hasProcessingMessage = computed(() =>
    this.messages().some(
      message => message.papel === 'ASSISTENTE' && message.status === 'PROCESSANDO'
    )
  );
  protected readonly canSend = computed(
    () =>
      !!this.newMessage().trim() &&
      !!this.conversation() &&
      !this.isBootstrapping() &&
      !this.isSending() &&
      !this.isRestarting() &&
      !this.hasProcessingMessage()
  );
  protected readonly evidencePeriod = getEvidencePeriod;
  protected readonly evidenceRows = getEvidenceRows;
  protected readonly rowEntries = getRowEntries;
  protected readonly formatFieldName = formatAssistenteFieldName;
  protected readonly formatSource = formatAssistenteSource;
  protected readonly formatValue = formatAssistenteValue;
  protected readonly availableSuggestions = computed(() => {
    const authorities = new Set(this.auth.currentUser()?.permissoes ?? []);
    return CHAT_SUGGESTIONS.filter(suggestion =>
      suggestion.anyAuthority.some(authority => authorities.has(authority))
    );
  });
  constructor() {
    this.newMessage.set(this.storage.readDraft());
  }
  ngAfterViewChecked(): void {
    if (!this.shouldScroll || !this.scrollContainer) return;
    const container = this.scrollContainer.nativeElement;
    container.scrollTop = container.scrollHeight;
    this.shouldScroll = false;
  }
  ngOnDestroy(): void {
    this.persistDraft();
    this.destroy$.next();
    this.destroy$.complete();
    this.pollSubscriptions.forEach(subscription => subscription.unsubscribe());
  }
  protected toggleChat(): void {
    const opening = !this.isOpen();
    this.isOpen.set(opening);
    if (opening && !this.conversation() && !this.isBootstrapping()) {
      this.loadOrCreateConversation();
    }
    const shouldFocus = typeof window.matchMedia === 'function' &&
      window.matchMedia('(min-width: 769px) and (pointer: fine)').matches;
    if (opening && shouldFocus) {
      window.setTimeout(() => this.messageInput?.nativeElement.focus(), 0);
    }
  }
  protected sendMessage(): void {
    const content = this.newMessage().trim();
    if (!content || !this.canSend()) return;
    this.newMessage.set('');
    this.persistDraft();
    this.resetTextareaHeight();
    this.submitQuestion(content, this.createClientMessageId());
  }

  protected confirmRestart(): void {
    if (!this.conversation() || this.isSending() || this.isRestarting() || this.hasProcessingMessage()) return;
    this.confirmationService.confirm({
      key: 'assistant-new-chat',
      header: 'Começar uma nova conversa?',
      message: 'Esta conversa será encerrada aqui. O Copiloto começará sem o contexto das mensagens anteriores.',
      icon: 'pi pi-comments',
      acceptLabel: 'Começar nova conversa',
      rejectLabel: 'Continuar aqui',
      accept: () => this.restartConversation()
    });
  }

  protected retryTransport(): void {
    const pending = this.transportError();
    if (!pending || this.isSending()) return;
    this.submitQuestion(pending.conteudo, pending.clientMessageId, false);
  }

  protected retryAssistant(message: AssistenteMensagem): void {
    const question = this.findQuestionFor(message.clientMessageId);
    if (!question || this.isSending() || this.hasProcessingMessage()) return;
    this.submitQuestion(question, this.createClientMessageId());
  }

  protected useSuggestion(suggestion: ChatSuggestion): void {
    if (!this.conversation() || this.hasProcessingMessage()) return;
    this.newMessage.set(suggestion.prompt);
    this.persistDraft();
    this.messageInput?.nativeElement.focus();
  }

  protected handleEnter(event: Event): void {
    const keyboardEvent = event as KeyboardEvent;
    if (keyboardEvent.shiftKey) return;
    keyboardEvent.preventDefault();
    this.sendMessage();
  }

  protected onDraftChange(content: string): void {
    this.newMessage.set(content);
    this.persistDraft();
  }

  protected adjustTextareaHeight(target: EventTarget | null): void {
    if (!(target instanceof HTMLTextAreaElement)) return;
    target.style.height = 'auto';
    target.style.height = `${Math.min(target.scrollHeight, 128)}px`;
  }

  protected isAssistant(message: AssistenteMensagem): boolean {
    return message.papel === 'ASSISTENTE';
  }

  protected statusLabel(message: AssistenteMensagem): string {
    const labels: Record<string, string> = {
      PROCESSANDO: 'Buscando sua resposta',
      CONCLUIDA: 'Resposta pronta',
      INDISPONIVEL: 'Tente novamente em instantes',
      DADOS_INSUFICIENTES: 'Tente perguntar de outro jeito'
    };
    return message.status ? labels[message.status] ?? message.status : 'Enviada';
  }

  protected canRetry(message: AssistenteMensagem): boolean {
    return message.status === 'INDISPONIVEL' && !!this.findQuestionFor(message.clientMessageId);
  }

  protected availableActions(message: AssistenteMensagem): AssistenteAcao[] {
    return (message.acoes ?? []).filter(action => !!this.resolveNavigation(action));
  }

  protected navigate(action: AssistenteAcao): void {
    const navigation = this.resolveNavigation(action);
    if (!navigation) return;
    void this.router.navigate([navigation.route], { queryParams: navigation.queryParams })
      .then(navigated => {
        if (navigated) this.isOpen.set(false);
      });
  }

  protected trackMessage(_index: number, message: AssistenteMensagem): string {
    return message.id;
  }

  private loadOrCreateConversation(): void {
    this.isBootstrapping.set(true);
    this.loadError.set(null);
    const storedId = this.storage.readConversationId();
    if (!storedId) {
      this.createConversation();
      return;
    }
    this.assistenteService.consultarConversa(storedId).pipe(takeUntil(this.destroy$)).subscribe({
      next: conversation => this.finishConversationLoad(conversation),
      error: (error: HttpErrorResponse) => {
        if (error.status === 404) {
          this.storage.clearConversationId();
          this.createConversation();
          return;
        }
        this.handleConversationLoadError(error);
      }
    });
  }

  private createConversation(): void {
    this.assistenteService.criarConversa().pipe(takeUntil(this.destroy$)).subscribe({
      next: conversation => this.finishConversationLoad(conversation),
      error: error => this.handleConversationLoadError(error)
    });
  }

  private restartConversation(): void {
    this.isRestarting.set(true);
    this.restartError.set(null);
    this.assistenteService.criarConversa().pipe(takeUntil(this.destroy$)).subscribe({
      next: conversation => {
        this.pollSubscriptions.forEach(subscription => subscription.unsubscribe());
        this.pollSubscriptions.clear();
        this.conversation.set(this.normalizeConversation(conversation));
        this.storage.saveConversationId(conversation.id);
        this.storage.clearTransientState();
        this.newMessage.set('');
        this.transportError.set(null);
        this.loadError.set(null);
        this.isRestarting.set(false);
        this.resetTextareaHeight();
        this.liveAnnouncement.set('Nova conversa iniciada.');
        this.shouldScroll = true;
        window.setTimeout(() => this.messageInput?.nativeElement.focus(), 0);
      },
      error: () => {
        this.isRestarting.set(false);
        this.restartError.set('Não consegui começar uma nova conversa agora. Tente novamente em instantes.');
        this.liveAnnouncement.set(this.restartError() ?? 'Não foi possível começar uma nova conversa.');
      }
    });
  }

  private finishConversationLoad(conversation: AssistenteConversa): void {
    this.conversation.set(this.normalizeConversation(conversation));
    this.storage.saveConversationId(conversation.id);
    this.isBootstrapping.set(false);
    this.loadError.set(null);
    this.shouldScroll = true;
    this.reconcilePendingMessage();
  }

  private handleConversationLoadError(error: HttpErrorResponse): void {
    this.isBootstrapping.set(false);
    this.loadError.set(
      error.status === 403
        ? 'Seu acesso atual não inclui o assistente.'
        : 'A conversa não carregou. Confira sua conexão e tente de novo.'
    );
    this.liveAnnouncement.set(this.loadError() ?? 'Falha ao carregar a conversa.');
  }

  private submitQuestion(content: string, clientMessageId: string, addUserMessage = true): void {
    const conversation = this.conversation();
    if (!conversation) return;
    this.isSending.set(true);
    this.transportError.set(null);
    const pending: MensagemPendente = { conversaId: conversation.id, clientMessageId, conteudo: content };
    this.storage.savePendingMessage(pending);
    if (addUserMessage) this.appendOptimisticUserMessage(pending);

    this.assistenteService.enviarMensagem(conversation.id, {
      clientMessageId,
      conteudo: content
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: response => {
        this.isSending.set(false);
        this.upsertAssistantMessage(response);
        if (response.status === 'PROCESSANDO') {
          this.startPolling(clientMessageId);
        } else {
          this.clearPendingMessage(clientMessageId);
          this.liveAnnouncement.set(this.statusLabel(response));
        }
      },
      error: () => {
        this.isSending.set(false);
        this.transportError.set(pending);
        this.liveAnnouncement.set(
          'Não foi possível confirmar o envio. A pergunta foi preservada para nova tentativa.'
        );
        this.shouldScroll = true;
      }
    });
  }

  private appendOptimisticUserMessage(pending: MensagemPendente): void {
    const conversation = this.conversation();
    if (!conversation) return;
    const alreadyExists = conversation.mensagens.some(
      message => message.papel === 'USUARIO' && message.clientMessageId === pending.clientMessageId
    );
    if (alreadyExists) return;
    const message: AssistenteMensagem = {
      id: `local-${pending.clientMessageId}`,
      clientMessageId: pending.clientMessageId,
      papel: 'USUARIO',
      conteudo: pending.conteudo,
      criadaEm: new Date().toISOString()
    };
    this.conversation.set({ ...conversation, mensagens: [...conversation.mensagens, message] });
    this.liveAnnouncement.set('Pergunta enviada. Estou buscando a resposta.');
    this.shouldScroll = true;
  }

  private upsertAssistantMessage(response: AssistenteMensagem): void {
    const conversation = this.conversation();
    if (!conversation) return;
    const index = conversation.mensagens.findIndex(
      message =>
        message.papel === 'ASSISTENTE' &&
        (message.id === response.id ||
          (!!response.clientMessageId && message.clientMessageId === response.clientMessageId))
    );
    const messages = [...conversation.mensagens];
    if (index >= 0) messages[index] = response;
    else messages.push(response);
    this.conversation.set({ ...conversation, mensagens: this.sortMessages(messages) });
    this.shouldScroll = true;
  }

  private startPolling(clientMessageId: string): void {
    if (this.pollSubscriptions.has(clientMessageId)) return;
    let attempts = 0;
    const subscription = timer(1500, 2000).pipe(
      take(30),
      switchMap(() => {
        const conversationId = this.conversation()?.id;
        return conversationId
          ? this.assistenteService.consultarConversa(conversationId).pipe(catchError(() => EMPTY))
          : EMPTY;
      }),
      takeUntil(this.destroy$)
    ).subscribe({
      next: conversation => {
        attempts += 1;
        this.conversation.set(this.normalizeConversation(conversation));
        const response = conversation.mensagens.find(
          message => message.papel === 'ASSISTENTE' && message.clientMessageId === clientMessageId
        );
        this.shouldScroll = true;
        if (response && response.status !== 'PROCESSANDO') {
          this.clearPendingMessage(clientMessageId);
          this.liveAnnouncement.set(this.statusLabel(response));
          this.stopPolling(clientMessageId);
        } else if (attempts === 30) {
          this.setPollingTimeout(clientMessageId);
        }
      },
      complete: () => {
        if (this.pollSubscriptions.has(clientMessageId)) this.setPollingTimeout(clientMessageId);
      }
    });
    this.pollSubscriptions.set(clientMessageId, subscription);
  }

  private setPollingTimeout(clientMessageId: string): void {
    const pending = this.storage.readPendingMessage();
    if (pending?.clientMessageId === clientMessageId) this.transportError.set(pending);
    this.liveAnnouncement.set('A resposta está levando mais tempo que o normal. Você pode verificar novamente.');
    this.stopPolling(clientMessageId);
  }

  private stopPolling(clientMessageId: string): void {
    this.pollSubscriptions.get(clientMessageId)?.unsubscribe();
    this.pollSubscriptions.delete(clientMessageId);
  }

  private reconcilePendingMessage(): void {
    const pending = this.storage.readPendingMessage();
    const conversation = this.conversation();
    if (!pending || !conversation || pending.conversaId !== conversation.id) return;
    const response = conversation.mensagens.find(
      message => message.papel === 'ASSISTENTE' && message.clientMessageId === pending.clientMessageId
    );
    if (response?.status === 'PROCESSANDO') {
      this.startPolling(pending.clientMessageId);
    } else if (response) {
      this.clearPendingMessage(pending.clientMessageId);
    } else {
      this.transportError.set(pending);
    }
  }
  private resolveNavigation(action: AssistenteAcao): ResolvedChatNavigation | null {
    return resolveChatNavigation(action, this.auth.currentUser()?.permissoes ?? []);
  }

  private findQuestionFor(clientMessageId?: string | null): string | null {
    if (!clientMessageId) return null;
    return this.messages().find(
      message => message.papel === 'USUARIO' && message.clientMessageId === clientMessageId
    )?.conteudo ?? null;
  }

  private normalizeConversation(conversation: AssistenteConversa): AssistenteConversa {
    return { ...conversation, mensagens: this.sortMessages(conversation.mensagens ?? []) };
  }

  private sortMessages(messages: AssistenteMensagem[]): AssistenteMensagem[] {
    return [...messages].sort(
      (first, second) => new Date(first.criadaEm).getTime() - new Date(second.criadaEm).getTime()
    );
  }

  private clearPendingMessage(clientMessageId: string): void {
    if (this.storage.clearPendingMessage(clientMessageId)) this.transportError.set(null);
  }

  private persistDraft(): void {
    this.storage.saveDraft(this.newMessage());
  }

  private resetTextareaHeight(): void {
    if (this.messageInput) this.messageInput.nativeElement.style.height = 'auto';
  }

  private createClientMessageId(): string {
    return globalThis.crypto?.randomUUID?.() ??
      `${Date.now()}-${Math.random().toString(16).slice(2)}-${Math.random().toString(16).slice(2)}`;
  }
}
