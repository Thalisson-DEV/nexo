package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.ConfirmarImportacaoEventoSstRequest;
import com.nexo.app.modules.sst.application.dto.EventoSstImportacaoDTO;
import com.nexo.app.modules.sst.application.dto.ImportacaoEventoSstResponse;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Importação em lote no mesmo fluxo validar → conferir → confirmar dos demais cadastros. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarEventosSstUseCase {

    private static final String SEPARADOR = ";";
    private static final int LIMITE_LINHAS = 2000;

    private final MontadorEventoSstImportacao montador;
    private final ImportarEventosSstAsyncService asyncService;

    public ImportacaoEventoSstResponse executarValidacao(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Selecione um arquivo CSV preenchido");
        }

        List<EventoSstImportacaoDTO> registros = new ArrayList<>();
        int sucessos = 0;
        int falhas = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(arquivo.getInputStream(), StandardCharsets.UTF_8))) {
            String linha;
            int numeroLinha = 0;
            while ((linha = reader.readLine()) != null) {
                numeroLinha++;
                if (numeroLinha == 1 || linha.isBlank() || linha.trim().startsWith("#")) {
                    continue;
                }
                if (registros.size() >= LIMITE_LINHAS) {
                    log.warn("Arquivo de eventos excede o limite de {} registros", LIMITE_LINHAS);
                    break;
                }

                EventoSstImportacaoDTO dto = validarRegistro(processarLinhaCsv(linha, numeroLinha - 1));
                if (dto.status() == ImportacaoStatus.COM_ERRO) {
                    falhas++;
                } else {
                    sucessos++;
                }
                registros.add(dto);
            }
        } catch (Exception e) {
            log.error("Falha crítica ao ler arquivo de importação de eventos", e);
            throw new IllegalArgumentException("Falha ao ler o arquivo CSV: " + e.getMessage(), e);
        }

        return new ImportacaoEventoSstResponse(registros, registros.size(), sucessos, falhas);
    }

    public ImportacaoEventoSstResponse confirmarImportacao(ConfirmarImportacaoEventoSstRequest request) {
        List<EventoSstImportacaoDTO> revalidados = request.eventos().stream()
                .map(this::validarRegistro)
                .toList();
        long falhas = revalidados.stream().filter(r -> r.status() == ImportacaoStatus.COM_ERRO).count();

        List<EventoSstImportacaoDTO> emProcessamento = revalidados.stream()
                .filter(r -> r.status() != ImportacaoStatus.COM_ERRO)
                .map(r -> r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();
        if (!emProcessamento.isEmpty()) {
            asyncService.processarPersistencia(emProcessamento);
        }

        List<EventoSstImportacaoDTO> resposta = revalidados.stream()
                .map(r -> r.status() == ImportacaoStatus.COM_ERRO
                        ? r
                        : r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();
        return new ImportacaoEventoSstResponse(
                resposta, resposta.size(), emProcessamento.size(), Math.toIntExact(falhas));
    }

    private EventoSstImportacaoDTO validarRegistro(EventoSstImportacaoDTO dto) {
        try {
            montador.montarEValidar(dto);
            return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
        } catch (RuntimeException e) {
            String mensagem = e.getMessage() == null || e.getMessage().isBlank()
                    ? "Não foi possível validar este registro"
                    : e.getMessage();
            return dto.withStatus(ImportacaoStatus.COM_ERRO, mensagem);
        }
    }

    private EventoSstImportacaoDTO processarLinhaCsv(String linha, int indice) {
        String[] c = linha.split(SEPARADOR, -1);
        return new EventoSstImportacaoDTO(
                indice,
                coluna(c, 0), coluna(c, 1), coluna(c, 2), coluna(c, 3), coluna(c, 4),
                coluna(c, 5), coluna(c, 6), coluna(c, 7), coluna(c, 8), coluna(c, 9),
                coluna(c, 10), coluna(c, 11), coluna(c, 12), coluna(c, 13), coluna(c, 14),
                coluna(c, 15), coluna(c, 16), coluna(c, 17), coluna(c, 18), coluna(c, 19),
                coluna(c, 20), coluna(c, 21), coluna(c, 22), coluna(c, 23), coluna(c, 24),
                coluna(c, 25),
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
    }

    private String coluna(String[] colunas, int indice) {
        return colunas.length > indice ? colunas[indice].trim() : "";
    }

    public byte[] obterModeloCsv() {
        String cabecalho = "Tipo;Data da Ocorrência (dd/MM/yyyy HH:mm);Data da Comunicação (dd/MM/yyyy HH:mm);"
                + "Atividade;Gestor;Base Operacional;Equipe;Veículo;Classificação;Processo;Telemetria;Filmagem;"
                + "Local da Ocorrência;Latitude;Longitude;Descrição;Local da Lesão;Natureza da Lesão;"
                + "Agente Causador;Dias Perdidos;Possui CAT;Tipo CAT;Número CAT;Data de Emissão CAT (dd/MM/yyyy);"
                + "Técnicos;Envolvidos\n";
        String instrucoes = "# Use ponto e vírgula entre as colunas. Não use ponto e vírgula dentro dos textos.\n"
                + "# Nomes de cadastro devem ser exatos. Também é aceito o UUID do registro.\n"
                + "# Separe técnicos por |. Exemplo: Maria Souza|João Lima\n"
                + "# Envolvido colaborador: COLABORADOR~ACIDENTADO~Nome Completo\n"
                + "# Envolvido terceiro: TERCEIRO~TESTEMUNHA~Nome~Função~Empresa. Separe vários por |.\n"
                + "# Exemplo: Acidente típico;01/08/2026 08:00;01/08/2026 09:00;Abertura de Cava;"
                + "Nome do Gestor;Base Sul;EQ-01;ABC1D23;FAC;;Não aplicável;Não aplicável;Rua Exemplo;;;"
                + "Descrição do acidente;Mão;Corte, laceração ou ferida aberta;Ferramenta manual - não especificada;"
                + "0;Não;;;;Nome do Técnico;COLABORADOR~ACIDENTADO~Nome do Colaborador\n";
        return ("\uFEFF" + cabecalho + instrucoes).getBytes(StandardCharsets.UTF_8);
    }
}
