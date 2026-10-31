package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.contrato.AcaoResponse;
import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.obras.application.dto.CarteiraImportacaoLinha;
import com.nexo.app.modules.obras.application.dto.ConfirmarImportacaoPlanilhaRequest;
import com.nexo.app.modules.obras.application.dto.OrcamentoImportacaoLinha;
import com.nexo.app.modules.obras.application.dto.PreviaImportacaoObraResponse;
import com.nexo.app.modules.obras.application.usecases.LeitorPlanilhaImportacao.Linha;
import com.nexo.app.modules.obras.domain.ClasseTensao;
import com.nexo.app.modules.obras.domain.ImportacaoObra;
import com.nexo.app.modules.obras.domain.PrioridadeObra;
import com.nexo.app.modules.obras.infrastructure.persistence.ImportacaoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.StatusObraRepository;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.CentroFaturamentoApi;
import com.nexo.app.modules.rh.CoordenadorApi;
import com.nexo.app.modules.rh.MunicipioApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.SupervisorApi;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import com.nexo.app.shared.application.exportacao.PlanilhaXlsx;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static com.nexo.app.modules.obras.application.usecases.ImportarKitsUseCase.tentar;
import static com.nexo.app.modules.obras.application.usecases.ImportarKitsUseCase.texto;
import static com.nexo.app.modules.obras.application.usecases.ProcessarImportacaoCarteiraService.*;

/**
 * Importações da carteira de obras e dos serviços/orçamento previstos, em duas etapas como as demais importações:
 * validação com a situação de cada linha para conferência e correção, e confirmação que revalida e processa as
 * linhas válidas em segundo plano, registrando a importação no histórico.
 */
@Service
@RequiredArgsConstructor
public class ImportarCarteiraUseCase {

    static final String[] CABECALHO_CARTEIRA = {
            "NUMERO", "TITULO", "TENSAO", "PRAZO", "PRAZO_REGULADO", "PRIORIDADE", "LOTE_PREVISTO",
            "SITUACAO_CARTEIRA", "VALOR_PROJETO", "VALOR_MATERIAL_PREVISTO", "VALOR_SERVICO_PREVISTO",
            "LATITUDE", "LONGITUDE", "PROCESSO_NEGOCIO", "BASE_OPERACIONAL", "MUNICIPIO", "CENTRO_CUSTO",
            "SUPERVISOR", "COORDENADOR", "STATUS", "DATA_INICIO", "TIPO_OBRA"
    };

    static final String[] CABECALHO_ORCAMENTO = {
            "NUMERO", "CODIGO_SERVICO", "DESCRICAO", "UNIDADE", "QUANTIDADE", "VALOR_UNITARIO", "VALOR_TOTAL",
            "VALOR_MATERIAL_PREVISTO", "VALOR_SERVICO_PREVISTO"
    };

    private static final String[] COL_ORC_CODIGO = {"CODIGO_SERVICO", "CODIGO", "SERVICO", "COD_SERVICO"};
    private static final String[] COL_ORC_DESCRICAO = {"DESCRICAO", "DESCRICAO_SERVICO"};
    private static final String[] COL_ORC_UNIDADE = {"UNIDADE", "UN", "UND"};
    private static final String[] COL_ORC_QUANTIDADE = {"QUANTIDADE", "QTD", "QTDE"};
    private static final String[] COL_ORC_VALOR_UNITARIO = {"VALOR_UNITARIO", "PRECO", "PRECO_UNITARIO"};
    private static final String[] COL_ORC_VALOR_TOTAL = {"VALOR_TOTAL", "TOTAL"};

    private final ImportacaoObraRepository importacaoRepository;
    private final ObraRepository obraRepository;
    private final StatusObraRepository statusRepository;
    private final ContratoApi contratoApi;
    private final ProcessoNegocioApi processoNegocioApi;
    private final BaseOperacionalApi baseOperacionalApi;
    private final MunicipioApi municipioApi;
    private final CentroFaturamentoApi centroFaturamentoApi;
    private final SupervisorApi supervisorApi;
    private final CoordenadorApi coordenadorApi;
    private final NormalizadorObraService normalizador;
    private final LeitorPlanilhaImportacao leitor;
    private final ProcessarImportacaoCarteiraService processadorCarteira;
    private final ProcessarImportacaoOrcamentoService processadorOrcamento;
    private final AuditorAware<String> auditor;

    // ═══════════════════════════════════════════════════════════════════════
    // Carteira
    // ═══════════════════════════════════════════════════════════════════════

