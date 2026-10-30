import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { Router } from '@angular/router';
import { ConfirmationService } from 'primeng/api';
import { Subject, of, throwError } from 'rxjs';

import { AssistenteConversa, AssistenteMensagem } from '../../../core/models/assistente.models';
import { AssistenteService } from '../../../core/services/assistente.service';
import { AuthService } from '../../../core/services/auth.service';
import { ChatbotComponent } from './chatbot.component';

registerLocaleData(localePt);

describe('ChatbotComponent', () => {
  let fixture: ComponentFixture<ChatbotComponent>;
  let service: {
    criarConversa: ReturnType<typeof vi.fn>;
    consultarConversa: ReturnType<typeof vi.fn>;
    enviarMensagem: ReturnType<typeof vi.fn>;
  };
  let router: { navigate: ReturnType<typeof vi.fn> };
  let currentUser: WritableSignal<{ id: string; permissoes: string[] }>;

  const conversation: AssistenteConversa = {
    id: 'conversation-id',
    criadaEm: '2026-08-02T10:00:00-03:00',
    mensagens: []
  };

  beforeEach(async () => {
    localStorage.clear();
    sessionStorage.clear();
    service = {
      criarConversa: vi.fn(() => of(conversation)),
      consultarConversa: vi.fn(() => of(conversation)),
      enviarMensagem: vi.fn()
    };
    router = { navigate: vi.fn(() => Promise.resolve(true)) };
    currentUser = signal({
      id: 'user-id',
      permissoes: [
        'assistente:chat:use',
        'sst:dashboard:read',
        'sst:inspecao:read',
        'desconto:read',
        'desconto:solicitar'
      ]
    });

    await TestBed.configureTestingModule({
      imports: [ChatbotComponent],
      providers: [
        { provide: AssistenteService, useValue: service },
        {
          provide: AuthService,
          useValue: {
            currentUser
          }
        },
        { provide: Router, useValue: router }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ChatbotComponent);
    fixture.detectChanges();
  });

  it('exibe o aviso sobre conteúdo gerado por IA', () => {
    const disclaimer = fixture.nativeElement.querySelector('.assistant-disclaimer') as HTMLElement;

    expect(disclaimer.textContent).toContain('Conteúdo gerado por IA');
    expect(disclaimer.textContent).toContain('O Copiloto pode cometer erros');
  });

  it('cria a conversa ao abrir e mostra apenas sugestões autorizadas', () => {
    currentUser.set({
      id: 'user-id',
      permissoes: ['assistente:chat:use', 'sst:dashboard:read']
    });
    clickButtonByLabel('Abrir conversa com o Copiloto');
    fixture.detectChanges();

    expect(service.criarConversa).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.textContent).toContain('Me ajude a entender as inspeções');
    expect(fixture.nativeElement.textContent).not.toContain('Quero analisar o sucesso dos descontos');
    expect(fixture.nativeElement.textContent).toContain('Como posso facilitar seu dia?');
  });

  it('envia uma pergunta idempotente e apresenta evidência e ação autorizada', () => {
    const response: AssistenteMensagem = {
      id: 'answer-id',
      clientMessageId: 'client-id-from-response',
      papel: 'ASSISTENTE',
      status: 'CONCLUIDA',
      conteudo: 'A **Equipe Norte** teve mais desvios. Veja [o relatório](https://example.com). <script>alert(1)</script>',
      criadaEm: '2026-08-02T10:00:02-03:00',
      evidencias: [{
        fonte: 'sst_desvios',
        versao: '1',
        timezone: 'America/Sao_Paulo',
        amostra: 82,
        truncada: false,
        calculadaEm: '2026-08-02T10:00:02-03:00',
        dados: [{ equipe: 'Equipe Norte', quantidade: 12 }]
      }],
      acoes: [{
        tipo: 'NAVIGATE',
        chaveDestino: 'DESCONTO_NOVO',
        rotulo: 'Abrir novo desconto',
        autoridade: 'desconto:solicitar'
      }],
      apresentacao: {
        versao: '1.0',
        formatoConteudo: 'MARKDOWN_CES_V1',
        blocos: [{
          id: 'texto-principal',
          tipo: 'TEXTO',
          conteudo: 'A **Equipe Norte** teve mais desvios. Veja [o relatório](https://example.com). <script>alert(1)</script>',
          visualizacao: null
        }]
      }
    };
    service.enviarMensagem.mockImplementation((_id: string, request: { clientMessageId: string }) => {
      response.clientMessageId = request.clientMessageId;
      return of(response);
    });
    clickButtonByLabel('Abrir conversa com o Copiloto');
    fixture.detectChanges();

    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = 'Qual equipe teve mais desvios?';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    clickButtonByLabel('Enviar pergunta');
    fixture.detectChanges();

    expect(service.enviarMensagem).toHaveBeenCalledOnce();
    const request = service.enviarMensagem.mock.calls[0][1];
    expect(request.conteudo).toBe('Qual equipe teve mais desvios?');
    expect(request.clientMessageId).toEqual(expect.any(String));
    expect(fixture.nativeElement.textContent).toContain('Ver detalhes da consulta');
    const markdown = fixture.nativeElement.querySelector('.markdown-content') as HTMLElement;
    expect(markdown.querySelector('strong')?.textContent).toBe('Equipe Norte');
    expect(markdown.textContent).not.toContain('**');
    expect(markdown.querySelector('a')?.getAttribute('href')).toBe('https://example.com');
    expect(markdown.querySelector('script')).toBeNull();

    clickButtonByText('Abrir novo desconto');
    expect(router.navigate).toHaveBeenCalledWith(['/dp/descontos/novo'], { queryParams: {} });
  });

  it('resolve parâmetros PATH e QUERY e fecha o chat após navegar', async () => {
    const id = '550e8400-e29b-41d4-a716-446655440000';
    service.enviarMensagem.mockImplementation((_conversationId: string, request: { clientMessageId: string }) => of({
      id: 'answer-with-navigation',
      clientMessageId: request.clientMessageId,
      papel: 'ASSISTENTE',
      status: 'CONCLUIDA',
      conteudo: 'Encontrei a inspeção.',
      criadaEm: new Date().toISOString(),
      acoes: [{
        tipo: 'NAVIGATE',
        chaveDestino: 'SST_INSPECAO_DETALHE',
        rotulo: 'Ver detalhe da inspeção',
        autoridade: 'sst:inspecao:read',
        parametros: { id }
      }]
    } satisfies AssistenteMensagem));
    clickButtonByLabel('Abrir conversa com o Copiloto');
    fixture.detectChanges();

    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = 'Abra a inspeção';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    clickButtonByLabel('Enviar pergunta');
    fixture.detectChanges();
    clickButtonByText('Ver detalhe da inspeção');
    await fixture.whenStable();
    fixture.detectChanges();

    expect(router.navigate).toHaveBeenCalledWith([`/sst/inspecoes/${id}`], { queryParams: {} });
    expect(fixture.nativeElement.querySelector('.chatbot-panel').getAttribute('aria-hidden')).toBe('true');
  });

  it('reutiliza o clientMessageId quando o envio não pôde ser confirmado', () => {
    service.enviarMensagem
      .mockImplementationOnce(() => throwError(() => new Error('network')))
      .mockImplementationOnce((_id: string, request: { clientMessageId: string }) => of({
        id: 'answer-id',
        clientMessageId: request.clientMessageId,
        papel: 'ASSISTENTE',
        status: 'CONCLUIDA',
        conteudo: 'Resposta confirmada.',
        criadaEm: new Date().toISOString()
      } satisfies AssistenteMensagem));
    clickButtonByLabel('Abrir conversa com o Copiloto');
    fixture.detectChanges();

    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = 'Pergunta preservada';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    clickButtonByLabel('Enviar pergunta');
    fixture.detectChanges();

    const originalId = service.enviarMensagem.mock.calls[0][1].clientMessageId;
    clickButtonByText('Tentar de novo');
    fixture.detectChanges();

    expect(service.enviarMensagem.mock.calls[1][1].clientMessageId).toBe(originalId);
    expect(fixture.nativeElement.querySelectorAll('article[aria-label="Sua pergunta"]')).toHaveLength(1);
  });

  it('mostra que o Pensando enquanto aguarda o servidor', () => {
    const pendingResponse = new Subject<AssistenteMensagem>();
    service.enviarMensagem.mockReturnValue(pendingResponse.asObservable());
    clickButtonByLabel('Abrir conversa com o Copiloto');
    fixture.detectChanges();

    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = 'Como estão as inspeções?';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    clickButtonByLabel('Enviar pergunta');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('article[aria-label="Pensando"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelectorAll('article[aria-label="Pensando"] .thinking-dots > span')
    ).toHaveLength(3);
    expect(fixture.nativeElement.querySelector('.typing-avatar')).toBeNull();

    pendingResponse.next({
      id: 'answer-id',
      papel: 'ASSISTENTE',
      status: 'CONCLUIDA',
      conteudo: 'Aqui está a resposta.',
      criadaEm: new Date().toISOString()
    });
    pendingResponse.complete();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('article[aria-label="Pensando"]')).toBeNull();
  });

  it('mantém Markdown e HTML da mensagem do usuário como texto literal', () => {
    service.enviarMensagem.mockReturnValue(new Subject<AssistenteMensagem>().asObservable());
    clickButtonByLabel('Abrir conversa com o Copiloto');
    fixture.detectChanges();

    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = 'Mostre **Alfa** e <strong>Beta</strong>';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    clickButtonByLabel('Enviar pergunta');
    fixture.detectChanges();

    const userMessage = fixture.nativeElement.querySelector('article[aria-label="Sua pergunta"]') as HTMLElement;
    expect(userMessage.textContent).toContain('**Alfa**');
    expect(userMessage.textContent).toContain('<strong>Beta</strong>');
    expect(userMessage.querySelector('strong')).toBeNull();
  });

  it('começa uma nova conversa sem reutilizar o contexto anterior', () => {
    const previousConversation: AssistenteConversa = {
      ...conversation,
      mensagens: [{
        id: 'old-message',
        papel: 'ASSISTENTE',
        status: 'CONCLUIDA',
        conteudo: 'Contexto da conversa anterior.',
        criadaEm: '2026-08-02T10:00:01-03:00'
      }]
    };
    const freshConversation: AssistenteConversa = {
      id: 'fresh-conversation-id',
      criadaEm: '2026-08-02T11:00:00-03:00',
      mensagens: []
    };
    service.criarConversa.mockReturnValueOnce(of(previousConversation));
    clickButtonByLabel('Abrir conversa com o Copiloto');
    fixture.detectChanges();
    sessionStorage.setItem('ces_assistente_rascunho_user-id', 'Rascunho antigo');
    sessionStorage.setItem('ces_assistente_pendente_user-id', JSON.stringify({
      conversaId: previousConversation.id,
      clientMessageId: 'pending-id',
      conteudo: 'Pergunta antiga'
    }));

    const confirmationService = fixture.debugElement.injector.get(ConfirmationService);
    const confirmSpy = vi.spyOn(confirmationService, 'confirm');
    clickButtonByLabel('Começar nova conversa');
    expect(confirmSpy).toHaveBeenCalledOnce();

    service.criarConversa.mockReturnValueOnce(of(freshConversation));
    confirmSpy.mock.calls[0][0].accept?.();
    fixture.detectChanges();

    expect(service.criarConversa).toHaveBeenCalledTimes(2);
    expect(localStorage.getItem('ces_assistente_conversa_user-id')).toBe(freshConversation.id);
    expect(sessionStorage.getItem('ces_assistente_rascunho_user-id')).toBeNull();
    expect(sessionStorage.getItem('ces_assistente_pendente_user-id')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Contexto da conversa anterior.');
    expect(fixture.nativeElement.textContent).toContain('Como posso facilitar seu dia?');
  });

  function clickButtonByLabel(label: string): void {
    const button = fixture.nativeElement.querySelector(`button[aria-label="${label}"]`) as HTMLButtonElement;
    expect(button).toBeTruthy();
    button.click();
  }

  function clickButtonByText(text: string): void {
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    const button = buttons.find(candidate => candidate.textContent?.includes(text));
    expect(button).toBeTruthy();
    button?.click();
  }
});
