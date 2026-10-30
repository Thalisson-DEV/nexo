---
nome: investigacao-divergencias
titulo: Investigação de divergências de estoque
descricao: Investiga diferenças entre saldo, movimentações, reservas e solicitações de um material ou estoque, reconstruindo a linha do tempo.
autoridades: [estoque:saldo:read, estoque:movimentacao:read]
---
# Investigação de divergências de estoque

## Passo a passo
1. Identifique o material (código ou descrição) e o estoque/base. Use `consultar_estoque` para o saldo atual (disponível, reservado, total).
2. Reconstrua a linha do tempo com `consultar_movimentacoes` para o material no período (entradas, saídas, transferências, estornos, devoluções), ordenada por data.
3. Verifique reservas ativas e vencidas (`consultar_reservas`) e solicitações em aberto (`consultar_solicitacoes_material`) que comprometem o saldo.
4. Calcule o saldo esperado = saldo inicial + entradas − saídas ± transferências e compare com o saldo registrado. Para cálculos mais longos, use `consultar_sql` com soma acumulada (função de janela) sobre `copiloto.movimentacoes`.
5. Aponte as causas prováveis com evidência: estorno sem nova saída, transferência sem recebimento, reserva vencida retendo saldo, entrega parcial expirada, lançamento fora de ordem.
6. Ofereça atalho para a tela do material ou estoque com `listar_acoes_navegacao` + `propor_acao_navegacao`.

## Cuidados
- Não afirme erro de lançamento sem mostrar as movimentações que sustentam a hipótese.
- Registre o horário da consulta: saldos mudam o tempo todo.
