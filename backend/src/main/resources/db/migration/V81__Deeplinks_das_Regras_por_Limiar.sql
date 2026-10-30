-- V81: os deeplinks que faltaram na V80.
--
-- A V80 corrigiu os destinos das regras POR OCORRÊNCIA — as que apontam para um
-- registro específico (/dp/descontos/{{idDesconto}} e companhia). Ficaram de fora as
-- quatro regras POR LIMIAR da V77, que não apontam para um registro e sim para uma
-- listagem, e que estavam erradas de duas maneiras ao mesmo tempo:
--
--   1) o caminho não existe no frontend;
--   2) o filtro em query string é inerte.
--
-- Sobre (2): conferido componente a componente em ces-frontend. Nenhuma dessas telas
-- lê 'status' da URL — o dashboard de desvios lê 'gravidade', 'inicio' e 'fim'; a
-- lista de chamados decide o recorte pelo 'modo' declarado na própria rota. Levar à
-- listagem certa entrega mais do que preservar um filtro que o destino ignora.
--
-- Por que uma migration separada, e não uma edição da V80: a V80 já havia sido
-- aplicada em desenvolvimento quando estas quatro correções foram identificadas.
-- Alterar o arquivo mudaria o checksum e exigiria 'flyway repair' — que realinha o
-- histórico mas NÃO reexecuta nada, deixando o banco permanentemente sem estas
-- quatro linhas. Uma migration nova é o caminho que faz todo ambiente convergir para
-- o mesmo estado, tenha ele recebido a V80 antes ou depois.
--
-- Os UPDATE são guardados pelo valor antigo, então são idempotentes: um banco criado
-- do zero apenas não encontra nada para atualizar.

UPDATE regras_notificacao SET deeplink_template = '/chamados/fila'
 WHERE deeplink_template = '/chamados?status=ABERTO';

UPDATE regras_notificacao SET deeplink_template = '/dp/descontos/lancamento'
 WHERE deeplink_template = '/descontos?status=SOLICITADO';

UPDATE regras_notificacao SET deeplink_template = '/sst/dashboards/desvios'
 WHERE deeplink_template = '/sst/desvios?status=ABERTO';

-- Compras e pagamentos têm telas de aprovação separadas, e esta regra conta os dois
-- juntos (o gatilho fornece só 'total' e 'limiar', sem o tipo). Aprovações de compra
-- é o destino de maior tráfego; quem trabalha com pagamentos alterna pela própria
-- tela.
UPDATE regras_notificacao SET deeplink_template = '/financeiro/aprovacoes/compras'
 WHERE deeplink_template = '/financeiro/solicitacoes?status=SOLICITADO';


-- ═══════════════════════════════════════════════════════════════════════════════
-- Nota sobre a remoção física na V80
-- ═══════════════════════════════════════════════════════════════════════════════
-- A função pg_temp.clonar_para_publico da V80 usa DELETE para tirar o público que
-- migrou para a regra nova, em vez do soft delete que é a política do projeto. O
-- arquivo não foi corrigido porque já havia sido aplicado, e reescrevê-lo custaria o
-- 'flyway repair' descrito acima.
--
-- Não há o que consertar aqui: a linha removida representava um destinatário que
-- deixou de existir naquela regra, e o registro do que aconteceu está na própria
-- migration. Fica anotado para que a próxima migration que mexa em destinatários use
-- desativação, e para que ninguém tome aquele DELETE como precedente.
