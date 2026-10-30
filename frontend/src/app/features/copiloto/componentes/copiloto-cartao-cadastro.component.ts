import { ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { Router } from '@angular/router';
import { CesButtonComponent, CesIconButtonComponent, CesStatusComponent, CesTone } from '@ces/ui';
import { CesCalloutComponent, NotificationService } from '@ces/ui/feedback';
import {
  AcaoCartao,
  AlteracaoCartao,
  PreviaCadastro,
  RegistroCadastro,
  RegistroGravado,
} from '../../../core/models/assistente.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AssistenteService } from '../../../core/services/assistente.service';
import { AuthService } from '../../../core/services/auth.service';
import { resolveChatNavigation } from '../navegacao/copiloto-navegacao';
import { COPILOTO_DESTINOS } from '../navegacao/copiloto-destinos';
import { PropostaAcaoView } from '../copiloto-pacotes';
import { CopilotoStore } from '../copiloto.store';
import { AjusteCampo, CopilotoCampoCadastroComponent } from './copiloto-campo-cadastro.component';

type Ocupado = 'revisando' | 'confirmando' | 'cancelando' | null;

interface LinkRegistro {
  rotulo: string;
  route: string;
  queryParams: Record<string, string>;
}

/** Ícone do módulo no cabeçalho do cartão. */
const ICONE_MODULO: Record<string, string> = {
  'Pessoas e estrutura': 'pi-users',
  'Acessos': 'pi-key',
  'Almoxarifado': 'pi-box',
  'Financeiro': 'pi-wallet',
  'Descontos': 'pi-percentage',
  'Chamados': 'pi-comments',
  'Obras': 'pi-building',
  'Segurança do trabalho': 'pi-shield',
};

/**
 * Cartão de confirmação de um cadastro preparado pelo Copiloto: mostra os dados com o que falta, deixa
 * corrigir cada campo ali mesmo e só grava quando a pessoa confirma.
 */
@Component({
  selector: 'app-copiloto-cartao-cadastro',
  standalone: true,
  imports: [CesButtonComponent, CesIconButtonComponent, CesStatusComponent, CesCalloutComponent, CopilotoCampoCadastroComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './copiloto-cartao-cadastro.component.html',
  styleUrl: './copiloto-cartao-cadastro.component.css',
})
export class CopilotoCartaoCadastroComponent {
  private readonly api = inject(AssistenteService);
  private readonly store = inject(CopilotoStore);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly notificacao = inject(NotificationService);

  readonly proposta = input.required<PropostaAcaoView>();

  protected readonly ocupado = signal<Ocupado>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  /** Senhas provisórias: só existem na resposta da confirmação e nunca voltam do servidor. */
  protected readonly senhas = signal<{ registro: string; rotulo: string; valor: string }[]>([]);
  protected readonly senhaVisivel = signal<Record<number, boolean>>({});

  protected readonly acao = computed<AcaoCartao>(() => {
    const proposta = this.proposta();
    return this.store.acoesCartao()[proposta.acaoId] ?? {
      acaoId: proposta.acaoId,
      ferramenta: proposta.ferramenta,
      status: 'PENDENTE',
      previa: proposta.previa as PreviaCadastro,
    };
  });
  protected readonly previa = computed(() => {
    const atual = this.acao().previa;
    return Array.isArray(atual?.registros) ? atual : (this.proposta().previa as PreviaCadastro);
  });
  protected readonly status = computed(() => this.acao().status);
  protected readonly pendente = computed(() => this.status() === 'PENDENTE');
  protected readonly lote = computed(() => (this.previa()?.registros.length ?? 0) > 1);
  protected readonly icone = computed(() => 'pi ' + (ICONE_MODULO[this.previa()?.modulo ?? ''] ?? 'pi-file-edit'));

  /** Registro aberto no lote: começa no primeiro que tem pendência. */
  protected readonly aberto = linkedSignal<number>(() => {
    const registros = this.previa()?.registros ?? [];
    const comPendencia = registros.findIndex((r) => !r.pronto);
    return comPendencia >= 0 ? comPendencia : registros.length > 1 ? -1 : 0;
  });

  protected readonly pendencias = computed(() =>
    (this.previa()?.registros ?? []).reduce((total, r) => total + (r.pronto ? 0 : Math.max(r.pendencias, 1)), 0),
  );

  protected readonly selo = computed<{ tom: CesTone; texto: string; icone: string }>(() => {
    switch (this.status()) {
      case 'CONFIRMADA': {
        const falhas = this.acao().resultado?.falhas ?? 0;
        return falhas
          ? { tom: 'warning', texto: 'Gravado em parte', icone: 'pi pi-exclamation-circle' }
          : { tom: 'success', texto: this.previa()?.modo === 'EDITAR' ? 'Atualizado' : 'Cadastrado', icone: 'pi pi-check' };
      }
      case 'FALHOU':
        return { tom: 'danger', texto: 'Não gravado', icone: 'pi pi-times-circle' };
      case 'REJEITADA':
        return { tom: 'neutral', texto: 'Cancelado', icone: 'pi pi-ban' };
      case 'EXPIRADA':
        return { tom: 'neutral', texto: 'Sem efeito', icone: 'pi pi-clock' };
      default:
        return this.previa()?.pronta
          ? { tom: 'info', texto: 'Pronto para confirmar', icone: 'pi pi-check-circle' }
          : { tom: 'warning', texto: 'Falta completar', icone: 'pi pi-pencil' };
    }
  });

  protected readonly subtitulo = computed(() => {
    const previa = this.previa();
    if (!previa) return '';
    const resultado = this.acao().resultado;
    if (resultado && !this.pendente()) {
      const gravados = resultado.gravados === 1 ? '1 gravado' : `${resultado.gravados} gravados`;
      return resultado.falhas ? `${previa.modulo} · ${gravados}, ${resultado.falhas} não` : `${previa.modulo} · ${gravados}`;
    }
    if (previa.registros.length > 1) {
      return `${previa.modulo} · ${previa.registros.length} registros, ${previa.prontos} prontos`;
    }
    return `${previa.modulo} · ${previa.registros[0]?.titulo ?? previa.registro}`;
  });

  protected readonly rotuloConfirmar = computed(() => {
    const previa = this.previa();
    if (!previa) return 'Confirmar';
    if (previa.modo === 'EDITAR') return 'Salvar alterações';
    return previa.registros.length > 1 ? `Cadastrar ${previa.registros.length}` : 'Confirmar cadastro';
  });

  protected readonly resumoPendencias = computed(() => {
    const n = this.pendencias();
    if (!n) return 'Revise os dados antes de confirmar. Nada foi gravado ainda.';
    return n === 1 ? 'Falta 1 informação para confirmar.' : `Faltam ${n} informações para confirmar.`;
  });

  protected readonly gravados = computed(() =>
    (this.acao().resultado?.registros ?? []).filter((r) => r.status === 'GRAVADO').map((r) => ({ ...r, link: this.link(r) })),
  );
  protected readonly falhas = computed(() => (this.acao().resultado?.registros ?? []).filter((r) => r.status === 'FALHOU'));

  protected alternar(indice: number): void {
    this.aberto.set(this.aberto() === indice ? -1 : indice);
  }

  protected rotuloRegistro(registro: RegistroCadastro): string {
    if (registro.pronto) return 'Pronto';
    const n = Math.max(registro.pendencias, 1);
    return n === 1 ? '1 pendência' : `${n} pendências`;
  }

  protected ajustar(registro: number, ajuste: AjusteCampo): void {
    this.revisar([{ registro, caminho: ajuste.caminho, valor: ajuste.valor, remover: ajuste.remover }]);
  }

  protected removerRegistro(registro: number): void {
    this.revisar([{ registro, remover: true }]);
  }

  private revisar(alteracoes: AlteracaoCartao[]): void {
    if (!this.pendente() || this.ocupado() === 'confirmando') return;
    this.ocupado.set('revisando');
    this.erro.set(null);
    this.aviso.set(null);
    this.api.revisarAcao(this.proposta().acaoId, alteracoes).subscribe({
      next: (acao) => {
        this.store.atualizarAcao(acao);
        this.ocupado.set(null);
      },
      error: (erro) => {
        this.ocupado.set(null);
        this.tratarErro(erro);
      },
    });
  }

  protected confirmar(): void {
    if (!this.pendente() || this.ocupado()) return;
    this.ocupado.set('confirmando');
    this.erro.set(null);
    this.aviso.set(null);
    this.api.confirmarAcao(this.proposta().acaoId).subscribe({
      next: (acao) => {
        this.ocupado.set(null);
        this.senhas.set(acao.sigiloso?.senhas ?? []);
        this.store.atualizarAcao({ ...acao, sigiloso: null });
        if (acao.status === 'PENDENTE' && acao.mensagem) {
          if (/falta/i.test(acao.mensagem)) this.aviso.set(acao.mensagem);
          else this.erro.set(acao.mensagem);
        }
      },
      error: (erro) => {
        this.ocupado.set(null);
        this.tratarErro(erro);
      },
    });
  }

  protected cancelar(): void {
    if (!this.pendente() || this.ocupado()) return;
    this.ocupado.set('cancelando');
    this.erro.set(null);
    this.api.cancelarAcao(this.proposta().acaoId).subscribe({
      next: (acao) => {
        this.ocupado.set(null);
        this.store.atualizarAcao(acao);
      },
      error: (erro) => {
        this.ocupado.set(null);
        this.tratarErro(erro);
      },
    });
  }

  private tratarErro(erro: unknown): void {
    const mensagem = getErrorMessage(erro);
    this.erro.set(mensagem);
    // Cartão decidido em outra aba: busca o estado atual.
    const conversa = this.store.conversaId();
    if (conversa && /confirmado|cancelado|expirou/i.test(mensagem)) {
      this.api.listarAcoes(conversa).subscribe({
        next: (acoes) => acoes.forEach((acao) => this.store.atualizarAcao(acao)),
        error: () => undefined,
      });
    }
  }

  protected abrir(link: LinkRegistro): void {
    void this.router.navigate([link.route], { queryParams: link.queryParams });
  }

  protected alternarSenha(indice: number): void {
    this.senhaVisivel.update((atual) => ({ ...atual, [indice]: !atual[indice] }));
  }

  protected async copiarSenha(valor: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(valor);
      this.notificacao.success('Senha copiada.');
    } catch {
      this.notificacao.error('Não foi possível copiar. Selecione e copie a senha.');
    }
  }

  private link(registro: RegistroGravado): LinkRegistro | null {
    if (!registro.destino) return null;
    const alvo = COPILOTO_DESTINOS[registro.destino];
    if (!alvo) return null;
    const destino = resolveChatNavigation(
      {
        tipo: 'NAVIGATE',
        chaveDestino: registro.destino,
        rotulo: registro.rotulo ?? registro.titulo,
        autoridade: alvo.routeAuthority,
        parametros: registro.parametros ?? {},
      },
      this.auth.currentUser()?.permissoes ?? [],
    );
    if (!destino) return null;
    return {
      rotulo: alvo.parameters.length ? 'Abrir' : 'Ver na lista',
      route: destino.route,
      queryParams: destino.queryParams,
    };
  }
}
