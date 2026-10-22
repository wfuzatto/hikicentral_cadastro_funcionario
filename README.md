# HikCentral • Cadastro rápido de funcionários (Android)

Aplicativo Android nativo para cadastrar **Person** no HikCentral Professional e habilitar acesso facial. Uma tela, sem login do operador: **tirar foto → nome → sexo → marcar as catracas/setores → cadastrar**.

## Funcionalidades

- Configuração persistente pela engrenagem: DNS, porta HTTPS, OpenAPI AppKey/AK e AppSecret/SK.
- Segredos criptografados pelo **Android Keystore AES-GCM**; nunca versionados no GitHub.
- Captura de foto via câmera do celular, normalização de rotação EXIF e compressão JPEG; foto enviada no campo `faces: [{ "faceData": "<Base64 JPEG>" }]`.
- Cadastro remoto de pessoa em `/artemis/api/resource/v1/person/single/add`.
- Departamento raiz buscado em `/artemis/api/resource/v1/org/rootOrg`, sem criar organizações novas.
- Lista real de níveis via `/artemis/api/acs/v1/privilege/group`. Os nomes locais são comparados ignorando acentos, maiúsculas e espaços.
- Associação de cada nível via `/artemis/api/acs/v1/privilege/group/single/addPersons`, seguida de `/artemis/api/visitor/v1/auth/reapplication`.
- Persistência de cadastro parcialmente concluído (`personId` + níveis que faltam), com botão para concluir **sem criar outra pessoa**.
- Validade automática de **10 anos** a partir da data da inclusão; matrícula/código omitidos para geração pela plataforma. PIN, cartão, e-mail e demais dados opcionais não são solicitados.
- Nenhuma foto armazenada permanentemente no telefone: arquivo temporário removido após sucesso.

### Níveis disponíveis no aplicativo

REFEITORIO, DESENVOLVIMENTO, LOJA ACQUAVALE, CATRACAS SABIA, SAIDA ACQUAVALE, ENTRADA ACQUAVALE.

São **níveis de acesso** (não departamentos). Para o primeiro uso, é necessário que esses níveis já existam no HikCentral com **esses nomes**. Os IDs não são fixados no aplicativo.

## Configuração inicial

1. Em **HikCentral Web Client**, habilite a **OpenAPI / Open Platform** e crie uma integração/partner com privilégios para Person, organização e Access Control. Obtenha **AppKey e AppSecret**. A conta `admin` do painel web **não é equivalente** à chave de integração.
2. Instale o app e abra a engrenagem.
3. DNS inicial: `608905397dde.sn.mynetname.net`; porta: `37443`. Ajuste se a OpenAPI Artemis estiver publicada em outro host/porta. Não confunda porta do Web Client com a porta da OpenAPI.
4. Informe AppKey/AppSecret, use **Testar conexão**. O app valida o departamento raiz e a listagem dos níveis, sem criar ninguém.
5. Cadastre uma pessoa de **teste autorizada** e confira no HikCentral a foto, os quatro campos principais, os níveis atribuídos e a distribuição para os terminais faciais, antes de usar na portaria em massa.

O aplicativo requer **HTTPS com certificado confiável pelo Android e hostname correspondente**. Não desabilite a verificação TLS: ajuste o certificado do servidor ou use uma conexão privada para um gateway de integração. Caso o HikCentral não disponibilize diretamente a OpenAPI nessa porta, será preciso expô-la adequadamente.

## Compilar / instalar

- **Android Studio** com JDK 17, Gradle 8.10.2 e Android SDK 35.
- Abra a pasta raiz, sincronize e execute `app` via USB (Android 8.0+, API 26).
- Ou em terminal com Gradle instalado: `gradle :app:assembleDebug`.
- GitHub: **Actions → Android APK**. Após execução bem-sucedida, baixe o artifact `hikicentral-cadastro-debug` que contém o APK.

## Segurança / limites

- Este é um aplicativo **sem login**, conforme solicitado. Acesso físico ao telefone deve ser restrito ao operador; para vários dispositivos, a arquitetura preferível é um *backend intermediário* que guarda AppSecret fora dos celulares, com permissões limitadas por aplicativo.
- Este repositório é público: **nunca inclua a senha de administrador, AppSecret, foto de funcionário ou arquivos de produção aqui**.
- A senha de administrador informada durante o planejamento deve ser trocada, pois já foi compartilhada em uma conversa.
- Dados faciais são dados pessoais sensíveis. Defina base legal aplicável, aviso aos colaboradores, política de retenção e segurança conforme LGPD.
- O app **não utiliza login do Web Client**, rotas privadas ou bypass de certificados.
- Resultado da chamada de reaplicação é **solicitação ao HikCentral**, não prova de que todas as catracas já receberam a atualização; confira no painel de aplicação de permissões.
- Se houver timeout durante criação da pessoa, confira primeiro no painel para evitar duplicidades. Níveis não encontrados são detectados **antes** da criação.
- Esta integração precisa ser validada com o modelo/versão real de HikCentral e OpenAPI implantada no ambiente.

### Documentação de referência

- [HikCentral Professional OpenAPI V3.1.1](https://enpinfo.hikvision.com/hkwsen/unzip/20260723200832_95640_doc/index.html)
- [HikCentral Professional — OpenAPI Developer Guide](https://qhub.md/upload/iblock/72d/53zg2jwh5fxgpengme17mvljwiaccjd8/OpenAPI.pdf)
