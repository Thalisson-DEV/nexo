package com.nexo.app.modules.rh.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.validator.constraints.br.CPF;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import com.nexo.app.shared.infrastructure.security.HashUtils;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Representa os dados básicos e funcionais de um funcionário da empresa.
 * Campos sensíveis são protegidos por criptografia AES-256 🔒.
 */
@Entity
@Table(name = "colaboradores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Colaborador extends BaseEntity {

    @Column(name = "id_usuario", unique = true)
    private UUID idUsuario;

    @NotBlank(message = "Primeiro nome é obrigatório")
    @Size(min = 2, max = 255, message = "O primeiro nome deve ter entre 2 e 255 caracteres")
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "primeiro_nome", nullable = false)
    private String primeiroNome; // 🔒 Cifrado no banco

    @NotBlank(message = "Último nome é obrigatório")
    @Size(min = 2, max = 255, message = "O ultimo nome deve ter entre 2 e 255 caracteres")
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "ultimo_nome", nullable = false)
    private String ultimoNome; // 🔒 Cifrado no banco

    @NotBlank(message = "Nome completo é obrigatório")
    @Size(min = 3, max = 512, message = "O nome completo deve ter entre 3 e 512 caracteres")
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "nome_completo", nullable = false)
    private String nomeCompleto; // 🔒 Cifrado no banco

    @Column(name = "nome_completo_hash", nullable = false)
    private String nomeCompletoHash; // 🔍 Blind Index para busca exata

    @Column(name = "nome_busca")
    private String nomeBusca; // 🔎 Nome normalizado (texto puro) para busca rápida parcial

    @CPF(message = "CPF inválido")
    @NotBlank(message = "CPF é obrigatório")
    @Size(min = 3, max = 255, message = "O CPF deve ter entre 3 e 255 caracteres")
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "cpf", nullable = false, unique = true)
    private String cpf; // 🔒 Cifrado no banco

    @Column(name = "cpf_hash", nullable = false, unique = true)
    private String cpfHash; // 🔍 Blind Index para busca exata

    @NotNull(message = "O processo é obrigatório")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_processo", nullable = false)
    private ProcessoNegocio processo;

    @NotNull(message = "A base operacional é obrigatória")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_base_operacional", nullable = false)
    private BaseOperacional baseOperacional;

    @NotNull(message = "A função é obrigatória")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_funcao", nullable = false)
    private Funcao funcao;
    
    @NotNull(message = "A data de contratação é obrigatória")
    @Column(name = "data_contratacao")
    private LocalDate dataContratacao;

    public Colaborador(String primeiroNome, String ultimoNome, String nomeCompleto, String cpf, ProcessoNegocio processo, BaseOperacional baseOperacional, Funcao funcao, LocalDate dataContratacao) {
        validarCamposObrigatorios(primeiroNome, ultimoNome, nomeCompleto, cpf, processo, baseOperacional, funcao);
        validarEntidadesAtivas(processo, baseOperacional, funcao);
        this.primeiroNome = primeiroNome.toUpperCase().trim();
        this.ultimoNome = ultimoNome.toUpperCase().trim();
        this.nomeCompleto = nomeCompleto.toUpperCase();
        this.cpf = cpf.replace("-", "").replace(".", "").trim();
        this.processo = processo;
        this.baseOperacional = baseOperacional;
        this.funcao = funcao;
        this.dataContratacao = dataContratacao;
        gerarBlindIndices();
    }

    public void atualizarProcesso(ProcessoNegocio novoProcesso) {
        Objects.requireNonNull(novoProcesso, "Novo processo não pode ser nulo");
        if (!novoProcesso.isAtivo()) throw new InvarianteVioladaException("Não é possível vincular um colaborador a um processo desativado");
        this.processo = novoProcesso;
    }

    public void atualizarFuncao(Funcao novaFuncao) {
        Objects.requireNonNull(novaFuncao, "Nova função não pode ser nula");
        if (!novaFuncao.isAtivo()) throw new InvarianteVioladaException("Não é possível vincular um colaborador a uma função desativada");
        this.funcao = novaFuncao;
    }

    public void atualizarBaseOperacional(BaseOperacional novaBase) {
        Objects.requireNonNull(novaBase, "Nova base operacional não pode ser nula");
        if (!novaBase.isAtivo()) throw new InvarianteVioladaException("Não é possível vincular um colaborador a uma base operacional desativada");
        this.baseOperacional = novaBase;
    }

    public void atualizarDadosPessoais(String primeiroNome, String ultimoNome, String nomeCompleto) {
        if (primeiroNome == null || primeiroNome.isBlank()) throw new IllegalArgumentException("Primeiro nome inválido");
        if (ultimoNome == null || ultimoNome.isBlank()) throw new IllegalArgumentException("Último nome inválido");
        this.primeiroNome = primeiroNome.toUpperCase();
        this.ultimoNome = ultimoNome.toUpperCase();
        this.nomeCompleto = nomeCompleto.toUpperCase();
        gerarBlindIndices();
    }

    public void vincularUsuario(UUID idUsuario) {
        this.idUsuario = Objects.requireNonNull(idUsuario, "ID do Usuário é obrigatório");
    }

    public void validarStatus() {
        if (!isAtivo()) {
            throw new IllegalStateException("Não é possível alterar um registro desativado.");
        }
    }

    private void gerarBlindIndices() {
        this.cpfHash = HashUtils.gerarBlindIndex(this.cpf);
        this.nomeCompletoHash = HashUtils.gerarBlindIndex(this.nomeCompleto.toUpperCase());
        atualizarNomeBusca();
    }

    private void atualizarNomeBusca() {
        this.nomeBusca = SpecificationUtils.normalizarTermo(this.nomeCompleto);
    }

    /** Recalcula o nome de busca a partir do nome completo já carregado (uso no backfill). */
    public void backfillNomeBusca() {
        atualizarNomeBusca();
    }

    private void validarEntidadesAtivas(ProcessoNegocio processo, BaseOperacional base, Funcao funcao) {
        if (!processo.isAtivo()) throw new InvarianteVioladaException("Não é possível vincular um colaborador a um processo desativado");
        if (!base.isAtivo()) throw new InvarianteVioladaException("Não é possível vincular um colaborador a uma base operacional desativada");
        if (!funcao.isAtivo()) throw new InvarianteVioladaException("Não é possível vincular um colaborador a uma função desativada");
    }

    private void validarCamposObrigatorios(String primeiroNome, String ultimoNome, String nomeCompleto, String cpf, ProcessoNegocio processo, BaseOperacional baseOperacional, Funcao funcao) {
        if (primeiroNome == null || primeiroNome.isBlank()) throw new IllegalArgumentException("Primeiro nome inválido");
        if (ultimoNome == null || ultimoNome.isBlank()) throw new IllegalArgumentException("Último nome inválido");
        if (nomeCompleto == null || nomeCompleto.isBlank()) throw new IllegalArgumentException("Nome completo inválido");
        if (cpf == null || cpf.isBlank()) throw new IllegalArgumentException("CPF inválido");
        if (processo == null) throw new IllegalArgumentException("Processo é obrigatório");
        if (baseOperacional == null) throw new IllegalArgumentException("Base Operacional é obrigatória");
        if (funcao == null) throw new IllegalArgumentException("Função é obrigatória");
    }

    public String getNomeCompleto() {
        return this.nomeCompleto;
    }
}
