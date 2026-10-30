# Modelo de Fiscalização — Luzes de Emergência, Identificação de Voltagem e Extintores

## 1. Objetivo

Este documento normaliza o modelo de fiscalização fornecido para uso por agentes de IA e pela aplicação.

A planilha original possui dois blocos na mesma tabela:

1. **Luzes de emergência e identificação de voltagem das tomadas**;
2. **Extintores de incêndio**.

Na versão normalizada, cada bloco é independente e cada item de inspeção é um registro próprio.

> **Importante:** campos de inspeção vazios na fonte são representados como `null`. Isso significa **“ainda não informado/preenchido”**, e não “conforme”, “aprovado” ou “não aplicável”.

---

## 2. Regras gerais de interpretação

### 2.1 Estados de preenchimento

Use:

```text
null
```

quando o campo ainda não possui informação registrada.

O agente **não deve converter `null` em `SIM`, `NÃO`, `OK` ou `CONFORME`**.

### 2.2 Resultado da vistoria

A fonte fornecida não define uma legenda de valores para os campos de inspeção.

Por isso, este documento preserva os campos como valores livres inicialmente nulos.

Para futura implementação, a aplicação pode definir uma enumeração própria, por exemplo:

```text
CONFORME
NAO_CONFORME
NAO_APLICAVEL
NAO_VERIFICADO
```

mas essa enumeração **não faz parte da fonte original** e não deve ser considerada regra existente sem validação do processo.

### 2.3 Conteúdo preservado

Os nomes de locais, agentes, capacidades e tipos foram preservados semanticamente.

Foram removidos apenas excessos de espaços usados para apresentação visual na planilha.

### 2.4 Dados suspeitos

O seguinte valor da fonte aparenta inconsistência:

```text
Extintor Posto 10:
agente = "AP"
capacidade = "10 L"
tipo = "8"
localizacao = "ALMOXARIFADO FINAL DO GALPÃO"
```

O valor `tipo = "8"` foi **preservado literalmente**.

O agente deve sinalizar esse registro para validação humana caso o tipo do extintor seja necessário para uma decisão.

---

# PARTE A — LUZES DE EMERGÊNCIA E IDENTIFICAÇÃO DE VOLTAGEM

## 3. Campos da inspeção

Cada ponto de inspeção possui:

```yaml
sequencia: integer
localizacao: string

inspecao:
  sinalizacao: null | valor
  limpeza_e_obstrucoes: null | valor
  teste_funcionamento: null | valor
  identificacao_tomadas: null | valor
```

### Significado dos campos

- `sinalizacao`: condição da sinalização relacionada à luz de emergência;
- `limpeza_e_obstrucoes`: verificação de limpeza e existência de obstruções;
- `teste_funcionamento`: resultado do teste funcional da luz de emergência;
- `identificacao_tomadas`: verificação da identificação de voltagem das tomadas.

> As descrições acima apenas tornam explícitos os títulos das colunas da fonte. Não introduzem critérios adicionais de aprovação.

---

## 4. Registros normalizados — Luzes de emergência

