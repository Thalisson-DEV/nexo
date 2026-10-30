import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';

import { RankingEventoItem } from '../../../core/models/sst-evento.models';
import {
  LOCAIS_LESAO_MIGRATION,
  PontoRegiaoCorpo,
  RegiaoCorpo,
  RegiaoCorpoConfig,
  REGIOES_CORPO_POR_ID,
  regiaoDoLocal
} from './mapa-corpo-lesoes.config';

export { regiaoDoLocal } from './mapa-corpo-lesoes.config';
export type { RegiaoCorpo } from './mapa-corpo-lesoes.config';

interface DetalheRegiao {
  id: string;
  nome: string;
  total: number;
}

interface RegiaoPintada {
  regiao: RegiaoCorpo;
  config: RegiaoCorpoConfig;
  total: number;
  intensidade: number;
  detalhes: DetalheRegiao[];
}

@Component({
  selector: 'app-mapa-corpo-lesoes',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: './mapa-corpo-lesoes.component.html',
  styleUrl: './mapa-corpo-lesoes.component.css'
})
export class MapaCorpoLesoesComponent {
  readonly itens = input.required<RankingEventoItem[]>();
  readonly regiaoSelecionada = output<RegiaoCorpo>();

  protected readonly assetCorpo = '/assets/sst/mapa-corporal-anatomico-v1.png';
  protected readonly totalLocaisCatalogo = LOCAIS_LESAO_MIGRATION.length;
  protected readonly destacada = signal<RegiaoCorpo | null>(null);

  private readonly agrupado = computed(() => {
    const porRegiao = new Map<RegiaoCorpo, RegiaoPintada>();
    const foraDoMapa: RankingEventoItem[] = [];

    for (const item of this.itens()) {
      const regiao = regiaoDoLocal(item.nome ?? '');
      const config = regiao ? REGIOES_CORPO_POR_ID.get(regiao) : null;
      if (!regiao || !config) {
        foraDoMapa.push(item);
        continue;
      }

      const atual = porRegiao.get(regiao);
      if (atual) {
        atual.total += item.total;
        atual.detalhes.push({ id: item.id, nome: item.nome, total: item.total });
      } else {
        porRegiao.set(regiao, {
          regiao,
          config,
          total: item.total,
          intensidade: 0,
          detalhes: [{ id: item.id, nome: item.nome, total: item.total }]
        });
      }
    }

    const maior = Math.max(1, ...[...porRegiao.values()].map((item) => item.total));
    porRegiao.forEach((item) => (item.intensidade = item.total / maior));
    return { porRegiao, foraDoMapa };
  });

  protected readonly regioesComLesao = computed(() =>
    [...this.agrupado().porRegiao.values()].sort((a, b) => b.total - a.total)
  );

  protected readonly regioesCorporais = computed(() =>
    this.regioesComLesao().filter((item) => item.config.pontos.length > 0)
  );

  protected readonly regioesComplementares = computed(() =>
    this.regioesComLesao().filter((item) => item.config.pontos.length === 0)
  );

  protected readonly naoMapeados = computed(() => this.agrupado().foraDoMapa);

  protected readonly totalLesoes = computed(() =>
    this.itens().reduce((total, item) => total + item.total, 0)
  );

  protected readonly descricaoAcessivel = computed(() => {
    const regioes = this.regioesComLesao();
    if (!regioes.length) return 'Mapa anatômico sem lesões registradas no período.';
    return `Mapa anatômico. ${regioes.map((item) => `${item.config.rotulo}: ${item.total}`).join('; ')}.`;
  });

  protected destacar(regiao: RegiaoCorpo | null): void {
    this.destacada.set(regiao);
  }

  protected selecionar(regiao: RegiaoCorpo): void {
    this.destacada.set(regiao);
    this.regiaoSelecionada.emit(regiao);
  }

  protected corDaIntensidade(intensidade: number): string {
    const escala = ['#fee2e2', '#fecaca', '#fca5a5', '#f87171', '#ef4444', '#b91c1c'];
    const indice = Math.min(
      escala.length - 1,
      Math.max(0, Math.ceil(intensidade * escala.length) - 1)
    );
    return escala[indice];
  }

  protected tamanhoPonto(ponto: PontoRegiaoCorpo, intensidade: number): number {
    return (ponto.tamanho ?? 27) + Math.round(intensidade * 5);
  }

  protected titulo(regiao: RegiaoPintada): string {
    const detalhes = regiao.detalhes.map((item) => `${item.nome}: ${item.total}`).join(', ');
    return `${regiao.config.rotulo}: ${regiao.total}. ${detalhes}`;
  }
}
