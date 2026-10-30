package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.ConfirmarImportacaoEventoSstRequest;
import com.nexo.app.modules.sst.application.dto.EventoSstImportacaoDTO;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportarEventosSstUseCaseTest {

    @Mock
    private MontadorEventoSstImportacao montador;
    @Mock
    private ImportarEventosSstAsyncService asyncService;
    @InjectMocks
    private ImportarEventosSstUseCase useCase;

    @Test
    void deveValidarCadaLinhaIgnorandoCabecalhoEComentarios() {
        when(montador.montarEValidar(any())).thenAnswer(invocation -> {
            EventoSstImportacaoDTO dto = invocation.getArgument(0);
            if ("ERRO".equals(dto.descricao())) {
                throw new IllegalArgumentException("Descrição inválida");
            }
            return null;
        });

        String csv = "Tipo;Data;Comunicação;Atividade;Gestor;Base;Equipe;Veículo;Classificação;Processo;"
                + "Telemetria;Filmagem;Local;Latitude;Longitude;Descrição;Local lesão;Natureza;Agente;Dias;"
                + "CAT;Tipo CAT;Número;Emissão;Técnicos;Envolvidos\n"
                + "# comentário do modelo\n"
                + linha("Acidente típico", "OK") + "\n"
                + linha("Dano material", "ERRO") + "\n";
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "eventos.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        var response = useCase.executarValidacao(arquivo);

        assertThat(response.totalProcessados()).isEqualTo(2);
        assertThat(response.totalSucesso()).isEqualTo(1);
        assertThat(response.totalFalha()).isEqualTo(1);
        assertThat(response.registros())
                .extracting(EventoSstImportacaoDTO::linha)
                .containsExactly(2, 3);
        assertThat(response.registros())
                .extracting(EventoSstImportacaoDTO::status)
                .containsExactly(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, ImportacaoStatus.COM_ERRO);
        assertThat(response.registros().get(1).mensagemErro()).isEqualTo("Descrição inválida");
    }

    @Test
    void confirmarDeveRevalidarEPersistirSomenteLinhasValidas() {
        when(montador.montarEValidar(any())).thenAnswer(invocation -> {
            EventoSstImportacaoDTO dto = invocation.getArgument(0);
            if ("ERRO".equals(dto.descricao())) {
                throw new IllegalArgumentException("Linha inválida");
            }
            return null;
        });
        EventoSstImportacaoDTO valido = dto(1, "OK");
        EventoSstImportacaoDTO invalido = dto(2, "ERRO");

        var response = useCase.confirmarImportacao(
                new ConfirmarImportacaoEventoSstRequest(List.of(valido, invalido)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EventoSstImportacaoDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(asyncService).processarPersistencia(captor.capture());
        assertThat(captor.getValue())
                .singleElement()
                .extracting(EventoSstImportacaoDTO::status)
                .isEqualTo(ImportacaoStatus.EM_PROCESSAMENTO);
        assertThat(response.totalProcessados()).isEqualTo(2);
        assertThat(response.totalSucesso()).isEqualTo(1);
        assertThat(response.totalFalha()).isEqualTo(1);
        assertThat(response.registros())
                .extracting(EventoSstImportacaoDTO::status)
                .containsExactly(ImportacaoStatus.EM_PROCESSAMENTO, ImportacaoStatus.COM_ERRO);
    }

    @Test
    void modeloDeveTerBomCabecalhoEInstrucoesDosCamposCompostos() {
        String modelo = new String(useCase.obterModeloCsv(), StandardCharsets.UTF_8);

        assertThat(modelo).startsWith("\uFEFFTipo;Data da Ocorrência");
        assertThat(modelo).contains("Técnicos;Envolvidos");
        assertThat(modelo).contains("COLABORADOR~ACIDENTADO~Nome Completo");
        assertThat(modelo).contains("TERCEIRO~TESTEMUNHA~Nome~Função~Empresa");
        String[] linhas = modelo.split("\\R");
        assertThat(linhas[0].split(";", -1)).hasSize(26);
        assertThat(java.util.Arrays.stream(linhas)
                .filter(linha -> linha.startsWith("# Exemplo: "))
                .findFirst()
                .orElseThrow()
                .substring("# Exemplo: ".length())
                .split(";", -1)).hasSize(26);
    }

    private String linha(String tipo, String descricao) {
        return String.join(";", tipo, "01/08/2026 08:00", "01/08/2026 09:00",
                "Abertura de Cava", "", "Base Sul", "", "", "FAC", "",
                "Não aplicável", "Não aplicável", "Rua", "", "", descricao,
                "Mão", "Fratura", "Chão", "0", "Não", "", "", "",
                "Nome do Técnico", "COLABORADOR~ACIDENTADO~Nome do Colaborador");
    }

    private EventoSstImportacaoDTO dto(int linha, String descricao) {
        String[] colunas = linha("Acidente típico", descricao).split(";", -1);
        return new EventoSstImportacaoDTO(
                linha, colunas[0], colunas[1], colunas[2], colunas[3], colunas[4],
                colunas[5], colunas[6], colunas[7], colunas[8], colunas[9], colunas[10],
                colunas[11], colunas[12], colunas[13], colunas[14], colunas[15], colunas[16],
                colunas[17], colunas[18], colunas[19], colunas[20], colunas[21], colunas[22],
                colunas[23], colunas[24], colunas[25], ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
    }
}
