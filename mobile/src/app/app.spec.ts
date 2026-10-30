import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { AppUpdateService } from './core/sync/app-update.service';
import { provideRouter } from '@angular/router';
import { SwUpdate } from '@angular/service-worker';
import { Subject } from 'rxjs';
import { SessaoService } from './core/sessao/sessao.service';
import { PlataformaService } from './core/plataforma/plataforma.service';
import { TelemetriaEnvioService } from './core/sessao/telemetria-envio.service';

describe('App', () => {
  beforeEach(async () => {
    const appUpdateSpy = jasmine.createSpyObj('AppUpdateService', ['init']);

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        { provide: AppUpdateService, useValue: appUpdateSpy },
        { provide: SessaoService, useValue: jasmine.createSpyObj('SessaoService', ['iniciar']) },
        { provide: TelemetriaEnvioService, useValue: { enviarPendentes: () => Promise.resolve(0) } },
        { provide: PlataformaService, useValue: { iniciar: () => {}, instalado: () => true, sugerirInstalacao: () => false } },
        {
          provide: SwUpdate,
          useValue: {
            versionUpdates: new Subject(),
            checkForUpdate: () => Promise.resolve(false)
          }
        }
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });
});
