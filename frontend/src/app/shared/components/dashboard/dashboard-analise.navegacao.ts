import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { CesDrillStateService } from '@ces/ui';
import { AlvoAnalise } from './dashboard-analise.models';

interface PassoAnalise {
  readonly alvo: AlvoAnalise;
  readonly titulo: string;
}

/**
 * Navegação da análise detalhada sobre o `CesDrillStateService`: o alvo atual fica em `?drill=`
 * (link compartilhável, Voltar do navegador fecha) e a trilha das análises anteriores fica em memória,
 * para "Voltar" dentro do painel retornar à análise de onde o usuário aprofundou.
 *
 * Forneça por componente: `providers: [CesDrillStateService, NavegacaoAnaliseService]`.
 */
@Injectable()
export class NavegacaoAnaliseService {
  private readonly drill = inject(CesDrillStateService);
  private readonly pilha = signal<readonly PassoAnalise[]>([]);

  readonly alvo = this.drill.target;
  readonly trilha = computed(() => this.pilha().map((p) => p.titulo));

  constructor() {
    // Painel fechado por fora (Voltar do navegador, link sem drill): a trilha não vale mais.
    effect(() => {
      if (!this.drill.target()) this.pilha.set([]);
    });
  }

  /** Abre uma análise a partir do dashboard (começa uma trilha nova). */
  abrir(dimension: string, value: string): void {
    this.pilha.set([]);
    this.drill.open({ dimension, value });
  }

  /** Aprofunda a partir da análise atual; "Voltar" retorna a ela. */
  aprofundar(alvo: AlvoAnalise, tituloAtual: string): void {
    const atual = this.drill.target();
    if (atual) this.pilha.update((p) => [...p, { alvo: atual, titulo: tituloAtual }]);
    this.drill.open(alvo);
  }

  /** Troca para uma análise vizinha (dia seguinte, outra equipe) sem aumentar a trilha. */
  trocar(alvo: AlvoAnalise): void {
    this.drill.open(alvo);
  }

  voltar(passos = 1): void {
    const pilha = this.pilha();
    const destino = pilha[pilha.length - passos];
    if (!destino) return;
    this.pilha.set(pilha.slice(0, pilha.length - passos));
    this.drill.open(destino.alvo);
  }

  fechar(): void {
    this.pilha.set([]);
    this.drill.close();
  }
}
