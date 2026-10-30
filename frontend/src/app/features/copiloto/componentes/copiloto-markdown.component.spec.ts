import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CopilotoMarkdownComponent } from './copiloto-markdown.component';

describe('CopilotoMarkdownComponent', () => {
  let fixture: ComponentFixture<CopilotoMarkdownComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CopilotoMarkdownComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(CopilotoMarkdownComponent);
  });

  it('renderiza os principais elementos do GitHub Flavored Markdown', () => {
    fixture.componentRef.setInput('content', [
      '# Resumo',
      '',
      '- [x] Item concluído',
      '- [ ] Item pendente',
      '',
      '| Equipe | Total |',
      '| --- | ---: |',
      '| Norte | 12 |',
      '',
      'Use `npm test` e ~~ignore~~ este texto.',
      '',
      '```ts',
      'const total = 12;',
      '```'
    ].join('\n'));
    fixture.detectChanges();

    const markdown = fixture.nativeElement.querySelector('.markdown-body') as HTMLElement;

    expect(markdown.querySelector('h1')?.textContent).toBe('Resumo');
    expect(markdown.querySelectorAll('[role="checkbox"]')).toHaveLength(2);
    expect(markdown.querySelector('[aria-checked="true"]')).toBeTruthy();
    expect(markdown.querySelector('table tbody td')?.textContent).toBe('Norte');
    expect(markdown.querySelector('table tbody td:last-child')?.getAttribute('align')).toBe('right');
    expect(markdown.querySelector('.markdown-table')?.getAttribute('role')).toBe('region');
    expect(markdown.querySelector('code')?.textContent).toBe('npm test');
    expect(markdown.querySelector('del')?.textContent).toBe('ignore');
    expect(markdown.querySelector('pre code')?.textContent).toContain('const total = 12;');
  });

  it('sanitiza HTML perigoso sem remover a formatação segura', () => {
    fixture.componentRef.setInput(
      'content',
      '**Seguro** <img src="x" onerror="alert(1)"> <script>alert(2)</script>'
    );
    fixture.detectChanges();

    const markdown = fixture.nativeElement.querySelector('.markdown-body') as HTMLElement;
    const image = markdown.querySelector('img');

    expect(markdown.querySelector('strong')?.textContent).toBe('Seguro');
    expect(image?.hasAttribute('onerror')).toBe(false);
    expect(markdown.querySelector('script')).toBeNull();
  });

  it('abre links externos com segurança e transforma citações conhecidas em marcadores', () => {
    fixture.componentRef.setInput('content', 'Conforme a norma [1] e o item [7]. Veja [site](https://www.gov.br/nr10).');
    fixture.componentRef.setInput('fontes', [{ numero: 1, url: 'https://www.gov.br/nr10', titulo: 'NR-10' }]);
    fixture.detectChanges();

    const markdown = fixture.nativeElement.querySelector('.markdown-body') as HTMLElement;
    const citacao = markdown.querySelector('sup a.markdown-citacao') as HTMLAnchorElement;
    expect(citacao.textContent).toBe('1');
    expect(citacao.getAttribute('aria-label')).toBe('Fonte 1: NR-10');
    expect(citacao.rel).toBe('noopener noreferrer');
    expect(markdown.textContent).toContain('[7]');
    const link = [...markdown.querySelectorAll('a')].find((a) => a.textContent === 'site') as HTMLAnchorElement;
    expect(link.target).toBe('_blank');
  });
});