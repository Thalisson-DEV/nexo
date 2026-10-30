package com.nexo.app.shared.domain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exclui um campo do snapshot genérico de auditoria.
 *
 * <p>Use apenas quando o próprio domínio mantém uma trilha estrutural ou versionada e o
 * valor em claro não pode ser duplicado em {@code logs_sistema}. A anotação não altera a
 * persistência do campo nem impede a auditoria dos demais atributos da entidade.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AuditIgnore {
}
