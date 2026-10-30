# Matriz de Treinamentos por Função — versão normalizada para agentes de IA

## 1. Objetivo

Este arquivo representa, em formato textual e sem células mescladas, a **matriz de treinamentos por função** e a **matriz de POP-00** fornecidas na fonte.

A finalidade é permitir que uma aplicação ou agente de IA consiga responder, de forma determinística, perguntas como:

- quais treinamentos uma função exige;
- quais requisitos são obrigatórios, condicionais ou dependentes da atividade;
- qual é a carga horária explícita na matriz;
- quais regras globais precisam ser aplicadas além da linha da função;
- qual treinamento de POP-00 corresponde à função/processo;
- como tratar acúmulo de função, função não listada, atuação em subestação e condução de veículos.

> **Regra de integridade:** o conteúdo das células foi preservado. Células vazias foram apenas omitidas das listas compactas. Símbolos sem definição suficiente não foram reinterpretados.

---

## 2. Chave de identificação da função

A aplicação **não deve usar apenas o nome da função como chave**.

Existem nomes iguais ou semelhantes em processos distintos. A chave recomendada é:

```text
processo/categoria + função
```

Exemplos:

```text
REDES + TÉCNICO DE SEGURANÇA
OUTROS / CONSTRUÇÃO CIVIL / SERVIÇOS GERAIS + TÉCNICO DE SEGURANÇA
MANUTENÇÃO DA SUBTRANSMISSÃO + TÉCNICO ELETROTÉCNICO
EFICIÊNCIA ENERGÉTICA | ILUMINAÇÃO PÚBLICA - IP + TÉCNICO ELETROTÉCNICO
```

Não fundir automaticamente funções apenas porque os nomes são parecidos.

---

## 3. Como ler a matriz principal

Cada curso recebeu um identificador estável `TR-01` a `TR-36`.

Nas tabelas por função, o campo **Requisitos da linha** usa o formato:

```text
TR-01=6; TR-02=6; TR-03=*; TR-04=40
```

Interpretação:

- `TR-xx=N` → a célula da matriz contém explicitamente o número `N`;
- `TR-xx=*`, `##`, `###`, `¨` ou `****` → a célula contém esse marcador e deve ser interpretada pela legenda;
- curso ausente da lista → a célula correspondente estava vazia;
- `total_horas_fonte` → total informado na própria matriz;
- os marcadores não numéricos **não entram no total numérico da fonte**.

### Validação da normalização

Foram normalizadas **55 funções** da matriz principal.

Para as 55 funções:

```text
soma de todas as células numéricas da linha == TOTAL DE HORAS da fonte
```

Isso foi validado programaticamente em todas as linhas.

A planilha original/paste possuía deslocamentos causados por células mescladas e, em algumas linhas, uma célula estrutural vazia adicional. A associação foi normalizada usando a ordem dos 36 cursos e o `TOTAL DE HORAS` como âncora, sem alterar valores.

---

## 4. Legenda e regras condicionais

### `*` — Direção defensiva

Todos os colaboradores que dirigem veículos automotores devem possuir treinamento de direção defensiva.

Todos os motoristas de veículos **4x4** devem possuir adicionalmente treinamento específico de **8 horas**.

Na matriz principal, `*` deve ser tratado como um requisito condicional, não como quantidade de horas.

> **Anomalia preservada da fonte:** existe também uma ocorrência de `*` em `TR-34` para `REDES + ENCARREGADO LINHA MORTA`. Como a legenda fornecida associa `*` à direção defensiva, essa ocorrência deve permanecer registrada, mas não deve ter outro significado inventado pelo agente.

### `¨` — Formação correspondente à função

O colaborador deve possuir curso de formação correspondente à função, podendo ser:

- curso técnico com registro ativo;
- capacitação da própria empresa com conteúdo adequado para a função;
- qualificação profissional;
- habilitação profissional.

Exemplo fornecido na fonte: profissional que atua na manutenção da subestação.

### `*******` e `****` — POP's específicos

Por definição fornecida pelo usuário, `****` e `*******` representam a **mesma regra**.

Antes do início das atividades, o colaborador deverá receber treinamento dos procedimentos específicos de suas atividades.

Durante a vigência do contrato poderá haver solicitação de treinamento de POP's da concessionária, ministrado por multiplicador devidamente treinado.

Em outras palavras:

```text
**** == *******
```

O marcador `****` aparece nas linhas da matriz em `TR-36`, cujo curso é `Pop's específicos *******`.

A sequência `****` também está presente como nota no título do curso `TR-30`; ela deve ser preservada como parte da fonte e associada à mesma legenda, conforme orientação do usuário.

### `###` — Regras para atuação em subestação

A fonte associa `###` às seguintes exigências:

1. todos os colaboradores que realizarem serviços em subestações deverão passar, **antes do início das atividades**, por integração/briefing de acesso ministrado pela área de manutenção da subestação;
2. todos os colaboradores que realizarem atividades em **subestações energizadas** deverão possuir:
   - NR-10 Básico;
   - NR-10 SEP;
   - reciclagem correspondente.

