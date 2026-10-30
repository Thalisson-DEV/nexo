package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Atividade pré-cadastrada sob a qual um evento de SST acontece — trajeto, detonação,
 * carregamento, e assim por diante.
 *
 * <p>Não é uma execução: é a natureza do que estava sendo feito. Por isso vive aqui e
 * não no módulo de execução, e por isso carrega um gestor responsável, que é quem
 * responde pela atividade na governança.</p>
 *
 * <p>O gestor é um colaborador do RH e pode ser trocado a qualquer momento — a
 * rotatividade é justamente o motivo pelo qual o evento <b>copia</b> atividade e gestor
 * no instante do registro em vez de apontar para cá. Trocar o gestor amanhã muda quem
 * responde pelos próximos eventos e não toca em nenhum evento passado.</p>
 *
 * <p>O gestor é <b>opcional aqui e obrigatório no evento</b>. A carga inicial traz as
 * atividades do contrato já cadastradas, e quem as cadastra em massa não sabe, naquele
 * momento, quem responde por cada uma. Enquanto a atividade estiver sem gestor, o
 * registro do evento exige que ele seja informado — a exigência não desaparece, só
 * muda de lugar.</p>
 */
@Entity
@Table(name = "atividades_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AtividadeSst extends BaseEntity {

    private static final int NOME_MINIMO = 2;
    private static final int NOME_MAXIMO = 120;

    @Column(name = "nome", nullable = false, length = NOME_MAXIMO)
    private String nome;

    /** Nome sem acento e em minúsculas: é sobre ele que a unicidade e a busca comparam. */
    @Column(name = "nome_normalizado", nullable = false, length = NOME_MAXIMO)
    private String nomeNormalizado;

    @Column(name = "descricao", length = 500)
    private String descricao;

    /** Colaborador responsável pela atividade. Sem FK: a fronteira com o RH é a API pública. */
    @Column(name = "id_gestor")
    private UUID idGestor;

    /** Processo de negócio a que a atividade pertence, quando ela é específica de um. */
    @Column(name = "id_processo")
    private UUID idProcesso;

    public AtividadeSst(String nome, String descricao, UUID idGestor, UUID idProcesso) {
        aplicarDados(nome, descricao, idGestor, idProcesso);
    }

    public void atualizar(String nome, String descricao, UUID idGestor, UUID idProcesso) {
        exigirAtiva();
        aplicarDados(nome, descricao, idGestor, idProcesso);
    }

    /**
     * Indica se a atividade já tem gestor definido. Quando não tem, o registro do
     * evento cobra o gestor de quem está registrando.
     */
    public boolean possuiGestor() {
        return idGestor != null;
    }

    private void aplicarDados(String nome, String descricao, UUID idGestor, UUID idProcesso) {
        if (nome == null || nome.isBlank()) {
            throw new InvarianteVioladaException("O nome da atividade é obrigatório.");
        }
        String nomeLimpo = nome.trim();
        if (nomeLimpo.length() < NOME_MINIMO || nomeLimpo.length() > NOME_MAXIMO) {
            throw new InvarianteVioladaException(
                    "O nome da atividade deve ter entre " + NOME_MINIMO + " e " + NOME_MAXIMO + " caracteres.");
        }
        this.nome = nomeLimpo;
        this.nomeNormalizado = SpecificationUtils.normalizarTermo(nomeLimpo);
        this.descricao = descricao == null || descricao.isBlank() ? null : descricao.trim();
        this.idGestor = idGestor;
        this.idProcesso = idProcesso;
    }

    private void exigirAtiva() {
        if (!isAtivo()) {
            throw new InvarianteVioladaException(
                    "Não é possível alterar uma atividade desativada. Reative-a antes de editá-la.");
        }
    }
}
