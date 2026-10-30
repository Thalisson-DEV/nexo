package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.CatalogoSstRequest;
import com.nexo.app.modules.sst.application.dto.CatalogoSstResponse;
import com.nexo.app.modules.sst.application.mappers.CatalogoSstMapper;
import com.nexo.app.modules.sst.domain.CatalogoSst;
import com.nexo.app.modules.sst.domain.TipoCatalogoSst;
import com.nexo.app.modules.sst.infrastructure.persistence.CatalogoSstRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogoSstUseCasesTest {

    private static final UUID ID = UUID.randomUUID();

    @Mock
    private CatalogoSstRepository repository;
    @Mock
    private CatalogoSstMapper mapper;

    @InjectMocks
    private CatalogoSstUseCases useCases;

    @BeforeEach
    void configurarPadroes() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenAnswer(inv -> respostaDe(inv.getArgument(0)));
    }

    private CatalogoSstResponse respostaDe(CatalogoSst entrada) {
        return new CatalogoSstResponse(entrada.getId(), entrada.getTipo(), entrada.getNome(),
                entrada.getDescricao(), entrada.getCodigo(), entrada.getOrdem(), entrada.isAtivo());
    }

    private CatalogoSstRequest request(TipoCatalogoSst tipo, String nome) {
        return new CatalogoSstRequest(tipo, nome, null, null, 10);
    }

    private CatalogoSst existente(TipoCatalogoSst tipo, String nome) {
        return new CatalogoSst(tipo, nome, null, null, 0);
    }

    @Test
    @DisplayName("criar deve persistir a entrada no vocabulário informado")
    void criarDevePersistir() {
        CatalogoSstResponse response = useCases.criar(
                request(TipoCatalogoSst.CLASSIFICACAO_EVENTO, "Queda de mesmo nível"));

        ArgumentCaptor<CatalogoSst> captor = ArgumentCaptor.forClass(CatalogoSst.class);
        verify(repository).save(captor.capture());

        assertEquals(TipoCatalogoSst.CLASSIFICACAO_EVENTO, captor.getValue().getTipo());
        assertEquals("queda de mesmo nivel", captor.getValue().getNomeNormalizado());
        assertEquals("Queda de mesmo nível", response.nome());
    }

    @Test
    @DisplayName("criar deve recusar nome já usado por outra entrada ativa do mesmo tipo")
    void criarDeveRecusarNomeDuplicado() {
        when(repository.existsByTipoAndNomeNormalizadoAndAtivoTrue(
                TipoCatalogoSst.CLASSIFICACAO_EVENTO, "choque eletrico")).thenReturn(true);

        // O acento e a caixa não podem escapar da checagem: é a mesma entrada para quem preenche.
        assertThrows(InvarianteVioladaException.class,
                () -> useCases.criar(request(TipoCatalogoSst.CLASSIFICACAO_EVENTO, "CHOQUE ELÉTRICO")));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("criar deve aceitar o mesmo nome em vocabulários diferentes")
    void criarDeveAceitarMesmoNomeEmTiposDiferentes() {
        when(repository.existsByTipoAndNomeNormalizadoAndAtivoTrue(
                TipoCatalogoSst.LOCAL_LESAO, "mao direita")).thenReturn(true);

        assertDoesNotThrow(() -> useCases.criar(request(TipoCatalogoSst.AGENTE_CAUSADOR, "Mão direita")));
    }

    @Test
    @DisplayName("atualizar deve recusar troca de tipo — mudaria o significado dos eventos existentes")
    void atualizarDeveRecusarTrocaDeTipo() {
        when(repository.findById(ID)).thenReturn(
                Optional.of(existente(TipoCatalogoSst.LOCAL_LESAO, "Joelho esquerdo")));

        assertThrows(InvarianteVioladaException.class,
                () -> useCases.atualizar(ID, request(TipoCatalogoSst.CLASSIFICACAO_EVENTO, "Joelho esquerdo")));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("atualizar deve ignorar a própria entrada na checagem de unicidade")
    void atualizarDeveIgnorarSiMesma() {
        when(repository.findById(ID)).thenReturn(
                Optional.of(existente(TipoCatalogoSst.LOCAL_LESAO, "Mão direita")));

        useCases.atualizar(ID, new CatalogoSstRequest(
                TipoCatalogoSst.LOCAL_LESAO, "Mão direita", "Palma e dedos", null, 3));

        verify(repository).existsByTipoAndNomeNormalizadoAndAtivoTrueAndIdNot(
                TipoCatalogoSst.LOCAL_LESAO, "mao direita", ID);
        verify(repository).save(any());
    }

    @Test
    @DisplayName("ativar deve recusar quando o nome foi tomado durante a desativação")
    void ativarDeveRecusarNomeTomado() {
        CatalogoSst desativada = existente(TipoCatalogoSst.NATUREZA_LESAO, "Corte");
        desativada.desativar();
        when(repository.findById(ID)).thenReturn(Optional.of(desativada));
        when(repository.existsByTipoAndNomeNormalizadoAndAtivoTrueAndIdNot(
                eq(TipoCatalogoSst.NATUREZA_LESAO), eq("corte"), any())).thenReturn(true);

        assertThrows(InvarianteVioladaException.class, () -> useCases.ativar(ID));

        assertFalse(desativada.isAtivo());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("desativar deve aplicar soft delete, sem apagar a linha")
    void desativarDeveAplicarSoftDelete() {
        CatalogoSst entrada = existente(TipoCatalogoSst.AGENTE_CAUSADOR, "Produto químico");
        when(repository.findById(ID)).thenReturn(Optional.of(entrada));

        useCases.desativar(ID);

        assertFalse(entrada.isAtivo());
        assertNotNull(entrada.getDataDesativacao());
        verify(repository).save(entrada);
        verify(repository, never()).deleteById(any());
    }

    @Test
    @DisplayName("listarOpcoes deve devolver apenas as entradas ativas do vocabulário")
    void listarOpcoesDeveFiltrarPorTipo() {
        when(repository.findByTipoAndAtivoTrueOrderByOrdemAscNomeAsc(TipoCatalogoSst.LOCAL_LESAO))
                .thenReturn(List.of(existente(TipoCatalogoSst.LOCAL_LESAO, "Mão direita")));

        List<CatalogoSstResponse> opcoes = useCases.listarOpcoes(TipoCatalogoSst.LOCAL_LESAO);

        assertEquals(1, opcoes.size());
        assertEquals("Mão direita", opcoes.getFirst().nome());
    }

    @Test
    @DisplayName("buscarPorId deve falhar quando a entrada não existe")
    void buscarPorIdDeveFalharQuandoNaoExiste() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> useCases.buscarPorId(ID));
    }
}
