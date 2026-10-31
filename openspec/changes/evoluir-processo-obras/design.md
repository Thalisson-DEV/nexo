## Context

O processo de Obras do Nexo (módulo `obras`) tem Obra com subprojetos, relacionamentos, anexos tipados, status e agente
responsável, além de importação de obras. O módulo `execucao` tem Turno (equipe, veículo, colaboradores, abertura/fechamento) e a
execução comercial por nota; o frontend tem listagem/detalhamento de turnos e o dashboard de turnos. `contrato` tem Ação com valor
versionado por vigência (`AcaoValor`), `estoque` tem Material, Estoque, Movimentação e Solicitação de Material (incluindo
solicitação técnica), `rh` tem Equipe, TipoEquipe, BaseOperacional (com coordenadas) e MetaFaturamentoEquipe, e `frota` integra
provedores de telemetria.

A operação usa hoje um sistema externo de referência para o ciclo técnico (planejamento sobre o PDF, programação, medição). O
levantamento desse sistema (somente leitura) serviu de insumo funcional; a interface segue o Design System Nexo e não copia a
referência. A execução de campo no app fica para a change `execucao-campo-obras`.

Restrições: white label, módulos isolados por eventos, BaseEntity com soft delete e versão, Problem Details, Design System
source-owned, offline-first no app, sem regressão em Obra/Turno comercial/Equipe/Solicitação de Material.

## Goals / Non-Goals

**Goals:**
- Modelo de dados único para o ciclo planejar → programar → executar → medir, com valores congelados e rastreáveis.
- Editor de planejamento sobre PDF fluido para obras grandes (centenas de pontos por obra, PDFs multipágina).
- Frentes de trabalho independentes após a fundação (kits + modelo de planejamento), permitindo desenvolvimento em paralelo.
- Reaproveitar Turno, dashboard de turnos, estoque/solicitação e telemetria existentes, estendendo-os.

**Non-Goals:**
- Execução e evidências no app de campo, avaliação de fotos, status em tempo real da equipe, georreferenciamento automático
  (etapa 2, change `execucao-campo-obras`).
- Valoração em US (unidade de serviço).
- OCR de PDFs escaneados (a extração de texto usa só a camada de texto do PDF; ver Open Questions).
- Módulo de homologação/tickets.

## Decisions

### D1. Kits como subdomínio de `obras`, referências por UUID
Kit, ItemKit ficam em `com.nexo.app.modules.obras` (pacote de domínio próprio), referenciando Ação (`contrato`) e Material
(`estoque`) por UUID, com leitura via portas/adapters já usados pelo módulo (`*ApiAdapter`).
- Alternativa: módulo `kits` separado. Rejeitada: o kit só existe para planejar obras; um módulo a mais aumenta eventos sem ganho.
- Alternativa: kit dentro de `contrato`. Rejeitada: mistura catálogo comercial com engenharia de obra.

### D2. Agregado de planejamento por obra
`PlanejamentoObra` (1:1 com Obra) é a raiz de: Ponto, Vao, KitAplicado, ItemPlanejado, Anotacao e preferências do projeto mestre
(rotação por página). Invariantes no domínio: número de ponto único, vão sem auto-ligação/duplicata, remoção bloqueada com item
programado/executado, quantidade prevista = qtd do kit × multiplicador.
- Gravação por comandos pequenos (adicionar ponto, mover, aplicar kit, ...) com `versao` por entidade (optimistic locking), não
  "salvar o planejamento inteiro": evita sobrescrever edição concorrente e mantém o log de auditoria granular.
- Leitura: um endpoint devolve o planejamento completo da obra em DTO compacto (pontos, vãos, kits, itens, programações
  resumidas), para o editor montar a árvore sem N chamadas.

### D3. Coordenadas normalizadas no espaço da página não rotacionada
Ponto/vão/anotação guardam `pagina`, `x`, `y` em [0,1] relativos à página do PDF sem rotação. O frontend converte para a tela
aplicando rotação do PDF + rotação salva. Assim rotação e zoom nunca alteram dados, e a rotação vira preferência do anexo
(persistida no servidor, não no navegador, diferente da referência).
- Alternativa: coordenada relativa ao canvas exibido (como a referência). Rejeitada: acopla dado à rotação de visualização.

