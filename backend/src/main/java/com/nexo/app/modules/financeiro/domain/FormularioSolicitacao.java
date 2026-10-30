package com.nexo.app.modules.financeiro.domain;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Raiz do agregado de <b>template</b>: a variante de formulário que uma equipe usa
 * para abrir uma solicitação (compra padrão, compra de frota, compra de obras
 * civis, pagamento).
 *
 * <p>A variante é uma <em>seleção ordenada</em> de campos do sistema. Entra e sai
 * campo sem deploy, muda-se rótulo, ordem e obrigatoriedade — mas nunca o
 * comportamento do campo, que pertence ao {@link CampoCanonico}. Essa fronteira é
 * o que permite configurar em produção sem colocar as regras financeiras em risco:
 * o pior que uma configuração ruim causa é um formulário feio, nunca um dado
 * inválido.</p>
 */
@Entity
@Table(name = "financeiro_formulario_solicitacao",
        uniqueConstraints = @UniqueConstraint(name = "uk_formulario_tipo_codigo",
                columnNames = {"tipo_solicitacao", "codigo"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FormularioSolicitacao extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_solicitacao", nullable = false, length = 20)
    private TipoSolicitacao tipoSolicitacao;

    /** Identificador estável da variante (ex.: PADRAO, FROTA, OBRA_CIVIL). */
    @Column(name = "codigo", nullable = false, length = 40)
    private String codigo;

    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "descricao", length = 500)
    private String descricao;

    /** Variante sugerida ao usuário quando ele não escolhe outra. */
    @Column(name = "padrao", nullable = false)
    private boolean padrao;

    @JsonManagedReference
    @OrderBy("ordem ASC")
    @OneToMany(mappedBy = "formulario", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CampoFormulario> campos = new ArrayList<>();

    public FormularioSolicitacao(TipoSolicitacao tipoSolicitacao, String codigo, String titulo,
                                 String descricao, boolean padrao) {
        if (tipoSolicitacao == null) {
            throw new InvarianteVioladaException("O tipo de solicitação do formulário é obrigatório.");
        }
        this.tipoSolicitacao = tipoSolicitacao;
        this.codigo = exigirCodigo(codigo);
        this.titulo = exigirTitulo(titulo);
        this.descricao = normalizar(descricao);
        this.padrao = padrao;
    }

    public void atualizarDados(String titulo, String descricao, boolean padrao) {
        this.titulo = exigirTitulo(titulo);
        this.descricao = normalizar(descricao);
        this.padrao = padrao;
    }

    /** Usado quando outra variante assume o posto de padrão do mesmo tipo. */
    public void deixarDeSerPadrao() {
        this.padrao = false;
    }

    // ── Campos ──────────────────────────────────────────────────────────────────

    /**
     * Acrescenta um campo do sistema ao fim do formulário. Cada canônico só pode
     * aparecer uma vez — do contrário, dois controles disputariam a mesma coluna.
     */
    public CampoFormulario adicionarCampo(CampoCanonico canonico, String rotulo, String ajuda,
                                          boolean obrigatorio) {
        if (canonico != null && possuiCanonico(canonico)) {
            throw new InvarianteVioladaException(
                    "O campo '" + canonico.getRotuloPadrao() + "' já faz parte deste formulário.");
        }
        CampoFormulario campo = new CampoFormulario(
                this, canonico, rotulo, ajuda, obrigatorio, campos.size());
        this.campos.add(campo);
        return campo;
    }

    public void atualizarCampo(UUID idCampo, String rotulo, String ajuda, boolean obrigatorio) {
        buscarCampo(idCampo).atualizarApresentacao(rotulo, ajuda, obrigatorio);
    }

    /**
     * Remove um campo, desde que nenhum outro dependa dele para se exibir — tirar
     * "Tem NF" deixaria a justificativa inalcançável.
     */
    public void removerCampo(UUID idCampo) {
        CampoFormulario campo = buscarCampo(idCampo);
        dependenteDe(campo.getCampoCanonico()).ifPresent(dependente -> {
            throw new InvarianteVioladaException(
                    "O campo '" + campo.getRotulo() + "' governa a exibição de '" + dependente.getRotulo()
                            + "'. Remova o dependente antes.");
        });
        this.campos.remove(campo);
        renumerar();
    }

    public void reordenarCampos(List<UUID> idsNaOrdem) {
        ReordenacaoValidator.validarPermutacao(
                campos.stream().map(CampoFormulario::getId).toList(), idsNaOrdem, "campos");

        for (int i = 0; i < idsNaOrdem.size(); i++) {
            UUID id = idsNaOrdem.get(i);
            buscarCampo(id).atualizarOrdem(i);
        }
        campos.sort(Comparator.comparingInt(CampoFormulario::getOrdem));
    }

    // ── Consultas ───────────────────────────────────────────────────────────────

    /** Campos ativos do formulário, na ordem de apresentação. */
    public List<CampoFormulario> camposOrdenados() {
        return campos.stream()
                .filter(CampoFormulario::isAtivo)
                .sorted(Comparator.comparingInt(CampoFormulario::getOrdem))
                .toList();
    }

    public Optional<CampoFormulario> buscarCampoPorCanonico(CampoCanonico canonico) {
        return campos.stream().filter(c -> canonico == c.getCampoCanonico()).findFirst();
    }

    public boolean possuiCanonico(CampoCanonico canonico) {
        return buscarCampoPorCanonico(canonico).isPresent();
    }

    public CampoFormulario buscarCampo(UUID idCampo) {
        return campos.stream()
                .filter(c -> idCampo.equals(c.getId()))
                .findFirst()
                .orElseThrow(() -> new InvarianteVioladaException("Campo não encontrado neste formulário."));
    }

    /**
     * Confere que o formulário está pronto para receber solicitações. É chamada a
     * cada alteração estrutural e novamente a cada submissão, de modo que uma
     * configuração incompleta nunca chegue ao solicitante.
     */
    public void validarEstrutura() {
        List<CampoFormulario> ativos = camposOrdenados();
        if (ativos.isEmpty()) {
            throw new InvarianteVioladaException("O formulário deve possuir ao menos um campo.");
        }
        if (!possuiCanonico(CampoCanonico.VALOR)) {
            throw new InvarianteVioladaException(
                    "O formulário deve conter o campo de valor — é ele que a diretoria autoriza.");
        }
        for (CampoFormulario campo : ativos) {
            campo.getCondicao().ifPresent(condicao -> {
                if (!possuiCanonico(condicao.governante())) {
                    throw new InvarianteVioladaException(
                            "O campo '" + campo.getRotulo() + "' só aparece conforme a resposta de '"
                                    + condicao.governante().getRotuloPadrao()
                                    + "', que precisa estar no formulário.");
                }
            });
        }
    }

    // ── Apoio ───────────────────────────────────────────────────────────────────

    /** Primeiro campo do formulário cuja exibição é governada pelo canônico dado. */
    private Optional<CampoFormulario> dependenteDe(CampoCanonico governante) {
        return campos.stream()
                .filter(c -> c.getCondicao().filter(cond -> cond.governante() == governante).isPresent())
                .findFirst();
    }

    private void renumerar() {
        campos.sort(Comparator.comparingInt(CampoFormulario::getOrdem));
        for (int i = 0; i < campos.size(); i++) {
            campos.get(i).atualizarOrdem(i);
        }
    }

    private static String exigirCodigo(String codigo) {
        if (!StringUtils.hasText(codigo)) {
            throw new InvarianteVioladaException("O código da variante é obrigatório.");
        }
        String normalizado = codigo.trim().toUpperCase();
        if (!normalizado.matches("[A-Z][A-Z0-9_]{0,39}")) {
            throw new InvarianteVioladaException(
                    "O código da variante deve começar com letra e conter apenas letras, números e '_'.");
        }
        return normalizado;
    }

    private static String exigirTitulo(String titulo) {
        if (!StringUtils.hasText(titulo)) {
            throw new InvarianteVioladaException("O título do formulário é obrigatório.");
        }
        return titulo.trim();
    }

    private static String normalizar(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }
}
