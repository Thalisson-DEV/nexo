<!--
Frentes e dependências:
  A (fundação) → todas. Após 1.x concluído, B, C, D, E, F, G, H e I podem seguir em paralelo,
  respeitando: C usa o modelo de B (só leitura), F usa C e D, I usa C e D.

Linha de base (1.1, 29/10/2026): backend 1649 testes, 3 erros pré-existentes (RenderizacaoAtaDocxServiceTest, .docx de
referência ausente); frontend 1514 testes, 2 falhas pré-existentes (solicitacoes-comerciais-kanban, destinos-deeplink).
-->

## 1. Frente A: fundação (kits, carteira, permissões)

- [x] 1.1 Rodar a suíte atual de backend e frontend e registrar a linha de base (regressão de Obra, Turno, Equipe, Solicitação)
- [x] 1.2 Migração: tabelas `kit_construtivo` e `item_kit` (contrato, código único ativo, grupo, classes de tensão, ação, qtd)
- [x] 1.3 Domínio Kit/ItemKit com invariantes (código único, item serviço→Ação, material→Material, kit sem item não aplicável)
- [x] 1.4 KitUseCases (CRUD, filtro por contrato/grupo/tensão, valor em R$ pela vigência) + controller, doc OpenAPI e testes
- [x] 1.5 Importação de kits (kits, materiais, serviços) assíncrona com relatório por linha + testes com planilha de amostra
- [x] 1.6 Migração e domínio: novos campos de carteira na Obra (classe de tensão com autor/data, prazo, prazo regulado,
      prioridade, lote, situação, valores previstos, status operacional com histórico)
- [x] 1.7 Importação da carteira e dos serviços/orçamento previstos com histórico de importações
      <!-- V197, ItemOrcamentoObra, ProcessarImportacao{Carteira,Orcamento}Service, ImportarCarteiraUseCase (modelos XLSX),
           POST /v1/obras/importacoes/{carteira,orcamento}, GET .../{carteira,orcamento}/modelo, GET /v1/obras/{id}/orcamento.
           Testes: ProcessarImportacao{Carteira,Orcamento}ServiceTest, CarteiraObraUseCasesTest. -->
- [x] 1.8 `RotaRodoviariaPort` + adaptador + fallback estimado + cache; cálculo de deslocamento base→obra por evento
- [x] 1.9 Endpoint de obras próximas (raio configurável) e indicadores da listagem
      <!-- GET /v1/obras/{id}/proximas e GET /v1/obras/indicadores. Teste de obras próximas em CarteiraObraUseCasesTest;
           indicadores (Criteria sobre ObraSpecification) sem teste unitário: o projeto não tem teste de integração com banco.
           Colunas por obra (valor planejado, % programado/concluído, pré-fechamento) entram com as frentes B, C e F. -->
- [x] 1.10 Migração de permissões `obras:*` novas e vínculo aos perfis administrativos
      <!-- V198: ADMINISTRADOR recebe todas; SUPERVISOR recebe leitura de kits, planejamento, programação, execução,
           leitura de medição, documentação e central. Rótulos no catálogo de permissões do frontend.
           PermissoesObrasMigrationContractTest garante que toda permissão obras:* dos controllers existe em migração. -->
- [x] 1.11 Frontend: tela de kits (lista padrão, drawer de kit e itens, importação) e serviço `obras-kit.service`
      <!-- /obras/cadastros/kits (obras:kit:read), KitsConstrutivosComponent, ObrasKitService, ImportacaoPlanilhaObrasComponent
           sobre o ces-import-drawer (validação, correção na tela, revalidação e confirmação). Conferido no navegador. -->
- [x] 1.12 Frontend: campos de carteira no form/detalhe da obra, colunas e indicadores na listagem, obras próximas, importações
      <!-- Listagem: faixa de indicadores do recorte, colunas de carteira (tensão, prioridade, prazo regulado, situação,
           valores previstos; ordenação no backend), "Importar" com escolha cadastro/carteira/orçamento. Ficha refeita em
           obra-detalhe (carteira em drawer, deslocamento, histórico, obras próximas, orçamento, pontos de atenção em
           obra-insights.ts). Carteira é editada na ficha, não no form de cadastro, para não mexer no fluxo do ObraRequest.
           Conferidos no navegador: importação de kits, ficha nos dois temas, ações. Ainda não: importações de carteira e
           orçamento no fluxo novo, drawers de status/anexo/vínculo, ficha com anexos e subprojetos reais, impressão. -->

## 2. Frente B: planejamento sobre o PDF (depende de 1.2–1.6)

