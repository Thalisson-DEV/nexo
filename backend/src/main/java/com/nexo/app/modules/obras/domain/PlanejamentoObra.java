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

import java.util.HashMap;
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
}
