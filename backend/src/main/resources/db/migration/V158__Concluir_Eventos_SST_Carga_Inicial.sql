-- =====================================================================
-- V158: Concluir todos os eventos SST da carga inicial.
--
-- Os registros em produção no momento desta migration são exclusivamente
-- provenientes de uma carga histórica de acidentes que já foram tratados
-- anteriormente. Não faz sentido cobrar uma nova tratativa deles.
--
-- A migration marca todos os eventos ainda abertos (REGISTRADO ou
-- EM_TRATATIVA) como CONCLUIDO, preenche data_conclusao com a data
-- atual e registra na tratativa que a conclusão foi administrativa,
-- referente à carga inicial.
--
-- Eventos já CONCLUIDO ou CANCELADO não são tocados.
-- =====================================================================

UPDATE eventos_sst
SET
    status                  = 'CONCLUIDO',
    data_conclusao          = CURRENT_TIMESTAMP,
    tratativa               = COALESCE(
                                  NULLIF(TRIM(tratativa), ''),
                                  'Concluído administrativamente — evento pertencente à carga inicial de acidentes históricos já tratados antes da implantação do sistema.'
                              ),
    data_ultima_alteracao   = CURRENT_TIMESTAMP,
    usuario_ultima_alteracao = 'migration:V158'
WHERE status IN ('REGISTRADO', 'EM_TRATATIVA');
