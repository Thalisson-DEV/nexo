package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.usecases.LeitorPlanilhaImportacao.Linha;
import com.nexo.app.modules.obras.domain.ClasseTensao;
import com.nexo.app.modules.obras.domain.ImportacaoObra;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.PrioridadeObra;
import com.nexo.app.modules.obras.domain.StatusObra;
import com.nexo.app.modules.obras.infrastructure.persistence.ImportacaoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.StatusObraRepository;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.CentroFaturamentoApi;
import com.nexo.app.modules.rh.CoordenadorApi;
import com.nexo.app.modules.rh.MunicipioApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.SupervisorApi;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.config.CacheNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Processa a planilha da carteira em segundo plano, uma obra por linha, identificada pelo número.
 *
 * <p>Obra existente: atualiza só os dados de carteira, o status e as coordenadas presentes na linha (célula vazia
 * mantém o valor atual); planejamento, programação e medições não são tocados. Obra nova: exige os mesmos dados do
 * cadastro (título, processo de negócio, base, município, centro de custo, supervisor, coordenador e datas).
 * Cada linha é gravada na própria transação.</p>
 */
@Slf4j
@Service
public class ProcessarImportacaoCarteiraService {

    static final String[] COL_NUMERO = {"NUMERO", "NUMERO_OBRA", "OBRA", "NUMERO_PROCESSO", "PROCESSO"};
    static final String[] COL_TITULO = {"TITULO", "NOME", "DESCRICAO_OBRA"};
    static final String[] COL_TENSAO = {"TENSAO", "CLASSE_TENSAO"};
    static final String[] COL_PRAZO = {"PRAZO", "DATA_PREVISAO_FIM", "PREVISAO_FIM"};
    static final String[] COL_PRAZO_REGULADO = {"PRAZO_REGULADO"};
    static final String[] COL_PRIORIDADE = {"PRIORIDADE"};
    static final String[] COL_LOTE = {"LOTE_PREVISTO", "LOTE"};
    static final String[] COL_VALOR_PROJETO = {"VALOR_PROJETO", "VALOR"};
    static final String[] COL_VALOR_MATERIAL = {"VALOR_MATERIAL_PREVISTO", "VALOR_MATERIAL", "MATERIAL_PREVISTO"};
    static final String[] COL_VALOR_SERVICO = {"VALOR_SERVICO_PREVISTO", "VALOR_SERVICO", "SERVICO_PREVISTO"};
    static final String[] COL_LATITUDE = {"LATITUDE", "LAT"};
    static final String[] COL_LONGITUDE = {"LONGITUDE", "LNG", "LON"};
    static final String[] COL_PROCESSO_NEGOCIO = {"PROCESSO_NEGOCIO"};
    static final String[] COL_BASE = {"BASE_OPERACIONAL", "BASE"};
    static final String[] COL_MUNICIPIO = {"MUNICIPIO"};
    static final String[] COL_CENTRO_CUSTO = {"CENTRO_CUSTO", "CENTRO_DE_CUSTO"};
    static final String[] COL_SUPERVISOR = {"SUPERVISOR"};
    static final String[] COL_COORDENADOR = {"COORDENADOR"};
    static final String[] COL_STATUS = {"STATUS", "STATUS_OBRA", "SITUACAO_CARTEIRA", "SITUACAO"};
    static final String[] COL_DATA_INICIO = {"DATA_INICIO", "INICIO"};
    static final String[] COL_TIPO_OBRA = {"TIPO_OBRA"};

    private final ImportacaoObraRepository importacaoRepository;
    private final ObraRepository obraRepository;
    private final StatusObraRepository statusRepository;
    private final ProcessoNegocioApi processoNegocioApi;
    private final BaseOperacionalApi baseOperacionalApi;
    private final MunicipioApi municipioApi;
    private final CentroFaturamentoApi centroFaturamentoApi;
    private final SupervisorApi supervisorApi;
    private final CoordenadorApi coordenadorApi;
    private final NormalizadorObraService normalizador;
    private final TransactionTemplate transacao;