```yaml
luzes_emergencia:
  - sequencia: 1
    localizacao: "ADMINISTRATIVO INICIO DO CORREDOR"
    inspecao: {sinalizacao: null, limpeza_e_obstrucoes: null, teste_funcionamento: null, identificacao_tomadas: null}
  - sequencia: 2
    localizacao: "ADMINISTRATIVO MEIO DO CORREDOR"
    inspecao: {sinalizacao: null, limpeza_e_obstrucoes: null, teste_funcionamento: null, identificacao_tomadas: null}
  - sequencia: 3
    localizacao: "ADMINISTRATIVO FINAL DO CORREDOR"
    inspecao: {sinalizacao: null, limpeza_e_obstrucoes: null, teste_funcionamento: null, identificacao_tomadas: null}
  - sequencia: 4
    localizacao: "SALA DO SETOR DA GERENCIA OPERACIONAL"
    inspecao: {sinalizacao: null, limpeza_e_obstrucoes: null, teste_funcionamento: null, identificacao_tomadas: null}
  - sequencia: 5
    localizacao: "SALA DO SESMT"
    inspecao: {sinalizacao: null, limpeza_e_obstrucoes: null, teste_funcionamento: null, identificacao_tomadas: null}
  - sequencia: 6
    localizacao: "BANHEIRO FEMININO"
    inspecao: {sinalizacao: null, limpeza_e_obstrucoes: null, teste_funcionamento: null, identificacao_tomadas: null}
  - sequencia: 7
    localizacao: "BANHEIRO MASCULINO"
    inspecao: {sinalizacao: null, limpeza_e_obstrucoes: null, teste_funcionamento: null, identificacao_tomadas: null}
  - sequencia: 8
    localizacao: "SALA DA CONSTRUÇÃO"
    inspecao: {sinalizacao: null, limpeza_e_obstrucoes: null, teste_funcionamento: null, identificacao_tomadas: null}
  - sequencia: 9
    localizacao: "SALA DO RH"
    inspecao: {sinalizacao: null, limpeza_e_obstrucoes: null, teste_funcionamento: null, identificacao_tomadas: null}
  - sequencia: 10
    localizacao: "SALA DO FINANCEIRO"
    inspecao: {sinalizacao: null, limpeza_e_obstrucoes: null, teste_funcionamento: null, identificacao_tomadas: null}
  - sequencia: 11
    localizacao: "SALA DE TREINAMENTO"
    inspecao: {sinalizacao: null, limpeza_e_obstrucoes: null, teste_funcionamento: null, identificacao_tomadas: null}
  - sequencia: 12
    localizacao: "SALA DO MÉDICO"
    inspecao: {sinalizacao: null, limpeza_e_obstrucoes: null, teste_funcionamento: null, identificacao_tomadas: null}
  - sequencia: 13
    localizacao: "SALA DO COMERCIAL"
    inspecao: {sinalizacao: null, limpeza_e_obstrucoes: null, teste_funcionamento: null, identificacao_tomadas: null}
  - sequencia: 14
    localizacao: "SALA DA COORDENAÇÃO DO COMERCIAL"
    inspecao: {sinalizacao: null, limpeza_e_obstrucoes: null, teste_funcionamento: null, identificacao_tomadas: null}
  - sequencia: 15
    localizacao: "SALA DA LEITURA"
    inspecao: {sinalizacao: null, limpeza_e_obstrucoes: null, teste_funcionamento: null, identificacao_tomadas: null}
  - sequencia: 16
    localizacao: "SALA DO FECHAMENTO"
    inspecao: {sinalizacao: null, limpeza_e_obstrucoes: null, teste_funcionamento: null, identificacao_tomadas: null}
  - sequencia: 17
    localizacao: "SALA DO DESMPENHO"
    inspecao: {sinalizacao: null, limpeza_e_obstrucoes: null, teste_funcionamento: null, identificacao_tomadas: null}
  - sequencia: 18
    localizacao: "SALA DA LOGISTICA"
    inspecao: {sinalizacao: null, limpeza_e_obstrucoes: null, teste_funcionamento: null, identificacao_tomadas: null}
  - sequencia: 19
    localizacao: "SALA DA COORDENAÇÃO LOGISTICA"
    inspecao: {sinalizacao: null, limpeza_e_obstrucoes: null, teste_funcionamento: null, identificacao_tomadas: null}
  - sequencia: 20
    localizacao: "SALA DO ALMOXARIFADO DE MEDIDORES"
    inspecao: {sinalizacao: null, limpeza_e_obstrucoes: null, teste_funcionamento: null, identificacao_tomadas: null}
  - sequencia: 21
    localizacao: "SALA DA OFICINA"
    inspecao: {sinalizacao: null, limpeza_e_obstrucoes: null, teste_funcionamento: null, identificacao_tomadas: null}
  - sequencia: 22
    localizacao: "SALA DA PORTARIA"
    inspecao: {sinalizacao: null, limpeza_e_obstrucoes: null, teste_funcionamento: null, identificacao_tomadas: null}
```

