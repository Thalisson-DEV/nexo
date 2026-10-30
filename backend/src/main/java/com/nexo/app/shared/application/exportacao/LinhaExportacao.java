package com.nexo.app.shared.application.exportacao;

import java.io.IOException;

/** Destino de uma linha exportada (CSV, XLSX...): os casos de uso montam as colunas uma vez so. */
@FunctionalInterface
public interface LinhaExportacao {

    void escrever(Object... valores) throws IOException;
}