    public PreviaImportacaoObraResponse<CarteiraImportacaoLinha> validarCarteira(UUID idContrato, MultipartFile arquivo) {
        exigirContrato(idContrato);
        List<CarteiraImportacaoLinha> registros = ler(arquivo).stream().map(ImportarCarteiraUseCase::carteiraDe).toList();
        return resumo(validarCarteira(registros), null);
    }

    /** Revalida as linhas da pré-visualização depois de uma correção, sem gravar. */
    public PreviaImportacaoObraResponse<CarteiraImportacaoLinha> revalidarCarteira(
            ConfirmarImportacaoPlanilhaRequest<CarteiraImportacaoLinha> request) {
        exigirContrato(request.idContrato());
        return resumo(validarCarteira(request.registros()), null);
    }

    public PreviaImportacaoObraResponse<CarteiraImportacaoLinha> confirmarCarteira(
            ConfirmarImportacaoPlanilhaRequest<CarteiraImportacaoLinha> request) {
        exigirContrato(request.idContrato());
        List<CarteiraImportacaoLinha> revalidadas = validarCarteira(request.registros());
        List<CarteiraImportacaoLinha> validas = revalidadas.stream().filter(r -> r.status() != ImportacaoStatus.COM_ERRO).toList();
        if (validas.isEmpty()) {
            return resumo(revalidadas, null);
        }
        ImportacaoObra importacao = importacaoRepository.save(
                new ImportacaoObra(ImportacaoObra.Tipo.CARTEIRA, request.idContrato(), request.nomeArquivo()));
        // O processamento roda fora da requisição: o autor da classe de tensão é capturado aqui.
        processadorCarteira.processar(importacao.getId(), validas.stream().map(ImportarCarteiraUseCase::paraLinha).toList(),
                auditor.getCurrentAuditor().orElse("SYSTEM"));
        return resumo(revalidadas.stream()
                .map(r -> r.status() == ImportacaoStatus.COM_ERRO ? r : r.withStatus(r.obraNova(), ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList(), importacao.getId());
    }

    List<CarteiraImportacaoLinha> validarCarteira(List<CarteiraImportacaoLinha> registros) {
        Map<String, Optional<UUID>> cache = new HashMap<>();
        Set<String> numerosNoLote = new HashSet<>();
        List<CarteiraImportacaoLinha> validadas = new ArrayList<>();
        for (CarteiraImportacaoLinha r : registros) {
            validadas.add(validarObra(r, cache, numerosNoLote));
        }
        return validadas;
    }

    private CarteiraImportacaoLinha validarObra(CarteiraImportacaoLinha r, Map<String, Optional<UUID>> cache,
                                               Set<String> numerosNoLote) {
        List<String> erros = new ArrayList<>();
        String numero = normalizador.normalizarNumero(r.numero());
        if (numero == null) {
            return r.withStatus(null, ImportacaoStatus.COM_ERRO, "Número da obra não informado.");
        }
        if (!numerosNoLote.add(numero.toUpperCase(Locale.ROOT))) {
            erros.add("Obra repetida na planilha.");
        }
        tentar(erros, () -> {
            if (texto(r.tensao()) != null && ClasseTensao.interpretar(r.tensao()).isEmpty()) {
                throw new InvarianteVioladaException("Classe de tensão inválida: " + r.tensao() + ".");
            }
        });
        tentar(erros, () -> {
            if (texto(r.prioridade()) != null && PrioridadeObra.interpretar(r.prioridade()).isEmpty()) {
                throw new InvarianteVioladaException("Prioridade inválida: " + r.prioridade() + ".");
            }
        });
        tentar(erros, () -> normalizador.parseData(r.prazoRegulado()));
        tentar(erros, () -> naoNegativo(normalizador.parseValor(r.valorProjeto()), "valor de projeto"));
        tentar(erros, () -> naoNegativo(normalizador.parseValor(r.valorMaterialPrevisto()), "valor de material previsto"));
        tentar(erros, () -> naoNegativo(normalizador.parseValor(r.valorServicoPrevisto()), "valor de serviço previsto"));
        if ((texto(r.latitude()) == null) != (texto(r.longitude()) == null)) {
            erros.add("Informe latitude e longitude juntas.");
        }

        boolean nova = obraRepository.findByNumeroIgnoreCaseAndAtivoTrue(numero).isEmpty();
        if (nova) {
            if (normalizador.normalizarTitulo(r.titulo()) == null) {
                erros.add("Obra nova sem título.");
            }
            tentar(erros, () -> {
                if (normalizador.parseData(r.dataInicio()) == null || normalizador.parseData(r.prazo()) == null) {
                    throw new InvarianteVioladaException("Obra nova sem data de início ou prazo.");
                }
            });
            exigirCadastro(erros, cache, "processo de negócio", r.processoNegocio(), processoNegocioApi::buscarIdPorNome);
            exigirCadastro(erros, cache, "base operacional", r.baseOperacional(), baseOperacionalApi::buscarIdPorNome);
            exigirCadastro(erros, cache, "município", r.municipio(), municipioApi::buscarIdPorNome);
            exigirCadastro(erros, cache, "centro de custo", r.centroCusto(), centroFaturamentoApi::buscarIdPorNome);
            exigirCadastro(erros, cache, "supervisor", r.supervisor(), supervisorApi::buscarIdPorNomeColaborador);
            exigirCadastro(erros, cache, "coordenador", r.coordenador(), coordenadorApi::buscarIdPorNomeColaborador);
            tentar(erros, () -> normalizador.parseTipoObra(r.tipoObra()));
            if (texto(r.statusObra()) != null && statusRepository.findByNomeIgnoreCase(r.statusObra().trim()).isEmpty()) {
                erros.add("Status de obra não encontrado: " + r.statusObra() + ".");
            }
        }
        return erros.isEmpty()
                ? r.withStatus(nova, ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null)
                : r.withStatus(nova, ImportacaoStatus.COM_ERRO, String.join(" ", erros));
    }

    private static void exigirCadastro(List<String> erros, Map<String, Optional<UUID>> cache, String campo, String nome,
                                       Function<String, Optional<UUID>> busca) {
        if (texto(nome) == null) {
            erros.add("Obra nova sem " + campo + ".");
            return;
        }
        boolean existe = cache.computeIfAbsent(campo + "|" + nome.trim().toUpperCase(Locale.ROOT), k -> busca.apply(nome.trim()))
                .isPresent();
        if (!existe) {
            erros.add(Character.toUpperCase(campo.charAt(0)) + campo.substring(1) + " não encontrado: " + nome.trim() + ".");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Serviços e orçamento previstos
    // ═══════════════════════════════════════════════════════════════════════

    public PreviaImportacaoObraResponse<OrcamentoImportacaoLinha> validarOrcamento(MultipartFile arquivo) {
        List<OrcamentoImportacaoLinha> registros = ler(arquivo).stream().map(ImportarCarteiraUseCase::orcamentoDe).toList();
        return resumo(validarOrcamento(registros), null);
    }

    /** Revalida as linhas da pré-visualização depois de uma correção, sem gravar. */
    public PreviaImportacaoObraResponse<OrcamentoImportacaoLinha> revalidarOrcamento(
            ConfirmarImportacaoPlanilhaRequest<OrcamentoImportacaoLinha> request) {
        return resumo(validarOrcamento(request.registros()), null);
    }

    public PreviaImportacaoObraResponse<OrcamentoImportacaoLinha> confirmarOrcamento(
            ConfirmarImportacaoPlanilhaRequest<OrcamentoImportacaoLinha> request) {
        List<OrcamentoImportacaoLinha> revalidadas = validarOrcamento(request.registros());
        List<OrcamentoImportacaoLinha> validas = revalidadas.stream().filter(r -> r.status() != ImportacaoStatus.COM_ERRO).toList();
        if (validas.isEmpty()) {
            return resumo(revalidadas, null);
        }
        ImportacaoObra importacao = importacaoRepository.save(
                new ImportacaoObra(ImportacaoObra.Tipo.ORCAMENTO, null, request.nomeArquivo()));
        processadorOrcamento.processar(importacao.getId(), validas.stream().map(ImportarCarteiraUseCase::paraLinha).toList());
        return resumo(revalidadas.stream()
                .map(r -> r.status() == ImportacaoStatus.COM_ERRO ? r : r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, r.mensagemErro()))
                .toList(), importacao.getId());
    }

    List<OrcamentoImportacaoLinha> validarOrcamento(List<OrcamentoImportacaoLinha> registros) {
        Map<String, Boolean> obras = new HashMap<>();
        Map<String, Boolean> acoes = new HashMap<>();
        List<OrcamentoImportacaoLinha> validadas = new ArrayList<>();
        for (OrcamentoImportacaoLinha r : registros) {
            List<String> erros = new ArrayList<>();
            String numero = normalizador.normalizarNumero(r.numeroObra());
            if (numero == null) {
                erros.add("Número da obra não informado.");
            } else if (!obras.computeIfAbsent(numero.toUpperCase(Locale.ROOT),
                    n -> obraRepository.findByNumeroIgnoreCaseAndAtivoTrue(numero).isPresent())) {
                erros.add("Obra " + numero + " não encontrada.");
            }
            String codigo = texto(r.codigoServico());
            if (codigo == null) {
                erros.add("Código do serviço não informado.");
            }
            tentar(erros, () -> {
                BigDecimal quantidade = LeitorPlanilhaImportacao.interpretarDecimal(r.quantidade());
                if (quantidade == null || quantidade.signum() < 0) {
                    throw new InvarianteVioladaException("Informe a quantidade (zero ou maior).");
                }
            });
            tentar(erros, () -> {
                BigDecimal unitario = LeitorPlanilhaImportacao.interpretarDecimal(r.valorUnitario());
                BigDecimal total = LeitorPlanilhaImportacao.interpretarDecimal(r.valorTotal());
                if (unitario == null && total == null) {
                    throw new InvarianteVioladaException("Informe o valor unitário ou o valor total.");
                }
                naoNegativo(unitario, "valor unitário");
                naoNegativo(total, "valor total");
            });
            tentar(erros, () -> naoNegativo(normalizador.parseValor(r.valorMaterialPrevisto()), "valor de material previsto"));
            tentar(erros, () -> naoNegativo(normalizador.parseValor(r.valorServicoPrevisto()), "valor de serviço previsto"));
            if (!erros.isEmpty()) {
                validadas.add(r.withStatus(ImportacaoStatus.COM_ERRO, String.join(" ", erros)));
                continue;
            }
            boolean temAcao = acoes.computeIfAbsent(codigo.toUpperCase(Locale.ROOT),
                    c -> contratoApi.buscarAcaoAtivaPorCodigo(c).map(AcaoResponse::id).isPresent());
            validadas.add(r.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO,
                    temAcao ? null : "Aviso: código sem Ação no contrato; entra no orçamento sem vínculo."));
        }
        return validadas;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Modelos
    // ═══════════════════════════════════════════════════════════════════════

    public byte[] gerarModeloCarteira() {
        return gerarModelo("Carteira", CABECALHO_CARTEIRA, new Object[]{
                "1234567", "Extensão de rede BT", "15 kV", "30/06/2027", "15/06/2027", "Alta", "LOTE-01",
                "Liberada", "150000,00", "60000,00", "90000,00", "-9,4111", "-40,5030", "Expansão", "Base Exemplo",
                "Município Exemplo", "Centro de Custo Exemplo", "Nome do Supervisor", "Nome do Coordenador",
                "PLANEJADA", "01/11/2026", "OBRA"});
    }

    public byte[] gerarModeloOrcamento() {
        return gerarModelo("Orçamento", CABECALHO_ORCAMENTO, new Object[]{
                "1234567", "COD-ACAO", "Instalar estrutura BT", "UN", 2, "350,00", "", "60000,00", "90000,00"});
    }

    private static byte[] gerarModelo(String aba, String[] cabecalho, Object[] exemplo) {
        try (PlanilhaXlsx planilha = new PlanilhaXlsx(aba, cabecalho);
             ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            planilha.linha(exemplo);
            planilha.escrever(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar o modelo de importação.", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Conversões planilha ↔ linhas da pré-visualização
    // ═══════════════════════════════════════════════════════════════════════

    static CarteiraImportacaoLinha carteiraDe(Linha l) {
        return new CarteiraImportacaoLinha(l.numero(), l.valor(COL_NUMERO), l.valor(COL_TITULO), l.valor(COL_TENSAO),
                l.valor(COL_PRAZO), l.valor(COL_PRAZO_REGULADO), l.valor(COL_PRIORIDADE), l.valor(COL_LOTE),
                l.valor(COL_SITUACAO), l.valor(COL_VALOR_PROJETO), l.valor(COL_VALOR_MATERIAL), l.valor(COL_VALOR_SERVICO),
                l.valor(COL_LATITUDE), l.valor(COL_LONGITUDE), l.valor(COL_PROCESSO_NEGOCIO), l.valor(COL_BASE),
                l.valor(COL_MUNICIPIO), l.valor(COL_CENTRO_CUSTO), l.valor(COL_SUPERVISOR), l.valor(COL_COORDENADOR),
                l.valor(COL_STATUS), l.valor(COL_DATA_INICIO), l.valor(COL_TIPO_OBRA), null, null, null);
    }

    static Linha paraLinha(CarteiraImportacaoLinha r) {
        Map<String, String> v = new HashMap<>();
        v.put(COL_NUMERO[0], r.numero());
        v.put(COL_TITULO[0], r.titulo());
        v.put(COL_TENSAO[0], r.tensao());
        v.put(COL_PRAZO[0], r.prazo());
        v.put(COL_PRAZO_REGULADO[0], r.prazoRegulado());
        v.put(COL_PRIORIDADE[0], r.prioridade());
        v.put(COL_LOTE[0], r.lotePrevisto());
        v.put(COL_SITUACAO[0], r.situacaoCarteira());
        v.put(COL_VALOR_PROJETO[0], r.valorProjeto());
        v.put(COL_VALOR_MATERIAL[0], r.valorMaterialPrevisto());
        v.put(COL_VALOR_SERVICO[0], r.valorServicoPrevisto());
        v.put(COL_LATITUDE[0], r.latitude());
        v.put(COL_LONGITUDE[0], r.longitude());
        v.put(COL_PROCESSO_NEGOCIO[0], r.processoNegocio());
        v.put(COL_BASE[0], r.baseOperacional());
        v.put(COL_MUNICIPIO[0], r.municipio());
        v.put(COL_CENTRO_CUSTO[0], r.centroCusto());
        v.put(COL_SUPERVISOR[0], r.supervisor());
        v.put(COL_COORDENADOR[0], r.coordenador());
        v.put(COL_STATUS[0], r.statusObra());
        v.put(COL_DATA_INICIO[0], r.dataInicio());
        v.put(COL_TIPO_OBRA[0], r.tipoObra());
        return new Linha(r.linha() != null ? r.linha() : 0, v);
    }

    static OrcamentoImportacaoLinha orcamentoDe(Linha l) {
        return new OrcamentoImportacaoLinha(l.numero(), l.valor(COL_NUMERO), l.valor(COL_ORC_CODIGO),
                l.valor(COL_ORC_DESCRICAO), l.valor(COL_ORC_UNIDADE), l.valor(COL_ORC_QUANTIDADE),
                l.valor(COL_ORC_VALOR_UNITARIO), l.valor(COL_ORC_VALOR_TOTAL), l.valor(COL_VALOR_MATERIAL),
                l.valor(COL_VALOR_SERVICO), null, null);
    }

    static Linha paraLinha(OrcamentoImportacaoLinha r) {
        Map<String, String> v = new HashMap<>();
        v.put(COL_NUMERO[0], r.numeroObra());
        v.put(COL_ORC_CODIGO[0], r.codigoServico());
        v.put(COL_ORC_DESCRICAO[0], r.descricao());
        v.put(COL_ORC_UNIDADE[0], r.unidade());
        v.put(COL_ORC_QUANTIDADE[0], r.quantidade());
        v.put(COL_ORC_VALOR_UNITARIO[0], r.valorUnitario());
        v.put(COL_ORC_VALOR_TOTAL[0], r.valorTotal());
        v.put(COL_VALOR_MATERIAL[0], r.valorMaterialPrevisto());
        v.put(COL_VALOR_SERVICO[0], r.valorServicoPrevisto());
        return new Linha(r.linha() != null ? r.linha() : 0, v);
    }

    private List<Linha> ler(MultipartFile arquivo) {
        return leitor.ler(ArquivoImportacao.bytes(arquivo), arquivo.getOriginalFilename());
    }

    private void exigirContrato(UUID idContrato) {
        if (idContrato == null || !contratoApi.existeContrato(idContrato)) {
            throw new InvarianteVioladaException("Selecione um contrato ativo para importar a carteira.");
        }
    }

    private static void naoNegativo(BigDecimal valor, String campo) {
        if (valor != null && valor.signum() < 0) {
            throw new InvarianteVioladaException("O " + campo + " não pode ser negativo.");
        }
    }

    private static <T> PreviaImportacaoObraResponse<T> resumo(List<T> registros, UUID idImportacao) {
        int falhas = (int) registros.stream().filter(ArquivoImportacao::comErro).count();
        return new PreviaImportacaoObraResponse<>(registros, registros.size(), registros.size() - falhas, falhas, idImportacao);
    }
}
