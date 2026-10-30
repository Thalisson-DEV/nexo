import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ConexoesIaService } from '../../../core/services/conexoes-ia.service';
import { ChaveAcessoDrawerComponent } from './chave-acesso-drawer.component';

describe('ChaveAcessoDrawerComponent', () => {
  let criarChave: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    TestBed.resetTestingModule();
    criarChave = vi.fn(() => of({ chave: 'ces_key_segredo', conexao: { nome: 'Cursor do notebook' } }));
    TestBed.configureTestingModule({
      providers: [
        provideNoopAnimations(),
        MessageService,
        ConfirmationService,
        { provide: ConexoesIaService, useValue: { criarChave } }
      ]
    });
  });

  async function abrir(podeEscrever = true) {
    const fixture = TestBed.createComponent(ChaveAcessoDrawerComponent);
    fixture.componentRef.setInput('endereco', 'https://ces.exemplo/api/mcp');
    fixture.componentRef.setInput('podeEscrever', podeEscrever);
    fixture.componentRef.setInput('visivel', true);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  const botao = (texto: string) =>
    Array.from(document.body.querySelectorAll('button')).find((b) => b.textContent?.trim() === texto) as HTMLButtonElement;

  it('pede o nome antes de gerar e mostra a chave uma única vez', async () => {
    const fixture = await abrir();
    botao('Gerar chave').click();
    fixture.detectChanges();
    expect(document.body.textContent).toContain('Dê um nome para a chave.');
    expect(criarChave).not.toHaveBeenCalled();

    const tela = fixture.componentInstance as unknown as { nome: { set(v: string): void } };
    tela.nome.set('Cursor do notebook');
    fixture.detectChanges();
    botao('Gerar chave').click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(criarChave).toHaveBeenCalledWith({ nome: 'Cursor do notebook', permiteEscrita: false, validadeDias: 90 });
    expect(document.body.textContent).toContain('Copie agora');
    const campo = document.body.querySelector('input[readonly]') as HTMLInputElement;
    expect(campo.value).toBe('ces_key_segredo');
  });
});
