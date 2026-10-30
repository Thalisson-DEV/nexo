package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorAtivoResumo;
import com.nexo.app.modules.rh.ContextoOrganizacional;
import com.nexo.app.modules.sst.application.dto.MatrizTreinamentoSstFilter;
import com.nexo.app.modules.sst.application.dto.MatrizTreinamentoSstResponse;
import com.nexo.app.modules.sst.application.dto.ResumoMatrizTreinamentoSstResponse;
import com.nexo.app.modules.sst.domain.DocumentoSst;
import com.nexo.app.modules.sst.domain.ExigenciaTreinamentoSst;
import com.nexo.app.modules.sst.domain.MembroCorpoSst;
import com.nexo.app.modules.sst.domain.PapelMembroGovernancaSst;
import com.nexo.app.modules.sst.domain.SituacaoConferenciaDocumentoSst;
import com.nexo.app.modules.sst.domain.StatusMatrizTreinamentoSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import com.nexo.app.modules.sst.domain.TreinamentoGovernancaSst;
import com.nexo.app.modules.sst.infrastructure.persistence.DocumentoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.ExigenciaTreinamentoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.MembroCorpoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.TreinamentoGovernancaSstRepository;
import com.nexo.app.shared.application.SelecaoMultipla;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatrizTreinamentoSstUseCases {

    private final ExigenciaTreinamentoSstRepository exigenciaRepository;
    private final TreinamentoGovernancaSstRepository treinamentoRepository;
    private final DocumentoSstRepository documentoRepository;
    private final MembroCorpoSstRepository membroRepository;
    private final ColaboradorApi colaboradorApi;
    private final AvaliadorMatrizTreinamentoSst avaliador;

    @Transactional(readOnly = true)
    public PageResponse<MatrizTreinamentoSstResponse> listar(
            MatrizTreinamentoSstFilter filtro, Pageable pageable) {
        List<MatrizTreinamentoSstResponse> linhas = calcular(filtro);
        int inicio = Math.toIntExact(Math.min(pageable.getOffset(), linhas.size()));
        int fim = Math.min(inicio + pageable.getPageSize(), linhas.size());
        return PageResponse.fromPage(new PageImpl<>(linhas.subList(inicio, fim), pageable, linhas.size()));
    }

    /** Resumo em lote consumível pelo dashboard, com os mesmos filtros e a mesma evidência da grade. */
    @Transactional(readOnly = true)
    public ResumoMatrizTreinamentoSstResponse resumo(MatrizTreinamentoSstFilter filtro) {
        LocalDate data = dataCalculo(filtro);
        List<MatrizTreinamentoSstResponse> linhas = calcular(filtro);
        return new ResumoMatrizTreinamentoSstResponse(linhas.size(),
                contar(linhas, StatusMatrizTreinamentoSst.REGULAR),
                contar(linhas, StatusMatrizTreinamentoSst.A_VENCER),
                contar(linhas, StatusMatrizTreinamentoSst.VENCIDO),
                contar(linhas, StatusMatrizTreinamentoSst.AUSENTE),
                contar(linhas, StatusMatrizTreinamentoSst.INCOMPATIVEL), data, filtro);
    }

    List<MatrizTreinamentoSstResponse> calcular(MatrizTreinamentoSstFilter filtro) {
        LocalDate data = dataCalculo(filtro);
        List<ExigenciaTreinamentoSst> exigencias = exigenciaRepository
                .findAllByAtualTrueAndAtivoTrue().stream()
                .filter(item -> item.vigenteEm(data))
                .filter(item -> aceita(filtro == null ? null : filtro.tipoCorpo(), item.getTipoCorpo()))
                .filter(item -> aceita(filtro == null ? null : filtro.idTreinamento(), item.getIdTreinamento()))
                .filter(item -> filtro == null || SelecaoMultipla.vazia(filtro.idBase())
                        || item.getIdBase() == null || filtro.idBase().contains(item.getIdBase()))
                .toList();
        if (exigencias.isEmpty()) return List.of();

        Map<UUID, TreinamentoGovernancaSst> treinamentos = treinamentoRepository
                .findAllByIdIn(exigencias.stream().map(ExigenciaTreinamentoSst::getIdTreinamento)
                        .distinct().toList()).stream()
                .collect(Collectors.toMap(TreinamentoGovernancaSst::getId, Function.identity()));
        if (treinamentos.size() < exigencias.stream().map(ExigenciaTreinamentoSst::getIdTreinamento)
                .distinct().count()) {
            throw new InvarianteVioladaException(
                    "A matriz possui exigência vinculada a treinamento indisponível.");
        }

        List<PessoaMatriz> pessoasFuncao = pessoasPorFuncao(exigencias, filtro);
        List<PessoaMatriz> pessoasPapel = pessoasPorPapel(exigencias, filtro, data);
        List<Celula> celulas = montarCelulas(exigencias, pessoasFuncao, pessoasPapel, filtro);
        if (celulas.isEmpty()) return List.of();

        Set<UUID> idsPessoas = celulas.stream().map(c -> c.pessoa().idColaborador())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> idsTreinamentos = celulas.stream().map(c -> c.exigencia().getIdTreinamento())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<ChaveCertificado, List<DocumentoSst>> certificados = documentoRepository
                .buscarCertificadosElegiveis(idsPessoas, idsTreinamentos,
                        SituacaoConferenciaDocumentoSst.APPROVED).stream()
                .collect(Collectors.groupingBy(item -> new ChaveCertificado(
                        item.getIdColaborador(), item.getIdTreinamento())));

        List<MatrizTreinamentoSstResponse> resultado = new ArrayList<>(celulas.size());
        for (Celula celula : celulas) {
            PessoaMatriz pessoa = celula.pessoa();
            ExigenciaTreinamentoSst exigencia = celula.exigencia();
            TreinamentoGovernancaSst treinamento = treinamentos.get(exigencia.getIdTreinamento());
            AvaliadorMatrizTreinamentoSst.Resultado avaliacao = avaliador.avaliar(exigencia,
                    pessoa.idFuncao(), pessoa.papel(), certificados.getOrDefault(
                            new ChaveCertificado(pessoa.idColaborador(), exigencia.getIdTreinamento()),
                            List.of()), data);
            MatrizTreinamentoSstResponse linha = new MatrizTreinamentoSstResponse(
                    pessoa.idColaborador(), pessoa.nome(), pessoa.idFuncao(), pessoa.nomeFuncao(),
                    pessoa.idBase(), pessoa.nomeBase(), pessoa.idCorpo(), exigencia.getTipoCorpo(),
                    pessoa.papel(), treinamento.getId(), treinamento.getCodigo(), treinamento.getNome(),
                    exigencia.getId(), avaliacao.status(), avaliacao.dataEfetivaVencimento(),
                    avaliacao.evidencia());
            if (aceitaResultado(filtro, linha)) resultado.add(linha);
        }
        resultado.sort(Comparator
                .comparing(MatrizTreinamentoSstResponse::nomeColaborador,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(MatrizTreinamentoSstResponse::treinamentoCodigo)
                .thenComparing(MatrizTreinamentoSstResponse::idExigencia)
                .thenComparing(MatrizTreinamentoSstResponse::idCorpo,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return List.copyOf(resultado);
    }

    private List<PessoaMatriz> pessoasPorFuncao(List<ExigenciaTreinamentoSst> exigencias,
                                                MatrizTreinamentoSstFilter filtro) {
        List<ExigenciaTreinamentoSst> porFuncao = exigencias.stream()
                .filter(item -> item.getModoAplicabilidade().exigeFuncao()).toList();
        if (porFuncao.isEmpty()) return List.of();
        Set<UUID> funcoes = porFuncao.stream().map(ExigenciaTreinamentoSst::getIdFuncao)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (filtro != null && !SelecaoMultipla.vazia(filtro.idFuncao())) {
            funcoes.retainAll(filtro.idFuncao());
        }
        if (funcoes.isEmpty()) return List.of();

        Set<UUID> bases = basesParaConsulta(porFuncao, filtro);
        List<ColaboradorAtivoResumo> pessoas = colaboradorApi.listarAtivosPorEscopo(
                bases.isEmpty() ? null : bases, funcoes);
        return pessoas.stream().map(item -> new PessoaMatriz(item.id(), item.nomeCompleto(),
                item.idFuncao(), item.funcao(), item.idBaseOperacional(), item.baseOperacional(),
                null, null, null)).toList();
    }

    private List<PessoaMatriz> pessoasPorPapel(List<ExigenciaTreinamentoSst> exigencias,
                                               MatrizTreinamentoSstFilter filtro,
                                               LocalDate data) {
        List<ExigenciaTreinamentoSst> porPapel = exigencias.stream()
                .filter(item -> item.getModoAplicabilidade().exigePapel()).toList();
        if (porPapel.isEmpty()) return List.of();
        Set<TipoCorpoGovernancaSst> tipos = porPapel.stream().map(ExigenciaTreinamentoSst::getTipoCorpo)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<PapelMembroGovernancaSst> papeis = porPapel.stream().map(ExigenciaTreinamentoSst::getPapelCorpo)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (filtro != null && !SelecaoMultipla.vazia(filtro.papelCorpo())) {
            papeis.retainAll(filtro.papelCorpo());
        }
        if (tipos.isEmpty() || papeis.isEmpty()) return List.of();
        List<MembroCorpoSst> membros = membroRepository.buscarAtivosPorTipoEPapelEm(tipos, papeis, data);
        Set<UUID> ids = membros.stream().map(MembroCorpoSst::getIdColaborador)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, ContextoOrganizacional> contextos = ids.isEmpty() ? Map.of()
                : colaboradorApi.buscarContextoOrganizacional(ids);
        List<PessoaMatriz> pessoas = new ArrayList<>();
        for (MembroCorpoSst membro : membros) {
            ContextoOrganizacional contexto = contextos.get(membro.getIdColaborador());
            if (contexto == null || !contexto.ativo()) {
                throw new InvarianteVioladaException(
                        "Há integrante ativo na governança sem colaborador ativo correspondente no RH.");
            }
            pessoas.add(new PessoaMatriz(membro.getIdColaborador(), contexto.nomeCompleto(),
                    contexto.idFuncao(), contexto.funcao(), membro.getCorpo().getIdBaseOperacional(),
                    membro.getCorpo().getBaseNome(), membro.getCorpo().getId(),
                    membro.getCorpo().getTipo(), membro.getPapel()));
        }
        return pessoas;
    }

    private List<Celula> montarCelulas(List<ExigenciaTreinamentoSst> exigencias,
                                       List<PessoaMatriz> pessoasFuncao,
                                       List<PessoaMatriz> pessoasPapel,
                                       MatrizTreinamentoSstFilter filtro) {
        Map<String, Celula> unicas = new LinkedHashMap<>();
        for (ExigenciaTreinamentoSst exigencia : exigencias) {
            List<PessoaMatriz> candidatas = exigencia.getModoAplicabilidade().exigePapel()
                    ? pessoasPapel : pessoasFuncao;
            for (PessoaMatriz pessoa : candidatas) {
                if (pessoa.tipoCorpo() != null && pessoa.tipoCorpo() != exigencia.getTipoCorpo()) continue;
                if (!exigencia.aplicaA(pessoa.idFuncao(), pessoa.papel())) continue;
                if (exigencia.getIdBase() != null && !exigencia.getIdBase().equals(pessoa.idBase())) continue;
                if (!aceitaPessoa(filtro, pessoa)) continue;
                String chave = exigencia.getId() + ":" + pessoa.idColaborador() + ":"
                        + Objects.toString(pessoa.idCorpo(), "FUNCAO");
                unicas.putIfAbsent(chave, new Celula(exigencia, pessoa));
            }
        }
        return List.copyOf(unicas.values());
    }

    private static boolean aceitaPessoa(MatrizTreinamentoSstFilter filtro, PessoaMatriz pessoa) {
        if (filtro == null) return true;
        if (!aceita(filtro.idBase(), pessoa.idBase())) return false;
        if (!aceita(filtro.idColaborador(), pessoa.idColaborador())) return false;
        if (!aceita(filtro.idFuncao(), pessoa.idFuncao())) return false;
        if (!SelecaoMultipla.vazia(filtro.idCorpo())
                && (pessoa.idCorpo() == null || !filtro.idCorpo().contains(pessoa.idCorpo()))) return false;
        return aceita(filtro.papelCorpo(), pessoa.papel());
    }

    private static boolean aceitaResultado(MatrizTreinamentoSstFilter filtro,
                                            MatrizTreinamentoSstResponse linha) {
        if (filtro == null) return true;
        if (!aceita(filtro.status(), linha.status())) return false;
        if (filtro.vencimentoDe() != null && (linha.dataEfetivaVencimento() == null
                || linha.dataEfetivaVencimento().isBefore(filtro.vencimentoDe()))) return false;
        return filtro.vencimentoAte() == null || (linha.dataEfetivaVencimento() != null
                && !linha.dataEfetivaVencimento().isAfter(filtro.vencimentoAte()));
    }

    private static Set<UUID> basesParaConsulta(List<ExigenciaTreinamentoSst> exigencias,
                                               MatrizTreinamentoSstFilter filtro) {
        if (filtro != null && !SelecaoMultipla.vazia(filtro.idBase())) {
            return new LinkedHashSet<>(filtro.idBase());
        }
        boolean algumaGlobal = exigencias.stream().anyMatch(item -> item.getIdBase() == null);
        if (algumaGlobal) return Set.of();
        return exigencias.stream().map(ExigenciaTreinamentoSst::getIdBase)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static LocalDate dataCalculo(MatrizTreinamentoSstFilter filtro) {
        return filtro == null || filtro.dataCalculo() == null ? LocalDate.now() : filtro.dataCalculo();
    }

    private static long contar(List<MatrizTreinamentoSstResponse> linhas,
                               StatusMatrizTreinamentoSst status) {
        return linhas.stream().filter(item -> item.status() == status).count();
    }

    private static <T> boolean aceita(Collection<T> filtro, T valor) {
        return filtro == null || filtro.isEmpty() || filtro.contains(valor);
    }

    private record PessoaMatriz(UUID idColaborador, String nome, UUID idFuncao,
                                String nomeFuncao, UUID idBase, String nomeBase,
                                UUID idCorpo, TipoCorpoGovernancaSst tipoCorpo,
                                PapelMembroGovernancaSst papel) {
    }

    private record Celula(ExigenciaTreinamentoSst exigencia, PessoaMatriz pessoa) {
    }

    private record ChaveCertificado(UUID idColaborador, UUID idTreinamento) {
    }
}
