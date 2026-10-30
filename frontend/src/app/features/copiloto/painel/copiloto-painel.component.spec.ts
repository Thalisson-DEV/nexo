import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { NotificationService } from '@ces/ui/feedback';
import { PacoteAssistente } from '../../../core/models/assistente.models';
import { AssistenteService } from '../../../core/services/assistente.service';
import { AuthService } from '../../../core/services/auth.service';
import { CopilotoPainelComponent } from './copiloto-painel.component';

const stop: PacoteAssistente = { placement: { turnIndex: 0, tabIndex: 0 }, obj: { type: 'stop', status: 'CONCLUIDA' } };

describe('CopilotoPainelComponent — abrir no workspace', () => {
  function montar() {
    sessionStorage.clear();
    const api: Partial<AssistenteService> = {
      capacidades: () => of({
        disponivel: true, python: false, pesquisaWeb: false, leituraWeb: false, ferramentas: [], competencias: [], sugestoes: [],
        limites: { arquivoMaxBytes: 1, anexosPorMensagem: 1, formatosAceitos: [] },
      }),
      listarConversas: () => of({ content: [], page: { size: 1, number: 0, totalElements: 0, totalPages: 0 } }),
      criarConversa: () => of({ id: 'conversa-42', titulo: 'Nova conversa', ultimaAtividadeEm: '', dataCriacao: '', emAndamento: false }),
      transmitir: async (_id, _req, aoPacote) => aoPacote(stop),
    };
    TestBed.configureTestingModule({
      imports: [CopilotoPainelComponent],
      providers: [
        provideRouter([]),
        { provide: AssistenteService, useValue: api },
        { provide: AuthService, useValue: { currentUser: signal({ id: 'u1', permissoes: ['assistente:chat:use'] }), token: () => null } },
        { provide: NotificationService, useValue: { error: vi.fn(), warning: vi.fn(), success: vi.fn() } },
      ],
    });
    const fixture = TestBed.createComponent(CopilotoPainelComponent);
    fixture.componentRef.setInput('contexto', { id: 'financeiro.executivo', nome: 'Financeiro', filtros: [] });
    fixture.detectChanges();
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    return { fixture, navegar };
  }

  function botaoAbrir(el: HTMLElement): HTMLButtonElement {
    const host = [...el.querySelectorAll('ces-icon-button')].find((b) =>
      b.querySelector('button')?.getAttribute('aria-label')?.startsWith('Abrir no Copiloto'));
    return host!.querySelector('button') as HTMLButtonElement;
  }

  it('navega para a mesma conversa no workspace depois da primeira pergunta', async () => {
    const { fixture, navegar } = montar();
    await (fixture.componentInstance as unknown as { enviar(t: string): Promise<void> }).enviar('Explique este painel');
    await fixture.whenStable();
    fixture.detectChanges();

    const botao = botaoAbrir(fixture.nativeElement);
    expect(botao.disabled).toBe(false);
    botao.click();
    expect(navegar).toHaveBeenCalledWith(['/copiloto', 'conversa-42']);
  });

  it('antes de haver conversa, abre o workspace com o contexto do painel (nunca fica sem ação)', () => {
    const { fixture, navegar } = montar();
    fixture.componentRef.setInput('contexto', {
      id: 'financeiro.executivo', nome: 'Financeiro', rota: '/financeiro/dashboard',
      filtros: [{ chave: 'base', rotulo: 'Base', valor: 'Norte' }, { chave: 'obra', rotulo: 'Obra', valor: null }],
    });
    fixture.detectChanges();
    const botao = botaoAbrir(fixture.nativeElement);
    expect(botao.disabled).toBe(false);
    botao.click();
    expect(navegar).toHaveBeenCalledWith(['/copiloto'], {
      queryParams: {
        origem: 'Financeiro', tipo: 'DASHBOARD', entidade: 'financeiro.executivo', rotulo: 'Financeiro',
        rota: '/financeiro/dashboard', f_Base: 'Norte',
      },
    });
  });
});
