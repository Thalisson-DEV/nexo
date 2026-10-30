import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { NotificationService } from '@ces/ui/feedback';
import { AssistenteService } from '../../../core/services/assistente.service';
import { AuthService } from '../../../core/services/auth.service';
import { CopilotoDashboardContext } from '../contexto/copiloto-contexto';
import { CopilotoAreaComponent } from './copiloto-area.component';
import { CopilotoGatilhoComponent } from './copiloto-gatilhos.component';
import { CopilotoPainelService } from './copiloto-painel.service';

@Component({
  standalone: true,
  imports: [CopilotoAreaComponent, CopilotoGatilhoComponent],
  template: `
    <app-copiloto-area [contexto]="contexto">
      <header><app-copiloto-gatilho></app-copiloto-gatilho></header>
      <p class="conteudo">Dashboard original</p>
    </app-copiloto-area>
  `,
})
class DashboardDeTesteComponent {
  contexto: CopilotoDashboardContext = { id: 'teste.painel', nome: 'Painel de teste', filtros: [] };
}

function configurar(permissoes: string[]) {
  sessionStorage.clear();
  const api: Partial<AssistenteService> = {
    capacidades: vi.fn(() => of({
      disponivel: true, python: false, pesquisaWeb: false, leituraWeb: false, ferramentas: [], competencias: [], sugestoes: [],
      limites: { arquivoMaxBytes: 1, anexosPorMensagem: 1, formatosAceitos: [] },
    })),
    listarConversas: vi.fn(() => of({ content: [], page: { size: 1, number: 0, totalElements: 0, totalPages: 0 } })),
  };
  TestBed.configureTestingModule({
    imports: [DashboardDeTesteComponent],
    providers: [
      provideRouter([]),
      { provide: AssistenteService, useValue: api },
      { provide: AuthService, useValue: { currentUser: signal({ id: 'u1', permissoes }), token: () => null } },
      { provide: NotificationService, useValue: { error: vi.fn(), warning: vi.fn(), success: vi.fn() } },
    ],
  });
  const fixture = TestBed.createComponent(DashboardDeTesteComponent);
  fixture.detectChanges();
  return { fixture, api, painel: TestBed.inject(CopilotoPainelService) };
}

describe('Copiloto contextual no dashboard', () => {
  it('mantém o dashboard intacto e só monta o painel na primeira abertura', () => {
    const { fixture, api, painel } = configurar(['assistente:chat:use']);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.conteudo')?.textContent).toBe('Dashboard original');
    expect(el.querySelector('app-copiloto-painel')).toBeNull();
    expect(api.capacidades).not.toHaveBeenCalled();

    const gatilho = el.querySelector('app-copiloto-gatilho button') as HTMLButtonElement;
    expect(gatilho.getAttribute('aria-expanded')).toBe('false');
    gatilho.click();
    fixture.detectChanges();

    expect(painel.aberto()).toBe(true);
    expect(gatilho.getAttribute('aria-expanded')).toBe('true');
    expect(el.querySelector('app-copiloto-painel')).not.toBeNull();
    expect(el.textContent).toContain('Painel de teste');
    expect(api.capacidades).toHaveBeenCalledTimes(1);
  });

  it('fechar oculta o painel sem destruí-lo (a conversa é preservada)', () => {
    const { fixture, painel } = configurar(['assistente:chat:use']);
    painel.abrir();
    fixture.detectChanges();
    const componente = (fixture.nativeElement as HTMLElement).querySelector('app-copiloto-painel');
    painel.fechar();
    fixture.detectChanges();
    const aside = (fixture.nativeElement as HTMLElement).querySelector('aside') as HTMLElement;
    expect(aside.getAttribute('aria-hidden')).toBe('true');
    expect(aside.hasAttribute('inert')).toBe(true);
    expect((fixture.nativeElement as HTMLElement).querySelector('app-copiloto-painel')).toBe(componente);
  });

  it('sem a permissão do Copiloto, mostra apenas o dashboard', () => {
    const { fixture } = configurar(['estoque:solicitacao:read']);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.conteudo')).not.toBeNull();
    expect(el.querySelector('app-copiloto-gatilho button')).toBeNull();
    expect(el.querySelector('aside')).toBeNull();
  });
});

describe('CopilotoPainelService', () => {
  it('lembra a conversa de cada painel separadamente e por usuário', () => {
    const { painel } = configurar(['assistente:chat:use']);
    painel.lembrarConversa('financeiro.executivo', 'c-fin');
    painel.lembrarConversa('sst.inspecoes', 'c-sst');
    expect(painel.conversaDoPainel('financeiro.executivo')).toBe('c-fin');
    expect(painel.conversaDoPainel('sst.inspecoes')).toBe('c-sst');
    painel.lembrarConversa('financeiro.executivo', null);
    expect(painel.conversaDoPainel('financeiro.executivo')).toBeNull();
    expect(Object.keys(sessionStorage).some((chave) => chave.endsWith('.u1'))).toBe(true);
  });

  it('pedido de foco abre o painel com o elemento escolhido', () => {
    const { painel } = configurar(['assistente:chat:use']);
    painel.perguntarSobre({ tipo: 'grafico', id: 'porStatus', rotulo: 'Por status' }, 'Explique');
    expect(painel.aberto()).toBe(true);
    expect(painel.pedido()?.foco.id).toBe('porStatus');
  });
});