---

## 5. Índice compacto — Luzes de emergência

| Sequência | Localização |
|---:|---|
| 1 | ADMINISTRATIVO INICIO DO CORREDOR |
| 2 | ADMINISTRATIVO MEIO DO CORREDOR |
| 3 | ADMINISTRATIVO FINAL DO CORREDOR |
| 4 | SALA DO SETOR DA GERENCIA OPERACIONAL |
| 5 | SALA DO SESMT |
| 6 | BANHEIRO FEMININO |
| 7 | BANHEIRO MASCULINO |
| 8 | SALA DA CONSTRUÇÃO |
| 9 | SALA DO RH |
| 10 | SALA DO FINANCEIRO |
| 11 | SALA DE TREINAMENTO |
| 12 | SALA DO MÉDICO |
| 13 | SALA DO COMERCIAL |
| 14 | SALA DA COORDENAÇÃO DO COMERCIAL |
| 15 | SALA DA LEITURA |
| 16 | SALA DO FECHAMENTO |
| 17 | SALA DO DESMPENHO |
| 18 | SALA DA LOGISTICA |
| 19 | SALA DA COORDENAÇÃO LOGISTICA |
| 20 | SALA DO ALMOXARIFADO DE MEDIDORES |
| 21 | SALA DA OFICINA |
| 22 | SALA DA PORTARIA |

---

# PARTE B — EXTINTORES DE INCÊNDIO

## 6. Campos cadastrais do extintor

Cada extintor possui dados fixos provenientes da matriz:

```yaml
posto: integer
agente: string
capacidade: string
tipo: string
localizacao: string
```

E campos de fiscalização:

```yaml
inspecao:
  proxima_recarga: null | valor
  proximo_teste_hidrostatico: null | valor
  mangueira_difusor: null | valor
  existe_etiqueta: null | valor
  lacre_nao_violado: null | valor
  manometro_no_verde: null | valor
  conservacao_suporte: null | valor
  sinalizacao_parede: null | valor
  sinalizacao_piso: null | valor
```

### Observação sobre datas

`proxima_recarga` e `proximo_teste_hidrostatico` aparecem como campos vazios na fonte.

A aplicação poderá armazená-los como data, desde que a regra de negócio defina o formato. Neste documento eles permanecem `null`.

---

## 7. Registros normalizados — Extintores