    public ProcessarImportacaoCarteiraService(ImportacaoObraRepository importacaoRepository,
                                              ObraRepository obraRepository,
                                              StatusObraRepository statusRepository,
                                              ProcessoNegocioApi processoNegocioApi,
                                              BaseOperacionalApi baseOperacionalApi,
                                              MunicipioApi municipioApi,
                                              CentroFaturamentoApi centroFaturamentoApi,
                                              SupervisorApi supervisorApi,
                                              CoordenadorApi coordenadorApi,
                                              NormalizadorObraService normalizador,
                                              PlatformTransactionManager transacoes) {
        this.importacaoRepository = importacaoRepository;
        this.obraRepository = obraRepository;
        this.statusRepository = statusRepository;
        this.processoNegocioApi = processoNegocioApi;
        this.baseOperacionalApi = baseOperacionalApi;
        this.municipioApi = municipioApi;
        this.centroFaturamentoApi = centroFaturamentoApi;
        this.supervisorApi = supervisorApi;
        this.coordenadorApi = coordenadorApi;
        this.normalizador = normalizador;
        this.transacao = new TransactionTemplate(transacoes);
    }

    @Async
    @CacheEvict(cacheNames = CacheNames.OBRAS, allEntries = true)
    public void processar(UUID idImportacao, List<Linha> linhas, String usuario) {
        ImportacaoObra importacao = importacaoRepository.findById(idImportacao).orElse(null);
        if (importacao == null) {
            log.warn("Importação de carteira {} não encontrada para processamento", idImportacao);
            return;
        }
        try {
            Resolvedor resolvedor = new Resolvedor();
            for (Linha linha : linhas) {
                processarLinha(importacao, linha, resolvedor, usuario);
            }
            importacao.concluir(linhas.size());
        } catch (InvarianteVioladaException e) {
            importacao.falhar(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Falha inesperada na importação de carteira {}", idImportacao, e);
            importacao.falhar("Falha inesperada ao processar a planilha. Tente novamente ou contate o suporte.");
        }
        importacaoRepository.save(importacao);
    }

    private void processarLinha(ImportacaoObra importacao, Linha linha, Resolvedor resolvedor, String usuario) {
        String numero = normalizador.normalizarNumero(linha.valor(COL_NUMERO));
        if (numero == null) {
            importacao.registrarErro(linha.numero(), null, "Número da obra não informado.");
            return;
        }
        try {
            Boolean criada = transacao.execute(status -> aplicar(importacao.getIdContrato(), numero, linha, resolvedor, usuario));
            if (Boolean.TRUE.equals(criada)) {
                importacao.registrarInserido();
            } else {
                importacao.registrarAtualizado();
            }
        } catch (InvarianteVioladaException | IllegalArgumentException e) {
            importacao.registrarErro(linha.numero(), numero, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Falha ao gravar a obra {} na importação {}", numero, importacao.getId(), e);
            importacao.registrarErro(linha.numero(), numero, "Falha ao gravar a obra.");
        }
    }

    /** @return true quando a obra foi criada */
    private boolean aplicar(UUID idContrato, String numero, Linha linha, Resolvedor resolvedor, String usuario) {
        Optional<Obra> existente = obraRepository.findByNumeroIgnoreCaseAndAtivoTrue(numero);
        Obra obra = existente.orElseGet(() -> criarObra(numero, linha, resolvedor));

        BigDecimal valorProjeto = normalizador.parseValor(linha.valor(COL_VALOR_PROJETO));
        BigDecimal material = normalizador.parseValor(linha.valor(COL_VALOR_MATERIAL));
        BigDecimal servico = normalizador.parseValor(linha.valor(COL_VALOR_SERVICO));
        LocalDate prazoRegulado = normalizador.parseData(linha.valor(COL_PRAZO_REGULADO));
        String prioridadeTexto = linha.valor(COL_PRIORIDADE);
        PrioridadeObra prioridade = prioridadeTexto == null ? null : PrioridadeObra.interpretar(prioridadeTexto)
                .orElseThrow(() -> new InvarianteVioladaException("Prioridade inválida: " + prioridadeTexto));

        obra.informarCarteira(
                idContrato != null ? idContrato : obra.getIdContrato(),
                prazoRegulado != null ? prazoRegulado : obra.getPrazoRegulado(),
                prioridade != null ? prioridade : obra.getPrioridade(),
                manter(linha.valor(COL_LOTE), obra.getLotePrevisto()),
                valorProjeto,
                material != null ? material : obra.getValorMaterialPrevisto(),
                servico != null ? servico : obra.getValorServicoPrevisto());

        String tensao = linha.valor(COL_TENSAO);
        if (tensao != null) {
            obra.informarClasseTensao(ClasseTensao.interpretar(tensao)
                    .orElseThrow(() -> new InvarianteVioladaException("Classe de tensão inválida: " + tensao)), usuario);
        }
        Double latitude = normalizador.parseCoordenada(linha.valor(COL_LATITUDE));
        Double longitude = normalizador.parseCoordenada(linha.valor(COL_LONGITUDE));
        if (latitude != null && longitude != null) {
            obra.atualizarLocalizacao(latitude, longitude);
        }
        String status = linha.valor(COL_STATUS);
        if (status != null && existente.isPresent()) {
            StatusObra novoStatus = resolverStatus(status);
            if (!novoStatus.equals(obra.getStatusObra())) {
                obra.alterarStatus(novoStatus, obra.getDataFimEfetiva());
            }
        }
        obraRepository.save(obra);
        return existente.isEmpty();
    }

    private Obra criarObra(String numero, Linha linha, Resolvedor resolvedor) {
        String titulo = normalizador.normalizarTitulo(linha.valor(COL_TITULO));
        LocalDate inicio = normalizador.parseData(linha.valor(COL_DATA_INICIO));
        LocalDate prazo = normalizador.parseData(linha.valor(COL_PRAZO));
        UUID processo = resolvedor.obrigatorio("processo de negócio", linha.valor(COL_PROCESSO_NEGOCIO), processoNegocioApi::buscarIdPorNome);
        UUID base = resolvedor.obrigatorio("base operacional", linha.valor(COL_BASE), baseOperacionalApi::buscarIdPorNome);
        UUID municipio = resolvedor.obrigatorio("município", linha.valor(COL_MUNICIPIO), municipioApi::buscarIdPorNome);
        UUID centro = resolvedor.obrigatorio("centro de custo", linha.valor(COL_CENTRO_CUSTO), centroFaturamentoApi::buscarIdPorNome);
        UUID supervisor = resolvedor.obrigatorio("supervisor", linha.valor(COL_SUPERVISOR), supervisorApi::buscarIdPorNomeColaborador);
        UUID coordenador = resolvedor.obrigatorio("coordenador", linha.valor(COL_COORDENADOR), coordenadorApi::buscarIdPorNomeColaborador);
        if (titulo == null) {
            throw new InvarianteVioladaException("Obra nova sem título.");
        }
        if (inicio == null || prazo == null) {
            throw new InvarianteVioladaException("Obra nova sem data de início ou prazo.");
        }
        return new Obra(numero, titulo, null, null, null, inicio, prazo, null, null, processo, base, municipio,
                centro, supervisor, coordenador, resolverStatus(linha.valor(COL_STATUS)), null, Set.of(),
                normalizador.parseTipoObra(linha.valor(COL_TIPO_OBRA)));
    }

    private StatusObra resolverStatus(String nome) {
        if (nome != null) {
            return statusRepository.findByNomeIgnoreCase(nome.trim())
                    .orElseThrow(() -> new InvarianteVioladaException("Status de obra não encontrado: " + nome));
        }
        return statusRepository.findByNomeIgnoreCase("PLANEJADA")
                .orElseGet(() -> statusRepository.findAll().stream().filter(StatusObra::isAtivo).findFirst()
                        .orElseThrow(() -> new InvarianteVioladaException("Cadastre ao menos um status de obra.")));
    }

    private static String manter(String novo, String atual) {
        return novo != null ? novo : atual;
    }

    /** Cache das buscas por nome durante uma importação. */
    private static final class Resolvedor {
        private final Map<String, Optional<UUID>> cache = new HashMap<>();

        UUID obrigatorio(String campo, String nome, Function<String, Optional<UUID>> busca) {
            if (nome == null) {
                throw new InvarianteVioladaException("Obra nova sem " + campo + ".");
            }
            return cache.computeIfAbsent(campo + "|" + nome.trim().toUpperCase(), k -> busca.apply(nome.trim()))
                    .orElseThrow(() -> new InvarianteVioladaException(
                            capitalizar(campo) + " não encontrado: " + nome));
        }

        private static String capitalizar(String texto) {
            return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
        }
    }
}
