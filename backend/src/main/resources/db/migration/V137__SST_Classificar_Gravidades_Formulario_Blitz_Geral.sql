-- Classifica as opcoes que geram desvio no formulario produtivo
-- "FORMULARIO BLITZ DE SEGURANCA - GERAL" sem recriar sua estrutura.
--
-- A lista funcional recebida usa B/M/G/GR para BAIXA/MEDIA/ALTA/CRITICA.
-- O trecho "GGR" entre os itens 20 e 23 representa dois itens consecutivos:
-- G (item 21) e GR (item 22). Assim, as 54 perguntas objetivas ficam cobertas.
--
-- Desvios ja registrados nao sao alterados: sua gravidade e um snapshot historico.

DO $$
DECLARE
    v_formulario_id CONSTANT UUID := '98e41822-9276-496a-967b-9b46e1f70130';
    v_total_esperado CONSTANT INTEGER := 54;
    v_total_opcoes_desvio INTEGER;
    v_total_valido INTEGER;
    v_total_classificado INTEGER;
BEGIN
    -- Este formulario existe somente no ambiente em que foi cadastrado. A cadeia
    -- Flyway deve continuar portavel para bancos novos e ambientes isolados.
    IF NOT EXISTS (
        SELECT 1
          FROM formularios_inspecao
         WHERE id = v_formulario_id
    ) THEN
        RAISE NOTICE
            'V137: formulario % ausente; nenhuma classificacao de gravidade foi aplicada.',
            v_formulario_id;
        RETURN;
    END IF;

    CREATE TEMPORARY TABLE gravidades_formulario_v137 (
        id_opcao UUID PRIMARY KEY,
        id_pergunta UUID NOT NULL UNIQUE,
        gravidade VARCHAR(20) NOT NULL
            CHECK (gravidade IN ('BAIXA', 'MEDIA', 'ALTA', 'CRITICA'))
    ) ON COMMIT DROP;

    INSERT INTO gravidades_formulario_v137 (id_opcao, id_pergunta, gravidade)
    VALUES
        ('2291f4d5-7eb0-4c2a-8a51-27f53c944c60', 'c428ad4e-f667-4fcb-8f24-096e6632b901', 'CRITICA'), -- 1
        ('08f340d5-d1be-4f13-aae4-1957732fc040', '7f9daf58-88db-4a8c-b894-921419b56fe6', 'CRITICA'), -- 2
        ('af9ed1a2-ae8f-4e7e-96ef-ce6a3735cca9', '49444e6b-e456-407e-926b-37f5d5091daf', 'CRITICA'), -- 3
        ('f060f752-89ed-4b7a-9428-c3fe26d13318', 'dde52d79-312b-4e5b-82e3-2b0b05d8abca', 'ALTA'), -- 4
        ('5920a1d0-c51d-4c6b-bb15-1d323912b1b2', '83261935-e7d4-4372-9593-bba7d27d0a85', 'CRITICA'), -- 5
        ('692439f3-c841-47ad-8dae-da721f4d8989', '6cb169ff-42d2-4b7d-8aa0-5c797f9a6de5', 'ALTA'), -- 6
        ('250c76f3-e440-4dc6-8c96-f6184b1550d5', '1480536f-dab7-4211-9457-788723ca5ad7', 'ALTA'), -- 7
        ('dcaec193-5304-4845-844f-d5e47f0d02bf', 'ebb93834-efcd-468d-826a-dde0e04ef229', 'ALTA'), -- 8
        ('d728fd2d-703c-4840-bb29-c96db9a73188', '30187b43-a459-4187-a88b-7b845ac0df0f', 'ALTA'), -- 9
        ('bb8c8249-d93c-461e-94ba-f43ec4ef13d9', 'a864ba12-5129-486b-a7a5-0991322e6b81', 'ALTA'), -- 10
        ('00aefdc1-6eba-4336-a48b-ac131199d527', '5f84cf88-3c68-4203-9b8b-0081b12b600a', 'ALTA'), -- 11
        ('a747f71e-de85-4082-b5f7-a35257701697', '56c4120b-4305-4071-95f2-d41b118376ce', 'ALTA'), -- 12
        ('a3d5bb54-939e-4509-8e1b-cf77797d8ba1', '5b02082c-4bae-491e-b4ec-390c706c451c', 'ALTA'), -- 13
        ('01a70b24-14db-46bd-87c8-ef46691935a7', '6e6c1dbf-0441-49c0-9c62-619c5da77421', 'MEDIA'), -- 14
        ('e9c884b6-f2a9-4ad7-80f7-0edba8040606', '72285bac-47d0-4b0a-bfa9-0a9513ab30dc', 'ALTA'), -- 15
        ('421d9f31-b9fc-452e-9944-35c6f2991e66', '6877c327-9ea6-400e-abd0-6c2ac0b07047', 'ALTA'), -- 16
        ('20627594-f8ab-4257-9199-b5f70b9b8882', '6702d0b3-a25c-4e01-8e95-5dda01605ca8', 'ALTA'), -- 17
        ('22b3a083-31f6-4226-a0ce-3e1e08442330', 'c69e913e-cf3e-49cb-9bbe-62e3845cc6a8', 'CRITICA'), -- 18
        ('f5427444-03f4-44c7-8dd1-1e57b7e395de', 'd746c6af-c894-4eda-bcec-7ae5334ef4c2', 'CRITICA'), -- 19
        ('1103cfe1-b820-4db1-aef1-6f8fdbd1051f', 'df0d0632-19ba-4e46-a18d-8872ef11659e', 'CRITICA'), -- 20
        ('bbb4d4bb-c286-4919-b8bb-3b932ff13ede', '0e877d8f-4b22-4b05-b41c-7d4cdf464a1e', 'ALTA'), -- 21
        ('0f469cd0-298a-4ed8-a73f-78ac80169807', '1fd84ff1-3f66-4393-aa23-564247a2fe00', 'CRITICA'), -- 22
        ('e8db8aa9-2ad6-4574-8ae4-3b4d38104fb3', 'e4ae3255-cda7-48d4-bfef-9c2edf635511', 'CRITICA'), -- 23
        ('5f216d72-6c55-434a-9dbc-d47d3edf8497', '5f332e63-59f0-4fa0-af56-7d3d777487fa', 'CRITICA'), -- 24
        ('f8c88660-ef83-4717-affd-a9bdf53237d8', 'c8a63bf7-be63-4c3e-b359-95817fa47ee4', 'ALTA'), -- 25
        ('cc5df142-0ac8-476d-8266-42b0f2de3198', '3c09fa69-e19d-4604-810e-aadc575efc4d', 'ALTA'), -- 26
        ('bb0cb452-a673-447d-88d2-e73ac3828dbe', '501a9ab0-936c-4dec-82c8-970d0b31482c', 'MEDIA'), -- 27
        ('1b393339-9dee-47b7-aadd-86bb48fc060e', '692f5dfb-75f8-44e0-993a-14a55b4acfe0', 'MEDIA'), -- 28
        ('a330703b-8d77-4da8-962f-9da0075834f0', '61ea5843-d504-4856-a970-5422e77da007', 'MEDIA'), -- 29
        ('ccea5424-bfdd-473b-bd04-bef1c75fc648', '3ded943b-8335-4493-8d5e-3eb51c59b453', 'CRITICA'), -- 30
        ('70fde254-4d0d-4956-9c5e-105c50f952b4', 'de91b8ef-5e48-4743-a413-1e661900ff99', 'ALTA'), -- 31
        ('a8b53234-8d40-4fc7-855a-ac926e30309e', '50835006-a167-4d06-973a-723b3582ee1a', 'MEDIA'), -- 32
        ('922339f6-6875-4842-90f3-99882dd94f61', '3a069e07-a5bd-435d-81db-869e991b51a4', 'BAIXA'), -- 33
        ('088638e4-f239-4ff1-89b2-625b6df2d3e8', '58c1d76c-f72a-403f-8307-d05931d1e274', 'MEDIA'), -- 34
        ('7e4e98b3-c9a0-4534-8100-161dd9e0a35f', '42e5b4ad-0ecc-4d59-ab37-dc8dff460745', 'MEDIA'), -- 35
        ('b8dbb2fc-be7d-45e6-bfc6-5049770fc434', '718d731f-3834-43db-85f6-f9c819889c5f', 'MEDIA'), -- 36
        ('e6a9d17b-a5e5-4180-9739-b514df8dec5f', 'ca7af871-ba3a-492c-8870-ff4fd9374dc1', 'ALTA'), -- 37
        ('6d05b6eb-3ec8-486c-aa27-b8c66d820730', '82c5c249-173e-44e3-b86c-8f4a07dc4cd6', 'ALTA'), -- 38
        ('7b97f1eb-1d19-4483-bfc3-c7fc36af84d0', 'dfab096d-eba1-4970-b383-7ea606a6152c', 'ALTA'), -- 39
        ('e675f5d4-a979-4ec1-8611-bef6f525255b', 'ea6105a9-fcae-49ff-8bb6-2d6a7cca5f50', 'ALTA'), -- 40
        ('ab688131-4b86-4f2d-a262-56b3ecf86cd0', '32870cf8-ca7b-4b24-a7d5-56c62fa8e558', 'MEDIA'), -- 41
        ('ace3b1dd-24a6-4e86-ab59-65f349fc4df5', '38ef54ee-835b-4ce6-9606-75dfa0d8a4ef', 'MEDIA'), -- 42
        ('f6481b6c-1abe-485a-8f35-25fb9b7d8f81', '5bbd131a-0791-4d1c-beb3-c1a6358c8a80', 'MEDIA'), -- 43
        ('f51297b5-e151-40d4-9385-0afbcbbd5a08', '09bfed9c-4368-481d-a41f-995103a44bba', 'CRITICA'), -- 44
        ('dbbcd4db-100d-4e21-a8ca-039738de24e1', '7a7de755-dd17-4862-abf1-bee069187ce7', 'MEDIA'), -- 45
        ('b3a2c636-601b-4055-867c-4b73418fedd5', '897328f3-e7e5-489b-bee1-415ce6511641', 'MEDIA'), -- 46
        ('1aebf462-b3de-411e-b2f7-3bcfdd62ec28', 'ee178c21-b8bb-477e-965e-e3e908c8005c', 'CRITICA'), -- 47
        ('89e1a9cf-0610-4bf4-9d0e-23a3566d5d09', 'cbcc3223-8d92-41e9-b148-65b69145e8b3', 'ALTA'), -- 48
        ('f12c63d9-fb72-435e-a847-fce677cf1a3e', '0976d611-6e21-45cb-b0bd-1e014533311f', 'CRITICA'), -- 49
        ('28fb2327-b802-41f7-9c0f-89e9d1ae881a', 'e0886037-a864-4c82-a70a-107e3e3e4cc7', 'MEDIA'), -- 50
        ('125e5322-2dd5-4479-920c-cca613fa0fe0', 'f7074fd2-73c6-4f49-921a-d12348f4b5e1', 'ALTA'), -- 51
        ('c79cff66-2e97-4b02-ae65-ef1d7f0b4c96', 'c39dc0d7-b8cd-4cd0-b0f4-e003b852e3cf', 'ALTA'), -- 52
        ('0be5ad35-0ca1-4085-9a7e-508638b67135', '1b249d34-3444-46f9-ac7d-d5a002561b23', 'MEDIA'), -- 53
        ('47ace849-b11c-4cd9-8a07-bcc92ef49efa', '72b7c61b-a243-4de9-a1f3-f76babd5763e', 'MEDIA'); -- 54

    IF (SELECT COUNT(*) FROM gravidades_formulario_v137) <> v_total_esperado THEN
        RAISE EXCEPTION
            'V137: mapeamento incompleto; esperadas % classificacoes.',
            v_total_esperado;
    END IF;

    SELECT COUNT(*)
      INTO v_total_opcoes_desvio
      FROM opcoes_resposta opcao
      JOIN perguntas_inspecao pergunta ON pergunta.id = opcao.id_pergunta
      JOIN grupos_pergunta grupo ON grupo.id = pergunta.id_grupo
     WHERE grupo.id_formulario = v_formulario_id
       AND opcao.gera_desvio = TRUE
       AND opcao.ativo = TRUE
       AND pergunta.ativo = TRUE
       AND grupo.ativo = TRUE;

    IF v_total_opcoes_desvio <> v_total_esperado THEN
        RAISE EXCEPTION
            'V137: estrutura divergente no formulario %; encontradas % opcoes ativas que geram desvio, esperadas %. Nenhum dado foi alterado.',
            v_formulario_id,
            v_total_opcoes_desvio,
            v_total_esperado;
    END IF;

    -- Confere os dois lados da relacao e todo o caminho ate o formulario antes
    -- da primeira escrita. Tambem impede classificar uma opcao conforme por engano.
    SELECT COUNT(*)
      INTO v_total_valido
      FROM gravidades_formulario_v137 mapeamento
      JOIN opcoes_resposta opcao
        ON opcao.id = mapeamento.id_opcao
       AND opcao.id_pergunta = mapeamento.id_pergunta
      JOIN perguntas_inspecao pergunta
        ON pergunta.id = mapeamento.id_pergunta
      JOIN grupos_pergunta grupo
        ON grupo.id = pergunta.id_grupo
     WHERE grupo.id_formulario = v_formulario_id
       AND opcao.gera_desvio = TRUE
       AND opcao.ativo = TRUE
       AND pergunta.ativo = TRUE
       AND grupo.ativo = TRUE
       AND pergunta.tipo_resposta = 'MULTIPLA_ESCOLHA';

    IF v_total_valido <> v_total_esperado THEN
        RAISE EXCEPTION
            'V137: estrutura divergente no formulario %; validados % de % itens. Nenhum dado foi alterado.',
            v_formulario_id,
            v_total_valido,
            v_total_esperado;
    END IF;

    UPDATE opcoes_resposta opcao
       SET gravidade = mapeamento.gravidade,
           versao = COALESCE(opcao.versao, 0) + 1,
           data_ultima_alteracao = CURRENT_TIMESTAMP,
           usuario_ultima_alteracao = 'migration:V137'
      FROM gravidades_formulario_v137 mapeamento
     WHERE opcao.id = mapeamento.id_opcao
       AND opcao.gravidade IS DISTINCT FROM mapeamento.gravidade;

    SELECT COUNT(*)
      INTO v_total_classificado
      FROM gravidades_formulario_v137 mapeamento
      JOIN opcoes_resposta opcao ON opcao.id = mapeamento.id_opcao
     WHERE opcao.gravidade = mapeamento.gravidade;

    IF v_total_classificado <> v_total_esperado THEN
        RAISE EXCEPTION
            'V137: verificacao posterior falhou; classificadas % de % opcoes.',
            v_total_classificado,
            v_total_esperado;
    END IF;
END
$$;
