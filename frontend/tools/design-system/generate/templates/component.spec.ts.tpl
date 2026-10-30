import { TestBed } from '@angular/core/testing';

import { {{CLASS_NAME}}Component } from './{{NAME}}.component';

describe('{{CLASS_NAME}}Component', () => {
  it('creates the standalone ces-{{NAME}} host', async () => {
    await TestBed.configureTestingModule({ imports: [{{CLASS_NAME}}Component] }).compileComponents();
    const fixture = TestBed.createComponent({{CLASS_NAME}}Component);
    fixture.componentRef.setInput('ariaLabel', 'Exemplo acessível');
    fixture.detectChanges();

    expect(fixture.componentInstance).toBeTruthy();
    expect(fixture.nativeElement.tagName).toBe('CES-{{NAME}}');
    expect(fixture.nativeElement.getAttribute('aria-label')).toBe('Exemplo acessível');
  });

  it.todo('covers public inputs, outputs, keyboard behavior, relevant states, and cleanup');
  it.todo('adds automated accessibility and catalog integration coverage');
});
