package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.TipoEstoqueRepository;
import com.nexo.app.modules.obras.ObraApi.ObraPublica;
import com.nexo.app.modules.obras.ObraApi.TipoObraPublico;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstoqueObraProvisioningServiceTest {

    @Mock
    private EstoqueRepository estoqueRepository;
    @Mock
    private TipoEstoqueRepository tipoEstoqueRepository;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void encerrarExecutor() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void reutilizaEstoqueExistenteSemTentarProvisionar() {
        ObraPublica obra = obraPublica();
        Estoque existente = estoqueObra(obra.id());
        when(estoqueRepository.findByIdObra(obra.id())).thenReturn(Optional.of(existente));

        Estoque resultado = service().resolverOuProvisionar(obra);

        assertThat(resultado).isSameAs(existente);
        verifyNoInteractions(tipoEstoqueRepository);
    }

    @Test
    void duasPrimeirasResolucoesConcorrentesRetornamOMesmoEstoque() throws Exception {
        ObraPublica obra = obraPublica();
        TipoEstoque tipoObra = comId(new TipoEstoque("OBRA"));
        Estoque estoqueCriado = estoqueObra(obra.id());
        CyclicBarrier primeirasLeituras = new CyclicBarrier(2);
        AtomicInteger leituras = new AtomicInteger();

        when(estoqueRepository.findByIdObra(obra.id())).thenAnswer(invocation -> {
            if (leituras.incrementAndGet() <= 2) {
                primeirasLeituras.await(5, TimeUnit.SECONDS);
                return Optional.empty();
            }
            return Optional.of(estoqueCriado);
        });
        when(tipoEstoqueRepository.findByNomeIgnoreCase("OBRA")).thenReturn(Optional.of(tipoObra));
        when(estoqueRepository.inserirEstoqueObraSeAusente(
                any(), any(), eq(tipoObra.getId()), eq(obra.idBaseOperacional()), eq(obra.id()), eq("SISTEMA")))
                .thenReturn(1, 0);

        EstoqueObraProvisioningService service = service();
        var primeira = executor.submit(() -> service.resolverOuProvisionar(obra));
        var segunda = executor.submit(() -> service.resolverOuProvisionar(obra));

        assertThat(primeira.get(10, TimeUnit.SECONDS)).isSameAs(estoqueCriado);
        assertThat(segunda.get(10, TimeUnit.SECONDS)).isSameAs(estoqueCriado);
        verify(estoqueRepository, org.mockito.Mockito.times(2)).inserirEstoqueObraSeAusente(
                any(), any(), eq(tipoObra.getId()), eq(obra.idBaseOperacional()), eq(obra.id()), eq("SISTEMA"));
    }

    private EstoqueObraProvisioningService service() {
        return new EstoqueObraProvisioningService(estoqueRepository, tipoEstoqueRepository);
    }

    private static ObraPublica obraPublica() {
        return new ObraPublica(
                UUID.randomUUID(),
                "001234",
                "AMPLIACAO DA REDE",
                TipoObraPublico.OBRA,
                true,
                UUID.randomUUID(),
                "OBRAS",
                true,
                UUID.randomUUID(),
                Set.of(),
                null,
                List.of()
        );
    }

    private static Estoque estoqueObra(UUID idObra) {
        TipoEstoque tipo = comId(new TipoEstoque("OBRA"));
        Estoque estoque = new Estoque("OBRA 001234", tipo);
        estoque.vincularBaseOperacional(UUID.randomUUID());
        estoque.vincularObra(idObra);
        return comId(estoque);
    }

    private static <T> T comId(T entidade) {
        ReflectionTestUtils.setField(entidade, "id", UUID.randomUUID());
        return entidade;
    }
}
