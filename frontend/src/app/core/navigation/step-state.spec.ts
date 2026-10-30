import { Component, OnInit, signal } from '@angular/core';
import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';
import { bindStepToUrl } from './step-state';
import { openRoute, settleNavigation } from './testing';

let permitir = true;

@Component({ template: '', standalone: true })
class WizardHost implements OnInit {
  readonly passo = signal(1);
  readonly url = bindStepToUrl(this.passo);
  ngOnInit(): void {
    this.url.restore(() => permitir);
  }
}

describe('bindStepToUrl', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
    permitir = true;
  });

  function setup(): void {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: '**', component: WizardHost }]), provideLocationMocks()],
    });
  }

  it('grava o passo com replace (passo 1 omitido) e restaura no F5', async () => {
    setup();
    const fixture = await openRoute('/obras/nova?x=1', WizardHost);
    fixture.componentInstance.passo.set(3);
    await settleNavigation();
    const location = TestBed.inject(Location);
    expect(location.path()).toBe('/obras/nova?x=1&passo=3');

    fixture.componentInstance.passo.set(1);
    await settleNavigation();
    expect(location.path()).toBe('/obras/nova?x=1');
  });

  it('restaura o passo só quando os dados o sustentam', async () => {
    setup();
    let fixture = await openRoute('/obras/nova?passo=2', WizardHost);
    expect(fixture.componentInstance.passo()).toBe(2);

    TestBed.resetTestingModule();
    permitir = false;
    setup();
    fixture = await openRoute('/obras/nova?passo=2', WizardHost);
    expect(fixture.componentInstance.passo()).toBe(1);
    expect(TestBed.inject(Location).path()).toBe('/obras/nova');
  });
});
