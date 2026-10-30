package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import com.nexo.app.shared.domain.exception.ArmazenamentoException;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;

/** Monta o corpo da ata sem tocar nos cabeçalhos, imagens, estilos e demais partes da base DOCX. */
@Component
public class RenderizacaoAtaDocxService {
    private static final String FONTE = "Arial";

    public byte[] renderizar(InputStream baseDocx, DadosAta dados) {
        if (baseDocx == null) throw new InvarianteVioladaException("A base DOCX é obrigatória.");
        validar(dados);
        try (XWPFDocument documento = new XWPFDocument(baseDocx);
             ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            limparCorpo(documento);
            if (dados.tipoCorpo() == TipoCorpoGovernancaSst.CIPA) {
                titulo(documento, "ATA Nº " + dados.numeroAta() + " – " + dados.tipoReuniao() + " DA CIPAA – " + dados.ciclo());
            } else {
                titulo(documento, "BRIGADA DE INCÊNDIO");
                titulo(documento, dados.numeroAta() + "° ATA DE REUNIÃO " + dados.tipoReuniao());
            }
            
            paragrafo(documento, dados.conteudoAta(), false);
            assinaturas(documento, dados.participantes().stream().filter(ParticipanteAta::presente).toList(), true);
            
            documento.write(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new ArmazenamentoException("Não foi possível gerar o documento Word da ata.", e);
        }
    }

    private void assinaturas(XWPFDocument doc, List<ParticipanteAta> signatarios, boolean novaPagina) {
        if (novaPagina) {
            XWPFParagraph quebra = doc.createParagraph();
            quebra.createRun().addBreak(BreakType.PAGE);
        }
        secao(doc, "ASSINATURAS");
        XWPFTable tabela = doc.createTable(1, 2);
        tabela.setWidth("100%");
        preencher(tabela.getRow(0).getCell(0), "NOME");
        preencher(tabela.getRow(0).getCell(1), "ASSINATURA");
        for (ParticipanteAta participante : signatarios) {
            var linha = tabela.createRow();
            preencher(linha.getCell(0), participante.nome() + "\n" + participante.papel());
            preencher(linha.getCell(1), "");
            linha.setHeight(700);
        }
    }

    private static void limparCorpo(XWPFDocument documento) {
        for (int i = documento.getBodyElements().size() - 1; i >= 0; i--) {
            documento.removeBodyElement(i);
        }
    }

    private void titulo(XWPFDocument doc, String texto) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        executar(p, texto, true, 12);
    }

    private void secao(XWPFDocument doc, String texto) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(160);
        executar(p, texto, true, 11);
    }

    private void paragrafo(XWPFDocument doc, String texto, boolean negrito) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.BOTH);
        p.setFirstLineIndent(420);
        executar(p, texto, negrito, 11);
    }

    private void executar(XWPFParagraph p, String texto, boolean negrito, int tamanho) {
        String[] linhas = texto.split("\\R", -1);
        for (int i = 0; i < linhas.length; i++) {
            XWPFRun run = p.createRun();
            run.setFontFamily(FONTE);
            run.setFontSize(tamanho);
            run.setBold(negrito);
            run.setText(linhas[i]);
            if (i < linhas.length - 1) run.addBreak();
        }
    }

    private void preencher(XWPFTableCell celula, String texto) {
        celula.removeParagraph(0);
        XWPFParagraph p = celula.addParagraph();
        executar(p, texto, false, 10);
    }

    private static void validar(DadosAta d) {
        if (d == null || d.tipoCorpo() == null || vazio(d.numeroAta()) || vazio(d.tipoReuniao())
                || vazio(d.ciclo()) || d.inicio() == null || d.fim() == null || !d.fim().isAfter(d.inicio())
                || vazio(d.conteudoAta()) || d.participantes() == null) {
            throw new InvarianteVioladaException("Os dados confirmados da ata estão incompletos ou a duração é inválida.");
        }
        d.participantes().forEach(p -> {
            if (vazio(p.nome()) || vazio(p.papel()) || (!p.presente() && p.justificada() && vazio(p.motivoAusencia()))) {
                throw new InvarianteVioladaException("Há participante com dados de presença incompletos.");
            }
        });
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    public record DadosAta(TipoCorpoGovernancaSst tipoCorpo, String numeroAta, String tipoReuniao,
                           String ciclo, OffsetDateTime inicio, OffsetDateTime fim,
                           String conteudoAta, List<ParticipanteAta> participantes) {
        public DadosAta {
            participantes = participantes == null ? null : List.copyOf(participantes);
        }
    }

    public record ParticipanteAta(String nome, String papel, boolean presente,
                                  boolean justificada, String motivoAusencia) {}
}
