package com.nexo.app.shared.domain.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Conflito otimista conhecido pela aplicacao, com as versoes necessarias para
 * que o cliente descarte o snapshot obsoleto e recarregue o recurso.
 */
@Getter
public class ConflitoVersaoException extends BaseBusinessException {

    private final Recurso recurso;
    private final Long versaoEsperada;
    private final Long versaoAtual;

    public ConflitoVersaoException(Recurso recurso, Long versaoEsperada, Long versaoAtual) {
        super(
                "Conflito de Versao",
                "A versao conhecida do recurso esta desatualizada. Recarregue os dados e tente novamente.",
                HttpStatus.CONFLICT,
                "versao_desatualizada");
        this.recurso = java.util.Objects.requireNonNull(recurso, "O recurso em conflito e obrigatorio");
        this.versaoEsperada = versaoEsperada;
        this.versaoAtual = versaoAtual;
    }

    public enum Recurso {
        AGRUPADOR,
        SOLICITACAO
    }
}