### D4. Visualizador com `pdfjs-dist` encapsulado no Design System
Novo componente source-owned no `@ces/ui` (ex.: `ces-pdf-canvas`) cuidando de render, zoom ancorado, pan (mão, botão do meio,
toque), rotação e extração de texto por região (`getTextContent` + `convertToViewportPoint`, ordenação linha/coluna). Overlays
(pontos, trechos SVG, anotações, seleção por área) ficam na feature `obras/planejamento`, posicionados em porcentagem sobre o
canvas. Render só da página atual; worker do PDF.js empacotado localmente (sem CDN).
- Alternativa: biblioteca de viewer pronta (ex.: ngx-extended-pdf-viewer). Rejeitada: pesada, difícil de sobrepor camadas e de
  aderir ao Design System.
- Registro no catálogo e em `prime-ng-coverage.md` junto com o componente.

### D5. Status de item e valores congelados
`ItemPlanejado.status`: PENDENTE → PROGRAMADO → EM_ANDAMENTO → CONCLUIDO → MEDIDO (e volta a PENDENTE ao cancelar/excluir
programação sem execução). O valor unitário é congelado em `ProgramacaoItem` pela vigência da Ação na data de início da
programação (`AcaoValor.dataVigencia`); a medição usa esse valor. Itens sem programação são valorizados pelo vigente hoje apenas
para exibição.

### D6. Programação como agregado próprio com histórico
`Programacao` (obra, equipe, período, condições operacionais, observação, status) com `ProgramacaoItem` e `EventoProgramacao`
(EDITADA, REPROGRAMADA, CANCELADA, EXCLUIDA com antes/depois). Capacidade e meta são calculadas em leitura (não persistidas):
jornada e plataforma vêm de Equipe/TipoEquipe (novos campos com padrão 8h/30min), deslocamento de Obra (D7), meta de
MetaFaturamentoEquipe vigente e dias úteis do período (calendário com feriados configuráveis).

### D7. Deslocamento base → obra via provedor de rotas com cache
Porta `RotaRodoviariaPort` com adaptador para um motor de rotas (OSRM self-hosted ou serviço configurado), envolvido por
Resilience4j e cache Redis por par (base, coordenada arredondada). Falha ou ausência de provedor → fallback haversine × fator de
sinuosidade configurável, marcado como "estimado". Recalculo por evento quando base ou coordenadas mudam.
- Alternativa: só haversine. Rejeitada: distorce capacidade em regiões rurais.

### D8. Turno de Obras como extensão do Turno existente
Turno ganha vínculo opcional com programações do dia, atividades (obra programada/não programada), líder de turma e papel do
componente (titular/substituto), APR, ocorrências e relatório diário, sem alterar o fluxo comercial. `ApontamentoExecucao`
(item, turno opcional, data, quantidade, usuário, justificativa) é a única forma de mudar `quantidadeExecutada`; na etapa 2 o app
grava o mesmo apontamento. O dashboard de turnos ganha a visão por equipe (cartões), reutilizando `dashboard-turnos`.

### D9. Comunicação entre módulos por eventos
`ItemExecutado`, `ProgramacaoConfirmada/Alterada`, `MedicaoGerada/Estornada`, `ObraLocalizacaoAlterada` publicados via Spring
Modulith (`event_publication`). Consumidores: indicadores da obra e da equipe, central de operações, solicitação técnica de
materiais (`estoque`), recálculo de deslocamento.

### D10. Medições
`Medicao` (obra, tipo PARCIAL ou CICLO, ciclo, status GERADA/ESTORNADA) com `MedicaoItem` referenciando `ProgramacaoItem` e
quantidade. Unicidade de item medido garantida por constraint parcial (item ativo em medição não estornada). Relatórios XLSX/PDF
gerados assíncronos com download quando prontos.

### D11. Canteiros sobre o estoque existente
Canteiro de postes = novo tipo de estoque com coordenadas e município; entradas, contagem e movimentos usam `MovimentacaoEstoque`.
Requisição a partir da programação cria solicitação técnica pelo fluxo existente (evento consumido por `estoque`).

### D12. Central de operações e telemetria
Endpoints de leitura agregados (um por bloco do painel, cacheados por 30–60 s). Mapa com Leaflet já presente; posições de
veículos via `frota`. Atualização periódica por polling (30 s) nesta etapa; STOMP fica como evolução.

### D13. Permissões
`obras:kit:{read,write,import}`, `obras:planejamento:{read,write}`, `obras:programacao:{read,write}`,
`obras:execucao:write`, `obras:medicao:{read,write}`, `obras:documentacao:{read,write}`, `obras:central:read`, inseridas por
migração e consumidas por `authGuard`.

### Decisões tomadas na implementação (frente A)
- Classes de tensão: enum `ClasseTensao` (BT, MT_15, MT_25, MT_36, AT_72, AT_138). Kit sem classe vale para todas.
- Kit: contrato e código imutáveis após o cadastro; itens removidos são desativados (soft delete). Valor do kit usa
  `ContratoApi.listarAcoesComValor` (já com coeficiente do contrato); serviço sem valor vigente fica fora da soma e é contado
  em `servicosSemValor`.
