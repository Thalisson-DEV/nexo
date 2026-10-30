import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { iconeAlerta, rotuloAlerta } from './central-treinamento-ui';
import { AlertaManual, ManualModulo, TipoAlertaManual } from './central-treinamento.models';

/**
 * Composição exclusiva de impressão do manual de treinamento.
 *
 * Não é a tela reaproveitada no papel: é um documento próprio, paginado em A4, sem
 * controles interativos, sem vídeo incorporado e sem dependência do tema ativo ou da
 * largura da viewport. As cores são literais e monocromáticas por serem intrínsecas ao
 * papel — tokens de tema deixariam o texto ilegível ao imprimir no modo escuro.
 */
@Component({
  selector: 'app-manual-treinamento-documento',
  standalone: true,
  templateUrl: './manual-treinamento-documento.component.html',
  styleUrl: './manual-treinamento-documento.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ManualTreinamentoDocumentoComponent {
  readonly modulos = input.required<readonly ManualModulo[]>();
  readonly escopo = input<string>('Todos os módulos');
  readonly emitidoEm = input<Date | undefined>();

  protected readonly rotuloAlerta = (tipo: TipoAlertaManual) => rotuloAlerta(tipo);

  protected readonly totalModulos = computed(() => this.modulos().length);

  /**
   * Normaliza a classe do ícone para o papel. Os ícones são decorativos: se a fonte não for
   * impressa, o rótulo textual ao lado continua carregando a informação.
   */
  protected classeIcone(icone: string | undefined, alternativa: string): string {
    const bruto = (icone ?? '').trim() || alternativa;
    return `pi ${bruto.replace(/^pi\s+/, '')}`;
  }

  protected classeIconeAlerta(alerta: AlertaManual): string {
    return this.classeIcone(iconeAlerta(alerta.tipo, alerta.icone), 'pi-info-circle');
  }

  protected readonly mostrarSumario = computed(() => this.modulos().length > 1);

  protected readonly emissaoFormatada = computed(() => {
    const data = this.emitidoEm() ?? new Date();
    return new Intl.DateTimeFormat('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric'
    }).format(data);
  });
}
