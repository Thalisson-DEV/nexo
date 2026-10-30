package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.ComparacaoCertificadoTreinamentoSst;
import com.nexo.app.modules.sst.application.dto.EvidenciaMatrizTreinamentoSst;
import com.nexo.app.modules.sst.domain.DocumentoSst;
import com.nexo.app.modules.sst.domain.ExigenciaTreinamentoSst;
import com.nexo.app.modules.sst.domain.MotivoIncompatibilidadeTreinamentoSst;
import com.nexo.app.modules.sst.domain.StatusMatrizTreinamentoSst;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Regra pura usada pela grade e pelo resumo, evitando totais divergentes. */
@Component
public class AvaliadorMatrizTreinamentoSst {

    public Resultado avaliar(ExigenciaTreinamentoSst exigencia,
                             UUID idFuncao,
                             com.nexo.app.modules.sst.domain.PapelMembroGovernancaSst papel,
                             List<DocumentoSst> certificados,
                             LocalDate dataCalculo) {
        List<DocumentoSst> ordenados = certificados == null ? List.of() : certificados.stream()
                .filter(DocumentoSst::podeComporConformidade)
                .sorted(Comparator.comparing(DocumentoSst::getId)).toList();
        if (ordenados.isEmpty()) {
            EvidenciaMatrizTreinamentoSst evidencia = evidencia(exigencia, idFuncao, papel,
                    dataCalculo, List.of(), null, null, StatusMatrizTreinamentoSst.AUSENTE,
                    "Nenhum certificado aprovado foi localizado.");
            return new Resultado(StatusMatrizTreinamentoSst.AUSENTE, null, evidencia);
        }

        List<Candidato> candidatos = ordenados.stream().map(certificado -> comparar(exigencia, certificado))
                .toList();
        Candidato selecionado = candidatos.stream().filter(Candidato::compativel)
                .max(this::compararPreferencia).orElse(null);
        if (selecionado == null) {
            List<ComparacaoCertificadoTreinamentoSst> comparacoes = candidatos.stream()
                    .map(item -> item.comparacao(false)).toList();
            EvidenciaMatrizTreinamentoSst evidencia = evidencia(exigencia, idFuncao, papel,
                    dataCalculo, comparacoes, null, null, StatusMatrizTreinamentoSst.INCOMPATIVEL,
                    "Há certificado, mas nenhum atende integralmente aos critérios configurados.");
            return new Resultado(StatusMatrizTreinamentoSst.INCOMPATIVEL, null, evidencia);
        }

        LocalDate vencimento = selecionado.dataEfetivaVencimento();
        StatusMatrizTreinamentoSst status;
        String motivo;
        if (vencimento == null) {
            status = StatusMatrizTreinamentoSst.REGULAR;
            motivo = "Certificado compatível sem vencimento configurado ou declarado.";
        } else if (dataCalculo.isAfter(vencimento)) {
            status = StatusMatrizTreinamentoSst.VENCIDO;
            motivo = "A data de cálculo é posterior ao vencimento efetivo.";
        } else if (!vencimento.isAfter(dataCalculo.plusDays(exigencia.getAntecedenciaAlertaDias()))) {
            status = StatusMatrizTreinamentoSst.A_VENCER;
            motivo = "O vencimento efetivo está dentro da antecedência de alerta configurada.";
        } else {
            status = StatusMatrizTreinamentoSst.REGULAR;
            motivo = "O certificado selecionado atende aos critérios e está vigente.";
        }

        UUID idSelecionado = selecionado.documento().getId();
        List<ComparacaoCertificadoTreinamentoSst> comparacoes = candidatos.stream()
                .map(item -> item.comparacao(item.documento().getId().equals(idSelecionado))).toList();
        EvidenciaMatrizTreinamentoSst evidencia = evidencia(exigencia, idFuncao, papel,
                dataCalculo, comparacoes, idSelecionado, vencimento, status, motivo);
        return new Resultado(status, vencimento, evidencia);
    }

