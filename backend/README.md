# Backend intermediário HikCentral (não implantado)

Java 17 + Spring Boot 3.5.7, H2 persistente e API REST de escopo restrito. **Esta branch não foi implantada**. Não iniciar apontando para produção nem executar um cadastro real sem autorização.

## Arquitetura e limites

```text
Android -- HTTPS (CA pública ou privada verificada) + Bearer token individual
    --> Backend Windows (por VPN ou regra aprovada, NÃO exposição direta improvisada)
    --> HTTPS com CA ProductRoot configurada e hostname SAN válido
    --> OpenAPI /artemis do HikCentral
```

O aplicativo NÃO contém AppKey nem AppSecret, e não usa login admin. O backend carrega AK/SK exclusivamente de variáveis de ambiente de um serviço Windows controlado. Os logs não registram foto, tokens nem corpo de requisições. Tabela H2 guarda apenas hash da requisição, códigos de pessoa/grupo e estado de retomada. Configuração de backup e ACL de arquivos no Windows é responsabilidade administrativa.

Em particular, **TCP 37443 aberto não confirma que `https://localhost:37443` apresente a Artemis**: o endpoint deve ser comprovado a partir do host Windows antes de configurar o backend. A CA PEM deve ser obtida por canal administrativo confiável e instalada na pasta protegida; este repositório não contém certificados. O hostname do `HIK_BASE_URL` deve constar no SAN do certificado do servidor (por exemplo `localhost`, se realmente aceito).

## Endpoints REST

| Método e rota | Segurança | Ação |
|---|---|---|
| GET `/api/v1/health` | Público | `{"status":"ok"}` sem segredos |
| GET `/api/v1/organization` | Bearer do aparelho | Organização raiz |
| GET `/api/v1/access-groups` | Bearer | Níveis limitados aos seis autorizados |
| POST `/api/v1/enrollments` | Bearer + `Idempotency-Key: UUID` | Nome, sexo, grupos e JPEG Base64 |
| GET `/api/v1/enrollments/{uuid}` | Bearer | Estado somente do aparelho autenticado |
| POST `/api/v1/enrollments/{uuid}/resume` | Bearer | Retomar etapas permitidas sem criar Person |

O backend **não** é um proxy Artemis genérico. Restrições de setor são impostas no servidor independentemente da interface Android. Campos: `fullName`, `gender` (0/1/2), `groups` (nomes autorizados), `photoJpegBase64` (JPEG, 10–350 KB, cabeçalho, rodapé e dimensões validadas). Corpo total limitado a 750 KB inclusive para envio chunked.

Respostas: `COMPLETED` (HTTP 200); `PERMISSIONS_PENDING` ou `SYNC_PENDING` (HTTP 202); `AMBIGUOUS` ou `AMBIGUOUS_ASSIGNMENT` (HTTP 409). O servidor mantém o mesmo estado para chamadas repetidas com a mesma chave e recusa reutilização da chave com payload diferente. Qualquer conexão interrompida após início de inclusão marca `AMBIGUOUS`; nunca recria automaticamente.

O estado `SYNC_PENDING` indica que a aplicação da permissão ainda não foi comprovada. O estado `COMPLETED` requer respostas Artemis sem erro, grupos associados e consulta de `applicationResult` com todos os três contadores globais em zero. **Esses contadores são globais**: a confirmação individual nas catracas ainda deve ser realizada na homologação antes de uso em massa. Se o HikCentral V3.1.0 divergir do schema esperado, o backend deve permanecer pendente (fail closed).

## Compilar e testar offline

No diretório `backend`, com JDK 17 e Maven 3.9+:

```powershell
mvn -B clean verify
mvn -DskipTests package
```

Os testes usam um `ArtemisGateway` simulado (mock) e banco H2 em memória. **Nenhuma chamada real à Artemis ocorre durante os testes**. O workflow na branch executa os testes do backend e compila Android, mas não faz deploy nem instala APK.

## Preparar o Windows para eventual homologação (somente após autorização)

1. Criar conta de serviço Windows dedicada, sem privilégios administrativos. Criar `C:\secure\hikbridge\data` e `C:\secure\hikbridge\certs` com ACL exclusivamente para essa conta e administradores autorizados. Não compartilhar H2 via rede ou execução simultânea de instâncias.
2. Provisionar um certificado HTTPS para **o hostname pelo qual o Android acessará o backend**, com SAN, cadeia confiável e chave privada no arquivo PKCS#12 protegidos pela conta de serviço. Não usar o certificado SYS-Service como certificado externo do backend.
3. Verificar a fingerprint SHA-256 da CA interna ProductRoot por canal administrativo externo ao arquivo recebido, copiar **somente a CA pública** para `HIK_CA_PEM`. Não confiar cegamente em um PEM de fonte desconhecida.
4. Confirmar que o DNS e a rota do Android (via VPN/rede autorizada) alcançam o **backend**. Iniciar com `BACKEND_BIND_HOST=127.0.0.1` e somente mudar o binding após definir regra controlada. Nada aqui modifica NAT, VPN, firewall ou HikCentral.
5. Criar AppKey/AppSecret no Partner OpenAPI com privilégios mínimos para organização, consulta grupos, cadastrar Person/face, associar grupo, reaplicação e `applicationResult`. Armazenar nas variáveis de ambiente do serviço/gerenciador de segredos. A senha admin do painel NÃO serve.
6. Definir `HIK_BASE_URL` para HTTPS local cujo SAN corresponde ao hostname; `https://localhost:37443` só será válido se um teste local confirmar OpenAPI nessa porta e CA correspondente. Alternativa documentada no diagnóstico anterior: Nginx em 443, que precisa ser verificado no host.
7. Definir `BACKEND_TLS_KEYSTORE`, `BACKEND_TLS_PASSWORD`, `HIK_DATA_DIR`, `HIK_CA_PEM`, `HIK_APP_KEY`, `HIK_APP_SECRET`. Use `backend/.env.example` como referência **não executável**; nunca comite valores reais.

