# Interpretador Python no Railway

Adaptador privado para o contrato HTTP do `CodeInterpreterClient`. Cada execução usa
uma VM efêmera do Railway Sandbox. O processo executa Python em um namespace de rede
separado e como usuário sem privilégios. Se essa proteção não estiver disponível,
a execução falha.

Variáveis no serviço `code-interpreter`:

- `RAILWAY_TOKEN`: token de projeto restrito ao ambiente `production` (preferido).
- `RAILWAY_ENVIRONMENT_ID`: fornecida automaticamente pelo Railway.
- `PORT=8000`: fixa a porta usada pela rede privada.

O serviço não precisa de domínio público. O backend usa
`CODE_INTERPRETER_BASE_URL=http://code-interpreter.railway.internal:8000`.
Não coloque credenciais, banco de dados ou referências a outros serviços no
ambiente deste interpretador.

O serviço só responde `status: ok` em `/health` depois de criar uma VM de
verificação, importar as bibliotecas e executar Python sem privilégios em um
namespace de rede separado. Se Sandboxes não estiver habilitado para o projeto,
o healthcheck falha e o backend não deve receber a URL.
