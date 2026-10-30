package com.nexo.app.modules.automacao.application.usecases;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecSaida;
import com.nexo.app.modules.iam.ContatoUsuario;
import com.nexo.app.modules.rh.ContextoOrganizacional;

/**
 * A forma única de "pessoa" nas automações: o usuário com o colaborador por trás dele.
 * Quem configura o fluxo escolhe {@code base}, {@code funcao} ou {@code email} sem saber
 * de que módulo cada informação vem.
 */
public final class Pessoa {

    public static final List<EspecSaida> CAMPOS = List.of(
            EspecSaida.de("nome", "Nome", "string"),
            EspecSaida.de("email", "E-mail", "string"),
            EspecSaida.de("login", "Login", "string"),
            EspecSaida.de("base", "Base operacional", "string"),
            EspecSaida.de("funcao", "Função", "string"),
            EspecSaida.de("centro_custo", "Centro de custo (regional)", "string"),
            EspecSaida.de("processo", "Processo", "string"),
            EspecSaida.de("usuario_id", "Id do usuário", "string"),
            EspecSaida.de("colaborador_id", "Id do colaborador", "string"),
            EspecSaida.de("base_id", "Id da base", "string"));

    private Pessoa() {
    }

    public static Map<String, Object> de(ContatoUsuario contato, ContextoOrganizacional org) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("nome", org != null && org.nomeCompleto() != null ? org.nomeCompleto() : contato == null ? null : contato.login());
        p.put("email", contato == null ? null : contato.email());
        p.put("login", contato == null ? null : contato.login());
        p.put("base", org == null ? null : org.baseOperacional());
        p.put("funcao", org == null ? null : org.funcao());
        p.put("centro_custo", org == null ? null : org.centroCusto());
        p.put("processo", org == null ? null : org.processo());
        p.put("usuario_id", contato == null ? null : contato.id().toString());
        p.put("colaborador_id", org != null ? org.id().toString()
                : contato != null && contato.idColaborador() != null ? contato.idColaborador().toString() : null);
        p.put("base_id", org == null || org.idBaseOperacional() == null ? null : org.idBaseOperacional().toString());
        return p;
    }
}
