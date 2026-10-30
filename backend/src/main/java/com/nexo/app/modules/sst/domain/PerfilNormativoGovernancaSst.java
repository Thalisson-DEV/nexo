package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Edição imutável de uma fonte normativa ou configuração interna.
 *
 * <p>Criar uma nova edição gera outro registro. A confirmação humana apenas muda o
 * estado daquela edição e jamais reescreve perfis já referenciados por ciclos.</p>
 */
@Entity
@Table(name = "perfis_normativos_governanca_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerfilNormativoGovernancaSst extends BaseEntity {

    @Column(name = "codigo", nullable = false, length = 60)
    private String codigo;

    @Column(name = "codigo_normalizado", nullable = false, length = 60)
    private String codigoNormalizado;

    @Column(name = "versao_perfil", nullable = false)
    private Integer versaoPerfil;

    @Enumerated(EnumType.STRING)
    @Column(name = "escopo", nullable = false, length = 20)
    private EscopoPerfilNormativoSst escopo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_corpo", nullable = false, length = 20)
    private TipoCorpoGovernancaSst tipoCorpo;

    @Column(name = "jurisdicao", length = 20)
    private String jurisdicao;

    @Column(name = "orgao_emissor", length = 120)
    private String orgaoEmissor;

    @Column(name = "ato_normativo", length = 180)
    private String atoNormativo;

    @Column(name = "edicao", nullable = false, length = 80)
    private String edicao;

    @Column(name = "inicio_vigencia", nullable = false)
    private LocalDate inicioVigencia;

    @Column(name = "fim_vigencia")
    private LocalDate fimVigencia;

    @Column(name = "url_oficial", length = 1000)
    private String urlOficial;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "perfil_normativo_papeis_governanca_sst",
            joinColumns = @JoinColumn(name = "id_perfil_normativo"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "papel", length = 40)
    @Column(name = "quantidade_minima", nullable = false)
    private Map<PapelMembroGovernancaSst, Integer> requisitosPapeis =
            new EnumMap<>(PapelMembroGovernancaSst.class);

    @Column(name = "confirmado", nullable = false)
    private boolean confirmado;

    @Column(name = "data_confirmacao")
    private OffsetDateTime dataConfirmacao;

    @Column(name = "id_usuario_confirmacao")
    private UUID idUsuarioConfirmacao;

    public PerfilNormativoGovernancaSst(
            String codigo,
            Integer versaoPerfil,
            EscopoPerfilNormativoSst escopo,
            TipoCorpoGovernancaSst tipoCorpo,
            String jurisdicao,
            String orgaoEmissor,
            String atoNormativo,
            String edicao,
            LocalDate inicioVigencia,
            LocalDate fimVigencia,
            String urlOficial,
            Map<PapelMembroGovernancaSst, Integer> requisitosPapeis) {
        this.codigo = exigirTexto(codigo, "O código do perfil normativo é obrigatório.", 60);
        this.codigoNormalizado = SpecificationUtils.normalizarTermo(this.codigo);
        if (versaoPerfil == null || versaoPerfil < 1) {
            throw new InvarianteVioladaException("A versão do perfil normativo deve ser maior que zero.");
        }
        if (escopo == null || tipoCorpo == null) {
            throw new InvarianteVioladaException("O escopo e o tipo de corpo do perfil são obrigatórios.");
        }
        if (inicioVigencia == null || (fimVigencia != null && fimVigencia.isBefore(inicioVigencia))) {
            throw new InvarianteVioladaException("Informe um período de vigência válido para o perfil normativo.");
        }
        this.versaoPerfil = versaoPerfil;
        this.escopo = escopo;
        this.tipoCorpo = tipoCorpo;
        this.jurisdicao = textoOuNulo(jurisdicao, 20, "A jurisdição");
        this.orgaoEmissor = textoOuNulo(orgaoEmissor, 120, "O órgão emissor");
        this.atoNormativo = textoOuNulo(atoNormativo, 180, "O ato normativo");
        this.edicao = exigirTexto(edicao, "A edição da fonte normativa é obrigatória.", 80);
        this.inicioVigencia = inicioVigencia;
        this.fimVigencia = fimVigencia;
        this.urlOficial = textoOuNulo(urlOficial, 1000, "A URL oficial");
        if (escopo != EscopoPerfilNormativoSst.INTERNO
                && (this.orgaoEmissor == null || this.atoNormativo == null || this.urlOficial == null)) {
            throw new InvarianteVioladaException(
                    "Perfis normativos nacionais ou estaduais exigem órgão, ato e URL oficial.");
        }
        if (escopo == EscopoPerfilNormativoSst.ESTADUAL && this.jurisdicao == null) {
            throw new InvarianteVioladaException("Perfis estaduais exigem a jurisdição (UF).");
        }
        if (requisitosPapeis != null) {
            requisitosPapeis.forEach((papel, quantidade) -> {
                if (papel == null || !papel.pertenceA(tipoCorpo) || quantidade == null || quantidade < 1) {
                    throw new InvarianteVioladaException(
                            "Cada requisito deve possuir papel compatível e quantidade mínima maior que zero.");
                }
                this.requisitosPapeis.put(papel, quantidade);
            });
        }
    }

    public void confirmar(UUID idUsuario) {
        if (confirmado) {
            throw new InvarianteVioladaException("Esta edição do perfil normativo já foi confirmada.");
        }
        if (idUsuario == null) {
            throw new InvarianteVioladaException("Não foi possível identificar o responsável pela confirmação.");
        }
        if (tipoCorpo == TipoCorpoGovernancaSst.BRIGADA
                && escopo == EscopoPerfilNormativoSst.ESTADUAL
                && requisitosPapeis.isEmpty()) {
            throw new InvarianteVioladaException(
                    "Informe os papéis e quantitativos confirmados antes de validar um perfil estadual da Brigada.");
        }
        this.confirmado = true;
        this.idUsuarioConfirmacao = idUsuario;
        this.dataConfirmacao = OffsetDateTime.now();
    }

    public boolean aplicaA(TipoCorpoGovernancaSst tipo, String uf, LocalDate data) {
        if (!confirmado || tipoCorpo != tipo || data == null || data.isBefore(inicioVigencia)
                || (fimVigencia != null && data.isAfter(fimVigencia))) {
            return false;
        }
        return escopo != EscopoPerfilNormativoSst.ESTADUAL
                || (uf != null && jurisdicao != null && jurisdicao.equalsIgnoreCase(uf));
    }

    private static String exigirTexto(String valor, String mensagem, int limite) {
        if (valor == null || valor.isBlank()) {
            throw new InvarianteVioladaException(mensagem);
        }
        return limitar(valor.trim(), limite, mensagem);
    }

    private static String textoOuNulo(String valor, int limite, String rotulo) {
        return valor == null || valor.isBlank() ? null : limitar(valor.trim(), limite, rotulo);
    }

    private static String limitar(String valor, int limite, String rotulo) {
        if (valor.length() > limite) {
            throw new InvarianteVioladaException(rotulo + " deve ter no máximo " + limite + " caracteres.");
        }
        return valor;
    }
}