```yaml
extintores:
  - posto: 1
    agente: "PQS"
    capacidade: "08 KG"
    tipo: "BC"
    localizacao: "ADMINISTRATIVO INICIO CORREDOR"
    inspecao: {proxima_recarga: null, proximo_teste_hidrostatico: null, mangueira_difusor: null, existe_etiqueta: null, lacre_nao_violado: null, manometro_no_verde: null, conservacao_suporte: null, sinalizacao_parede: null, sinalizacao_piso: null}
  - posto: 2
    agente: "AP"
    capacidade: "10 L"
    tipo: "Agua"
    localizacao: "ADMINISTRATIVO INICIO CORREDOR"
    inspecao: {proxima_recarga: null, proximo_teste_hidrostatico: null, mangueira_difusor: null, existe_etiqueta: null, lacre_nao_violado: null, manometro_no_verde: null, conservacao_suporte: null, sinalizacao_parede: null, sinalizacao_piso: null}
  - posto: 3
    agente: "PQS"
    capacidade: "08 KG"
    tipo: "BC"
    localizacao: "ADMINISTRATIVO MEIO DO CORREDOR"
    inspecao: {proxima_recarga: null, proximo_teste_hidrostatico: null, mangueira_difusor: null, existe_etiqueta: null, lacre_nao_violado: null, manometro_no_verde: null, conservacao_suporte: null, sinalizacao_parede: null, sinalizacao_piso: null}
  - posto: 4
    agente: "AP"
    capacidade: "10 L"
    tipo: "Agua"
    localizacao: "ADMINISTRATIVO MEIO DO CORREDOR"
    inspecao: {proxima_recarga: null, proximo_teste_hidrostatico: null, mangueira_difusor: null, existe_etiqueta: null, lacre_nao_violado: null, manometro_no_verde: null, conservacao_suporte: null, sinalizacao_parede: null, sinalizacao_piso: null}
  - posto: 5
    agente: "PQS"
    capacidade: "08 KG"
    tipo: "BC"
    localizacao: "ADMINISTRATIVO FINAL DO CORREDOR"
    inspecao: {proxima_recarga: null, proximo_teste_hidrostatico: null, mangueira_difusor: null, existe_etiqueta: null, lacre_nao_violado: null, manometro_no_verde: null, conservacao_suporte: null, sinalizacao_parede: null, sinalizacao_piso: null}
  - posto: 6
    agente: "AP"
    capacidade: "10 L"
    tipo: "Agua"
    localizacao: "ADMINISTRATIVO FINAL DO CORREDOR"
    inspecao: {proxima_recarga: null, proximo_teste_hidrostatico: null, mangueira_difusor: null, existe_etiqueta: null, lacre_nao_violado: null, manometro_no_verde: null, conservacao_suporte: null, sinalizacao_parede: null, sinalizacao_piso: null}
  - posto: 7
    agente: "PQS"
    capacidade: "08 KG"
    tipo: "BC"
    localizacao: "ALMOXARIFADO FRENTE DO ADMINISTRATIVO"
    inspecao: {proxima_recarga: null, proximo_teste_hidrostatico: null, mangueira_difusor: null, existe_etiqueta: null, lacre_nao_violado: null, manometro_no_verde: null, conservacao_suporte: null, sinalizacao_parede: null, sinalizacao_piso: null}
  - posto: 8
    agente: "AP"
    capacidade: "10 L"
    tipo: "Agua"
    localizacao: "ALMOXARIFADO FRENTE DO ADMINISTRATIVO"
    inspecao: {proxima_recarga: null, proximo_teste_hidrostatico: null, mangueira_difusor: null, existe_etiqueta: null, lacre_nao_violado: null, manometro_no_verde: null, conservacao_suporte: null, sinalizacao_parede: null, sinalizacao_piso: null}
  - posto: 9
    agente: "PQS"
    capacidade: "08 KG"
    tipo: "BC"
    localizacao: "ALMOXARIFADO FINAL DO GALPÃO"
    inspecao: {proxima_recarga: null, proximo_teste_hidrostatico: null, mangueira_difusor: null, existe_etiqueta: null, lacre_nao_violado: null, manometro_no_verde: null, conservacao_suporte: null, sinalizacao_parede: null, sinalizacao_piso: null}
  - posto: 10
    agente: "AP"
    capacidade: "10 L"
    tipo: "8"
    localizacao: "ALMOXARIFADO FINAL DO GALPÃO"
    inspecao: {proxima_recarga: null, proximo_teste_hidrostatico: null, mangueira_difusor: null, existe_etiqueta: null, lacre_nao_violado: null, manometro_no_verde: null, conservacao_suporte: null, sinalizacao_parede: null, sinalizacao_piso: null}
  - posto: 11
    agente: "PQS"
    capacidade: "06 KG"
    tipo: "BC"
    localizacao: "OFICINA SALÃO DE MANUTENÇÃO"
    inspecao: {proxima_recarga: null, proximo_teste_hidrostatico: null, mangueira_difusor: null, existe_etiqueta: null, lacre_nao_violado: null, manometro_no_verde: null, conservacao_suporte: null, sinalizacao_parede: null, sinalizacao_piso: null}
  - posto: 12
    agente: "AP"
    capacidade: "10 L"
    tipo: "Agua"
    localizacao: "OFICINA SALÃO DE MANUTENÇÃO"
    inspecao: {proxima_recarga: null, proximo_teste_hidrostatico: null, mangueira_difusor: null, existe_etiqueta: null, lacre_nao_violado: null, manometro_no_verde: null, conservacao_suporte: null, sinalizacao_parede: null, sinalizacao_piso: null}
  - posto: 13
    agente: "PQS"
    capacidade: "08 KG"
    tipo: "BC"
    localizacao: "OFICINA SALA ADMINISTRATIVO"
    inspecao: {proxima_recarga: null, proximo_teste_hidrostatico: null, mangueira_difusor: null, existe_etiqueta: null, lacre_nao_violado: null, manometro_no_verde: null, conservacao_suporte: null, sinalizacao_parede: null, sinalizacao_piso: null}
  - posto: 14
    agente: "PQS"
    capacidade: "08 KG"
    tipo: "BC"
    localizacao: "EM FRENTE A SALA DE TREINAMENTO"
    inspecao: {proxima_recarga: null, proximo_teste_hidrostatico: null, mangueira_difusor: null, existe_etiqueta: null, lacre_nao_violado: null, manometro_no_verde: null, conservacao_suporte: null, sinalizacao_parede: null, sinalizacao_piso: null}
```

