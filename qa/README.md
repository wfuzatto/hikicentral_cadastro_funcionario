# QA offline — backend seguro (branch de desenvolvimento)

Execute `./qa/run-all.ps1` no PowerShell com JDK 17, Android SDK 35, Gradle 8.10.2 e Maven 3.9+.

Por padrão a suíte realiza **somente build e testes offline**, com HikCentral inteiramente simulado. Não executa ADB, `pm clear`, desinstalação, instalação do APK nem chamadas mutáveis ao servidor. O workflow `Secure Backend / Android offline CI` executa as mesmas etapas em runners isolados.

Os scripts `test-ui.ps1`, `test-camera.ps1`, `install-apk.ps1` e `test-connectivity.ps1` são artefatos históricos do aplicativo antigo: **não executar nesta etapa**, pois podem depender de ADB ou do host Artemis externo. Nenhum deles é invocado por `run-all.ps1`. O `test-api.ps1` foi substituído por orientação para o teste mock.

Somente após aprovação do backend, rede e TLS, estabeleça uma suíte separada para dispositivos físicos. APKs debug podem usar assinaturas distintas. Não desinstale nem limpe o telefone atual.

Os relatórios anteriores em `qa/RELATORIO_FINAL.md` descrevem o aplicativo legado V1.0.1, não constituem validação do backend.