Portanto, `###` deve ser tratado como um **gatilho condicional de contexto de subestação**.

### `##` — Treinamento legal específico

Todo colaborador deve possuir treinamento legal específico conforme a atividade a ser realizada.

Outros treinamentos poderão ser solicitados conforme necessidade.

O agente não deve transformar `##` automaticamente em um curso específico sem conhecer a atividade concreta.

### Símbolos sem definição textual suficiente

Os símbolos abaixo aparecem na fonte, mas não tiveram legenda explícita fornecida:

- `(+)`;
- `(++)`;
- `**`;
- `***`.

Eles devem ser mantidos como metadados da fonte e **não devem receber significado inventado**.

---

## 5. Regras gerais adicionais

### Acúmulo de atividade ou função

Quando houver acúmulo de atividade/função:

> devem ser somadas/combinadas as exigências contidas em cada uma das linhas de treinamentos obrigatórios.

Para implementação, isso significa que nenhuma exigência de uma das funções pode ser descartada por não aparecer na outra.

> A fonte não define explicitamente se, no cálculo de horas de acúmulo, cursos idênticos já válidos devem ser contados duas vezes. Essa política deve ser definida pela regra de negócio da aplicação, sem ser inferida pelo agente.

### Função não listada

Função não listada deve seguir uma métrica similar.

Caso não exista função similar adequada, deve-se entrar em contato com a **Segurança do Trabalho da concessionária** para deliberação.

O agente não deve criar requisitos definitivos para uma função não listada sem indicar que se trata de analogia ou que depende de deliberação.

### Escola homologada — contratos de REDES

Todos os treinamentos dos **CONTRATOS DE REDES** serão realizados por escola homologada, salvo as exceções indicadas na fonte:

- treinamento de capacitação profissional para atividades de eletromecânica;
- cursos de habilitação profissional;
- Instrução de Segurança e Meio Ambiente;
- Integração de Segurança da própria empresa;
- NR-26;
- operação de máquinas;
- qualificação profissional para atuação em subestação;
- treinamento de operação de máquinas e equipamentos.

Nas exceções, permanece obrigatório comprovar **habilitação e proficiência técnica do instrutor**.

---

## 6. Catálogo de cursos da matriz principal

A linha `DURAÇÃO / CH` e a linha `PERIODICIDADE / ANOS` possuíam uma coluna de rótulo estrutural (`CH` / `ANOS`). Na normalização, os valores seguintes foram associados sequencialmente aos 36 cursos.

