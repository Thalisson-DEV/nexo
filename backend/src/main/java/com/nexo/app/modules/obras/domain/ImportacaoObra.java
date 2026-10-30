package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Registro de uma importação de planilha do processo de Obras (kits, carteira, orçamento previsto), com o
 * relatório por linha. É criada em PROCESSANDO e concluída pelo processamento assíncrono.
 */
@Entity
@Table(name = "importacoes_obras")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImportacaoObra extends BaseEntity {

    private static final int LIMITE_ERROS_DETALHADOS = 1000;

    public enum Tipo { KITS, CARTEIRA, ORCAMENTO }

    public enum Status { PROCESSANDO, CONCLUIDA, FALHOU }

    public record ErroLinha(int linha, String referencia, String motivo) {
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private Tipo tipo;

    @Column(name = "id_contrato")
    private UUID idContrato;

    @Column(name = "nome_arquivo", nullable = false)
    private String nomeArquivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "total_linhas", nullable = false)
    private int totalLinhas;

    @Column(name = "inseridos", nullable = false)
    private int inseridos;

    @Column(name = "atualizados", nullable = false)
    private int atualizados;

    @Column(name = "erros", nullable = false)
    private int erros;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detalhe_erros", nullable = false, columnDefinition = "jsonb")
    private List<ErroLinha> detalheErros = new ArrayList<>();

    @Column(name = "mensagem", length = 1000)
    private String mensagem;

    @Column(name = "data_conclusao")
    private OffsetDateTime dataConclusao;

    public ImportacaoObra(Tipo tipo, UUID idContrato, String nomeArquivo) {
        if (tipo == null) {
            throw new InvarianteVioladaException("O tipo da importação é obrigatório.");
        }
        this.tipo = tipo;
        this.idContrato = idContrato;
        this.nomeArquivo = nomeArquivo != null && !nomeArquivo.isBlank() ? nomeArquivo.trim() : "planilha";
        this.status = Status.PROCESSANDO;
    }

    public void registrarInserido() {
        inseridos++;
    }

    public void registrarAtualizado() {
        atualizados++;
    }

    public void registrarErro(int linha, String referencia, String motivo) {
        erros++;
        if (detalheErros.size() < LIMITE_ERROS_DETALHADOS) {
            detalheErros = new ArrayList<>(detalheErros);
            detalheErros.add(new ErroLinha(linha, referencia, motivo));
        }
    }

    public void concluir(int totalLinhas) {
        this.totalLinhas = totalLinhas;
        this.status = Status.CONCLUIDA;
        this.dataConclusao = OffsetDateTime.now();
    }

    public void falhar(String mensagem) {
        this.status = Status.FALHOU;
        this.mensagem = mensagem != null && mensagem.length() > 1000 ? mensagem.substring(0, 1000) : mensagem;
        this.dataConclusao = OffsetDateTime.now();
    }
}