Para executar localmente, **após autorização e configurações válidas**:

```powershell
java -jar target\hikcentral-bridge-0.1.0-SNAPSHOT.jar
```

Para hospedar como serviço Windows, configure via WinSW ou NSSM sob conta dedicada: executável `java.exe`, argumentos `-jar C:\apps\hikbridge\hikcentral-bridge-0.1.0-SNAPSHOT.jar`, diretório `C:\apps\hikbridge`, variáveis protegidas, reinício em falha e ACLs restritas. Não incluir credenciais nos argumentos ou nos logs de serviço. Em serviço sem terminal, forneça a senha do keystore por mecanismo protegido de ambiente; a conta Windows deve ter leitura da chave e escrita exclusiva do banco.

### Administração local de dispositivos (não via HTTP)

Os comandos a seguir devem rodar **somente no servidor**, com o serviço parado para acesso exclusivo ao banco H2, na conta administrativa autorizada. Estabeleça as mesmas variáveis `HIK_DATA_DIR` do serviço.

```powershell
java -jar target\hikcentral-bridge-0.1.0-SNAPSHOT.jar --spring.profiles.active=admin --spring.main.web-application-type=none --admin.action=create --admin.label=MotoG34
java -jar target\hikcentral-bridge-0.1.0-SNAPSHOT.jar --spring.profiles.active=admin --spring.main.web-application-type=none --admin.action=rotate --admin.device=UUID_DO_DISPOSITIVO
java -jar target\hikcentral-bridge-0.1.0-SNAPSHOT.jar --spring.profiles.active=admin --spring.main.web-application-type=none --admin.action=revoke --admin.device=UUID_DO_DISPOSITIVO
```

O token bruto é exibido uma única vez. O banco armazena **somente SHA-256** de um token aleatório de 256 bits, nunca texto claro. Rotação substitui o hash e invalida o token anterior imediatamente; revogação não apaga histórico de cadastros. Entregue o token ao celular por canal protegido, nunca por foto, log ou GitHub.

Em caso de `AMBIGUOUS`, localize a tentativa pelo UUID no banco, confira no HikCentral o `personCode` gerado e o Person real, sem criar novamente. Somente após verificação humana, o CLI registra o Person ID e libera a retomada:

```powershell
java -jar target\hikcentral-bridge-0.1.0-SNAPSHOT.jar --spring.profiles.active=admin --spring.main.web-application-type=none --admin.action=reconcile-person --admin.device=UUID_DISPOSITIVO --admin.key=UUID_TENTATIVA --admin.person-id=PERSON_ID_VALIDADO --admin.confirm-verified=YES
```

Em `AMBIGUOUS_ASSIGNMENT`, **não reenvie grupos** até confirmar manualmente se a operação parcial já ocorreu. Procedimento administrativo documentado no relatório de homologação; nesta implementação não existe endpoint para pular atribuição incerta.

## Android

Na engrenagem configure:
- URL do backend `https://hostname-backend:porta`, sem caminho adicional;
- token exclusivo do dispositivo, protegido pelo Android Keystore;
- se for necessário CA privada do backend, cole a CA PEM **pública** e o fingerprint SHA-256 obtido por canal confiável. O aplicativo rejeita CA não correspondente, vencida ou sem `basicConstraints CA`. Isso afeta apenas as conexões HTTPS do aplicativo, sem mexer no armazenamento global de certificados.
- **NÃO** introduza AK/SK no Android. As credenciais antigas são removidas na migração. A chave da tentativa em andamento continua persistente em `pending_backend`; a pendência do aplicativo antigo é mantida separadamente para resolução manual.

**Não remova o app com ADB, não rode `pm clear` e não teste criação de pessoas reais** sem autorização expressa e consentimento.

## Pendências explícitas antes de produção

- Validar endpoints e a assinatura Artemis **contra o guia OpenAPI exato V3.1.0**; a lógica de assinatura e rotas foi migrada do app legado e preservada, **sem teste remoto**.
- Conferir se reapplication é endpoint adequado para pessoa/funcionário neste build e se estatísticas globais zeradas demonstram entrega na catraca selecionada.
- Provar endpoint interno/TLS e testar token revogado em uma rede controlada.
- Usar estratégia estável de assinatura Android: APKs debug de ambientes distintos podem ter assinaturas incompatíveis. Não desinstalar telefone atual sem backup/avaliação.
- Exigir autorização antes de instalar serviço, alterar rede, enviar fotografias ou criar Person de teste.
