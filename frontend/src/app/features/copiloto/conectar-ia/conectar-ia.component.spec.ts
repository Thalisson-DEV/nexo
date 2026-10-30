import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { CesConfirmationService } from '@ces/ui';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ConexaoIa } from '../../../core/models/conexoes-ia.models';
import { AuthService } from '../../../core/services/auth.service';
import { ConexoesIaService } from '../../../core/services/conexoes-ia.service';
import { abrirListagem, paginaTeste } from '../../../shared/listagem/listagem-teste';
import { ConectarIaComponent } from './conectar-ia.component';
import { comoConectou, tempoRelativo } from './conectar-ia.util';

const conexao: ConexaoIa = {
  id: 'c1',
  nome: 'Claude',
  tipo: 'OAUTH',
  permiteEscrita: false,
  situacao: 'CONECTADA',
  prefixoChave: null,
  aplicativo: 'claude-ai',
  criadaEm: new Date(Date.now() - 3 * 86400000).toISOString(),
  ultimoUsoEm: new Date(Date.now() - 5 * 60000).toISOString(),
  expiraEm: null,
  desconectadaEm: null,
  totalChamadas: 12,
  usuarioId: 'u1',
  usuarioLogin: null
};

describe('ConectarIaComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (permissoes: string[] = ['assistente:conexao:use']) =>
    abrirListagem(ConectarIaComponent, 'conectar', '/conectar', [
      { provide: ConexoesIaService, useValue: servico },
      { provide: AuthService, useValue: { currentUser: signal({ id: 'u1', login: 'USRADMIN', permissoes }) } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    // As abas observam o próprio tamanho; o jsdom não tem ResizeObserver.
    (globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
      observe(): void {}
      unobserve(): void {}
      disconnect(): void {}
    };
    servico = {
      info: vi.fn(() => of({ urlServidor: 'https://ces.exemplo/api/mcp', podeEscrever: true, ferramentas: 10, ferramentasEscrita: 2 })),
      listar: vi.fn(() => of([conexao])),
      listarTodas: vi.fn(() => paginaTeste([{ ...conexao, id: 'c2', usuarioLogin: 'USR123456' }])),
      desconectar: vi.fn(() => of(conexao))
    };
  });

  afterEach(() => localStorage.clear());

  it('lista as conexões em linguagem simples, sem abas para quem não é administrador', async () => {
    const { el } = await abrir();
    expect(el.querySelector('h1')?.textContent).toContain('Conectar sua IA');
    expect(el.textContent).toContain('Claude');
    expect(el.textContent).toContain('Só consultar');
    expect(el.textContent).toContain('Há 5 minutos');
    expect(el.textContent).toContain('Conectada');
    expect(el.textContent).not.toContain('Da empresa');
    expect(el.textContent).not.toMatch(/MCP|OAuth|token/);
  });

  it('estado vazio leva ao passo a passo', async () => {
    servico['listar'] = vi.fn(() => of([]));
    const { el, harness } = await abrir();
    expect(el.textContent).toContain('Você ainda não conectou nenhuma IA');
    const botao = Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Ver o passo a passo'));
    botao!.click();
    harness.detectChanges();
    await harness.fixture.whenStable();
    expect(document.body.textContent).toContain('Copie o endereço do Nexo');
    const enderecos = Array.from(document.body.querySelectorAll<HTMLInputElement>('input[readonly]')).map((i) => i.value);
    expect(enderecos).toContain('https://ces.exemplo/api/mcp');
  });

  it('desconecta depois da confirmação e recarrega a lista', async () => {
    const { el, harness } = await abrir();
    const confirmar = vi.spyOn(TestBed.inject(CesConfirmationService), 'confirm').mockResolvedValue(true);
    (el.querySelector('button[aria-label="Desconectar Claude"]') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
    expect(confirmar).toHaveBeenCalledWith(expect.objectContaining({ variant: 'destructive', confirmLabel: 'Desconectar' }));
    expect(servico['desconectar']).toHaveBeenCalledWith('c1');
    expect(servico['listar']).toHaveBeenCalledTimes(2);
  });

  it('administrador vê a aba Da empresa e carrega todas as conexões ao abri-la', async () => {
    const { el, harness } = await abrir(['assistente:conexao:use', 'assistente:conexao:manage']);
    expect(servico['listarTodas']).not.toHaveBeenCalled();
    const aba = Array.from(el.querySelectorAll('[role="tab"]')).find((t) => t.textContent?.includes('Da empresa')) as HTMLElement;
    aba.click();
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(servico['listarTodas']).toHaveBeenCalled();
    expect(el.textContent).toContain('USR123456');
  });
});

describe('textos de conexão', () => {
  it('descreve tempo e forma de conexão sem jargão', () => {
    const agora = new Date('2026-10-29T12:00:00Z');
    expect(tempoRelativo(null, agora)).toBe('Ainda não usou');
    expect(tempoRelativo('2026-10-29T11:59:30Z', agora)).toBe('Agora há pouco');
    expect(tempoRelativo('2026-10-28T11:00:00Z', agora)).toBe('Ontem');
    expect(comoConectou({ tipo: 'CHAVE' })).toBe('Chave de acesso');
    expect(comoConectou({ tipo: 'OAUTH' })).toBe('Pelo login');
  });
});
