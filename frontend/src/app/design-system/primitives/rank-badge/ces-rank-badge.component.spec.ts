import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { CesRankBadgeComponent } from './ces-rank-badge.component';

describe('CesRankBadgeComponent', () => {
  it('destaca o pódio com ícone e sempre anuncia a posição em texto', async () => {
    await TestBed.configureTestingModule({ imports: [CesRankBadgeComponent] }).compileComponents();
    const fixture = TestBed.createComponent(CesRankBadgeComponent);
    fixture.componentRef.setInput('position', 1);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.getAttribute('data-podium')).toBe('1');
    expect(el.querySelector('.pi-trophy')).not.toBeNull();
    expect(el.querySelector('.ces-sr-only')!.textContent).toBe('1º lugar');

    fixture.componentRef.setInput('position', 7);
    fixture.detectChanges();
    expect(el.getAttribute('data-podium')).toBeNull();
    expect(el.textContent).toContain('7');
  });
});