- Importações (kits, carteira, orçamento) seguem o fluxo das demais importações do sistema, no `ces-import-drawer`:
  `validacao` devolve cada linha com situação e erro sem gravar, `revalidacao` confere de novo depois de cada correção
  na tela e `confirmacao` revalida e processa as válidas em `@Async`, com uma transação por kit/obra. A tabela
  `importacoes_obras` guarda o histórico e os erros por linha (JSONB). Leitura XLSX ou CSV com cabeçalho normalizado e aliases.
- `ces-import-drawer` e `ces-form-drawer` trazidos da `main` do `ces` (largura máxima redimensionável, arrastar e soltar,
  tabela sem colunas espremidas), mais o slot `[import-params]` para parâmetros que vêm antes do arquivo (contrato).
- A ficha da obra (`/obras/:id`) é uma página de aplicação, não A4: cabeçalho com prazo, indicadores, pontos de atenção
  (`obra-insights.ts`, função pura), dados, solicitações, orçamento, anexos, subprojetos, mapa e histórico. As ações ficam
  fixas no canto inferior direito; o que altera a obra fica em "Atualizar obra", e as seções mantêm seus atalhos.
- Carteira: o "prazo" é a `dataPrevisaoFim` existente; novo só `prazoRegulado`. Status operacional reutiliza `StatusObra`
  com histórico em `historico_status_obras` (gravado pelo domínio em toda troca, inclusive no cadastro atual) + observação.
  Obra ganhou `idContrato` (opcional) para filtrar kits.
- Endpoints de carteira ficam em `CarteiraObraController`/`CarteiraObraUseCases`, sem alterar `ObraRequest`/`ObraUseCases`;
  `ObraResponse` e `ObraResumoResponse` ganharam o campo `carteira`.
- Deslocamento (D7): `RotaRodoviariaPort` + `OsrmRotaRodoviariaAdapter` (desligado sem `ces.obras.rotas.osrm-url`, circuit
  breaker `rotas-obras`, cache `obras-rotas`); fallback haversine × `fator-sinuosidade` (1,3) a `velocidade-media-kmh` (45),
  marcado como estimado. Recalculo por agendamento (`ces.obras.rotas.recalculo-intervalo`, 10 min) das obras cuja base ou
  coordenadas mudaram, em vez de evento, e sob demanda em `POST /v1/obras/{id}/deslocamento/recalcular`.
- Obras próximas: pré-filtro por retângulo no banco e distância em linha reta em memória (padrão 10 km, máximo 100 km).

## Risks / Trade-offs

- [PDFs grandes e muitos pontos travam o editor] → render só da página atual, overlay em porcentagem, árvore virtualizada,
  DTO compacto e atualização incremental após cada comando.
- [Concorrência entre programadores no mesmo planejamento] → versão por entidade, resposta 409 com estado atual e recarga parcial.
- [Provedor de rotas indisponível] → fallback estimado sinalizado, cache, circuit breaker.
- [Frentes em paralelo divergindo do modelo] → fundação (Frente A) entregue primeiro com migrações e contratos de API revisados;
  demais frentes só criam tabelas próprias.
- [Regressão em turnos comerciais e solicitações] → testes de regressão dos fluxos atuais antes de estender; novos campos
  opcionais.
- [Importações com planilhas inconsistentes] → processamento assíncrono por linha com relatório de erros (padrão de importação
  já existente em obras).
- [Valor congelado diverge do vigente em reajustes] → é intencional; relatórios mostram data de referência do valor.

## Migration Plan

1. Migrações V195+ só aditivas (novas tabelas e colunas opcionais), seguidas de permissões.
2. Carga inicial de kits por importação no ambiente de homologação, validada com a operação.
3. Liberação por permissões: telas novas só aparecem para perfis com as permissões novas.
4. Rollback: desativar permissões; migrações aditivas não precisam ser revertidas.

## Open Questions

- Motor de rotas: OSRM self-hosted (custo de infraestrutura) ou serviço externo pago?
- Checklist de visita prévia e campos da ficha As Built: usar os modelos da concessionária (precisamos dos formulários).
- Calendário de dias úteis: feriados por município/base ou só nacionais?
- OCR para PDFs sem camada de texto: necessário na etapa 1 ou pode aguardar?
- Formato exato das planilhas de carteira, kits e serviços/orçamento previstos (amostras para os importadores).