- [x] 2.1 Migração e domínio PlanejamentoObra: Ponto, Vao, KitAplicado, ItemPlanejado, Anotacao, rotação por página do anexo
- [x] 2.2 Marcação de projeto mestre e versionamento ao substituir o PDF preservando o planejamento
- [x] 2.3 Comandos: adicionar/mover/editar/excluir ponto; lançar/excluir vão (inclusive entre páginas); anotações
- [x] 2.4 Comandos: aplicar kit (ação, multiplicador, lote em seleção), remover kit, editar/remover item pendente
- [x] 2.5 Endpoint de leitura compacto do planejamento + indicadores (exec/prog/pend/total, avanço por kit, sem kit)
- [x] 2.6 Optimistic locking com 409 + estado atual; testes de domínio e de concorrência
- [x] 2.7 `@ces/ui`: componente `ces-pdf-canvas` (pdfjs-dist local, zoom ancorado, pan, toque, rotação, extração por região)
      com catálogo, cobertura, docs e testes
- [x] 2.8 Feature `obras/planejamento`: layout, toolbar, overlays de pontos/trechos/anotações, estados com forma além da cor
- [x] 2.9 Ferramentas: adicionar ponto, lançar vão, conectar entre folhas, mover, selecionar por clique/área, extrair texto,
      ocultar camadas, esmaecer, ocultar painel, atalhos de teclado
- [x] 2.10 Árvore lateral virtualizada (página→ponto→kit→itens, vãos), busca, só serviços/serviço+material, resumo sem kit
- [x] 2.11 Resumo rápido do ponto e detalhe completo (abas Kits e Orçamento)
- [x] 2.12 Cabeçalho de indicadores e painel Avanço por kit; estados vazio, carregando, erro e conflito
- [x] 2.13 Teste e2e do fluxo: anexar PDF → pontos → vão → kits → indicadores
      <!-- e2e/design-system/planejamento.spec.ts, com a API simulada. -->
- [x] 2.14 Copiloto: chamada com imagens e modelo com visão em `CopilotoApi`, render da página e análise assíncrona
- [x] 2.15 Copiloto: interpretação do resultado (blocos para página, duplicados, conflito de número, kits candidatos) + testes
- [x] 2.16 Copiloto: revisão das sugestões sobre a folha, aceite em lote e gravação pelos mesmos comandos do planejamento
      <!-- Estado "atrasado" do ponto depende das datas da programação: entra com a frente C. -->

## 3. Frente C: programação (depende de 2.1, 2.5)

- [x] 3.1 Migração e domínio Programacao, ProgramacaoItem (valor congelado), EventoProgramacao
- [x] 3.2 Campos de jornada e plataforma em Equipe/TipoEquipe (opcionais, padrão 8h/30min) e calendário de dias úteis
- [x] 3.3 Use cases: programar (ignora já programados), editar, reprogramar, cancelar, excluir, remover item; eventos
- [x] 3.4 Leitura de capacidade e meta do período por equipe (jornada − plataforma − deslocamento; meta/dia × dias úteis)
- [x] 3.5 Frontend: diálogo Programar (equipe com filtros base/tipo, projeção, detalhes operacionais, período, avisos)
- [x] 3.6 Frontend: badges de programação na árvore e remoção de item da programação
- [x] 3.7 Frontend: histórico de reprogramações (lista padrão com filtros por tipo)
- [x] 3.8 Frontend: Gantt por equipe com período/filtros na URL e acesso ao planejamento
- [x] 3.9 Testes de domínio (status, valores congelados, retorno a pendente) e e2e programar/reprogramar
      <!-- ProgramacaoTest, ProjecaoEquipesObraTest, programacao.logic.spec, e2e/design-system/programacao.spec.ts (API simulada).
           Fluxo inteiro também exercido na API real sobre nexo-db. Decisões em design.md, "Decisões da frente C". -->

## 4. Frente D: turnos de Obras (depende de 3.1)

- [x] 4.1 Testes de regressão do Turno comercial antes da extensão
- [x] 4.2 Migração e domínio: líder, papel titular/substituto, atividades (programada/não programada), relatório diário
- [x] 4.3 APR e ocorrências do turno de Obras (reutilizar SST/ocorrências existentes onde houver)
- [x] 4.4 ApontamentoExecucao (web) com limite e justificativa de excedente; evento ItemExecutado
- [x] 4.5 Desempenho do dia (realizado × meta) e indicadores da listagem de turnos
- [x] 4.6 Frontend: listagem e detalhe de turnos com filtros de Obras (só leitura; revisão de 01/11/2026 em design.md)
- [x] 4.7 Frontend: visão por equipe (cartões detalhado/painel) no dashboard de turnos
- [x] 4.8 Alerta "agendamento sem turno aberto"
      <!-- TurnoTest, LiberacaoExecucaoTest, ApontamentoExecucaoTest, ConsultarTurnosObraTest, turnos-obra.logic.spec, e2e/design-system/turnos-obra.spec.ts.
           Fluxo conferido no Chrome sobre nexo-db com a massa FIC-*. Decisões em design.md, "Decisões da frente D". -->