---

## 8. Índice compacto — Extintores

| Posto | Agente | Capacidade | Tipo | Localização |
|---:|---|---|---|---|
| 1 | PQS | 08 KG | BC | ADMINISTRATIVO INICIO CORREDOR |
| 2 | AP | 10 L | Agua | ADMINISTRATIVO INICIO CORREDOR |
| 3 | PQS | 08 KG | BC | ADMINISTRATIVO MEIO DO CORREDOR |
| 4 | AP | 10 L | Agua | ADMINISTRATIVO MEIO DO CORREDOR |
| 5 | PQS | 08 KG | BC | ADMINISTRATIVO FINAL DO CORREDOR |
| 6 | AP | 10 L | Agua | ADMINISTRATIVO FINAL DO CORREDOR |
| 7 | PQS | 08 KG | BC | ALMOXARIFADO FRENTE DO ADMINISTRATIVO |
| 8 | AP | 10 L | Agua | ALMOXARIFADO FRENTE DO ADMINISTRATIVO |
| 9 | PQS | 08 KG | BC | ALMOXARIFADO FINAL DO GALPÃO |
| 10 | AP | 10 L | 8 | ALMOXARIFADO FINAL DO GALPÃO |
| 11 | PQS | 06 KG | BC | OFICINA SALÃO DE MANUTENÇÃO |
| 12 | AP | 10 L | Agua | OFICINA SALÃO DE MANUTENÇÃO |
| 13 | PQS | 08 KG | BC | OFICINA SALA ADMINISTRATIVO |
| 14 | PQS | 08 KG | BC | EM FRENTE A SALA DE TREINAMENTO |

---

## 9. Regras recomendadas para o agente

### 9.1 Consultar um ponto de luz de emergência

```text
1. localizar pelo campo sequencia;
2. confirmar a localizacao;
3. ler cada campo de inspecao separadamente;
4. se o valor for null, informar que o item ainda não possui resultado registrado;
5. não inferir conformidade pela simples existência do ponto cadastrado.
```

### 9.2 Consultar um extintor

```text
1. localizar pelo campo posto;
2. retornar agente, capacidade, tipo e localizacao;
3. avaliar cada campo de inspecao de forma independente;
4. se algum campo estiver null, tratá-lo como não preenchido;
5. nunca considerar o extintor aprovado apenas porque está cadastrado.
```

### 9.3 Avaliar pendências

