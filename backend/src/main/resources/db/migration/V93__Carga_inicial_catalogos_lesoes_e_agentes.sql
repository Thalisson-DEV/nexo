-- =====================================================================
-- V93: Carga inicial dos catalogos de lesao e agentes causadores
--
-- Preenche os tres vocabularios que ficaram vazios na criacao do modulo
-- SST. Os codigos seguem as tabelas oficiais do eSocial S-1.3:
--
--   * Tabela 13 - Parte do corpo atingida;
--   * Tabela 14 - Agente causador do acidente de trabalho;
--   * Tabela 17 - Descricao da natureza da lesao.
--
-- Os nomes sao curtos para funcionarem bem nos seletores da aplicacao.
-- A coluna codigo preserva o vinculo com o eSocial, enquanto descricao
-- explica o escopo quando o rotulo sozinho poderia ser ambiguo.
--
-- A carga e idempotente dentro do modelo do catalogo: uma entrada ativa
-- existente com o mesmo tipo e nome normalizado e preservada.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Locais da lesao - eSocial, Tabela 13
-- ---------------------------------------------------------------------

INSERT INTO catalogos_sst (id, tipo, nome, nome_normalizado, descricao, codigo, ordem,
                           versao, data_criacao, usuario_criacao, ativo)
SELECT gen_random_uuid(), 'LOCAL_LESAO', v.nome,
       immutable_unaccent(lower(v.nome)), v.descricao, v.codigo, v.ordem,
       0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE
FROM (VALUES
    ('Crânio', 'Inclusive encéfalo.', '753030000', 10),
    ('Ouvido', 'Ouvido externo, médio ou interno; inclui audição e equilíbrio.', '753050000', 20),
    ('Olho', 'Inclusive nervo óptico e visão.', '753070100', 30),
    ('Nariz', 'Inclusive fossas nasais, seios da face e olfato.', '753070300', 40),
    ('Boca', 'Inclusive lábios, dentes, língua, garganta e paladar.', '753070500', 50),
    ('Mandíbula', 'Inclusive queixo.', '753070700', 60),
    ('Face - partes múltiplas', 'Combinação de duas ou mais partes da face.', '753070800', 70),
    ('Cabeça - partes múltiplas', 'Combinação de duas ou mais partes da cabeça.', '753080000', 80),
    ('Cabeça - não especificado', 'Cabeça, NIC.', '753090000', 90),

    ('Braço - total', 'Região entre o punho e o ombro.', '753510000', 100),
    ('Braço - acima do cotovelo', NULL, '753510200', 110),
    ('Pescoço', NULL, '754000000', 120),
    ('Cotovelo', NULL, '755010400', 130),
    ('Antebraço', 'Região entre o punho e o cotovelo.', '755010600', 140),
    ('Punho', NULL, '755030000', 150),
    ('Mão', 'Exceto punho e dedos.', '755050000', 160),
    ('Dedo da mão', NULL, '755070000', 170),
    ('Membros superiores - partes múltiplas', 'Combinação de duas ou mais partes dos membros superiores.', '755080000', 180),
    ('Membros superiores - não especificado', 'Membros superiores, NIC.', '755090000', 190),

    ('Ombro', NULL, '756020000', 200),
    ('Tórax', 'Inclusive órgãos internos.', '756030000', 210),
    ('Dorso e coluna', 'Inclusive músculos dorsais, coluna e medula espinhal.', '756040000', 220),
    ('Abdome', 'Inclusive órgãos internos.', '756050000', 230),
    ('Quadris e pelve', 'Inclusive órgãos pélvicos e nádegas.', '756060000', 240),
    ('Tronco - partes múltiplas', 'Combinação de duas ou mais partes do tronco.', '756070000', 250),
    ('Tronco - não especificado', 'Tronco, NIC.', '756090000', 260),

    ('Perna - total', 'Região entre o tornozelo e a pelve.', '757010000', 270),
    ('Coxa', NULL, '757010200', 280),
    ('Joelho', NULL, '757010400', 290),
    ('Perna - abaixo do joelho', 'Região entre o tornozelo e o joelho.', '757010600', 300),
    ('Tornozelo', NULL, '757030000', 310),
    ('Pé', 'Exceto dedos do pé.', '757050000', 320),
    ('Dedo do pé', 'Artelho.', '757070000', 330),
    ('Membros inferiores - partes múltiplas', 'Combinação de duas ou mais partes dos membros inferiores.', '757080000', 340),
    ('Membros inferiores - não especificado', 'Membros inferiores, NIC.', '757090000', 350),

    ('Partes múltiplas do corpo', 'Mais de uma parte importante do corpo atingida.', '758000000', 360),
    ('Sistemas e aparelhos', 'Quando o funcionamento de todo um sistema ou aparelho for afetado, sem lesão externa específica.', '758500000', 370),
    ('Aparelho circulatório', NULL, '758520000', 380),
    ('Aparelho respiratório', NULL, '758530000', 390),
    ('Sistema nervoso', NULL, '758540000', 400),
    ('Aparelho digestivo', NULL, '758550000', 410),
    ('Aparelho geniturinário', NULL, '758560000', 420),
    ('Sistema musculoesquelético', NULL, '758570000', 430),
    ('Sistemas e aparelhos - não especificado', 'Sistemas e aparelhos, NIC.', '758590000', 440),
    ('Local da lesão - não especificado', 'Localização da lesão, NIC.', '759000000', 450)
) AS v(nome, descricao, codigo, ordem)
ON CONFLICT (tipo, nome_normalizado) WHERE ativo DO NOTHING;

