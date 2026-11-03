package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.modules.obras.domain.DocumentoAsBuilt;
import com.nexo.app.modules.obras.domain.EquipamentoObra;
import com.nexo.app.modules.obras.domain.EstruturaChecklist;
import com.nexo.app.modules.obras.domain.RespostasChecklist;
import com.nexo.app.modules.obras.domain.TipoPontoPlanejamento;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class DocumentacaoTecnicaResponses {

    private DocumentacaoTecnicaResponses() {
    }

    public record Documentacao(boolean visitaRealizada, List<Visita> visitas, boolean temProjetoMestre, List<Ponto> pontos,
                               ResumoAsBuilt asBuilt, List<Documento> documentos, List<Equipamento> equipamentos) {
    }

    public record Visita(UUID id, UUID idModelo, String tituloModelo, LocalDate dataVisita, String responsavel, String observacoes,
                         RespostasChecklist respostas, String usuario, OffsetDateTime data) {
    }

    /** O modelo do ponto é o da ficha ou, antes dela, o das fotos já lançadas; sem nenhum dos dois, fica nulo. */
    public record Ponto(UUID id, String numero, int pagina, TipoPontoPlanejamento tipo, boolean executado, Ficha ficha,
                        UUID idModelo, String tituloModelo, List<EstruturaChecklist.ItemEvidencia> itensEvidencia, List<Evidencia> evidencias) {
    }

    public record Evidencia(UUID id, String idItem, String tituloItem, String legenda, String nomeArquivo, String usuario, OffsetDateTime data) {
    }

    public record Ficha(UUID id, UUID idModelo, String tituloModelo, boolean completa, List<String> pendencias,
                        RespostasChecklist respostas, String usuario, OffsetDateTime data) {
    }

    /** Faltar foto só gera aviso; ficha incompleta é que impede o documento. */
    public record ResumoAsBuilt(int pontosExecutados, int fichasCompletas, List<String> pontosPendentes, List<String> pontosSemEvidencia) {
    }

    public record Documento(UUID id, int numeroVersao, DocumentoAsBuilt.Status status, UUID idAnexo, String mensagemErro,
                            String usuario, OffsetDateTime data) {
    }

    public record Equipamento(UUID id, UUID idPonto, String numeroPonto, int pagina, EquipamentoObra.Tipo tipo, boolean trafoAuxiliar,
                              String observacao) {
    }
}