    private Candidato comparar(ExigenciaTreinamentoSst exigencia, DocumentoSst documento) {
        EnumSet<MotivoIncompatibilidadeTreinamentoSst> motivos =
                EnumSet.noneOf(MotivoIncompatibilidadeTreinamentoSst.class);
        boolean carga = documento.getCargaHorariaMinutos() != null
                && documento.getCargaHorariaMinutos() >= exigencia.getCargaHorariaMinutos();
        if (!carga) motivos.add(MotivoIncompatibilidadeTreinamentoSst.CARGA_HORARIA_INSUFICIENTE);
        boolean instituicao = exigencia.aceitaInstituicao(documento.getEmissor());
        if (!instituicao) motivos.add(MotivoIncompatibilidadeTreinamentoSst.INSTITUICAO_NAO_PERMITIDA);
        boolean modalidade = exigencia.getModalidade() == null
                || exigencia.getModalidade() == documento.getModalidade();
        if (!modalidade) motivos.add(MotivoIncompatibilidadeTreinamentoSst.MODALIDADE_NAO_PERMITIDA);
        LocalDate recorrencia = exigencia.dataRecorrencia(documento.getDataConclusaoTreinamento());
        LocalDate efetiva = anterior(documento.getDataValidade(), recorrencia);
        return new Candidato(documento, carga, instituicao, modalidade,
                Set.copyOf(motivos), recorrencia, efetiva);
    }

    private int compararPreferencia(Candidato esquerdo, Candidato direito) {
        int vencimento = compararDataComNuloNoFim(esquerdo.dataEfetivaVencimento(),
                direito.dataEfetivaVencimento());
        if (vencimento != 0) return vencimento;
        int conclusao = compararDataComNuloNoFim(esquerdo.documento().getDataConclusaoTreinamento(),
                direito.documento().getDataConclusaoTreinamento());
        if (conclusao != 0) return conclusao;
        return direito.documento().getId().compareTo(esquerdo.documento().getId());
    }

    private static int compararDataComNuloNoFim(LocalDate esquerda, LocalDate direita) {
        if (esquerda == null && direita == null) return 0;
        if (esquerda == null) return 1;
        if (direita == null) return -1;
        return esquerda.compareTo(direita);
    }

    private static LocalDate anterior(LocalDate primeira, LocalDate segunda) {
        if (primeira == null) return segunda;
        if (segunda == null) return primeira;
        return primeira.isBefore(segunda) ? primeira : segunda;
    }

    private EvidenciaMatrizTreinamentoSst evidencia(
            ExigenciaTreinamentoSst exigencia,
            UUID idFuncao,
            com.nexo.app.modules.sst.domain.PapelMembroGovernancaSst papel,
            LocalDate data,
            List<ComparacaoCertificadoTreinamentoSst> comparacoes,
            UUID idSelecionado,
            LocalDate vencimento,
            StatusMatrizTreinamentoSst status,
            String motivo) {
        List<UUID> ids = new ArrayList<>();
        comparacoes.forEach(item -> ids.add(item.idDocumento()));
        return new EvidenciaMatrizTreinamentoSst(data, exigencia.getId(),
                exigencia.getRevisaoExigencia(), exigencia.getModoAplicabilidade(), idFuncao,
                papel, List.copyOf(ids), idSelecionado, comparacoes, vencimento, status, motivo);
    }

    public record Resultado(StatusMatrizTreinamentoSst status,
                            LocalDate dataEfetivaVencimento,
                            EvidenciaMatrizTreinamentoSst evidencia) {
    }

    private record Candidato(DocumentoSst documento,
                             boolean carga,
                             boolean instituicao,
                             boolean modalidade,
                             Set<MotivoIncompatibilidadeTreinamentoSst> motivos,
                             LocalDate recorrencia,
                             LocalDate dataEfetivaVencimento) {
        boolean compativel() {
            return motivos.isEmpty();
        }

        ComparacaoCertificadoTreinamentoSst comparacao(boolean selecionado) {
            return new ComparacaoCertificadoTreinamentoSst(documento.getId(), carga, instituicao,
                    modalidade, motivos, documento.getDataConclusaoTreinamento(),
                    documento.getDataValidade(), recorrencia, dataEfetivaVencimento, selecionado);
        }
    }
}
