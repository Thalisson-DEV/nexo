import '@angular/compiler';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { Router, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MessageService } from 'primeng/api';
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { CentralTreinamentoComponent } from './central-treinamento.component';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

describe('CentralTreinamentoComponent', () => {
  let component: CentralTreinamentoComponent;
  let fixture: ComponentFixture<CentralTreinamentoComponent>;
  let host: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CentralTreinamentoComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        MessageService
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CentralTreinamentoComponent);
    component = fixture.componentInstance;
    host = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  it('deve instanciar o manual com os módulos oficiais', () => {
    expect(component).toBeTruthy();
    expect(component.totalModulos()).toBe(6);
  });

  it('deve carregar os 6 módulos com URL de vídeo sanitizada', () => {
    const modulos = component.modulos();
    expect(modulos.map((m) => m.id)).toEqual(['00', '07-1', '07-2', '07-1-1', '07-1-2', '07-1-3']);

    modulos.forEach((modulo) => {
      expect(modulo.safeEmbedUrl).toBeDefined();
      expect(modulo.videoId).toBeTruthy();
    });
  });

  it('não deve manter tema visual próprio por módulo', () => {
    const modulo = component.modulos()[0] as unknown as Record<string, unknown>;
    expect(modulo['temaVisual']).toBeUndefined();
  });

  it('deve listar todos os módulos em sequência', () => {
    const secoes = host.querySelectorAll('.manual section.modulo');

    expect(secoes.length).toBe(6);
    expect(secoes[0].getAttribute('data-modulo')).toBe('00');
    expect(secoes[5].getAttribute('data-modulo')).toBe('07-1-3');
  });

  it('deve iniciar com todos os módulos recolhidos', () => {
    expect(component.modulosAbertos()).toEqual([]);
    expect(host.querySelectorAll('.modulo__conteudo').length).toBe(0);
    expect(host.querySelectorAll('.manual figure.video').length).toBe(0);

    const gatilhos = host.querySelectorAll('.modulo__gatilho');
    expect(gatilhos.length).toBe(6);
    gatilhos.forEach((gatilho) => expect(gatilho.getAttribute('aria-expanded')).toBe('false'));
  });

  it('deve expandir e recolher um módulo pelo cabeçalho', () => {
    const gatilho = host.querySelector('.modulo__gatilho') as HTMLButtonElement;

    gatilho.click();
    fixture.detectChanges();
    expect(component.estaAberto('00')).toBe(true);
    expect(gatilho.getAttribute('aria-expanded')).toBe('true');
    expect(host.querySelector('.modulo__conteudo')?.id).toBe('conteudo-00');
    expect(gatilho.getAttribute('aria-controls')).toBe('conteudo-00');

    gatilho.click();
    fixture.detectChanges();
    expect(component.estaAberto('00')).toBe(false);
    expect(host.querySelectorAll('.modulo__conteudo').length).toBe(0);
  });

  it('deve alternar todos os módulos de uma vez', () => {
    component.alternarTodos();
    expect(component.modulosAbertos().length).toBe(6);
    expect(component.todosAbertos()).toBe(true);

    component.alternarTodos();
    expect(component.modulosAbertos()).toEqual([]);
    expect(component.todosAbertos()).toBe(false);
  });

  it('deve navegar pelo sumário abrindo o módulo, sem usar fragmento de URL', () => {
    const itens = Array.from(host.querySelectorAll('.indice__item')) as HTMLElement[];

    expect(itens.length).toBe(6);
    itens.forEach((item) => {
      expect(item.tagName).toBe('BUTTON');
      expect(item.getAttribute('href')).toBeNull();
    });
    expect(itens[0].textContent).toContain(component.modulos()[0].tituloCurto);

    itens[2].click();
    fixture.detectChanges();

    expect(component.estaAberto(component.modulos()[2].id)).toBe(true);
  });

  it('deve usar rótulos curtos no sumário, sem títulos longos truncados', () => {
    const rotulos = Array.from(host.querySelectorAll('.indice__texto strong')).map(
      (item) => item.textContent?.trim() ?? ''
    );

    expect(rotulos).toEqual(component.modulos().map((modulo) => modulo.tituloCurto));
    rotulos.forEach((rotulo) => expect(rotulo.length).toBeLessThanOrEqual(32));
  });

  it('deve manter a hierarquia de títulos do documento', () => {
    expect(host.querySelectorAll('.manual__titulo').length).toBe(1);
    expect(host.querySelectorAll('.manual h3.modulo__cabecalho').length).toBe(6);

    component.alternarTodos();
    fixture.detectChanges();
    expect(host.querySelectorAll('.manual h4.secao__titulo').length).toBeGreaterThan(6);
  });

  it('deve apresentar os passos como lista ordenada, não como grade de cartões', () => {
    component.alternarTodos();
    fixture.detectChanges();

    const passos = host.querySelectorAll('.manual ol.passos > li.passo');
    expect(passos.length).toBeGreaterThan(20);
    expect(host.querySelector('.manual .passo__numero')?.textContent?.trim()).toBe('1.1');
  });

  it('deve carregar o vídeo apenas quando o módulo está aberto', () => {
    expect(host.querySelectorAll('.manual figure.video iframe').length).toBe(0);

    component.alternarTodos();
    fixture.detectChanges();

    expect(host.querySelectorAll('.manual figure.video iframe').length).toBe(6);
    expect(host.querySelectorAll('.manual .video__legenda a[target="_blank"]').length).toBe(6);
  });

  it('deve buscar em títulos, palavras-chave e conteúdo dos passos', () => {
    component.digitarBusca('copiloto');
    expect(component.modulosFiltrados().map((m) => m.id)).toEqual(['00']);

    component.digitarBusca('offline');
    expect(component.modulosFiltrados().some((m) => m.id === '07-1-2')).toBe(true);

    component.digitarBusca('termo_totalmente_inexistente_xyz');
    expect(component.totalFiltrados()).toBe(0);
  });

  it('deve reduzir o manual aos módulos encontrados e abrir o que casou', () => {
    component.digitarBusca('copiloto');
    fixture.detectChanges();

    expect(host.querySelectorAll('.manual section.modulo').length).toBe(1);
    expect(host.querySelector('.indice__filtro')?.textContent).toContain('1 de 6 módulos');
    expect(component.estaAberto('00')).toBe(true);
  });

  it('deve recolher tudo ao limpar a busca', () => {
    component.digitarBusca('copiloto');
    expect(component.modulosAbertos().length).toBe(1);

    component.limparBusca();
    expect(component.modulosAbertos()).toEqual([]);
  });

  it('deve oferecer estado vazio acionável quando a busca não retorna módulos', () => {
    component.digitarBusca('termo_totalmente_inexistente_xyz');
    fixture.detectChanges();

    expect(host.querySelector('ces-async-state')).toBeTruthy();
    expect(host.querySelectorAll('.manual section.modulo').length).toBe(0);
    expect(host.querySelector('nav.indice')).toBeNull();
  });

  it('deve limpar a busca e restaurar o manual completo', () => {
    component.digitarBusca('desvios');
    expect(component.temBusca()).toBe(true);

    component.limparBusca();
    expect(component.termoBusca()).toBe('');
    expect(component.temBusca()).toBe(false);
    expect(component.totalFiltrados()).toBe(6);
  });

  it('deve declarar o escopo do documento impresso conforme a busca', () => {
    expect(component.escopoImpressao()).toBe('Manual completo');

    component.digitarBusca('gps');
    expect(component.escopoImpressao()).toBe('Busca "gps"');
  });

  it('deve imprimir diretamente, sem preparar a interface antes', () => {
    const printSpy = vi.spyOn(window, 'print').mockImplementation(() => {});

    component.imprimir();

    expect(printSpy).toHaveBeenCalledOnce();
    printSpy.mockRestore();
  });

  it('deve renderizar o documento de impressão à parte da tela', () => {
    const documento = host.querySelector('app-manual-treinamento-documento')!;

    expect(documento.className).toContain('print:block');
    expect(host.querySelector('.ces-page')?.className).toContain('print:hidden');
    expect(documento.textContent).toContain('[00]');
    expect(documento.textContent).toContain('[07.2]');
  });

  it('não deve renderizar controles interativos dentro do documento de impressão', () => {
    const documento = host.querySelector('app-manual-treinamento-documento')!;

    expect(documento.querySelectorAll('button, a, iframe, video, input').length).toBe(0);
  });

  it('deve destacar a abertura de chamado como caminho principal de dúvida', () => {
    const suporte = host.querySelector('.suporte')!;

    expect(suporte.textContent).toContain('Ficou com dúvida?');
    expect(suporte.textContent).toContain('Abra um chamado');
    expect(suporte.querySelector('ces-button')?.textContent).toContain('Abrir chamado');

    const alternativa = host.querySelector('.suporte__alternativa')!;
    expect(alternativa.textContent).toContain('Copiloto');
  });

  it('deve navegar para a abertura de chamado', async () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    component.abrirChamado();

    expect(navigate).toHaveBeenCalledWith(['/chamados/novo']);
    navigate.mockRestore();
  });

  it('deve copiar o link da página', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText } });

    await component.copiarLink();

    expect(writeText).toHaveBeenCalledWith(window.location.href);
  });
});
