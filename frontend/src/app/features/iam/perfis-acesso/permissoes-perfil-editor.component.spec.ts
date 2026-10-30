import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { PermissoesPerfilEditorComponent } from './permissoes-perfil-editor.component';

const permissoes = [
  { id: 'sr', nome: 'estoque:solicitacao:read', ativo: true },
  { id: 'sw', nome: 'estoque:solicitacao:write', ativo: true },
  { id: 'sa', nome: 'estoque:solicitacao:approve', ativo: true },
  { id: 'mr', nome: 'estoque:material:read', ativo: true },
  { id: 'mw', nome: 'estoque:material:write', ativo: true },
  { id: 'fr', nome: 'frota:veiculo:read', ativo: true }
];

@Component({
  standalone: true,
  imports: [PermissoesPerfilEditorComponent],
  template: `<app-permissoes-perfil-editor [permissoes]="permissoes" [(selecionadas)]="selecionadas" [iniciais]="iniciais" />`
})
class HostComponent {
  readonly permissoes = permissoes;
  readonly selecionadas = signal<ReadonlySet<string>>(new Set(['fr']));
  readonly iniciais: ReadonlySet<string> = new Set(['fr']);
}

async function montar() {
  TestBed.resetTestingModule();
  await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  const fixture = TestBed.createComponent(HostComponent);
  fixture.detectChanges();
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const editor = fixture.debugElement.children[0].componentInstance as any;
  return { fixture, host: fixture.componentInstance, editor };
}

describe('PermissoesPerfilEditorComponent', () => {
  it('marcar uma ação marca o Ver do mesmo item; desmarcar o Ver tira as demais', async () => {
    const { host, editor } = await montar();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const solicitacoes = editor.modulos()[0].recursos.find((r: any) => r.rotulo === 'Solicitações de material');

    editor.marcar(solicitacoes, permissoes[2], true);
    expect([...host.selecionadas()].sort()).toEqual(['fr', 'sa', 'sr']);

    editor.marcar(solicitacoes, permissoes[0], false);
    expect([...host.selecionadas()]).toEqual(['fr']);
  });

  it('atalhos do módulo: só consultar, tudo e nenhuma', async () => {
    const { host, editor } = await montar();
    const almox = editor.modulos()[0];

    editor.marcarModulo(almox, true);
    expect(host.selecionadas().size).toBe(6);

    editor.soConsultar(almox);
    expect([...host.selecionadas()].sort()).toEqual(['fr', 'mr', 'sr']);

    editor.marcarModulo(almox, false);
    expect([...host.selecionadas()]).toEqual(['fr']);
  });

  it('resume o que vai mudar, filtra pela busca em português e desfaz', async () => {
    const { fixture, host, editor } = await montar();
    host.selecionadas.set(new Set(['sr']));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('1 a conceder');
    expect(fixture.nativeElement.textContent).toContain('1 a remover');

    editor.busca.set('aprovar');
    fixture.detectChanges();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    expect(editor.visiveis().map((m: any) => m.recursos.map((r: any) => r.rotulo))).toEqual([['Solicitações de material']]);

    editor.desfazer();
    expect([...host.selecionadas()]).toEqual(['fr']);
  });
});
