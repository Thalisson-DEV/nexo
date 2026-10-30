import { CesTone } from '@ces/ui';
import { ContextoTag, TipoAlertaManual } from './central-treinamento.models';

/**
 * Apresentação do domínio de treinamento.
 *
 * O conteúdo dos módulos é dado de negócio; cor, ícone e rótulo pertencem a este arquivo
 * e sempre derivam de tons semânticos do Nexo. Cada tom acompanha um rótulo textual,
 * de modo que a cor nunca seja o único indicador do tipo de alerta.
 */
const APRESENTACAO_ALERTA: Record<
  TipoAlertaManual,
  { tom: CesTone; rotulo: string; icone: string }
> = {
  'regra-ouro': { tom: 'brand', rotulo: 'Regra de ouro', icone: 'pi-star' },
  atencao: { tom: 'warning', rotulo: 'Atenção', icone: 'pi-exclamation-triangle' },
  seguranca: { tom: 'info', rotulo: 'Segurança', icone: 'pi-lock' },
  dica: { tom: 'success', rotulo: 'Dica', icone: 'pi-lightbulb' }
};

export function tomAlerta(tipo: TipoAlertaManual): CesTone {
  return APRESENTACAO_ALERTA[tipo].tom;
}

export function rotuloAlerta(tipo: TipoAlertaManual): string {
  return APRESENTACAO_ALERTA[tipo].rotulo;
}

export function iconeAlerta(tipo: TipoAlertaManual, icone?: string): string {
  return icone?.trim() || APRESENTACAO_ALERTA[tipo].icone;
}

const ICONE_CONTEXTO: Record<ContextoTag, string> = {
  Web: 'pi-desktop',
  Mobile: 'pi-mobile',
  'Mobile / Offline': 'pi-wifi',
  'Geral / Web': 'pi-desktop'
};

export function iconeContexto(contexto: ContextoTag): string {
  return ICONE_CONTEXTO[contexto] ?? 'pi-desktop';
}
