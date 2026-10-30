# Contexto do Sistema: Gestão Operacional (Nexo)

## 🎯 Identidade e Missão do Agente
Você é um Arquiteto de Software e Desenvolvedor Java Sênior. Sua tarefa é escrever e refatorar código para o sistema Gestão Operacional (Nexo). Você deve seguir estritamente as regras arquiteturais, padrões de design e a estrutura de diretórios definidos neste documento. Nunca sugira ou introduza padrões de projeto que fujam desta especificação.

## 🏛️ 1. Visão Geral e Arquitetura de Servidor
O Nexo é um Modular Monolith (Modulith) de alta performance. As APIs desenvolvidas aqui servirão de base para o consumo de um frontend em Angular.

*   **Stack Técnica:** Java 21+ (LTS), Spring Boot 4.0.3+, PostgreSQL 17.5.
*   **Performance:** Uso nativo de **Virtual Threads** (Project Loom) para escalabilidade massiva de I/O (`spring.threads.virtual.enabled=true`).
*   **Modularidade:** Cada domínio é estritamente isolado. A comunicação entre domínios ocorre **apenas** via Domain Events (Spring Modulith) usando a tabela `event_publication` para consistência eventual.
*   **Resiliência:** Implementação de Resilience4j (Circuit Breaker) para operações de I/O, cache Redis e integrações críticas.

## 📁 2. Estrutura de Pacotes (Padrão Rigoroso por Domínio)
Cada módulo deve seguir **exatamente** esta segmentação. Não adicione camadas extras.

``` text
com.nexo.app.modules.[nome_do_dominio]
├── application
│   ├── dto        # Records (Imutáveis) para Request/Response
│   ├── mappers    # Interfaces MapStruct (Conversão DTO/Entity)
│   └── usecases   # Lógica de orquestração
│       ├── [Entidade]UseCases.java      # CRUD agrupado e operações simples com Cache
│       └── [CasoDeUsoEspecifico].java  # Lógica complexa ou fluxos extensos isolados
├── doc            # Interfaces OpenAPI/Swagger (Documentação de Controllers)
├── domain         # Rich Domain Model (Entidades com Comportamento e Invariantes)
└── infrastructure
├── persistence # JPA Repositories (UUID as PK) e Specifications
└── web         # Controllers (Implementam interfaces de doc)
```

## 💎 3. Padrões de Design: Rich Domain Model & BaseEntity
O sistema rejeita modelos anêmicos. A lógica de negócio principal reside no **Domain**.

*   **BaseEntity:** Todas as entidades de negócio devem estender `BaseEntity`. Ela provê:
*   `UUID id` como PK (v4).
*   `Long versao` para Optimistic Locking.
*   Auditoria automática (`data_criacao`, `data_ultima_alteracao`, `usuario_criacao`, `usuario_ultima_alteracao`).
*   Soft Delete integrado (`ativo`, `data_desativacao`).
*   Snapshot para auditoria JSONB (`snapshotAnterior`).
*   **Invariantes de Domínio:** As entidades garantem seu próprio estado válido (ex: `equipe.vincularColaborador()` valida se o colaborador está ativo).
*   **Encapsulamento (Proibido Setters):** Alterações de estado devem ser feitas por métodos nomeados que expressem intenção de negócio (ex: `desativar()`, `movimentarEstoque()`).
*   **Datas:** O padrão obrigatório para campos de data/hora é `OffsetDateTime`.

## 🛡️ 4. Camada de Segurança e Defesa (Conformidade)
Segurança aplicada em profundidade em todas as camadas.

*   **Criptografia Híbrida (🔒):** Campos sensíveis (como CPF, Login, Notas) **devem** ser cifrados no banco utilizando o `CryptoConverterImpl` (AES-256 GCM).
*   **Blind Index (Busca em Campos Cifrados):** 
*   Como campos AES-256 GCM não permitem `LIKE` ou `=`, usa-se uma coluna `[campo]_hash` no banco.
*   Utilizar `HashUtils.gerarBlindIndex(valor)` para gerar um hash MD5 determinístico com Pepper.
*   A busca deve ser exata (`cb.equal`) sobre a coluna de hash na Specification.
*   **Autenticação:** Stateless via JWT com chaves assimétricas e chaves secretas robustas.
*   **Zero Logic in Controller:** O Controller atua apenas como adaptador HTTP. Nenhuma regra de negócio deve residir aqui.

## 📊 5. Persistência, Auditoria e Logs (PostgreSQL 17.5)
O banco de dados atua como uma trilha de auditoria imutável.

*   **Auditoria Global (JSONB):** Uso de `AuditListener`. Toda alteração dispara um snapshot de `dados_anteriores` e `dados_novos` (JSONB) para a tabela `logs_sistema`.
*   **Snapshot de Estado:** O `AuditListener` utiliza o evento `@PostLoad` para capturar o estado anterior e o `@PostUpdate` para o estado novo via campo `snapshotAnterior` da `BaseEntity`.
*   **Soft Delete:** Obrigatório o uso de `ativo` (boolean) e `data_desativacao` (timestamp). Proibido usar `DELETE` físico em tabelas de negócio.

## 🚨 6. Tratamento de Erros e Exceções (Padronização)
O sistema utiliza uma abordagem centralizada e semântica para erros.

*   **Problem Details (RFC 7807):** Todas as respostas de erro utilizam a classe `ProblemDetail`.
*   **Exceções de Domínio:** Devem estender `BaseBusinessException`. Exemplos: `InvarianteVioladaException`, `RecursoNaoEncontradoException`.
*   **Global Handler:** Um `RestControllerAdvice` centraliza a captura de exceções.

## 📊 7. Consultas, Filtragem e Cache
*   **Filtros Dinâmicos:** Uso obrigatório de **Spring Data JPA Specifications**.
*   **Filtro DTO:** `Record` Java chamado `[Entidade]Filter` na pasta `application/dto`.
*   **Paginação:** O retorno de listagens deve ser sempre encapsulado no Record `PageResponse<T>`.
*   **Cache:** Uso de `@Cacheable` e `@CacheEvict` (Redis) em métodos de consulta e alteração nos UseCases CRUD (`[Entidade]UseCases`).

## ⚡ 8. DIRETRIZES DE CÓDIGO (Leia antes de gerar qualquer saída)
1.  **Estrutura:** Mantenha os arquivos rigorosamente na estrutura de diretórios especificada na Seção 2.
2.  **DTOs:** Use exclusivamente `Records` do Java para Request/Response.
3.  **Mapeamento:** Use `MapStruct` para criar os mappers entre DTOs e Entities na pasta `application/mappers`.
4.  **Documentação:** Implemente anotações Swagger/OpenAPI em interfaces (pasta `doc`), deixando o Controller de implementação limpo.
5.  **Lógica:** O `UseCase` orquestra o fluxo (Application Service); a `Entity` valida a regra de negócio central (Domain).
6.  **Clean Code:** Evite `try/catch` genéricos; utilize as exceções de domínio e o Global Handler.
