package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.application.dto.EquipeMembroRequest;
import com.nexo.app.modules.rh.EquipeMembroResponse;
import com.nexo.app.modules.rh.application.mappers.EquipeMapper;
import com.nexo.app.modules.rh.domain.Colaborador;
import com.nexo.app.modules.rh.domain.Equipe;
import com.nexo.app.modules.rh.domain.EquipeMembro;
import com.nexo.app.modules.rh.infrastructure.persistence.ColaboradorRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.EquipeMembroRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.EquipeRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "membros-equipes")
public class MembroEquipeUseCase {

    private final EquipeRepository equipeRepository;
    private final ColaboradorRepository colaboradorRepository;
    private final EquipeMembroRepository equipeMembroRepository;
    private final EquipeMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public void encerrarVinculo(UUID equipeId, UUID colaboradorId) {
        Equipe equipe = buscarEquipe(equipeId);
        Colaborador colaborador = buscarColaborador(colaboradorId);

        // Validação: Não permitir remover o último membro ativo da equipe
        long membrosAtivos = equipe.getMembros().stream()
                .filter(EquipeMembro::isVinculoAtivo)
                .count();
        
        if (membrosAtivos <= 1) {
            throw new InvarianteVioladaException("Não é possível remover o último colaborador da equipe. A equipe deve possuir pelo menos um membro ativo.");
        }

        equipe.encerrarVinculoMembro(colaborador);
        equipeRepository.save(equipe);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void alocarMembro(UUID equipeId, EquipeMembroRequest request) {
        Equipe equipe = buscarEquipe(equipeId);
        Colaborador colaborador = buscarColaborador(request.idColaborador());

        processarAlocacaoUnica(equipe, colaborador, request.funcao());
        equipeRepository.save(equipe);
    }

    /**
     * Processa múltiplos membros de uma vez. Ignora quem já está na equipe com a mesma função.
     * Aloca e remove automaticamente vínculos de outras equipes.
     */
    @Transactional
    @CacheEvict(allEntries = true)
    public void alocarMembros(UUID equipeId, List<EquipeMembroRequest> requests) {
        Equipe equipe = buscarEquipe(equipeId);

        for (EquipeMembroRequest request : requests) {
            Colaborador colaborador = buscarColaborador(request.idColaborador());
            processarAlocacaoUnica(equipe, colaborador, request.funcao());
        }

        equipeRepository.save(equipe);
    }

    /**
     * Lógica centralizada para alocação com tratamento de duplicidade e transferência entre equipes.
     */
    void processarAlocacaoUnica(Equipe equipeDestino, Colaborador colaborador, EquipeMembro.FuncaoMembro novaFuncao) {
        // 1. Verifica se já está na equipe atual
        boolean jaEhMembroComMesmaFuncao = equipeDestino.getMembros().stream()
                .anyMatch(m -> m.isVinculoAtivo() && 
                          m.getColaborador().getId().equals(colaborador.getId()) && 
                          m.getFuncao() == novaFuncao);

        if (jaEhMembroComMesmaFuncao) {
            return; // Se já está alocado e com a mesma função, não faz nada (Evita erro de duplicidade)
        }

        // 2. Busca vínculo ativo em QUALQUER equipe
        equipeMembroRepository.findVincuoAtivoPorColaborador(colaborador.getId())
                .ifPresent(vinculoAntigo -> {
                    // Se estiver em outra equipe (ou na mesma com função diferente)
                    if (vinculoAntigo.getFuncao() == EquipeMembro.FuncaoMembro.RESPONSAVEL) {
                        String msg = String.format("O colaborador %s é RESPONSÁVEL na equipe %s e não pode ser transferido automaticamente. Encerre o vínculo anterior manualmente.", 
                                colaborador.getNomeCompleto(), vinculoAntigo.getEquipe().getCodigo());
                        throw new InvarianteVioladaException(msg);
                    }
                    
                    // Se for parceiro, encerra o vínculo antigo automaticamente
                    vinculoAntigo.encerrarVinculo();
                    equipeMembroRepository.save(vinculoAntigo);
                });

        // 3. Aloca na nova equipe
        equipeDestino.alocarMembro(colaborador, novaFuncao);
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#equipeId")
    public List<EquipeMembroResponse> listarMembrosAtivos(UUID equipeId) {
        Equipe equipe = buscarEquipe(equipeId);

        return equipe.getMembros().stream()
                .filter(EquipeMembro::isVinculoAtivo)
                .map(mapper::toMembroResponse)
                .collect(Collectors.toList());
    }

    private Equipe buscarEquipe(UUID id) {
        return equipeRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Equipe não encontrada"));
    }

    private Colaborador buscarColaborador(UUID id) {
        return colaboradorRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Colaborador não encontrado"));
    }
}
