package com.nexo.app.shared.infrastructure.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;

public class ValidadorIntervaloData implements ConstraintValidator<ValidarIntervaloData, LocalDate> {

    @Override
    public boolean isValid(LocalDate dataAlvo, ConstraintValidatorContext context) {
        if (dataAlvo == null) {
            return true;
        }

        LocalDate dataMinima = LocalDate.of(2000, 1, 1);
        LocalDate dataMaxima = LocalDate.now().plusYears(5);

        return !dataAlvo.isBefore(dataMinima) && !dataAlvo.isAfter(dataMaxima);
    }
}
