package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.TipoEstoqueRepository;
import com.nexo.app.modules.obras.ObraApi.ObraPublica;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Resolve a custodia patrimonial unica de uma obra. */
@Service
@RequiredArgsConstructor
public class EstoqueObraProvisioningService {

    private static final String TIPO_ESTOQUE_OBRA = "OBRA";
    private static final String USUARIO_PROVISIONAMENTO = "SISTEMA";
    private static final int TAMANHO_MAXIMO_NOME = 100;

    private final EstoqueRepository estoqueRepository;
    private final TipoEstoqueRepository tipoEstoqueRepository;

    /**
     * Resolve o estoque existente ou o cria na transacao do comando chamador.
     * O INSERT com ON CONFLICT torna simultaneas primeiras resolucoes idempotentes.
     */
    @Transactional
    public Estoque resolverOuProvisionar(ObraPublica obra) {
        Objects.requireNonNull(obra, "A obra e obrigatoria");
        Objects.requireNonNull(obra.id(), "O ID da obra e obrigatorio");
        Objects.requireNonNull(obra.idBaseOperacional(), "A base operacional da obra e obrigatoria");

        return estoqueRepository.findByIdObra(obra.id())
                .orElseGet(() -> provisionar(obra));
    }

    private Estoque provisionar(ObraPublica obra) {
        TipoEstoque tipoObra = tipoEstoqueRepository.findByNomeIgnoreCase(TIPO_ESTOQUE_OBRA)
                .filter(TipoEstoque::isAtivo)
                .orElseThrow(() -> new InvarianteVioladaException(
                        "O tipo de estoque OBRA nao esta disponivel para provisionamento."));

        estoqueRepository.inserirEstoqueObraSeAusente(
                UUID.randomUUID(),
                montarNome(obra),
                tipoObra.getId(),
                obra.idBaseOperacional(),
                obra.id(),
                USUARIO_PROVISIONAMENTO
        );

        return estoqueRepository.findByIdObra(obra.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Nao foi possivel resolver o estoque patrimonial da obra " + obra.id()));
    }

    private String montarNome(ObraPublica obra) {
        String numero = textoOuVazio(obra.numero());
        String titulo = textoOuVazio(obra.titulo());
        String nome = titulo.isEmpty()
                ? "OBRA " + numero
                : "OBRA " + numero + " - " + titulo;
        nome = nome.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (nome.length() > TAMANHO_MAXIMO_NOME) {
            nome = nome.substring(0, TAMANHO_MAXIMO_NOME).trim();
        }
        return nome;
    }

    private String textoOuVazio(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
