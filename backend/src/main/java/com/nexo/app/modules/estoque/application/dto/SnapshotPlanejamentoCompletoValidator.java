package com.nexo.app.modules.estoque.application.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SnapshotPlanejamentoCompletoValidator
        implements ConstraintValidator<SnapshotPlanejamentoCompleto, PlanejarSolicitacaoTecnicaRequest> {

    @Override
    public boolean isValid(
            PlanejarSolicitacaoTecnicaRequest request,
            ConstraintValidatorContext context
    ) {
        if (request == null) {
            return true;
        }
        boolean valido = request.ocorrenciaInformada()
                && request.idEquipeInformado()
                && request.dataPrevistaInformada()
                && request.observacaoInformada();
        if (valido) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        adicionarSeAusente(context, request.ocorrenciaInformada(), "ocorrencia");
        adicionarSeAusente(context, request.idEquipeInformado(), "idEquipe");
        adicionarSeAusente(context, request.dataPrevistaInformada(), "dataPrevista");
        adicionarSeAusente(context, request.observacaoInformada(), "observacao");
        return false;
    }

    private void adicionarSeAusente(
            ConstraintValidatorContext context,
            boolean presente,
            String campo
    ) {
        if (!presente) {
            context.buildConstraintViolationWithTemplate(
                            "Informe o campo explicitamente; use null para limpar o valor.")
                    .addPropertyNode(campo)
                    .addConstraintViolation();
        }
    }
}
