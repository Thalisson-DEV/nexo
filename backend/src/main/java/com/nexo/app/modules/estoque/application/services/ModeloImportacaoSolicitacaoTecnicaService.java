package com.nexo.app.modules.estoque.application.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Modelo de importação técnica em CSV UTF-8. */
@Service
@Slf4j
public class ModeloImportacaoSolicitacaoTecnicaService {

    public static final List<String> CABECALHOS = List.of(
            "Numero Obra", "Ocorrencia OC OS",
            "Almoxarifado Atendimento", "Codigo Equipe", "Data Prevista",
            "Observacao", "Tipo Movimentacao", "Codigo Material", "Quantidade",
            "Numero Reserva", "Data Criacao Reserva", "Agente Responsavel"
    );

    private static final List<String> EXEMPLO = List.of(
            "OBRA-1001", "", "ALMOX CENTRAL", "EQ-01", "2026-08-31",
            "Retirada programada", "221", "MAT-001", "10.500", "12345678901234567890",
            "2026-08-20", ""
    );

    public byte[] gerarCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("# Modelo de Importacao de Solicitacoes Tecnicas de Material\r\n");
        csv.append("# As solicitacoes sao agrupadas automaticamente por Obra + Tipo Movimentacao + Data Prevista (se sem data: Obra + Tipo).\r\n");
        csv.append("# Tipo Movimentacao: 221, 222, 921 ou 922. Datas: AAAA-MM-DD. Quantidade: ate 3 casas decimais.\r\n");
        csv.append("# Ocorrencia e obrigatoria somente para CORRETIVA/PREVENTIVA. Reserva completa e opcional na criacao.\r\n");
        csv.append(String.join(";", CABECALHOS)).append("\r\n");
        csv.append(String.join(";", EXEMPLO)).append("\r\n");
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
}
