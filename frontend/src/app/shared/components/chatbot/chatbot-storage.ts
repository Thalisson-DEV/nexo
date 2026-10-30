import { MensagemPendente } from '../../../core/models/assistente.models';

export class ChatbotStorage {
  constructor(private readonly currentUserId: () => string | undefined) {}

  readConversationId(): string | null {
    return localStorage.getItem(this.key('conversa'));
  }

  saveConversationId(conversationId: string): void {
    localStorage.setItem(this.key('conversa'), conversationId);
  }

  clearConversationId(): void {
    localStorage.removeItem(this.key('conversa'));
  }

  readPendingMessage(): MensagemPendente | null {
    try {
      const stored = sessionStorage.getItem(this.key('pendente'));
      return stored ? JSON.parse(stored) as MensagemPendente : null;
    } catch {
      sessionStorage.removeItem(this.key('pendente'));
      return null;
    }
  }

  savePendingMessage(pending: MensagemPendente): void {
    sessionStorage.setItem(this.key('pendente'), JSON.stringify(pending));
  }

  clearPendingMessage(clientMessageId?: string): boolean {
    const pending = this.readPendingMessage();
    if (clientMessageId && pending?.clientMessageId !== clientMessageId) return false;
    sessionStorage.removeItem(this.key('pendente'));
    return !!pending;
  }

  readDraft(): string {
    return sessionStorage.getItem(this.key('rascunho')) ?? '';
  }

  saveDraft(content: string): void {
    if (content.trim()) sessionStorage.setItem(this.key('rascunho'), content);
    else sessionStorage.removeItem(this.key('rascunho'));
  }

  clearTransientState(): void {
    sessionStorage.removeItem(this.key('pendente'));
    sessionStorage.removeItem(this.key('rascunho'));
  }

  private key(type: 'conversa' | 'pendente' | 'rascunho'): string {
    return `ces_assistente_${type}_${this.currentUserId() ?? 'anonimo'}`;
  }
}
