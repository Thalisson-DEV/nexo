import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { HomeComponent } from './home.component';

describe('HomeComponent', () => {
  let fixture: ComponentFixture<HomeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
  });

  it('apresenta as sete áreas do Nexo como links de navegação', () => {
    const links = fixture.nativeElement.querySelectorAll('a.module-link') as NodeListOf<HTMLAnchorElement>;

    expect(links).toHaveLength(7);
    expect(links[0].textContent).toContain('Almoxarifado');
    expect(links[0].getAttribute('href')).toBe('/almoxarifado/solicitacoes');
    expect(links[6].textContent).toContain('Chamados');
  });

  it('mantém uma hierarquia semântica com título e navegação nomeada', () => {
    const title = fixture.nativeElement.querySelector('h1') as HTMLHeadingElement | null;
    const navigation = fixture.nativeElement.querySelector('nav') as HTMLElement | null;

    expect(title?.textContent).toContain('Pessoas, ativos e operação');
    expect(navigation?.getAttribute('aria-labelledby')).toBe('module-index-title');
  });

  it('atualiza o destaque editorial ao focar outra área', () => {
    const links = fixture.nativeElement.querySelectorAll('a.module-link') as NodeListOf<HTMLAnchorElement>;

    links[1].dispatchEvent(new FocusEvent('focus'));
    fixture.detectChanges();

    const featured = fixture.nativeElement.querySelector('.brand-panel__focus') as HTMLElement | null;
    expect(featured?.textContent).toContain('Execução');
    expect(featured?.textContent).toContain('Acompanhe a operação onde ela acontece.');
  });
});
