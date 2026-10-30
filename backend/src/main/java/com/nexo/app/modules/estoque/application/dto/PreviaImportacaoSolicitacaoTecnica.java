package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.TipoObraSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Snapshot normalizado e imutavel mantido pelo servidor entre validacao e confirmacao. */
public record PreviaImportacaoSolicitacaoTecnica(
        List<SolicitacaoPrevista> solicitacoesPrevistas
) {
    public PreviaImportacaoSolicitacaoTecnica {
        solicitacoesPrevistas = solicitacoesPrevistas == null ? List.of() : List.copyOf(solicitacoesPrevistas);
    }

    public PreviaImportacaoSolicitacaoTecnica comResultado(String chave, ResultadoConfirmacao resultado) {
        List<SolicitacaoPrevista> atualizados = solicitacoesPrevistas.stream()
                .map(grupo -> Objects.equals(grupo.chave(), chave) ? grupo.comResultado(resultado) : grupo)
                .toList();
        return new PreviaImportacaoSolicitacaoTecnica(atualizados);
    }

    public PreviaImportacaoSolicitacaoTecnica comResultado(String chave, String tipo,
                                                            ResultadoConfirmacao resultado) {
        List<SolicitacaoPrevista> atualizados = solicitacoesPrevistas.stream()
                .map(grupo -> Objects.equals(grupo.chave(), chave)
                        ? grupo.comResultado(tipo, resultado) : grupo)
                .toList();
        return new PreviaImportacaoSolicitacaoTecnica(atualizados);
    }

    public record SolicitacaoPrevista(
            String chave,
            UUID idObra,
            String numeroObra,
            TipoObraSnapshot tipoObra,
            String ocorrencia,
            UUID idAlmoxarifadoAtendimento,
            String almoxarifadoAtendimento,
            UUID idEquipe,
            String codigoEquipe,
            LocalDate dataPrevista,
            String observacao,
            List<Subgrupo> subgrupos,
            List<Mensagem> mensagens,
            ResultadoConfirmacao resultado
    ) {
        public SolicitacaoPrevista {
            subgrupos = subgrupos == null ? List.of() : List.copyOf(subgrupos);
            mensagens = mensagens == null ? List.of() : List.copyOf(mensagens);
        }

        public boolean confirmavel() {
            return mensagens.stream().noneMatch(mensagem -> mensagem.severidade() == Severidade.ERRO);
        }

        public boolean possuiAvisos() {
            return mensagens.stream().anyMatch(mensagem -> mensagem.severidade() == Severidade.AVISO);
        }

        public SolicitacaoPrevista comResultado(ResultadoConfirmacao novoResultado) {
            return new SolicitacaoPrevista(chave, idObra, numeroObra, tipoObra, ocorrencia,
                    idAlmoxarifadoAtendimento, almoxarifadoAtendimento, idEquipe, codigoEquipe,
                    dataPrevista, observacao, subgrupos, mensagens, novoResultado);
        }

        public SolicitacaoPrevista comResultado(String tipo, ResultadoConfirmacao novoResultado) {
            List<Subgrupo> atualizados = subgrupos.stream()
                    .map(subgrupo -> Objects.equals(subgrupo.tipo(), tipo)
                            ? subgrupo.comResultado(novoResultado) : subgrupo)
                    .toList();
            return new SolicitacaoPrevista(chave, idObra, numeroObra, tipoObra, ocorrencia,
                    idAlmoxarifadoAtendimento, almoxarifadoAtendimento, idEquipe, codigoEquipe,
                    dataPrevista, observacao, atualizados, mensagens, resultado);
        }
    }

    public record Subgrupo(
            String tipo,
            List<Item> itens,
            Reserva reserva,
            List<Integer> linhasOrigem,
            ResultadoConfirmacao resultado
    ) {
        public Subgrupo(String tipo, List<Item> itens, Reserva reserva, List<Integer> linhasOrigem) {
            this(tipo, itens, reserva, linhasOrigem, null);
        }

        public Subgrupo {
            itens = itens == null ? List.of() : List.copyOf(itens);
            linhasOrigem = linhasOrigem == null ? List.of() : List.copyOf(linhasOrigem);
        }

        public Subgrupo comResultado(ResultadoConfirmacao novoResultado) {
            return new Subgrupo(tipo, itens, reserva, linhasOrigem, novoResultado);
        }
    }

    public record Item(
            UUID idMaterial,
            String codigoMaterial,
            String descricaoMaterial,

            BigDecimal quantidade,
            boolean detalhaSerie,
            List<Integer> linhasOrigem
    ) {
        public Item {
            linhasOrigem = linhasOrigem == null ? List.of() : List.copyOf(linhasOrigem);
        }
    }

    public record Reserva(
            String numero,
            LocalDate dataCriacao,
            UUID idAgenteResponsavel,
            String agenteResponsavel
    ) {
    }

    public record Mensagem(
            Integer linha,
            String campo,
            String codigo,
            Severidade severidade,
            String valorOriginal,
            String mensagem
    ) {
    }

    public enum Severidade {
        ERRO,
        AVISO
    }

    public record ResultadoConfirmacao(
            List<SolicitacaoCriada> solicitacoes,
            String idempotencyKeyHash,
            OffsetDateTime confirmadoEm,
            String tipo
    ) {
        public ResultadoConfirmacao(List<SolicitacaoCriada> solicitacoes,
                                    String idempotencyKeyHash, OffsetDateTime confirmadoEm) {
            this(solicitacoes, idempotencyKeyHash, confirmadoEm,
                    solicitacoes == null || solicitacoes.isEmpty() ? null : solicitacoes.getFirst().tipo());
        }

        public ResultadoConfirmacao {
            solicitacoes = solicitacoes == null ? List.of() : List.copyOf(solicitacoes);
        }
    }

    public record SolicitacaoCriada(String tipo, UUID id) {
    }
}
