package com.nexo.app.shared.infrastructure.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidadorIntervaloData.class)
public @interface ValidarIntervaloData {

    String message() default "Intervalo de datas inválido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
