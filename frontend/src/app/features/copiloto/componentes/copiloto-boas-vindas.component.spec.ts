import { TestBed } from '@angular/core/testing';
import { CapacidadesAssistente } from '../../../core/models/assistente.models';
import { CopilotoBoasVindasComponent } from './copiloto-boas-vindas.component';

const capacidades: CapacidadesAssistente = {
  disponivel: true,
  python: true,
  pesquisaWeb: false,
  leituraWeb: false,
  ferramentas: [
    { nome: 'consultar_fonte', titulo: 'Consultar dados', tipo: 'DATA' },
    { nome: 'criar_artefato', titulo: 'Criar artefato', tipo: 'ARTIFACT' },
    { nome: 'gerar_relatorio_pdf', titulo: 'Gerar relatório', tipo: 'REPORT' },
    { nome: 'ler_arquivo', titulo: 'Ler arquivo', tipo: 'FILE' },
  ],
  competencias: [],
  sugestoes: [],
  limites: { arquivoMaxBytes: 1000, anexosPorMensagem: 2, formatosAceitos: ['pdf'] },
};

describe('boas-vindas do Copiloto', () => {
  it('mostra o catálogo completo com capacidades disponíveis e indisponíveis', async () => {
    await TestBed.configureTestingModule({ imports: [CopilotoBoasVindasComponent] }).compileComponents();
    const fixture = TestBed.createComponent(CopilotoBoasVindasComponent);
    fixture.componentRef.setInput('capacidades', capacidades);
    fixture.detectChanges();

    const disponiveis = fixture.nativeElement.querySelector('[aria-label="Capacidades disponíveis agora"]') as HTMLElement;
    const indisponiveis = fixture.nativeElement.querySelector('[aria-label="Capacidades indisponíveis nesta sessão"]') as HTMLElement;
    expect(disponiveis.textContent).toContain('Consultar dados do Nexo');
    expect(disponiveis.textContent).toContain('Dashboards');
    expect(disponiveis.textContent).toContain('Relatórios PDF');
    expect(disponiveis.textContent).toContain('Leitura de arquivos');
    expect(disponiveis.textContent).toContain('Python');
    expect(indisponiveis.textContent).toContain('Pesquisa web');
    expect(indisponiveis.textContent).toContain('Planilhas');
    expect(indisponiveis.textContent).toContain('Alterar dados do Nexo');
    expect(indisponiveis.textContent).not.toContain('Python');
  });

  it('mostra todo o catálogo como indisponível quando a IA está indisponível', async () => {
    await TestBed.configureTestingModule({ imports: [CopilotoBoasVindasComponent] }).compileComponents();
    const fixture = TestBed.createComponent(CopilotoBoasVindasComponent);
    fixture.componentRef.setInput('capacidades', { ...capacidades, disponivel: false });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[aria-label="Capacidades disponíveis agora"]')).toBeNull();
    const indisponiveis = fixture.nativeElement.querySelector('[aria-label="Capacidades indisponíveis nesta sessão"]') as HTMLElement;
    expect(indisponiveis.textContent).toContain('Consultar dados do Nexo');
    expect(indisponiveis.textContent).toContain('Pesquisa web');
    expect(fixture.nativeElement.textContent).toContain('Copiloto indisponível');
  });
});
