-- V87: o exemplo exibido no campo vazio passa a vir do catálogo
--
-- O placeholder era uma coluna configurável por variante, e na prática nascia
-- sempre vazio: ninguém ia escrever 28 textos de exemplo à mão para cada
-- formulário. O resultado eram telas sem nenhuma pista do que se esperava em cada
-- campo.
--
-- Agora ele é propriedade do campo canônico, ao lado do rótulo padrão: "0,00" no
-- valor, "Pesquise pela placa" no veículo, "dd/mm/aaaa" no vencimento. Isso vale
-- para toda variante, inclusive as que forem criadas depois, sem configuração
-- nenhuma — e some a coluna, porque duas fontes para o mesmo texto acabariam
-- divergindo.

ALTER TABLE financeiro_campo_formulario DROP COLUMN IF EXISTS placeholder;
