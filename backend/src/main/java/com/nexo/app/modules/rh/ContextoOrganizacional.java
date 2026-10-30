package com.nexo.app.modules.rh;

import java.util.UUID;

/**
 * Onde um colaborador se encaixa na estrutura da empresa, com os identificadores
 * ao lado dos nomes.
 *
 * <p>Existe porque {@link ResumoColaborador} resolve o problema de <em>exibir</em>
 * a pessoa, mas não o de <em>agrupar</em> por ela: um dashboard que quebra números
 * por base operacional ou por função precisa de uma chave estável, e nome de cadastro
 * não é chave — muda, se repete e não sobrevive a um drill-down por link.</p>
 *
 * <p>Continua sem CPF e sem datas, pela mesma razão do resumo: quem agrega números
 * não precisa de dado pessoal sensível. O {@code centroCusto} vem junto porque é a
 * regional à qual a base pertence, e quase toda leitura gerencial sobe um nível
 * antes de comparar bases entre si.</p>
 */
public record ContextoOrganizacional(
        UUID id,
        String nomeCompleto,
        UUID idFuncao,
        String funcao,
        UUID idBaseOperacional,
        String baseOperacional,
        UUID idCentroCusto,
        String centroCusto,
        UUID idProcesso,
        String processo,
        boolean ativo
) {
}
