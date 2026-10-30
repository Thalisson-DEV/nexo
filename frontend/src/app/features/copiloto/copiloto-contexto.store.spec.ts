import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { NotificationService } from '@ces/ui/feedback';
import { AssistenteContexto, EnviarMensagemRequest, PacoteAssistente } from '../../core/models/assistente.models';
import { AssistenteService } from '../../core/services/assistente.service';
import { AuthService } from '../../core/services/auth.service';
import { CopilotoStore } from './copiloto.store';

const painel = (base: string): AssistenteContexto => ({
  pagina: 'Solicitações',
  entidadeTipo: 'DASHBOARD',
  entidadeId: 'almoxarifado.solicitacoes',
  painel: {
    id: 'almoxarifado.solicitacoes',
    nome: 'Solicitações',
    filtros: [{ chave: 'base', rotulo: 'Base', valor: base }],
  },
});

const stop: PacoteAssistente = { placement: { turnIndex: 0, tabIndex: 0 }, obj: { type: 'stop', status: 'CONCLUIDA' } };

describe('CopilotoStore — contexto por mensagem', () => {
  let store: CopilotoStore;
  const enviados: EnviarMensagemRequest[] = [];
  const criadas: unknown[] = [];

  beforeEach(() => {
    enviados.length = 0;
    criadas.length = 0;
    const api: Partial<AssistenteService> = {
      criarConversa: (request) => {
        criadas.push(request);
        return of({ id: 'conversa-1', titulo: 'Nova conversa', ultimaAtividadeEm: '', dataCriacao: '', emAndamento: false });
      },
      transmitir: async (_id, request, aoPacote) => {
        enviados.push(request);
        aoPacote(stop);
      },
    };
    TestBed.configureTestingModule({
      providers: [
        CopilotoStore,
        { provide: AssistenteService, useValue: api },
        { provide: AuthService, useValue: { currentUser: signal({ id: 'u1', permissoes: [] }) } },
        { provide: NotificationService, useValue: { error: vi.fn(), warning: vi.fn(), success: vi.fn() } },
      ],
    });
    store = TestBed.inject(CopilotoStore);
  });

  it('envia o recorte atual da tela a cada pergunta e guarda o snapshot em cada mensagem', async () => {
    let atual = painel('Norte');
    store.definirFonteContexto(() => atual);

    await store.enviar('Qual material mais contribuiu?');
    atual = painel('Sul');
    await store.enviar('E agora?');

    expect(criadas).toEqual([{ contexto: painel('Norte') }]);
    expect(enviados.map((request) => request.contexto?.painel?.filtros[0].valor)).toEqual(['Norte', 'Sul']);
    const perguntas = store.mensagens().filter((mensagem) => mensagem.papel === 'USUARIO');
    expect(perguntas.map((mensagem) => mensagem.contexto?.painel?.filtros[0].valor)).toEqual(['Norte', 'Sul']);
    expect(store.contexto()?.painel?.filtros[0].valor).toBe('Sul');
  });

  it('sem fonte de contexto (workspace) só a primeira pergunta leva o contexto de origem', async () => {
    store.novaConversa(painel('Norte'));
    await store.enviar('Primeira');
    await store.enviar('Segunda');
    expect(enviados[0].contexto?.painel?.filtros[0].valor).toBe('Norte');
    expect(enviados[1].contexto).toBeUndefined();
  });

  it('encerrar solta a fonte de contexto da superfície destruída', async () => {
    store.definirFonteContexto(() => painel('Norte'));
    store.encerrar();
    store.novaConversa();
    await store.enviar('Pergunta sem painel');
    expect(enviados[0].contexto).toBeUndefined();
  });
});
