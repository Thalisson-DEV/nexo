package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "respostas_checklist_governanca_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RespostaChecklistGovernancaSst extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_execucao", nullable = false)
    private ExecucaoChecklistGovernancaSst execucao;

    @Column(name = "id_pergunta", nullable = false)
    private UUID idPergunta;

    @Column(name = "pergunta_snapshot", nullable = false, length = 500)
    private String perguntaSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_resposta", nullable = false, length = 30)
    private TipoResposta tipoResposta;

    @Column(name = "obrigatoria", nullable = false)
    private boolean obrigatoria;

    @Column(name = "valor_texto", length = 2000)
    private String valorTexto;

    @Column(name = "valor_numero", precision = 18, scale = 4)
    private BigDecimal valorNumero;

    @Column(name = "valor_data")
    private LocalDate valorData;

    @OneToMany(mappedBy = "resposta", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OpcaoRespostaChecklistGovernancaSst> opcoes = new ArrayList<>();

    RespostaChecklistGovernancaSst(ExecucaoChecklistGovernancaSst execucao, NovaResposta nova) {
        this.execucao = Objects.requireNonNull(execucao, "Execução é obrigatória");
        this.idPergunta = Objects.requireNonNull(nova.idPergunta(), "Pergunta é obrigatória");
        this.perguntaSnapshot = textoObrigatorio(nova.perguntaSnapshot(), "Pergunta", 500);
        this.tipoResposta = Objects.requireNonNull(nova.tipoResposta(), "Tipo de resposta é obrigatório");
        this.obrigatoria = nova.obrigatoria();
        this.valorTexto = limitarOpcional(nova.valorTexto(), 2000);
        this.valorNumero = nova.valorNumero();
        this.valorData = nova.valorData();
        if (nova.opcoes() != null) {
            nova.opcoes().forEach(opcao -> opcoes.add(new OpcaoRespostaChecklistGovernancaSst(this, opcao)));
        }
    }

    public boolean obrigatoriaSemResposta() {
        if (!obrigatoria) return false;
        return switch (tipoResposta) {
            case TEXTO -> valorTexto == null || valorTexto.isBlank();
            case NUMERO -> valorNumero == null;
            case DATA -> valorData == null;
            case MULTIPLA_ESCOLHA, MULTIPLA_SELECAO -> opcoes.isEmpty();
        };
    }

    private static String textoObrigatorio(String valor, String campo, int limite) {
        String normalizado = limitarOpcional(valor, limite);
        if (normalizado == null) throw new InvarianteVioladaException(campo + " é obrigatória.");
        return normalizado;
    }

    private static String limitarOpcional(String valor, int limite) {
        if (valor == null || valor.isBlank()) return null;
        String normalizado = valor.trim();
        if (normalizado.length() > limite) {
            throw new InvarianteVioladaException("O texto excede " + limite + " caracteres.");
        }
        return normalizado;
    }

    public record NovaResposta(
            UUID idPergunta,
            String perguntaSnapshot,
            TipoResposta tipoResposta,
            boolean obrigatoria,
            String valorTexto,
            BigDecimal valorNumero,
            LocalDate valorData,
            List<NovaOpcao> opcoes) {
    }

    public record NovaOpcao(
            UUID idOpcao,
            String opcaoSnapshot,
            boolean naoConforme,
            GravidadeDesvio gravidade) {
    }
}
