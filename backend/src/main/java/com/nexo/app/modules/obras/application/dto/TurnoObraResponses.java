package com.nexo.app.modules.obras.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexo.app.modules.execucao.domain.StatusTurno;
import com.nexo.app.modules.obras.domain.FormularioApr;
import com.nexo.app.modules.obras.domain.SituacaoLiberacao;
import com.nexo.app.modules.obras.domain.SituacaoProgramacao;
import com.nexo.app.modules.obras.domain.StatusAprTurno;
import com.nexo.app.modules.obras.domain.TipoItemKit;
import com.nexo.app.modules.obras.domain.TipoOcorrenciaTurno;
import com.nexo.app.shared.application.dto.Paged;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Leituras do turno de Obras: listagem com indicadores, detalhe e boletim das equipes. */
public final class TurnoObraResponses {

    private TurnoObraResponses() {
    }

    public record Lista(Indicadores indicadores, List<Linha> content, Paged page) {
    }

    public record Indicadores(int turnos, int emAndamento, long minutosTotais, int comMeta, int bateuMeta,
                              int semRelatorio, int comOcorrencias, long servicosExecutados, BigDecimal valorExecutado) {
    }

    public record Linha(UUID id, UUID idEquipe, String codigoEquipe, String tipoEquipe, String baseOperacional,
                        String placa, StatusTurno status, OffsetDateTime dataInicio, OffsetDateTime dataFim,
                        long duracaoMinutos, boolean abertoDesdeOutroDia, boolean encerradoAutomaticamente,
                        String motivoEncerramento, String abertoPor, int membros, int substitutos,
                        BigDecimal realizado, BigDecimal metaDiaria, BigDecimal percentualMeta, boolean bateuMeta,
                        boolean semRelatorio, int ocorrencias, int aprs, int aprsEmRascunho, long servicos,
                        List<String> obras) {
    }

    public record Membro(UUID idColaborador, String nome, String funcao, boolean lider, boolean substituto) {
    }

    public record Detalhe(UUID id, UUID idEquipe, String codigoEquipe, String tipoEquipe, String baseOperacional,
                          UUID idVeiculo, String placa, StatusTurno status, OffsetDateTime dataInicio, OffsetDateTime dataFim,
                          long duracaoMinutos, boolean abertoDesdeOutroDia, boolean encerradoAutomaticamente,
                          String motivoEncerramento, String abertoPor, List<Membro> composicao,
                          BigDecimal realizado, BigDecimal metaDiaria, BigDecimal percentualMeta, boolean bateuMeta,
                          List<ProgramacaoDoTurno> programacoes, List<AtividadeDoTurno> atividades,
                          List<ServicoApontado> apontamentos, RelatorioDoTurno relatorio, List<Foto> fotos,
                          List<OcorrenciaDoTurno> ocorrencias, List<AprDoTurno> aprs) {
    }

    public record ProgramacaoDoTurno(UUID id, long numero, UUID idObra, String numeroObra, String tituloObra, String descricao,
                                     @JsonFormat(pattern = "dd/MM/yyyy") LocalDate inicio,
                                     @JsonFormat(pattern = "dd/MM/yyyy") LocalDate fim,
                                     SituacaoProgramacao situacao, BigDecimal valor, int itens, int itensConcluidos,
                                     SituacaoLiberacao liberacao) {
    }

    public record AtividadeDoTurno(UUID id, UUID idObra, String numeroObra, String tituloObra, UUID idProgramacao,
                                   Long numeroProgramacao, boolean programada, String observacao, SituacaoLiberacao liberacao) {
    }

    public record ServicoApontado(UUID id, @JsonFormat(pattern = "dd/MM/yyyy") LocalDate data, UUID idProgramacao,
                                  UUID idObra, String numeroObra, String local, TipoItemKit tipo, String codigo,
                                  String descricao, String unidade, BigDecimal quantidade, BigDecimal excedente,
                                  BigDecimal valor, String justificativa, String usuario, OffsetDateTime dataRegistro,
                                  boolean estornavel) {
    }

    public record RelatorioDoTurno(String observacao, String usuario, OffsetDateTime data) {
    }

    public record Foto(UUID id, String nomeArquivo, String contentType, long tamanhoBytes) {
    }

    public record OcorrenciaDoTurno(UUID id, TipoOcorrenciaTurno tipo, String descricao, UUID idObra, String numeroObra,
                                    boolean impedeExecucao, String usuario, OffsetDateTime data) {
    }

    public record AprDoTurno(UUID id, String atividade, UUID idObra, String numeroObra, UUID idProgramacao, Long numeroProgramacao, UUID idResponsavel, String responsavel,
                             StatusAprTurno status, OffsetDateTime dataConclusao, String usuario, OffsetDateTime data,
                             List<Foto> anexos, FormularioApr formulario) {
    }

    public enum SituacaoEquipe {
        EM_TURNO,
        ENCERRADO,
        SEM_TURNO
    }

    public record Boletim(@JsonFormat(pattern = "dd/MM/yyyy") LocalDate data,
                          @JsonFormat(pattern = "dd/MM/yyyy") LocalDate diaAnterior,
                          boolean hoje, OffsetDateTime geradoEm, ResumoBoletim resumo, List<EquipeBoletim> equipes) {
    }

    public record ResumoBoletim(int equipes, int emTurno, int encerrados, int semTurno, int agendamentosSemTurno,
                                BigDecimal programadoDia, BigDecimal executadoDia) {
    }

    public record ValoresBoletim(BigDecimal programado, BigDecimal executado) {
    }

    public record EquipeBoletim(UUID idEquipe, String codigo, String tipoEquipe, UUID idBase, String baseOperacional,
                                SituacaoEquipe situacao, UUID idTurno, OffsetDateTime abertura, OffsetDateTime fechamento,
                                long minutosEmTurno, boolean abertoDesdeOutroDia,
                                String placa, Membro lider, List<Membro> componentes, ValoresBoletim noCiclo,
                                ValoresBoletim noDia, ValoresBoletim diaAnterior, BigDecimal metaDiaria,
                                BigDecimal percentualMeta, boolean agendamentoSemTurno, List<AgendaDoDia> agenda) {
    }

    public record AgendaDoDia(UUID idProgramacao, long numero, UUID idObra, String numeroObra, String tituloObra, String descricao,
                              BigDecimal valor, int itens, int itensConcluidos, SituacaoLiberacao liberacao) {
    }
}