-- ---------------------------------------------------------------------
-- Naturezas da lesao - eSocial, Tabela 17
-- ---------------------------------------------------------------------

INSERT INTO catalogos_sst (id, tipo, nome, nome_normalizado, descricao, codigo, ordem,
                           versao, data_criacao, usuario_criacao, ativo)
SELECT gen_random_uuid(), 'NATUREZA_LESAO', v.nome,
       immutable_unaccent(lower(v.nome)), v.descricao, v.codigo, v.ordem,
       0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE
FROM (VALUES
    ('Lesão imediata', 'Categoria geral para lesão com manifestação imediata.', '702000000', 10),
    ('Escoriação ou abrasão', 'Ferimento superficial.', '702005000', 20),
    ('Corte, laceração ou ferida aberta', 'Inclui ferida contusa e punctura.', '702010000', 30),
    ('Contusão ou esmagamento', 'Com a superfície cutânea intacta.', '702015000', 40),
    ('Distensão ou torção', NULL, '702020000', 50),
    ('Inflamação de articulação, tendão ou músculo', 'Inclui sinovite e tenossinovite; não inclui distensão ou torção.', '702025000', 60),
    ('Luxação', NULL, '702030000', 70),
    ('Fratura', NULL, '702035000', 80),
    ('Queimadura térmica ou escaldadura', 'Efeito de temperatura elevada ou contato com substância quente.', '702040000', 90),
    ('Queimadura química', 'Lesão de tecido provocada pela ação corrosiva de produto químico ou de suas emanações.', '702042000', 100),
    ('Efeito imediato de radiação', 'Lesão imediata de tecido, osso ou fluido orgânico por exposição à radiação.', '702045000', 110),
    ('Congelamento ou geladura', 'Efeito da exposição à baixa temperatura.', '702048000', 120),
    ('Asfixia, estrangulamento ou afogamento', NULL, '702050000', 130),
    ('Intermação, insolação ou exaustão térmica', 'Inclui cãibra e outros efeitos da temperatura ambiente elevada.', '702055000', 140),
    ('Choque elétrico ou eletrocussão', NULL, '702060000', 150),
    ('Hérnia ou ruptura', 'Hérnia de qualquer natureza ou ruptura.', '702065000', 160),
    ('Amputação ou enucleação', NULL, '702070000', 170),
    ('Perda ou diminuição imediata de sentido', 'Audição, visão, olfato, paladar ou tato; não se aplica quando for sequela de outra lesão.', '702075000', 180),
    ('Concussão cerebral', NULL, '702080000', 190),
    ('Lesão imediata - não especificada', 'Lesão imediata, NIC.', '702090000', 200),

    ('Doença contagiosa ou infecciosa', 'Inclui, por exemplo, tuberculose e brucelose.', '704020000', 210),
    ('Pneumoconiose', 'Inclui, por exemplo, silicose e asbestose.', '704030000', 220),
    ('Dermatose', 'Erupção ou inflamação da pele causada por agente sensibilizante ou irritante.', '704040000', 230),
    ('Envenenamento sistêmico', 'Condição sistêmica provocada por inalação, ingestão ou absorção cutânea de substância tóxica.', '704050000', 240),
    ('Perda ou diminuição mediata de sentido', 'Audição, visão, olfato, paladar ou tato, quando não for sequela de outra lesão.', '704060000', 250),
    ('Efeito mediato de radiação', 'Lesão com manifestação mediata causada por exposição à radiação.', '704070000', 260),
    ('Doença - não especificada', 'Doença, NIC.', '704090000', 270),
    ('Lesões múltiplas', NULL, '706050000', 280),
    ('Outras lesões - não especificadas', 'Outras lesões, NIC.', '706090000', 290)
) AS v(nome, descricao, codigo, ordem)
ON CONFLICT (tipo, nome_normalizado) WHERE ativo DO NOTHING;

