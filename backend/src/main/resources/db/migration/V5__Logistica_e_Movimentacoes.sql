CREATE TABLE solicitacoes_material (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    id_estoque_destino UUID REFERENCES estoques(id),
    id_requisitante UUID REFERENCES usuarios(id),
    id_aprovador UUID REFERENCES usuarios(id),
    id_entregador_almox UUID REFERENCES usuarios(id), -- Quem liberou no físico
    status VARCHAR(30) DEFAULT 'PENDENTE', -- PENDENTE, APROVADO, ENTREGUE, ESTORNADO
    assinatura_almox TEXT, -- 🔒
    assinatura_equipe TEXT, -- 🔒
    data_solicitacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_entrega TIMESTAMP WITH TIME ZONE
);

CREATE TABLE itens_solicitacao (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    id_solicitacao UUID REFERENCES solicitacoes_material(id) ON DELETE CASCADE,
    id_material UUID REFERENCES materiais(id),
    quantidade DECIMAL(12,3) NOT NULL,
    num_serie VARCHAR(100) -- 🔒
);

CREATE TABLE movimentacoes_estoque (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    id_estoque_origem UUID REFERENCES estoques(id), -- NULL em Entrada Avulsa
    id_estoque_destino UUID REFERENCES estoques(id), -- NULL em Baixa Avulsa
    id_material UUID REFERENCES materiais(id),
    quantidade DECIMAL(12,3) NOT NULL,
    tipo_movimentacao VARCHAR(30) NOT NULL, -- ENTRADA_AVULSA, BAIXA_AVULSA, TRANSFERENCIA, ENTREGA, ESTORNO
    id_usuario_operador UUID REFERENCES usuarios(id),
    id_referencia_operacao UUID, -- ID da Solicitação ou Execução
    data_hora TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);