Um agente pode considerar como **pendente de fiscalização** qualquer item cuja informação de inspeção necessária ainda esteja `null`.

Exemplo:

```text
se teste_funcionamento == null
=> teste de funcionamento ainda não registrado
```

ou:

```text
se lacre_nao_violado == null
=> condição do lacre ainda não registrada
```

Isso representa **pendência de informação**, não necessariamente não conformidade.

### 9.4 Não conformidade

A fonte não informa quais valores representam conformidade ou não conformidade.

Logo:

```text
null != NAO_CONFORME
null != CONFORME
```

A aplicação deverá definir os valores aceitos antes de o agente classificar resultados automaticamente.

---

## 10. Modelo sugerido para preenchimento pela aplicação

### Luz de emergência

```yaml
sequencia: 1
localizacao: "ADMINISTRATIVO INICIO DO CORREDOR"

inspecao:
  sinalizacao: "CONFORME"
  limpeza_e_obstrucoes: "CONFORME"
  teste_funcionamento: "CONFORME"
  identificacao_tomadas: "CONFORME"

observacao: null
data_fiscalizacao: "AAAA-MM-DD"
responsavel: "NOME/ID"
```

### Extintor

```yaml
posto: 1
agente: "PQS"
capacidade: "08 KG"
tipo: "BC"
localizacao: "ADMINISTRATIVO INICIO CORREDOR"

inspecao:
  proxima_recarga: "AAAA-MM-DD"
  proximo_teste_hidrostatico: "AAAA-MM-DD"
  mangueira_difusor: "CONFORME"
  existe_etiqueta: "CONFORME"
  lacre_nao_violado: "CONFORME"
  manometro_no_verde: "CONFORME"
  conservacao_suporte: "CONFORME"
  sinalizacao_parede: "CONFORME"
  sinalizacao_piso: "CONFORME"

observacao: null
data_fiscalizacao: "AAAA-MM-DD"
responsavel: "NOME/ID"
```

> Os campos `observacao`, `data_fiscalizacao`, `responsavel` e os valores `CONFORME` são apenas uma **sugestão de modelagem para a aplicação**. Eles não estavam presentes na planilha original.

---

## 11. Estrutura recomendada para banco/API

```text
FiscalizacaoSeguranca
├── LuzEmergencia
│   ├── Cadastro do ponto
│   └── Inspecoes
│       ├── sinalizacao
│       ├── limpeza_e_obstrucoes
│       ├── teste_funcionamento
│       └── identificacao_tomadas
│
└── Extintor
    ├── Cadastro do equipamento
    │   ├── posto
    │   ├── agente
    │   ├── capacidade
    │   ├── tipo
    │   └── localizacao
    └── Inspecoes
        ├── proxima_recarga
        ├── proximo_teste_hidrostatico
        ├── mangueira_difusor
        ├── existe_etiqueta
        ├── lacre_nao_violado
        ├── manometro_no_verde
        ├── conservacao_suporte
        ├── sinalizacao_parede
        └── sinalizacao_piso
```

### Separação importante

O cadastro físico do equipamento/ponto deve ser separado do histórico de fiscalizações.

Isso permite que:

```text
um extintor -> várias fiscalizações ao longo do tempo
uma luz de emergência -> várias fiscalizações ao longo do tempo
```

sem duplicar os dados cadastrais a cada vistoria.

---

## 12. Resumo de integridade

```text
Pontos de luz de emergência normalizados: 22
Extintores normalizados: 14
Campos de inspeção de luz por ponto: 4
Campos de inspeção de extintor por equipamento: 9
```

### Informação suspeita preservada

```text
Posto 10
agente: AP
capacidade: 10 L
tipo: 8
localizacao: ALMOXARIFADO FINAL DO GALPÃO
```

Esse valor deve ser validado na fonte antes de eventual correção.

Nenhum campo vazio foi interpretado como aprovado, reprovado ou não aplicável.
