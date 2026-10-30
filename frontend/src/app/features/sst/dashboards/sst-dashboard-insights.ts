/**
 * Linguagem de gestão para os textos técnicos que o backend dos dashboards de SST devolve
 * (definições de indicadores e prioridades).
 */
export function toPlainDashboardLanguage(value: string): string {
  const translated = value
    .replace(/\bSLA\b/gi, 'prazo')
    .replace(/\bbacklog\b/gi, 'pendências em aberto')
    .replace(/\bda coorte\b/gi, 'do período selecionado')
    .replace(/\bcoorte\b/gi, 'período selecionado')
    .replace(/\bPareto\b/gi, 'lista dos itens com mais desvios')
    .replace(/\bP90\b/gi, 'prazo de 9 em cada 10 casos')
    .replace(/\bmediana\b/gi, 'tempo de metade dos casos')
    .replace(/exposição normalizada/gi, 'comparação entre áreas')
    .replace(/taxa normalizada/gi, 'quantidade a cada 100 inspeções')
    .replace(/taxa de resolução/gi, 'percentual de desvios resolvidos')
    .replace(/indicador operacional/gi, 'acompanhamento das correções')
    .replace(/data de referência/gi, 'fim do período selecionado')
    .replace(/histórico pendente/gi, 'tudo que permanecia pendente')
    .replace(/\s*\/\s*/g, ' em relação ao ');
  return translated ? translated.charAt(0).toUpperCase() + translated.slice(1) : translated;
}