-- ---------------------------------------------------------------------
-- Agentes causadores - selecao da Tabela 14 do eSocial adequada ao
-- contexto operacional de distribuicao de energia do modulo.
-- ---------------------------------------------------------------------

INSERT INTO catalogos_sst (id, tipo, nome, nome_normalizado, descricao, codigo, ordem,
                           versao, data_criacao, usuario_criacao, ativo)
SELECT gen_random_uuid(), 'AGENTE_CAUSADOR', v.nome,
       immutable_unaccent(lower(v.nome)), v.descricao, v.codigo, v.ordem,
       0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE
FROM (VALUES
    -- Superficies, circulacao e estruturas
    ('Rua ou estrada', 'Superfície utilizada para sustentar pessoas.', '302010200', 10),
    ('Calçada ou caminho de pedestre', 'Superfície utilizada para sustentar pessoas.', '302010250', 20),
    ('Piso de edifício', 'Superfície utilizada para sustentar pessoas.', '302010300', 30),
    ('Escada permanente', 'Degraus que permitem apoio integral do pé.', '302010350', 40),
    ('Rampa', 'Superfície utilizada para sustentar pessoas.', '302010400', 50),
    ('Passarela ou plataforma permanente', 'Superfície utilizada para sustentar pessoas.', '302010450', 60),
    ('Chão', 'Superfície utilizada para sustentar pessoas.', '302010550', 70),
    ('Piso de andaime ou plataforma desmontável', 'Superfície utilizada para sustentar pessoas.', '302010600', 80),
    ('Piso de veículo', 'Superfície utilizada para sustentar pessoas.', '302010650', 90),
    ('Telhado', NULL, '302010700', 100),
    ('Superfície de sustentação - não especificada', 'Superfície utilizada para sustentar pessoas, NIC.', '302010900', 110),
    ('Escada móvel ou fixada - não especificada', NULL, '302030900', 120),
    ('Edifício', 'Edifício ou estrutura.', '302050100', 130),
    ('Tanque, silo ou depósito fixo', 'Edifício ou estrutura.', '302050200', 140),
    ('Ponte ou viaduto', 'Edifício ou estrutura.', '302050500', 150),
    ('Andaime ou plataforma', 'Edifício ou estrutura.', '302050700', 160),
    ('Torre ou poste', 'Edifício ou estrutura.', '302050800', 170),
    ('Edifício ou estrutura - não especificado', 'Exceto piso, superfície de sustentação ou área de circulação.', '302050900', 180),
    ('Escavação', 'Escavação para edifício, estrada ou atividade semelhante.', '302070100', 190),
    ('Canal ou fosso', NULL, '302070300', 200),
    ('Poço ou galeria', NULL, '302070500', 210),
    ('Túnel', NULL, '302070700', 220),
    ('Escavação, fosso ou túnel - não especificado', NULL, '302070900', 230),
    ('Superfície ou estrutura - não especificada', NULL, '302090000', 240),

    -- Ferramentas manuais e portateis
    ('Martelo, malho ou marreta', 'Ferramenta manual sem força motriz.', '303010040', 250),
    ('Machadinha ou enxó', 'Ferramenta manual sem força motriz.', '303010080', 260),
    ('Faca ou facão', 'Ferramenta manual sem força motriz.', '303010120', 270),
    ('Tesoura ou tesourão', 'Ferramenta manual sem força motriz.', '303010160', 280),
    ('Serra ou serrote manual', 'Ferramenta manual sem força motriz.', '303010240', 290),
    ('Alicate, torquês ou tenaz', 'Ferramenta manual sem força motriz.', '303010280', 300),
    ('Punção, ponteiro ou talhadeira manual', 'Ferramenta manual sem força motriz.', '303010400', 310),
    ('Chave de parafuso', 'Ferramenta manual sem força motriz.', '303010480', 320),
    ('Chave de boca ou chave ajustável', 'Ferramenta manual sem força motriz.', '303010520', 330),
    ('Alavanca ou pé-de-cabra', 'Ferramenta manual sem força motriz.', '303010560', 340),
    ('Corda, cabo ou corrente manual', 'Ferramenta manual sem força motriz.', '303010600', 350),
    ('Machado', 'Ferramenta manual sem força motriz.', '303010640', 360),
    ('Enxada ou enxadão', 'Ferramenta manual sem força motriz.', '303010680', 370),
    ('Pá ou cavadeira', 'Ferramenta manual sem força motriz.', '303010720', 380),
    ('Picareta', 'Ferramenta manual sem força motriz.', '303010760', 390),
    ('Ferramenta manual - não especificada', 'Ferramenta manual sem força motriz, NIC.', '303010900', 400),
    ('Martelete ou socador', 'Ferramenta portátil com força motriz.', '303015050', 410),
    ('Serra portátil motorizada', 'Ferramenta portátil com força motriz.', '303015200', 420),
    ('Perfuratriz portátil', 'Ferramenta portátil com força motriz.', '303015300', 430),
    ('Esmeril portátil', 'Ferramenta portátil com força motriz.', '303015450', 440),
    ('Ferramenta de soldagem', 'Ferramenta portátil com força motriz ou aquecimento.', '303015600', 450),
    ('Maçarico', 'Ferramenta portátil com força motriz ou aquecimento.', '303015650', 460),
    ('Ferramenta portátil motorizada - não especificada', 'Ferramenta portátil com força motriz ou aquecimento, NIC.', '303015900', 470),

    -- Maquinas, equipamentos e veiculos
    ('Máquina de corte', 'Serra, tesoura, guilhotina ou máquina de cortar.', '303020080', 480),
    ('Furadeira, torno ou fresa', 'Máquina com força motriz.', '303020160', 490),
    ('Prensa', 'Máquina com força motriz.', '303020200', 500),
    ('Máquina de terraplenagem', 'Máquina de terraplenagem ou construção de estrada.', '303020480', 510),
    ('Máquina - não especificada', 'Máquina, NIC.', '303020900', 520),
    ('Guindaste', 'Equipamento de guindar.', '303030050', 530),
    ('Ponte rolante', 'Equipamento de guindar.', '303030100', 540),
    ('Talha', 'Equipamento de guindar.', '303030300', 550),
    ('Macaco mecânico, hidráulico ou pneumático', 'Equipamento de guindar.', '303030400', 560),
    ('Guincho elétrico', 'Equipamento de guindar.', '303030500', 570),
    ('Equipamento de guindar - não especificado', 'Equipamento de guindar, NIC.', '303030900', 580),
    ('Correia de transmissão', 'Dispositivo de transmissão de energia mecânica.', '303035300', 590),
    ('Corrente, corda ou cabo de transmissão', 'Dispositivo de transmissão de energia mecânica.', '303035400', 600),
    ('Tambor, polia ou roldana', 'Dispositivo de transmissão de energia mecânica.', '303035500', 610),
    ('Engrenagem', 'Dispositivo de transmissão de energia mecânica.', '303035700', 620),
    ('Gerador elétrico', 'Equipamento elétrico.', '303040100', 630),
    ('Condutor elétrico', 'Equipamento elétrico.', '303040200', 640),
    ('Transformador ou conversor', 'Equipamento elétrico.', '303040300', 650),
    ('Painel, barramento, chave ou disjuntor', 'Inclui interruptor e fusível; equipamento elétrico.', '303040400', 660),
    ('Bateria ou equipamento de controle elétrico', 'Inclui capacitor, retificador e dispositivo de partida.', '303040500', 670),
    ('Motor elétrico', 'Equipamento elétrico.', '303040600', 680),
    ('Equipamento elétrico - não especificado', 'Equipamento elétrico, NIC.', '303040900', 690),
    ('Tubo ou mangueira sob pressão', 'Para líquido, gás ou vapor.', '303050600', 700),
    ('Forno, estufa ou aquecedor', 'Equipamento de aquecimento.', '303060000', 710),
    ('Arco elétrico', 'Equipamento emissor de radiação não ionizante.', '303065600', 720),
    ('Bicicleta', NULL, '303075100', 730),
    ('Motocicleta ou motoneta', NULL, '303075200', 740),
    ('Veículo rodoviário motorizado', NULL, '303075250', 750),
    ('Empilhadeira', NULL, '303075450', 760),
    ('Carro de mão', NULL, '303075550', 770),
    ('Trator', NULL, '303075600', 780),
    ('Veículo de terraplenagem', NULL, '303075650', 790),
    ('Veículo - não especificado', 'Veículo, NIC.', '303075900', 800),
    ('Ferramenta, máquina, equipamento ou veículo - não especificado', 'Agente desta família, NIC.', '303090000', 810),

    -- Substancias, materiais e seres vivos
    ('Ácido', 'Substância química.', '305004350', 820),
    ('Álcali', 'Substância química.', '305004400', 830),
    ('Substância química - não especificada', 'Substância química, NIC.', '305004900', 840),
    ('Água', 'Quando o estado líquido contribuir preponderantemente para a ocorrência.', '305008500', 850),
    ('Líquido - não especificado', 'Líquido, NIC.', '305008900', 860),
    ('Partículas não identificadas', NULL, '305020000', 870),
    ('Madeira', 'Inclui toro, madeira serrada, poste, barrote, ripa e outros produtos de madeira.', '305028000', 880),
    ('Metal', 'Inclui ligas, tubos, placas, perfis, vergalhões, arames e sucata metálica.', '305032500', 890),
    ('Areia, cascalho, pedra ou argila', 'Produto mineral não metálico.', '305036000', 900),
    ('Óleo lubrificante ou graxa', 'Inclui parafina e óleo de corte.', '305040250', 910),
    ('Óleo diesel', NULL, '305040300', 920),
    ('Gasolina', NULL, '305040450', 930),
    ('Gás combustível', 'Inclui gás liquefeito, gás natural e gás encanado de nafta.', '305040500', 940),
    ('Vidro ou fibra de vidro', 'Inclui vidraria, fibra de vidro e lâmina de vidro.', '305044000', 950),
    ('Plástico', 'Inclui pó, folha, barra, perfil e produtos plásticos.', '305056000', 960),
    ('Produto de limpeza, sabão ou detergente', NULL, '305072000', 970),
    ('Sucata, entulho ou resíduo', NULL, '305076000', 980),
    ('Material ou produto - não especificado', 'Substância química, material ou produto, NIC.', '305090000', 990),
    ('Animal vivo', NULL, '306020000', 1000),
    ('Vegetal, planta ou árvore', 'Vegetal em estado natural, não beneficiado.', '306040000', 1010),
    ('Agente infeccioso ou parasitário', 'Inclui bactéria, fungo, organismo parasitário e vírus.', '306060000', 1020),
    ('Ser vivo - não especificado', 'Ser vivo, NIC.', '306090000', 1030),

    -- Ambiente e formas de energia
    ('Área ou ambiente de trabalho', 'Utilizado também para fenômeno atmosférico, meteoro ou radiação solar.', '307070000', 1040),
    ('Energia - não especificada', NULL, '354000000', 1050),
    ('Ruído', NULL, '354020000', 1060),
    ('Fogo, chama, fumaça ou material quente', NULL, '354040000', 1070),
    ('Temperatura ambiente', 'Não inclui temperatura de objeto ou substância quente.', '354050300', 1080),
    ('Aerodispersoides', NULL, '355016000', 1090),
    ('Neblina', NULL, '355016600', 1100),
    ('Gás ou vapor', NULL, '355016800', 1110),

    -- Opcoes de contingencia previstas pela tabela oficial
    ('Agente causador - não especificado', 'Agente do acidente, NIC.', '309000000', 1120),
    ('Agente causador inexistente', 'Utilizar somente quando efetivamente não houver agente causador.', '309500000', 1130)
) AS v(nome, descricao, codigo, ordem)
ON CONFLICT (tipo, nome_normalizado) WHERE ativo DO NOTHING;
