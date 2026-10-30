@ApplicationModule(
    displayName = "estoques",
    allowedDependencies = {"shared", "modules.iam", "modules.execucao", "modules.obras", "modules.rh", "modules.assistente :: cadastro"}
)
package com.nexo.app.modules.estoque;

import org.springframework.modulith.ApplicationModule;
