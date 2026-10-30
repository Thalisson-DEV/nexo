import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { TurnoService } from '../../core/turno/turno.service';
import { ToastService } from '../../core/ui/toast.service';
import { AppUpdateService } from '../../core/sync/app-update.service';
import { TemaService, Tema, Contraste } from '../../core/plataforma/tema.service';
import { OutboxService } from '../../core/sync/outbox.service';
import { SyncService } from '../../core/sync/sync.service';
import { ConnectivityService } from '../../core/sync/connectivity.service';
import { SessaoService } from '../../core/sessao/sessao.service';
import { DbService } from '../../core/db/db.service';
import { environment } from '../../../environments/environment';
import { FolhaComponent, IconeComponent, IndicadorRedeComponent, OpcaoSegmento, SegmentadoComponent, TituloGrandeComponent } from '../../ui';

/**
 * Conta: quem está no aparelho, envios, rede, aparência, atualização e sair.
 * Regras mantidas: sair só com o turno encerrado; redes móveis e modo offline
 * continuam nas mesmas chaves do ConnectivityService.
 */
@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [FolhaComponent, IconeComponent, IndicadorRedeComponent, SegmentadoComponent, TituloGrandeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ces-titulo-grande titulo="Conta" [subtitulo]="rede.isOnline() ? 'Conectado' : 'Trabalhando sem internet'">
      <ces-indicador-rede />
    </ces-titulo-grande>

    <section class="identidade" aria-label="Quem está no aparelho">
      <span class="avatar" aria-hidden="true">{{ inicial() }}</span>
      <span class="quem">
        <strong>{{ nome() }}</strong>
        <span>{{ descricaoAcesso() }}</span>
      </span>
      <span class="turno" [class.aberto]="!!turno.turnoAtivo()">{{ turno.turnoAtivo() ? 'Turno aberto' : 'Sem turno' }}</span>
    </section>

    <h2 class="ces-secao">Envios</h2>
    <ul class="ces-grupo">
      <li><div class="ces-linha">
        <span class="ces-selo" [class.ces-selo--atencao]="outbox.pendingCount() > 0" [class.ces-selo--ok]="!outbox.pendingCount()"><ces-icone nome="nuvem" /></span>
        <span class="ces-linha__texto">
          <span class="ces-linha__titulo ces-num">{{ resumoFila() }}</span>
          <span class="ces-linha__apoio">{{ ultimaSincronizacao() }}</span>
        </span>
      </div></li>
      <li><button type="button" class="ces-linha ces-linha--acao" (click)="sincronizar()" [disabled]="sync.isSyncing() || !rede.isOnline()">
        <span class="ces-selo ces-selo--destaque"><ces-icone nome="atualizar" [class.girando]="sync.isSyncing()" /></span>
        <span class="ces-linha__texto">
          <span class="ces-linha__titulo">{{ sync.isSyncing() ? 'Sincronizando…' : 'Sincronizar agora' }}</span>
          <span class="ces-linha__apoio">{{ rede.isOnline() ? 'Envia o que falta e atualiza os dados' : 'Precisa de internet' }}</span>
        </span>
      </button></li>
    </ul>

    <h2 class="ces-secao">Rede</h2>
    <ul class="ces-grupo">
      <li><button type="button" role="switch" class="ces-linha ces-linha--acao interruptor" [attr.aria-checked]="rede.usarRedesMoveis()" (click)="alternarRedesMoveis()">
        <span class="ces-linha__texto">
          <span class="ces-linha__titulo">Usar dados móveis</span>
          <span class="ces-linha__apoio quebra">{{ rede.usarRedesMoveis() ? 'Envia também pelo 4G/5G' : 'Envia só no Wi-Fi' }}</span>
        </span>
        <span class="trilho" aria-hidden="true"><span></span></span>
      </button></li>
      <li><button type="button" role="switch" class="ces-linha ces-linha--acao interruptor" [attr.aria-checked]="rede.forcarOffline()" (click)="alternarOffline()">
        <span class="ces-linha__texto">
          <span class="ces-linha__titulo">Trabalhar sem internet</span>
          <span class="ces-linha__apoio quebra">{{ rede.forcarOffline() ? 'Nada é enviado até você desligar' : 'O app decide pela conexão' }}</span>
        </span>
        <span class="trilho" aria-hidden="true"><span></span></span>
      </button></li>
    </ul>
    @if (rede.isBloqueadoPorDadosMoveis()) {
      <p class="aviso"><ces-icone nome="alerta" [tamanho]="18" />Você está no 4G com dados móveis desligados. Tudo fica guardado no aparelho até conectar no Wi-Fi.</p>
    }

    <h2 class="ces-secao" id="titulo-aparencia">Aparência</h2>
    <div class="ces-grupo aparencia" role="group" aria-labelledby="titulo-aparencia">
      <ces-segmentado rotulo="Tema" [opcoes]="opcoesTema" [valor]="tema.tema()" (valorChange)="tema.definirTema($event)" />
      <ces-segmentado rotulo="Contraste" [opcoes]="opcoesContraste" [valor]="tema.contraste()" (valorChange)="tema.definirContraste($event)" />
      <p class="ajuda">Alto contraste deixa o texto mais forte para usar no sol.</p>
    </div>

    <h2 class="ces-secao">Aplicativo</h2>
    <ul class="ces-grupo">
      <li><button type="button" class="ces-linha ces-linha--acao" (click)="buscarAtualizacao()" [disabled]="checkingUpdate()">
        <span class="ces-selo"><ces-icone nome="baixar" /></span>
        <span class="ces-linha__texto">
          <span class="ces-linha__titulo">{{ checkingUpdate() ? 'Procurando…' : 'Buscar atualização' }}</span>
          <span class="ces-linha__apoio ces-num">Versão {{ versao }}</span>
        </span>
        <ces-icone nome="avancar" [tamanho]="18" />
      </button></li>
      <li><button type="button" class="ces-linha ces-linha--acao sair" (click)="tentarLogout()" [attr.aria-disabled]="!!turno.turnoAtivo()">
        <span class="ces-selo ces-selo--erro"><ces-icone nome="sair" /></span>
        <span class="ces-linha__texto">
          <span class="ces-linha__titulo">Sair da conta</span>
          <span class="ces-linha__apoio quebra">{{ turno.turnoAtivo() ? 'Encerre o turno na tela Hoje antes de sair' : 'Encerra a sessão neste aparelho' }}</span>
        </span>
      </button></li>
    </ul>

    <ces-folha [aberta]="confirmarSaida()" (fechar)="fecharSaida()" titulo="Sair da conta?"
      descricao="Para entrar de novo você vai precisar de internet.">
      @if (outbox.pendingCount() > 0) {
        <p class="aviso folha-aviso"><ces-icone nome="alerta" [tamanho]="18" />
          {{ outbox.pendingCount() }} {{ outbox.pendingCount() === 1 ? 'envio ainda não saiu' : 'envios ainda não saíram' }}. Ficam guardados neste aparelho.</p>
      }
      <div rodape class="folha-rodape">
        <button type="button" class="ces-btn ces-btn--neutro" (click)="fecharSaida()">Ficar</button>
        <button type="button" class="ces-btn ces-btn--perigo" (click)="fazerLogout()">Sair</button>
      </div>
    </ces-folha>
  `,
  styles: [`
    :host { display: block; padding-bottom: var(--ces-e6, 32px); }
    .identidade { display: flex; align-items: center; gap: var(--ces-e3); margin: 0 var(--ces-e3); padding: var(--ces-e4);
      background: var(--ces-superficie); border-radius: var(--ces-r-grupo); }
    .avatar { width: 52px; height: 52px; border-radius: 50%; display: grid; place-items: center; flex: none;
      background: var(--ces-destaque-suave); color: var(--ces-destaque); font: 700 1.375rem/1 var(--ces-fonte); }
    .quem { flex: 1; min-width: 0; display: grid; gap: 2px; }
    .quem strong { font: var(--ces-t-corpo-forte); overflow-wrap: anywhere; }
    .quem span { font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .turno { flex: none; padding: 4px 10px; border-radius: var(--ces-r-pilula); font: 600 .75rem/1.4 var(--ces-fonte);
      background: var(--ces-superficie-2); color: var(--ces-texto-2); }
    .turno.aberto { background: var(--ces-ok-suave); color: var(--ces-ok); }
    button.ces-linha { width: 100%; border: 0; background: transparent; color: inherit; text-align: left; font: inherit; }
    button.ces-linha:disabled { opacity: .55; cursor: default; }
    .quebra { white-space: normal; }
    .girando { display: inline-flex; animation: girar 1s linear infinite; }
    @keyframes girar { to { transform: rotate(360deg); } }
    @media (prefers-reduced-motion: reduce) { .girando { animation: none; } }
    .trilho { width: 48px; height: 28px; border-radius: 14px; background: var(--ces-linha-forte); position: relative; flex: none; transition: background-color var(--ces-m-entrar); }
    .trilho span { position: absolute; top: 3px; left: 3px; width: 22px; height: 22px; border-radius: 50%; background: #fff; transition: transform var(--ces-m-entrar) var(--ces-curva); }
    [aria-checked='true'] .trilho { background: var(--ces-destaque); }
    [aria-checked='true'] .trilho span { transform: translateX(20px); }
    .aviso { display: flex; gap: var(--ces-e2); align-items: flex-start; margin: var(--ces-e2) var(--ces-e3) 0; padding: var(--ces-e3) var(--ces-e4);
      border-radius: var(--ces-r-grupo); background: var(--ces-atencao-suave); font: var(--ces-t-apoio); font-size: .875rem; }
    .folha-aviso { margin: 0 var(--ces-e4); }
    .aparencia { display: grid; gap: var(--ces-e3); padding: var(--ces-e4); }
    .ajuda { margin: 0; font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .sair[aria-disabled='true'] { opacity: .6; }
    .sair .ces-linha__titulo { color: var(--ces-erro); }
    .folha-rodape { display: flex; gap: var(--ces-e3); padding: var(--ces-e3) var(--ces-e4) 0; }
    .folha-rodape .ces-btn { flex: 1; min-height: var(--ces-alvo-principal); }
  `],
})
export class PerfilComponent implements OnInit {
  readonly auth = inject(AuthService);
  readonly turno = inject(TurnoService);
  readonly outbox = inject(OutboxService);
  readonly sync = inject(SyncService);
  readonly rede = inject(ConnectivityService);
  readonly tema = inject(TemaService);
  private readonly toast = inject(ToastService);
  private readonly appUpdate = inject(AppUpdateService);
  private readonly sessao = inject(SessaoService);
  private readonly db = inject(DbService);
  private readonly equipe = signal('');

  readonly versao = environment.versaoApp;
  readonly opcoesTema: OpcaoSegmento<Tema>[] = [
    { valor: 'claro', rotulo: 'Claro', icone: 'sol' },
    { valor: 'escuro', rotulo: 'Escuro', icone: 'lua' },
  ];
  readonly opcoesContraste: OpcaoSegmento<Contraste>[] = [
    { valor: 'normal', rotulo: 'Normal' },
    { valor: 'alto', rotulo: 'Alto contraste' },
  ];
  readonly checkingUpdate = signal(false);
  // A folha de confirmação volta aberta se o app for fechado com ela na tela.
  readonly confirmarSaida = signal(!!this.sessao.estadoTela<{ folha?: string }>().folha);

  readonly nome = computed(() => this.auth.currentUser()?.nome || 'Usuário');
  readonly descricaoAcesso = computed(() => {
    const tipo = this.auth.isCampo() ? 'Equipe de campo' : 'Administrativo';
    return this.equipe() ? `${tipo} · ${this.equipe()}` : tipo;
  });
  readonly inicial = computed(() => this.nome().trim().charAt(0).toUpperCase() || '?');
  readonly resumoFila = computed(() => {
    const n = this.outbox.pendingCount();
    const erros = this.outbox.errorCount();
    if (erros) return `${erros} ${erros === 1 ? 'envio precisa' : 'envios precisam'} de atenção`;
    if (!n) return 'Tudo enviado';
    return `${n} ${n === 1 ? 'envio aguardando' : 'envios aguardando'}`;
  });
  readonly ultimaSincronizacao = computed(() => {
    const em = this.sync.lastSyncAt();
    if (!em) return 'Ainda não sincronizou neste aparelho';
    const d = new Date(em);
    const hoje = new Date();
    const hora = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
    const mesmoDia = d.toDateString() === hoje.toDateString();
    return `Última sincronização ${mesmoDia ? 'hoje' : `em ${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}`} às ${hora}`;
  });

  ngOnInit(): void {
    // Ao reabrir o app direto aqui ninguém carregou o turno: sem isso "sair" ficava liberado com turno aberto.
    void this.turno.loadTurnoAtivo();
    if (this.auth.isCampo()) void this.db.getEquipe().then(e => this.equipe.set(e?.codigo ?? ''));
  }

  alternarRedesMoveis(): void {
    this.rede.setUsarRedesMoveis(!this.rede.usarRedesMoveis());
  }

  alternarOffline(): void {
    this.rede.setForcarOffline(!this.rede.forcarOffline());
  }

  async sincronizar(): Promise<void> {
    if (this.sync.isSyncing() || !this.rede.isOnline()) return;
    try {
      await this.sync.forceFullSync();
      this.toast.success(this.outbox.errorCount() ? 'Sincronizado. Há envios que precisam de atenção.' : 'Tudo sincronizado.');
    } catch {
      this.toast.error('Não foi possível sincronizar agora. Tente de novo em instantes.');
    }
  }

  async buscarAtualizacao(): Promise<void> {
    if (this.checkingUpdate()) return;
    this.checkingUpdate.set(true);
    const resultado = await this.appUpdate.checkForUpdateManual();
    switch (resultado) {
      case 'updated':
        this.toast.success('Atualização encontrada. Reabrindo no mesmo ponto…');
        // Grava sessão e rascunhos antes: o app reabre no mesmo ponto, já na versão nova.
        setTimeout(() => void this.appUpdate.aplicar(), 800);
        break;
      case 'current':
        this.toast.success('O app já está na versão mais recente.');
        this.checkingUpdate.set(false);
        break;
      // SW indisponível ou erro: limpa cache e recarrega como saída garantida.
      case 'disabled':
      case 'error':
        this.toast.info('Limpando cache e recarregando…');
        await this.appUpdate.hardReset();
        break;
    }
  }

  tentarLogout(): void {
    if (this.turno.turnoAtivo()) {
      this.toast.warning('Encerre seu turno antes de sair.');
      return;
    }
    this.confirmarSaida.set(true);
    this.sessao.definirEstadoTela({ folha: 'sair' });
  }

  fecharSaida(): void {
    this.confirmarSaida.set(false);
    this.sessao.definirEstadoTela({ folha: null });
  }

  fazerLogout(): void {
    this.sessao.definirEstadoTela({ folha: null });
    this.auth.logout();
  }
}