## 5. Frente E: documentação técnica (depende de 2.1, 4.4)

- [x] 5.1 Visita prévia com checklist configurável e PDF
- [x] 5.2 Ficha As Built por ponto com "Não se aplica" e validação de completude
- [x] 5.3 Geração assíncrona das plantas marcadas + fichas como nova versão de anexo
- [x] 5.4 Book de equipamentos por ponto
- [x] 5.5 Frontend: seções no detalhe da obra e estados
      <!-- V206, ModeloChecklistObra, RespostasChecklist, VisitaPreviaObra, FichaAsBuilt, DocumentoAsBuilt, EquipamentoObra, DocumentoTecnicoPdf.
           /obras/cadastros/checklists e app-obra-documentacao. Testes: DocumentacaoTecnicaTest, DocumentoTecnicoPdfTest, checklist.logic.spec,
           e2e/design-system/documentacao-obra.spec.ts. Fluxo conferido na API real e no Chrome sobre nexo-db. Decisões em design.md, "Decisões da frente E". -->
- [x] 5.6 Book da obra: itens de foto no modelo, fotos por ponto com miniatura e legenda, visualização no web, fotos no As Built e aviso de foto faltando
      <!-- V207, EvidenciaPonto, FotoEvidencia, EvidenciaPontoUseCases, app-evidencia-miniatura, app-evidencia-visualizador. Testes: FotoEvidenciaTest,
           DocumentoTecnicoPdfTest, checklist.logic.spec, e2e/design-system/documentacao-obra.spec.ts. Decisões em design.md, "Book da obra". -->
- [ ] 5.7 Tela do book no app e perfil da equipe com `obras:documentacao:write` (etapa 2): escolher obra e ponto e lançar foto, inclusive sem sinal

## 6. Frente F: medições (depende de 3.1, 4.4)

- [x] 6.1 Pré-fechamento (contagens, levantadores, conclusão bloqueada com pendências) + listagem
- [x] 6.2 Medicao/MedicaoItem com unicidade de item medido; gerar e estornar medição parcial
- [x] 6.3 Medição por ciclo com saldo anterior
- [ ] 6.4 Relatório detalhado orçado × faturado (modelo, upload, seleção de obras, XLSX+PDF+fotos em pacote)
- [x] 6.5 Fechamento de obra para um ou vários códigos
- [ ] 6.6 Frontend das quatro telas e testes
      <!-- Feitas: /obras/pre-fechamento, /obras/medicoes (abas A medir e Medições geradas, drawers de medir e de detalhe com estorno)
           e /obras/fechamento (XLSX). Falta a tela do relatório detalhado (6.4).
           V208, Medicao, MedicaoItem, MedicaoObraUseCases, PreFechamentoObraUseCases, FechamentoObraUseCases, ContextoObras.
           Testes: MedicaoTest, FechamentoObraUseCasesTest, medicoes.logic.spec. Fluxo conferido na API real e no Chrome sobre
           nexo-db (concluir levantamento, medir por ciclo, estornar, filtros, fechamento). Decisões em design.md, "Decisões da frente F". -->

## 7. Frente G: recursos (depende de 1.x, 3.1)

- [ ] 7.1 Canteiro de postes como tipo de estoque com coordenadas; entradas, lote, contagem, anexos
- [ ] 7.2 Solicitação técnica de materiais a partir da programação (evento → estoque) com regressão do fluxo atual
- [ ] 7.3 Frontend de canteiros e ação "solicitar materiais" na programação

## 8. Frente H: central de operações e mapa (depende de 3.x, 4.x)

- [ ] 8.1 Endpoints agregados do painel (cacheados) e cálculo de ritmo/projeção
- [ ] 8.2 Frontend do painel com alertas acionáveis e atualização
- [ ] 8.3 Mapa de obras com filtros e rota da base
- [ ] 8.4 Telemetria: camadas, categorias, busca por placa, turmas do dia, offline

## 9. Frente I: relatórios (depende de 3.x, 4.x)

- [ ] 9.1 Avaliação da carteira e prazos
- [ ] 9.2 Programação e programado × executado com exportação
- [ ] 9.3 Execução por turno, produtividade (equipe e colaborador), financeiro e APRs

## 10. Fechamento

- [ ] 10.1 `npm run verify:design-system`, testes e build do frontend; testes e build do backend
- [ ] 10.2 Documentação (CLAUDE.md dos apps, catálogo do Design System, manual do usuário de Obras)
- [ ] 10.3 Validação com a operação em homologação e ajuste das Open Questions do design