| ID | Curso — texto da fonte | CH | Periodicidade (anos) |
|---|---|---:|---:|
| TR-01 | Instruções de Segurança e Meio ambiente (+) | 6h | — |
| TR-02 | Integração de Segurança (NR's: 01, 06, 07, 09,   17, 18, 23, etc.) | 6h | — |
| TR-03 | Direção Defensiva * | 16h | 2 |
| TR-04 | NR-10 Básico | 40h | — |
| TR-05 | NR-10 (SEP) | 40h | — |
| TR-06 | NR-10 Reciclagem | 16h | 2 |
| TR-07 | Eletricidade Básica | 40h | — |
| TR-08 | NR-17 Transporte Manual de Cargas | 2h | 1 |
| TR-09 | NR-26 Treinamento de FISPQ -FDS | 2h | 1 |
| TR-10 | NR-31 Curso para manipuladores de agrotóxico | 20h | — |
| TR-11 | NR-33 Autorizado | 16h | 1 |
| TR-12 | NR-33 Vigia | 16h | 1 |
| TR-13 | NR-33 Supervisor | 40h | 1 |
| TR-14 | NR-33 Reciclagem (Autorizado, Vigia e Supervisor) | 8h | 1 |
| TR-15 | NR-35 | 16h | 2 |
| TR-16 | NR-35 Reciclagem | 8h | 2 |
| TR-17 | Curso de capacitação, qualificação, habilitação para a função (diploma + registro de classe) | — | — |
| TR-18 | Formação em RD de Distribuição (++) | 200h | — |
| TR-19 | Linha Viva: Método a Distância e ao Potencial - Formação | 120h | — |
| TR-20 | Linha Viva: Método ao Contato - Formação | 280h | — |
| TR-21 | Linha Viva - Reciclagem | 40h | 2 |
| TR-22 | Curso de Liderança (Encarregado/Supervisor de Equipes/Cabo de Turma) - SEP | 40h | 2 |
| TR-23 | Curso de Liderança (Encarregado/Supervisor de Equipes) - Demais atividades | 16h | 2 |
| TR-24 | Curso específico para operacionalização em SE | — | — |
| TR-25 | Curso específico para operacionalização em LT até 138 kV | — | — |
| TR-26 | Operador de Munck / Guindauto /Guindaste ** | 32h | 2 |
| TR-27 | Máquinas Pesadas (retro, caçamba, trator, etc e MARTELETE)*** | 16 | 2 |
| TR-28 | Motopoda | 16h | — |
| TR-29 | Motosserra | 20h | — |
| TR-30 | Operador de betoneira, ferramenta ampact, termovácuo, pulverizador ou roçadeira, serra e policorte **** | 8h | 2 |
| TR-31 | Operador de empilhadeira / paleteira elétrica | 20 | 2 |
| TR-32 | Operador de Cesto Áereo / Acoplado | 24h | 2 |
| TR-33 | Blaster | 24h | — |
| TR-34 | Poda de Árvore - Manejo e vegetação | 16h | 2 |
| TR-35 | Guardião da Vida | 4h | — |
| TR-36 | Pop's específicos ******* | — | — |

> `—` significa que a célula correspondente não informou um valor na fonte. Não significa automaticamente “sem exigência”.

---

## 7. Matriz principal normalizada por processo e função

Os números abaixo são mantidos **sem acrescentar `h`**, porque representam literalmente os valores das células das linhas de função. A CH nominal do curso está no catálogo acima.

### REDES

| Função | Requisitos da linha | total_horas_fonte |
|---|---|---:|
| ELETRICISTA LINHA VIVA | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-08=2; TR-09=##; TR-11=##; TR-12=##; TR-13=##; TR-14=##; TR-15=16; TR-16=8; TR-18=200; TR-20=280; TR-21=40; TR-32=24; TR-35=4; TR-36=****` | 682 |
| ELETRICISTA PODADOR (LV) | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-08=2; TR-09=##; TR-11=##; TR-12=##; TR-13=##; TR-14=##; TR-15=16; TR-16=8; TR-18=200; TR-20=280; TR-21=40; TR-28=16; TR-29=20; TR-32=24; TR-34=16; TR-35=4; TR-36=****` | 734 |
| ENCARREGADO DE LINHA VIVA | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-08=2; TR-09=##; TR-15=16; TR-16=8; TR-18=200; TR-20=280; TR-21=40; TR-22=40; TR-32=24; TR-35=4; TR-36=****` | 722 |
| ELETRICISTA LINHA MORTA e/ou MONTADOR DE REDE | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-08=2; TR-09=##; TR-11=##; TR-12=##; TR-13=##; TR-14=##; TR-15=16; TR-16=8; TR-18=200; TR-32=##; TR-34=##; TR-35=4; TR-36=****` | 338 |
| AJUDANTE/AUXILIAR DE ELETRICISTA e/ou MONTADOR | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-07=40; TR-08=2; TR-09=##; TR-11=##; TR-12=##; TR-13=##; TR-14=##; TR-15=16; TR-16=8; TR-35=4; TR-36=****` | 178 |
| OPERADOR DE MUNCK/GUINDAUTO | `TR-01=6; TR-02=6; TR-03=16; TR-04=40; TR-05=40; TR-06=16; TR-07=40; TR-08=2; TR-09=##; TR-26=32; TR-35=4; TR-36=****` | 202 |
| ENCARREGADO LINHA MORTA | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-08=2; TR-09=##; TR-18=200; TR-22=40; TR-34=*; TR-35=4; TR-36=****` | 354 |
| PODADOR (em zona livre) | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-08=2; TR-09=##; TR-10=20; TR-15=16; TR-16=8; TR-28=16; TR-29=20; TR-32=##; TR-34=16; TR-35=4; TR-36=****` | 210 |
| LEITURISTA GRUPO A | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-07=40; TR-08=2; TR-09=##; TR-17=¨; TR-36=****` | 150 |
| LEITURISTA GRUPO B E/OU ENTREGADOR | `TR-01=6; TR-02=6; TR-03=*; TR-08=2; TR-09=##; TR-17=¨; TR-36=****` | 14 |
| CORTE GAVIÃO / SELO | `TR-01=6; TR-02=6; TR-03=*; TR-08=2; TR-09=##; TR-17=¨; TR-35=4; TR-36=****` | 18 |
| ENCARREGADO / SUPERVISOR LEITURA | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-07=40; TR-08=2; TR-09=##; TR-17=¨; TR-23=16; TR-35=4; TR-36=****` | 170 |
| TÉCNICO ELETROTÉCNICO E AFINS | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-09=##; TR-15=##; TR-16=##; TR-17=¨; TR-35=4; TR-36=****` | 112 |
| SUPERVISOR / COORDENADOR / ENGENHEIRO | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-09=##; TR-15=##; TR-16=##; TR-17=¨; TR-35=4; TR-36=****` | 112 |
| TÉCNICO DE SEGURANÇA | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-09=##; TR-15=16; TR-16=8; TR-17=¨; TR-35=4; TR-36=****` | 136 |
| ENG, TÉC, ELETRICISTA DE MANUT DE SUBESTAÇÃO | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-09=##; TR-15=16; TR-16=8; TR-17=¨; TR-35=4; TR-36=****` | 136 |

### MONTAGEM ELETROMECÂNICA

| Função | Requisitos da linha | total_horas_fonte |
|---|---|---:|
| MONTADOR ELETROMECÂNICO | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-07=40; TR-08=2; TR-09=##; TR-15=16; TR-16=8; TR-17=¨; TR-28=##; TR-29=##; TR-32=##; TR-34=##; TR-35=4` | 178 |
| AUXILIAR / AJUDANTE DE MONTADOR ELETROMECÂNICO | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-07=40; TR-08=2; TR-09=##; TR-15=16; TR-16=8; TR-17=¨; TR-28=##; TR-29=##; TR-34=##; TR-35=4` | 178 |
| ENCARREGADO DE TURMA ELETROMECÂNICA | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-07=40; TR-08=2; TR-09=##; TR-17=¨; TR-22=40; TR-35=4` | 194 |

### DETONAÇÃO

| Função | Requisitos da linha | total_horas_fonte |
|---|---|---:|
| BLASTER | `TR-01=6; TR-02=6; TR-03=*; TR-04=###; TR-05=###; TR-06=###; TR-08=2; TR-09=2; TR-17=¨; TR-33=24; TR-35=4` | 44 |
| MARTELETEIRO | `TR-01=6; TR-02=6; TR-03=*; TR-04=###; TR-05=###; TR-06=###; TR-08=2; TR-09=##; TR-17=¨; TR-27=16; TR-35=4` | 34 |
| AJUDANTE | `TR-01=6; TR-02=6; TR-03=*; TR-04=###; TR-05=###; TR-06=###; TR-08=2; TR-09=2; TR-35=4` | 20 |
| ENCARREGADO / SUPERVISOR | `TR-01=6; TR-02=6; TR-03=*; TR-04=###; TR-05=###; TR-06=###; TR-08=2; TR-09=##; TR-17=¨; TR-23=16; TR-35=4` | 34 |

### OUTROS / CONSTRUÇÃO CIVIL / SERVIÇOS GERAIS

| Função | Requisitos da linha | total_horas_fonte |
|---|---|---:|
| MOTORISTA | `TR-01=6; TR-02=6; TR-03=16; TR-04=###; TR-05=###; TR-06=###; TR-08=2; TR-09=##; TR-35=4` | 34 |
| SERVIÇOS GERAIS / AUXILIAR DE SERVIÇOS GERAIS | `TR-01=6; TR-02=6; TR-03=*; TR-04=###; TR-05=###; TR-06=###; TR-08=2; TR-09=2; TR-11=##; TR-12=##; TR-13=##; TR-14=##; TR-35=4` | 20 |
| AJUDANTE GERAL | `TR-01=6; TR-02=6; TR-03=*; TR-04=###; TR-05=###; TR-06=###; TR-08=2; TR-09=##; TR-11=##; TR-12=##; TR-13=##; TR-14=##; TR-35=4` | 18 |
| ALMOXARIFE / AUXILIAR ALMOXARIFE | `TR-01=6; TR-02=6; TR-03=*; TR-04=###; TR-05=###; TR-06=###; TR-08=2; TR-09=##; TR-35=4` | 18 |
| CARPINTEIRO/ PEDREIRO/ ARMADOR / ENCANADOR | `TR-01=6; TR-02=6; TR-03=*; TR-04=###; TR-05=###; TR-06=###; TR-08=2; TR-09=2; TR-15=##; TR-16=##; TR-30=8; TR-35=4` | 28 |
| CABO TURMA/ ENCARREGADO/ SUPERVISOR | `TR-01=6; TR-02=6; TR-03=*; TR-04=###; TR-05=###; TR-06=###; TR-09=##; TR-17=¨; TR-23=16; TR-35=4` | 32 |
| ENGENHEIRO | `TR-01=6; TR-02=6; TR-03=*; TR-04=###; TR-05=###; TR-06=###; TR-09=##; TR-17=¨; TR-35=4` | 16 |
| TÉCNICO / FISCAL | `TR-01=6; TR-02=6; TR-03=*; TR-04=###; TR-05=###; TR-06=###; TR-09=##; TR-17=¨; TR-35=4` | 16 |
| MOTORISTA DE CARRETA | `TR-01=6; TR-02=6; TR-03=16; TR-04=###; TR-05=###; TR-06=###; TR-08=2; TR-09=##; TR-35=4` | 34 |
| OPERADOR (GERAL) | `TR-01=6; TR-02=6; TR-03=*; TR-04=###; TR-05=###; TR-06=###; TR-08=2; TR-09=##; TR-30=8; TR-35=4` | 26 |
| OPERADOR DE GUINDAUTO / GUINDASTE | `TR-01=6; TR-02=6; TR-03=16; TR-04=###; TR-05=###; TR-06=###; TR-08=2; TR-09=##; TR-26=32; TR-35=4` | 66 |
| OPERADOR MÁQUINA PESADA | `TR-01=6; TR-02=6; TR-03=16; TR-04=###; TR-05=###; TR-06=###; TR-08=2; TR-09=##; TR-27=16; TR-35=4` | 50 |
| OPERADOR MÁQUINAS DIVERSAS | `TR-01=6; TR-02=6; TR-03=16; TR-04=###; TR-05=###; TR-06=###; TR-08=2; TR-09=##; TR-30=8; TR-35=4` | 42 |
| OPERADOR EMPILHADEIRA / PALETEIRA ELÉTRICA | `TR-01=6; TR-02=6; TR-03=16; TR-04=###; TR-05=###; TR-06=###; TR-08=2; TR-09=##; TR-31=20; TR-35=4` | 54 |
| PINTOR | `TR-01=6; TR-02=6; TR-03=*; TR-04=###; TR-05=###; TR-06=###; TR-08=2; TR-09=2; TR-15=##; TR-16=##; TR-35=4` | 20 |
| PROJETISTA | `TR-01=6; TR-02=6; TR-04=###; TR-05=###; TR-06=###; TR-08=2; TR-09=##; TR-17=¨; TR-35=4` | 18 |
| ROÇADOR \| LIMPEZA DE FAIXA | `TR-01=6; TR-02=6; TR-03=*; TR-04=###; TR-05=###; TR-06=###; TR-08=2; TR-09=2; TR-10=20; TR-28=16; TR-29=20; TR-30=8; TR-34=16; TR-35=4` | 100 |
| TOPÓGRAFO | `TR-01=6; TR-02=6; TR-03=*; TR-04=###; TR-05=###; TR-06=###; TR-08=2; TR-09=##; TR-17=¨; TR-35=4` | 18 |
| AUXILIAR DE TOPÓGRAFO | `TR-01=6; TR-02=6; TR-03=*; TR-04=###; TR-05=###; TR-06=###; TR-08=2; TR-09=##; TR-17=¨; TR-35=4` | 18 |
| TÉCNICO DE SEGURANÇA | `TR-01=6; TR-02=6; TR-03=*; TR-04=###; TR-05=###; TR-06=###; TR-09=##; TR-15=##; TR-16=##; TR-17=¨; TR-35=4` | 16 |
| VIGILANTE/PORTEIRO | `TR-01=6; TR-02=6; TR-03=*; TR-04=###; TR-05=###; TR-06=###; TR-08=2; TR-09=##; TR-17=¨; TR-35=4` | 18 |
| FUNÇÕES ADMINISTRATIVAS | `TR-01=6; TR-02=6; TR-03=*; TR-17=¨; TR-35=4` | 16 |

### TELECOMUNICAÇÃO

| Função | Requisitos da linha | total_horas_fonte |
|---|---|---:|
| TÉCNICO EM TELECOM | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-08=2; TR-09=##; TR-11=##; TR-12=##; TR-13=##; TR-14=##; TR-15=16; TR-16=8; TR-17=¨; TR-35=4` | 138 |
| AUXILIAR / AJUDANTE EM TELECOM | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-07=40; TR-08=2; TR-09=##; TR-11=##; TR-12=##; TR-13=##; TR-14=##; TR-15=16; TR-16=8; TR-17=¨; TR-35=4` | 178 |

### EFICIÊNCIA ENERGÉTICA | ILUMINAÇÃO PÚBLICA - IP

| Função | Requisitos da linha | total_horas_fonte |
|---|---|---:|
| TÉCNICO ELETROTÉCNICO | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-08=2; TR-09=##; TR-11=##; TR-12=##; TR-13=##; TR-14=##; TR-15=16; TR-16=8; TR-17=¨; TR-35=4` | 138 |
| ELETRICISTA | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-08=2; TR-09=##; TR-11=##; TR-12=##; TR-13=##; TR-14=##; TR-15=16; TR-16=8; TR-17=¨; TR-35=4` | 138 |
| AJUDANTE ELETRICISTA | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-08=2; TR-09=##; TR-11=##; TR-12=##; TR-13=##; TR-14=##; TR-15=16; TR-16=8; TR-17=¨; TR-35=4` | 138 |

### MANUTENÇÃO DA SUBTRANSMISSÃO

| Função | Requisitos da linha | total_horas_fonte |
|---|---|---:|
| ELETRICISTA DE MANUTENÇÃO DE SUBESTAÇÃO | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-08=2; TR-09=##; TR-11=##; TR-12=##; TR-13=##; TR-14=##; TR-15=16; TR-16=8; TR-18=200; TR-20=280; TR-21=40; TR-24=##; TR-26=##; TR-35=4; TR-36=****` | 658 |
| ELETRICISTA DE LINHA VIVA (SUBTRANSMISSÃO) | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-08=2; TR-09=##; TR-11=##; TR-12=##; TR-13=##; TR-14=##; TR-15=16; TR-16=8; TR-18=200; TR-19=##; TR-20=280; TR-21=40; TR-24=##; TR-25=##; TR-26=##; TR-32=24; TR-35=4; TR-36=****` | 682 |
| TÉCNICO ELETROTÉCNICO | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-09=##; TR-11=##; TR-12=##; TR-13=##; TR-14=##; TR-15=##; TR-16=##; TR-17=¨; TR-24=##; TR-25=##; TR-35=4; TR-36=****` | 112 |
| ENCARREGADO | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-09=##; TR-11=##; TR-12=##; TR-13=##; TR-14=##; TR-15=##; TR-16=##; TR-18=200; TR-20=280; TR-21=40; TR-22=40; TR-24=##; TR-25=##; TR-35=4; TR-36=****` | 672 |
| SUPERVISOR / COORDENADOR / ENGENHEIRO | `TR-01=6; TR-02=6; TR-03=*; TR-04=40; TR-05=40; TR-06=16; TR-09=##; TR-11=##; TR-12=##; TR-13=##; TR-14=##; TR-15=##; TR-16=##; TR-17=¨; TR-35=4; TR-36=****` | 112 |


---

## 8. Matriz de POP's para Distribuição e Outros Processos

### 8.1 Catálogo POP-00

| ID | Treinamento | CH indicada na fonte |
|---|---|---:|
| `POP-MULT` | CURSO DE MULTIPLICADOR DO POP 00 | 56h |
| `POP-LV` | POP-00 - Tarefas e Requisitos Básicos para Execução - LV | 56h |
| `POP-LM` | POP-00 - Tarefas e Requisitos Básicos para Execução - LM | 40h |
| `POP-NSEP` | POP-00 - Tarefas e Requisitos Básicos para Execução - Não SEP | 8 ou 24h |

A fonte também repete o texto:

```text
POP-00 - Tarefas e Requisitos Básicos para Execução ******
```

como subcabeçalho visual. O texto é preservado aqui como informação da fonte, mas não foi criado um quinto curso, pois a matriz apresentada possui quatro colunas de treinamento identificáveis.

### 8.2 Semântica do `#` na matriz POP

Na matriz POP, `#` está associado à nota **“Carga horária POP 00”**.

A nota define **Treinamento de Conceitos de POP 00 — 8 horas** para atividades que se enquadram nos critérios descritos na seção 8.4.

Portanto, no contexto da matriz POP:

```text
# => Treinamento de Conceitos de POP 00, 8h,
     condicionado aos critérios explicitados na nota.
```

Não confundir `#` da matriz POP com `##` ou `###` da matriz principal.

### 8.3 Função → treinamento POP

| Processo/Categoria | Função | Curso POP | Valor da célula |
|---|---|---|---:|
| REDES | ELETRICISTA LINHA VIVA | `POP-LV` | 56 |
| REDES | ELETRICISTA PODADOR (LV) | `POP-LV` | 56 |
| REDES | ENCARREGADO DE LINHA VIVA | `POP-MULT` | 56 |
| REDES | ELETRICISTA LINHA MORTA e/ou MONTADOR DE REDE | `POP-LM` | 40 |
| REDES | AJUDANTE/AUXILIAR DE ELETRICISTA e/ou MONTADOR | `POP-NSEP` | 24 |
| REDES | OPERADOR DE MUNCK/GUINDAUTO | `POP-NSEP` | 24 |
| REDES | ENCARREGADO LINHA MORTA | `POP-MULT` | 56 |
| REDES | PODADOR (em zona livre) | `POP-NSEP` | 24 |
| REDES | LEITURISTA GRUPO A | `POP-NSEP` | # |
| REDES | LEITURISTA GRUPO B E/OU ENTREGADOR | `POP-NSEP` | # |
| REDES | CORTE GAVIÃO / SELO | `POP-NSEP` | # |
| REDES | ENCARREGADO / SUPERVISOR LEITURA | `POP-NSEP` | 24 |
| REDES | TÉCNICO ELETROTÉCNICO E AFINS | `POP-LM` | 40 |
| REDES | SUPERVISOR / COORDENADOR / ENGENHEIRO | `POP-NSEP` | 24 |
| REDES | TÉCNICO DE SEGURANÇA | `POP-LV` | 56 |
| REDES | ENG, TÉC, ELETRICISTA DE MANUT DE SUBESTAÇÃO | `POP-LV` | 56 |
| MONTAGEM ELETROMECÂNICA | MONTADOR ELETROMECÂNICO | `POP-NSEP` | 24 |
| MONTAGEM ELETROMECÂNICA | AUXILIAR / AJUDANTE DE MONTADOR ELETROMECÂNICO | `POP-NSEP` | 24 |
| MONTAGEM ELETROMECÂNICA | ENCARREGADO DE TURMA ELETROMECÂNICA | `POP-NSEP` | 24 |
| DETONAÇÃO | BLASTER | `POP-NSEP` | 8 |
| DETONAÇÃO | MARTELETEIRO | `POP-NSEP` | 8 |
| DETONAÇÃO | AJUDANTE / AUXILIAR | `POP-NSEP` | 8 |
| DETONAÇÃO | ENCARREGADO | `POP-NSEP` | 8 |
| CIVIL / SERVIÇOS GERAIS / OUTROS | MOTORISTA | `POP-NSEP` | # |
| CIVIL / SERVIÇOS GERAIS / OUTROS | SERVIÇOS GERAIS / AUXILIAR DE SERVIÇOS GERAIS | `POP-NSEP` | # |
| CIVIL / SERVIÇOS GERAIS / OUTROS | AJUDANTE GERAL | `POP-NSEP` | # |
| CIVIL / SERVIÇOS GERAIS / OUTROS | ALMOXARIFE / AUXILIAR ALMOXARIFE | `POP-NSEP` | # |
| CIVIL / SERVIÇOS GERAIS / OUTROS | CARPINTEIRO/ PEDREIRO/ ARMADOR / ENCANADOR | `POP-NSEP` | # |
| CIVIL / SERVIÇOS GERAIS / OUTROS | CABO TURMA/ ENCARREGADO/ SUPERVISOR | `POP-NSEP` | # |
| CIVIL / SERVIÇOS GERAIS / OUTROS | ENGENHEIRO | `POP-NSEP` | # |
| CIVIL / SERVIÇOS GERAIS / OUTROS | FISCAL | `POP-NSEP` | # |
| CIVIL / SERVIÇOS GERAIS / OUTROS | MOTORISTA DE CARRETA | `POP-NSEP` | # |
| CIVIL / SERVIÇOS GERAIS / OUTROS | OPERADOR (GERAL) | `POP-NSEP` | # |
| CIVIL / SERVIÇOS GERAIS / OUTROS | OPERADOR DE GUINDAUTO / GUINDASTE | `POP-NSEP` | # |
| CIVIL / SERVIÇOS GERAIS / OUTROS | OPERADOR MÁQUINA PESADA | `POP-NSEP` | # |
| CIVIL / SERVIÇOS GERAIS / OUTROS | OPERADOR MÁQUINAS DIVERSAS | `POP-NSEP` | # |
| CIVIL / SERVIÇOS GERAIS / OUTROS | OPERADOR EMPLILHADEIRA / PALETEIRA ELÉTRICA | `POP-NSEP` | # |
| CIVIL / SERVIÇOS GERAIS / OUTROS | PINTOR | `POP-NSEP` | # |
| CIVIL / SERVIÇOS GERAIS / OUTROS | PROJETISTA | `POP-NSEP` | # |
| CIVIL / SERVIÇOS GERAIS / OUTROS | ROÇADOR \| LIMPEZA DE FAIXA | `POP-NSEP` | # |
| CIVIL / SERVIÇOS GERAIS / OUTROS | TOPÓGRAFO | `POP-NSEP` | # |
| CIVIL / SERVIÇOS GERAIS / OUTROS | AUXILIAR DE TOPÓGRAFO | `POP-NSEP` | # |
| CIVIL / SERVIÇOS GERAIS / OUTROS | TÉCNICO DE SEGURANÇA | `POP-NSEP` | # |
| CIVIL / SERVIÇOS GERAIS / OUTROS | VIGILANTE/PORTEIRO | `POP-NSEP` | # |
| TELECOMUNICAÇÃO | Técnico em Telecom | `POP-NSEP` | # |
| TELECOMUNICAÇÃO | Auxiliar / Ajudante em Telecom | `POP-NSEP` | # |
| ATIVIDADES DE ILUMINAÇÃO (IP) | Técnico Eletrotécnico | `POP-NSEP` | # |
| ATIVIDADES DE ILUMINAÇÃO (IP) | Eletricista | `POP-NSEP` | # |
| ATIVIDADES DE ILUMINAÇÃO (IP) | Ajudante | `POP-NSEP` | # |
| MANUTENÇÃO DA SUBTRANSMISSÃO | ELETRICISTA DE MANUTENÇÃO DE SUBESTAÇÃO | `POP-LM` | 40 |
| MANUTENÇÃO DA SUBTRANSMISSÃO | ELETRICISTA DE LINHA VIVA (SUBTRANSMISSÃO) | `POP-LV` | 56 |
| MANUTENÇÃO DA SUBTRANSMISSÃO | TÉCNICO ELETROTÉCNICO | `POP-LM` | 40 |
| MANUTENÇÃO DA SUBTRANSMISSÃO | ENCARREGADO | `POP-LM` | 40 |
| MANUTENÇÃO DA SUBTRANSMISSÃO | SUPERVISOR / COORDENADOR / ENGENHEIRO | `POP-NSEP` | # |

### 8.4 Critérios para Treinamento de Conceitos de POP 00 — 8 horas

O treinamento de conceitos de POP 00, com carga horária de 8 horas, é aplicado aos seguintes critérios:

1. equipes que realizam atividades em **zonas livres**;
2. equipes que **não realizam escaladas e atividades em altura**;
3. equipes que realizam atividades no **SEP**, porém sem manobras de chaves e equipamentos.

Atividades citadas na fonte como enquadradas nesses critérios:

- Operadores de Automação;
- Operadores de Topografia;
- Fiscais de Obra;
- colaboradores da Construção Civil executando atividades com malha de aterramento interligadas, em zona livre;
- colaboradores da Construção Civil executando adequações de bases de transformadores energizados, em zona livre;
- técnicos e engenheiros que realizam testes de TAC com caixa de corrente;
- operadores de guindautos e guindastes;
- profissionais que executam supervisão de montagem de equipamentos especiais, como transformadores e GIS;
- profissionais que executam estaca raiz em linhas de distribuição;
- atividades de subcontratadas que se enquadrem nas condições.

### 8.5 Conteúdo programático mínimo citado

O conteúdo programático deverá possuir:

- **Considerações Gerais**;
- **Requisitos Básico para a Intervenção**.

### 8.6 Projeto “Meu Encarregado é Massa”

Nota da fonte:

> Encarregado de Rede e Linha Viva realizará o treinamento de multiplicador.

A matriz POP identifica explicitamente:

- `ENCARREGADO DE LINHA VIVA` → `POP-MULT` = 56;
- `ENCARREGADO LINHA MORTA` → `POP-MULT` = 56.

---

## 9. Algoritmo recomendado para o agente de IA

Para determinar os treinamentos aplicáveis a uma pessoa/função:

1. **Identificar o processo/categoria.**
2. **Identificar a função exata dentro do processo.**
3. Recuperar a linha da **matriz principal**.
4. Expandir os IDs `TR-xx` usando o **catálogo de cursos**.
5. Tratar cada célula conforme seu tipo:
   - número → requisito explícito da matriz;
   - `*` → aplicar regra de direção defensiva conforme condução de veículo;
   - `¨` → exigir formação/qualificação/habilitação correspondente à função;
   - `##` → avaliar treinamentos legais específicos da atividade;
   - `###` → avaliar contexto de subestação e, se energizada, NR-10 Básico + SEP + reciclagem;
   - `****`/`*******` → aplicar regra de procedimentos/POP's específicos.
6. Consultar a **matriz POP** de forma separada e adicionar o requisito POP correspondente.
7. Aplicar regras globais que independem da linha:
   - motorista/condutor;
   - uso de 4x4;
   - atividade em subestação;
   - subestação energizada;
   - acúmulo de função;
   - exigência legal específica;
   - POP específico antes do início da atividade.
8. Em acúmulo de funções, combinar as exigências das duas ou mais linhas.
9. Se a função não existir:
   - procurar função realmente similar;
   - marcar a decisão como analogia;
   - se não houver correspondência segura, encaminhar para Segurança do Trabalho da concessionária.
10. **Nunca inventar o significado** de `(+)`, `(++)`, `**` ou `***`.
11. Manter `total_horas_fonte` separado de qualquer total dinâmico calculado após aplicação de condicionais.

---

## 10. Modelo de resposta recomendado para a aplicação

Um agente pode representar o resultado final de uma consulta no seguinte formato lógico:

```yaml
processo: "REDES"
funcao: "ELETRICISTA LINHA VIVA"

matriz_principal:
  total_horas_fonte: 682
  requisitos:
    - curso_id: "TR-01"
      valor_fonte: 6
    - curso_id: "TR-02"
      valor_fonte: 6
    - curso_id: "TR-03"
      valor_fonte: "*"
      tipo: "condicional"
    # ...

pop:
  curso_id: "POP-LV"
  valor_fonte: 56

regras_condicionais_avaliar:
  - direcao_defensiva
  - veiculo_4x4
  - atividade_em_subestacao
  - subestacao_energizada
  - treinamentos_legais_especificos
  - pops_especificos

observacoes:
  - "Não inferir símbolos sem legenda."
  - "Aplicar acúmulo de função quando houver."
```

Este bloco é apenas um **modelo de estrutura**; os dados válidos continuam sendo as tabelas normalizadas deste documento.

---

## 11. Regras de segurança contra interpretações erradas do agente

O agente deve seguir estas restrições:

- célula vazia não significa “curso proibido”; significa apenas ausência de exigência explícita naquela célula;
- marcador não numérico não deve ser convertido em horas sem regra explícita;
- `total_horas_fonte` é o total numérico da linha original, antes de condicionais;
- nomes parecidos em categorias diferentes não devem ser fundidos automaticamente;
- `#`, `##` e `###` são marcadores distintos;
- `****` e `*******` são equivalentes conforme orientação fornecida;
- requisitos de subestação dependem do contexto real da atividade;
- treinamentos adicionais podem ser exigidos por legislação/atividade;
- função não listada exige analogia controlada ou deliberação da Segurança do Trabalho;
- o agente não deve preencher lacunas de `(+)`, `(++)`, `**` e `***` por conhecimento externo ou suposição.

---

## 12. Resumo de integridade

- Cursos da matriz principal: **36**
- Funções normalizadas na matriz principal: **55**
- Categorias/processos da matriz principal: **7**
- Associações função → POP normalizadas: **54**
- Cursos POP identificados: **4**
- Linhas da matriz principal com soma numérica divergente do total da fonte: **0**
- `****` tratado como equivalente a `*******`: **sim**
- conteúdo dos marcadores desconhecidos inventado: **não**
