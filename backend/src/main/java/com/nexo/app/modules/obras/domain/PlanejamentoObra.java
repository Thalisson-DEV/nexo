package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dados do projeto mestre da obra: qual anexo é a base do planejamento e a rotação salva de cada página,
 * que vale para todos os usuários. Substituir o projeto só troca o anexo: pontos, vãos e anotações
 * continuam nas mesmas páginas e posições relativas.
 */
@Entity
@Table(name = "planejamentos_obras")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanejamentoObra extends BaseEntity {

    private static final Set<Integer> ROTACOES_VALIDAS = Set.of(0, 90, 180, 270);

    @Column(name = "id_obra", nullable = false)
    private UUID idObra;

    @Column(name = "id_anexo_mestre")
    private UUID idAnexoMestre;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rotacoes", nullable = false, columnDefinition = "jsonb")
    private Map<String, Integer> rotacoes = new HashMap<>();

    @Column(name = "levantamento_concluido_em")
    private OffsetDateTime levantamentoConcluidoEm;

    @Column(name = "levantamento_concluido_por", length = 255)
    private String levantamentoConcluidoPor;

    public PlanejamentoObra(UUID idObra) {
        if (idObra == null) {
            throw new InvarianteVioladaException("A obra do planejamento é obrigatória.");
        }
        this.idObra = idObra;
    }

    public void definirProjetoMestre(UUID idAnexo) {
        if (idAnexo == null) {
            throw new InvarianteVioladaException("Informe o anexo do projeto mestre.");
        }
        this.idAnexoMestre = idAnexo;
    }

    public void girarPagina(int pagina, int graus) {
        if (pagina < 1) {
            throw new InvarianteVioladaException("A página deve ser a partir de 1.");
        }
        if (!ROTACOES_VALIDAS.contains(graus)) {
            throw new InvarianteVioladaException("A rotação deve ser de 0, 90, 180 ou 270 graus.");
        }
        Map<String, Integer> novas = new HashMap<>(rotacoes);
        if (graus == 0) {
            novas.remove(String.valueOf(pagina));
        } else {
            novas.put(String.valueOf(pagina), graus);
        }
        this.rotacoes = novas;
    }

    public boolean levantamentoConcluido() {
        return levantamentoConcluidoEm != null;
    }

    /**
     * O levantamento só fecha com pontos e com todo ponto e vão com kit: é o que garante que o valor planejado da obra
     * está completo antes de programar e medir.
     */
    public void concluirLevantamento(int pontos, List<String> pontosSemKit, int vaosSemKit, String usuario,
                                     OffsetDateTime quando) {
        if (levantamentoConcluido()) {
            throw new InvarianteVioladaException("O levantamento desta obra já está concluído.");
        }
        if (pontos == 0) {
            throw new InvarianteVioladaException("A obra não tem pontos lançados no planejamento.");
        }
        if (!pontosSemKit.isEmpty() || vaosSemKit > 0) {
            StringBuilder mensagem = new StringBuilder("Não é possível concluir o levantamento:");
            if (!pontosSemKit.isEmpty()) {
                List<String> amostra = pontosSemKit.stream().limit(20).toList();
                mensagem.append(" ").append(pontosSemKit.size()).append(pontosSemKit.size() == 1 ? " ponto sem kit (" : " pontos sem kit (")
                        .append(String.join(", ", amostra)).append(pontosSemKit.size() > amostra.size() ? ", …)" : ")");
            }
            if (vaosSemKit > 0) {
                mensagem.append(pontosSemKit.isEmpty() ? " " : " e ").append(vaosSemKit).append(vaosSemKit == 1 ? " vão sem kit" : " vãos sem kit");
            }
            throw new InvarianteVioladaException(mensagem.append(".").toString());
        }
        this.levantamentoConcluidoEm = quando;
        this.levantamentoConcluidoPor = usuario;
    }

    public void reabrirLevantamento() {
        if (!levantamentoConcluido()) {
            throw new InvarianteVioladaException("O levantamento desta obra já está pendente.");
        }
        this.levantamentoConcluidoEm = null;
        this.levantamentoConcluidoPor = null;
    }
}
