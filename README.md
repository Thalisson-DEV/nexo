# Nexo

Plataforma de gestão operacional em versão white label: backend Java/Spring Boot,
frontend desktop Angular e PWA de campo. "Nexo" é um nome provisório; marca,
logo e assistente podem ser trocados por cliente.

| Aplicação | Diretório | Desenvolvimento | Build |
| --- | --- | --- | --- |
| Backend | `backend/` | `./mvnw spring-boot:run` | `./mvnw clean package` |
| Frontend | `frontend/` | `ng serve` e `npm start` | `npm run build` |
| Mobile (PWA) | `mobile/` | `npm start` | `npm run build` |

Execute os comandos dentro do diretório correspondente. No Windows, use
`mvnw.cmd`. Os Dockerfiles mantêm Java 21 e Node 22.

## Marca

- Logos e ícones: `frontend/public/` e `mobile/public/` (`logo_vertical*.svg`,
  `favicon.*`, `icons/`, `assets/logo*`).
- Avatar do assistente (Copiloto): `frontend/public/icons/copiloto-*.png`.
- Nome do sistema nos textos: "Nexo"; razão social nos relatórios impressos:
  "NOME DA EMPRESA".

## Módulos ocultos

Treinamento, CIPA, Brigada de Incêndio e Comunicação ficam fora do menu e das
rotas desta distribuição. A lista está em
`frontend/src/app/core/white-label/modulos-ocultos.ts`; para devolver um
módulo, basta tirá-lo de lá. O backend continua com os módulos.

## Implantação

Este repositório não está ligado a nenhuma infraestrutura. Cada implantação
precisa do próprio banco Postgres, Redis, armazenamento S3/R2, SMTP e chaves de
IA, configurados por variáveis de ambiente (veja `backend/src/main/resources/application.yaml`).
