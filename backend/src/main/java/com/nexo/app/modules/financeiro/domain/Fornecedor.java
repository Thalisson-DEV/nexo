package com.nexo.app.modules.financeiro.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;
import com.nexo.app.shared.infrastructure.security.HashUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * Fornecedor de materiais ou serviços.
 *
 * <p>Existe para eliminar a redigitação de razão social e dados bancários a cada
 * solicitação — o solicitante escolhe o fornecedor e o restante vem junto. Como
 * nem sempre o fornecedor já está cadastrado, ele pode ser criado na hora e
 * nasce {@link StatusFornecedor#PENDENTE} de curadoria do financeiro.</p>
 *
 * <p>🔒 Os dados bancários são cifrados em repouso (AES-256 GCM). A busca usa
 * blind indices dos termos, sem consultar nem persistir outra cópia em texto
 * aberto.</p>
 */
@Entity
@Table(name = "financeiro_fornecedor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Fornecedor extends BaseEntity {

    private static final int TAMANHO_CNPJ = 14;
    private static final int TAMANHO_CPF = 11;

    @Column(name = "nome_fantasia", nullable = false, length = 150)
    private String nomeFantasia;

    @Column(name = "razao_social", length = 200)
    private String razaoSocial;

    /** Somente dígitos; aceita CNPJ (14) ou CPF (11) para fornecedor pessoa física. */
    @Column(name = "documento", length = 14)
    private String documento;

    /** 🔒 Banco, agência, conta e/ou chave PIX. Cifrado em repouso. */
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "dados_bancarios", columnDefinition = "TEXT")
    private String dadosBancarios;

    /** Blind indices que tornam PIX/agencia/conta pesquisaveis sem expor o texto cifrado. */
    @Column(name = "dados_bancarios_busca", columnDefinition = "TEXT")
    private String dadosBancariosBusca;

    @Column(name = "observacoes", columnDefinition = "TEXT")
    private String observacoes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusFornecedor status = StatusFornecedor.PENDENTE;

    @Column(name = "motivo_bloqueio", columnDefinition = "TEXT")
    private String motivoBloqueio;

    public Fornecedor(String nomeFantasia, String razaoSocial, String documento,
                      String dadosBancarios, String observacoes) {
        this.nomeFantasia = exigirNomeFantasia(nomeFantasia);
        this.razaoSocial = normalizarTexto(razaoSocial);
        this.documento = validarDocumento(documento);
        this.dadosBancarios = normalizarTexto(dadosBancarios);
        this.dadosBancariosBusca = HashUtils.gerarIndicesBusca(this.dadosBancarios);
        this.observacoes = normalizarTexto(observacoes);
        this.status = StatusFornecedor.PENDENTE;
    }

    public void atualizar(String nomeFantasia, String razaoSocial, String documento,
                          String dadosBancarios, String observacoes) {
        this.nomeFantasia = exigirNomeFantasia(nomeFantasia);
        this.razaoSocial = normalizarTexto(razaoSocial);
        this.documento = validarDocumento(documento);
        this.dadosBancarios = normalizarTexto(dadosBancarios);
        this.dadosBancariosBusca = HashUtils.gerarIndicesBusca(this.dadosBancarios);
        this.observacoes = normalizarTexto(observacoes);
    }

    /**
     * Confirma que o financeiro conferiu os dados. Exige razão social e documento,
     * que é justamente o que costuma faltar num cadastro feito às pressas.
     */
    public void homologar() {
        if (status == StatusFornecedor.HOMOLOGADO) {
            throw new InvarianteVioladaException("O fornecedor já está homologado.");
        }
        if (!StringUtils.hasText(razaoSocial)) {
            throw new InvarianteVioladaException("Informe a razão social antes de homologar o fornecedor.");
        }
        if (!StringUtils.hasText(documento)) {
            throw new InvarianteVioladaException("Informe o CNPJ/CPF antes de homologar o fornecedor.");
        }
        this.status = StatusFornecedor.HOMOLOGADO;
        this.motivoBloqueio = null;
    }

    /** Impede novas solicitações para este fornecedor, preservando o histórico. */
    public void bloquear(String motivo) {
        if (!StringUtils.hasText(motivo)) {
            throw new InvarianteVioladaException("Informe o motivo do bloqueio do fornecedor.");
        }
        this.status = StatusFornecedor.BLOQUEADO;
        this.motivoBloqueio = motivo.trim();
    }

    /** Retorna o fornecedor bloqueado à curadoria do financeiro. */
    public void desbloquear() {
        if (status != StatusFornecedor.BLOQUEADO) {
            throw new InvarianteVioladaException("O fornecedor não está bloqueado.");
        }
        this.status = StatusFornecedor.PENDENTE;
        this.motivoBloqueio = null;
    }

    /**
     * Garante que este fornecedor pode ser alvo de uma nova solicitação.
     */
    public void exigirUtilizavel() {
        if (!isAtivo()) {
            throw new InvarianteVioladaException("O fornecedor '" + nomeFantasia + "' está inativo.");
        }
        if (!status.isUtilizavel()) {
            throw new InvarianteVioladaException(
                    "O fornecedor '" + nomeFantasia + "' está bloqueado"
                            + (StringUtils.hasText(motivoBloqueio) ? ": " + motivoBloqueio : "."));
        }
    }

    /** Reconstroi os blind indices depois de carregar um registro anterior a V131. */
    public void reindexarDadosBancarios() {
        this.dadosBancariosBusca = HashUtils.gerarIndicesBusca(dadosBancarios);
    }

    private static String exigirNomeFantasia(String valor) {
        if (!StringUtils.hasText(valor)) {
            throw new InvarianteVioladaException("O nome do fornecedor é obrigatório.");
        }
        return valor.trim();
    }

    private static String normalizarTexto(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }

    /**
     * Aceita o documento com ou sem máscara e o guarda apenas com dígitos, para que
     * a unicidade não dependa da formatação digitada.
     */
    private static String validarDocumento(String documento) {
        if (!StringUtils.hasText(documento)) {
            return null;
        }
        String digitos = documento.replaceAll("\\D", "");
        if (digitos.length() != TAMANHO_CNPJ && digitos.length() != TAMANHO_CPF) {
            throw new InvarianteVioladaException("O documento deve ser um CNPJ (14 dígitos) ou CPF (11 dígitos).");
        }
        return digitos;
    }
}
