package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.application.dto.ColaboradorImportacaoDTO;
import com.nexo.app.modules.rh.application.dto.ConfirmarImportacaoRequest;
import com.nexo.app.modules.rh.infrastructure.persistence.BaseOperacionalRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.ColaboradorRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.FuncaoRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.ProcessoNegocioRepository;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportarColaboradoresUseCaseTest {

    @Mock
    private ColaboradorRepository repository;
    @Mock
    private ProcessoNegocioRepository processoRepository;
    @Mock
    private BaseOperacionalRepository baseRepository;
    @Mock
    private FuncaoRepository funcaoRepository;
    @Mock
    private ImportarColaboradoresAsyncService asyncService;

    @InjectMocks
    private ImportarColaboradoresUseCase useCase;

    @Test
    void confirmarImportacaoDevePersistirValidosEManterErrosNoRetorno() {
        when(repository.existsByCpfHash(anyString())).thenReturn(false);
        when(processoRepository.existsByNomeIgnoreCase("PROCESSO")).thenReturn(true);
        when(baseRepository.existsByNomeIgnoreCase("BASE")).thenReturn(true);
        when(funcaoRepository.existsByNomeIgnoreCase("FUNCAO")).thenReturn(true);

        ColaboradorImportacaoDTO valido = registro(1, "JOAO", "52998224725");
        ColaboradorImportacaoDTO invalido = registro(2, "A", "11144477735");

        var response = useCase.confirmarImportacao(
                new ConfirmarImportacaoRequest(List.of(valido, invalido)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ColaboradorImportacaoDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(asyncService).processarPersistencia(captor.capture());

        assertThat(captor.getValue())
                .hasSize(1)
                .allMatch(r -> r.status() == ImportacaoStatus.EM_PROCESSAMENTO);
        assertThat(response.totalProcessados()).isEqualTo(2);
        assertThat(response.totalSucesso()).isEqualTo(1);
        assertThat(response.totalFalha()).isEqualTo(1);
        assertThat(response.registros())
                .extracting(ColaboradorImportacaoDTO::status)
                .containsExactly(ImportacaoStatus.EM_PROCESSAMENTO, ImportacaoStatus.COM_ERRO);
    }

    private ColaboradorImportacaoDTO registro(int linha, String primeiroNome, String cpf) {
        return new ColaboradorImportacaoDTO(
                linha,
                primeiroNome,
                "SILVA",
                primeiroNome + " SILVA",
                cpf,
                "PROCESSO",
                "BASE",
                "FUNCAO",
                "15/01/2026",
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO,
                null
        );
    }
}
