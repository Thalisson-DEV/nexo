import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { AnalisarComCopilotoComponent } from './analisar-com-copiloto.component';

describe('atalho Analisar com Copiloto', () => {
  const usuario = signal<{ permissoes: string[] } | null>(null);

  beforeEach(async () => {
    usuario.set(null);
    await TestBed.configureTestingModule({
      imports: [AnalisarComCopilotoComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: { currentUser: usuario } }],
    }).compileComponents();
  });

  it('não mostra o atalho sem permissão do assistente', () => {
    const fixture = TestBed.createComponent(AnalisarComCopilotoComponent);
    fixture.componentRef.setInput('origem', 'Detalhe da obra');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });

  it('leva a obra e apenas os filtros preenchidos para o rascunho do chat', () => {
    usuario.set({ permissoes: ['assistente:chat:use'] });
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'url', 'get').mockReturnValue('/obras/42');
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(AnalisarComCopilotoComponent);
    fixture.componentRef.setInput('origem', 'Detalhe da obra');
    fixture.componentRef.setInput('tipo', 'obra');
    fixture.componentRef.setInput('entidade', 42);
    fixture.componentRef.setInput('rotulo', 'Obra 42');
    fixture.componentRef.setInput('pergunta', 'Resuma esta obra');
    fixture.componentRef.setInput('filtros', { obra: 42, ativo: true, vazio: '', ausente: null });
    fixture.detectChanges();

    const botao = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(botao).not.toBeNull();
    botao.click();

    expect(navegar).toHaveBeenCalledWith(['/copiloto'], { queryParams: {
      origem: 'Detalhe da obra', rota: '/obras/42', tipo: 'obra', entidade: '42',
      rotulo: 'Obra 42', pergunta: 'Resuma esta obra', f_obra: '42', f_ativo: 'true',
    } });
  });
});
