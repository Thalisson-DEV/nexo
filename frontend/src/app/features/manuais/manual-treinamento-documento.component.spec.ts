import '@angular/compiler';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { describe, expect, it, beforeEach } from 'vitest';
import { ManualTreinamentoDocumentoComponent } from './manual-treinamento-documento.component';
import { MODULOS_TREINAMENTO } from './central-treinamento.data';
import { ManualModulo } from './central-treinamento.models';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

describe('ManualTreinamentoDocumentoComponent', () => {
  let fixture: ComponentFixture<ManualTreinamentoDocumentoComponent>;

  const montar = (modulos: readonly ManualModulo[], escopo = 'Todos os módulos') => {
    fixture = TestBed.createComponent(ManualTreinamentoDocumentoComponent);
    fixture.componentRef.setInput('modulos', modulos);
    fixture.componentRef.setInput('escopo', escopo);
    fixture.componentRef.setInput('emitidoEm', new Date('2026-09-15T12:00:00Z'));
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ManualTreinamentoDocumentoComponent]
    }).compileComponents();
  });

  it('deve montar a capa com metadados de emissão e escopo', () => {
    const host = montar(MODULOS_TREINAMENTO, 'Mobile / Campo');

    expect(host.querySelector('h1')?.textContent).toContain('Central de Treinamento');
    expect(host.textContent).toContain('15/09/2026');
    expect(host.textContent).toContain('Mobile / Campo');
  });

  it('deve exibir sumário apenas quando houver mais de um módulo', () => {
    expect(montar(MODULOS_TREINAMENTO).querySelector('.sumario')).toBeTruthy();
    expect(montar([MODULOS_TREINAMENTO[0]]).querySelector('.sumario')).toBeNull();
  });

  it('deve manter a hierarquia de títulos do documento', () => {
    const host = montar(MODULOS_TREINAMENTO);

    expect(host.querySelectorAll('h1').length).toBe(1);
    expect(host.querySelectorAll('section.modulo > header > h2').length).toBe(
      MODULOS_TREINAMENTO.length
    );
    expect(host.querySelectorAll('h3').length).toBeGreaterThan(0);
  });

  it('deve numerar os passos por seção e preservar atalhos e valores', () => {
    const host = montar([MODULOS_TREINAMENTO[0]]);
    const primeiroItem = host.querySelector('.item__rotulo');

    expect(primeiroItem?.textContent?.trim().startsWith('1.1')).toBe(true);
    expect(host.textContent).toContain('USR123456');
    expect(host.textContent).toContain('Atalho:');
  });

  it('deve substituir o vídeo incorporado pelo endereço de acesso', () => {
    const host = montar([MODULOS_TREINAMENTO[0]]);

    expect(host.querySelector('iframe')).toBeNull();
    expect(host.querySelector('.bloco__endereco')?.textContent).toContain(
      MODULOS_TREINAMENTO[0].videoUrlOriginal
    );
  });

  it('deve rotular alertas por texto, sem depender de cor', () => {
    const host = montar([MODULOS_TREINAMENTO[0]]);
    const avisos = Array.from(host.querySelectorAll('.bloco--aviso .bloco__rotulo')).map(
      (bloco) => bloco.textContent?.trim() ?? ''
    );

    expect(avisos.length).toBeGreaterThan(0);
    expect(avisos.some((texto) => texto.startsWith('Segurança —'))).toBe(true);
  });

  it('deve informar o usuário quando o filtro não retorna módulos', () => {
    const host = montar([]);

    expect(host.querySelector('.documento__vazio')).toBeTruthy();
    expect(host.querySelectorAll('section.modulo').length).toBe(0);
    expect(host.querySelector('.suporte')).toBeNull();
  });

  it('deve encerrar com os caminhos de suporte, priorizando o chamado', () => {
    const host = montar(MODULOS_TREINAMENTO);
    const caminhos = host.querySelectorAll('.suporte__caminho');

    expect(caminhos.length).toBe(2);
    expect(caminhos[0].className).toContain('suporte__caminho--principal');
    expect(caminhos[0].textContent).toContain('Abra um chamado');
    expect(caminhos[0].textContent).toContain('Chamados › Novo chamado');
    expect(caminhos[1].textContent).toContain('Copiloto');
  });

  it('deve usar ícones decorativos, sempre acompanhados de rótulo textual', () => {
    const host = montar([MODULOS_TREINAMENTO[0]]);
    const icones = Array.from(host.querySelectorAll('i[class*="pi-"]'));

    expect(icones.length).toBeGreaterThan(5);
    icones.forEach((icone) => expect(icone.closest('[aria-hidden="true"]')).not.toBeNull());
  });

  it('não deve conter elementos interativos', () => {
    const host = montar(MODULOS_TREINAMENTO);

    expect(host.querySelectorAll('button, a, input, select, iframe, video').length).toBe(0);
  });
});
