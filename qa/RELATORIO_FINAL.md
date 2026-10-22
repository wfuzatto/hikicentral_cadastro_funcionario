# Relatório de validação — HikCentral Cadastro

Data: 2026-10-21

## Resultado geral

**BLOQUEADO para validação física e ponta a ponta.** O Android `ZF524P9TF6` foi detectado por ADB, mas está `unauthorized`. Nenhum cadastro foi enviado.

## Ambiente e dispositivo

- Projeto: `wfuzatto/hikicentral_cadastro_funcionario`, branch `main`.
- Commit de referência: `da01cbd` (`fix(ui): diagnóstico acessível na engrenagem e header Android 15`).
- Java: Temurin 17.0.20.1; Gradle local: 8.9; Android SDK local.
- ADB: `C:\xampp\htdocs\android-platform-tools\platform-tools\adb.exe`.
- Modelo, Android, API, resolução e densidade: **BLOQUEADO**, pois o ADB não está autorizado.

## Matriz de requisitos

| Requisito | Estado | Evidência / limite |
|---|---|---|
| Análise estática do app e configuração | APROVADO | Manifesto, UI, API, assinatura, armazenamento e utilitários revisados. |
| Testes JVM e build debug | APROVADO | 3 testes JVM aprovados, 0 falhas. `testDebugUnitTest` e `assembleDebug` concluíram com sucesso no Gradle 8.9. `qa/run-all.ps1 -SkipDevice` repetiu ambos com sucesso. |
| Android físico, instalação e UI | BLOQUEADO | `adb devices -l` reportou `ZF524P9TF6 unauthorized`. |
| Câmera e foto | NÃO EXECUTADO | Requer dispositivo autorizado e captura consentida. |
| DNS/TCP/TLS no Windows | REPROVADO | DNS resolveu `608905397dde.sn.mynetname.net` para `200.149.203.116`; TCP `200.149.203.116:37443` expirou. TLS não alcançado. Evidência em `qa/reports/connectivity-*.txt`; não representa a rede do Android. |
| DNS/TCP/TLS no Android | BLOQUEADO | ADB não autorizado. |
| OpenAPI: autenticação, estrutura e endpoints | BLOQUEADO | Sem AppKey/AppSecret e sem conexão ao servidor. O teste OpenAPI do app é somente leitura. |
| Cadastro Person e foto | NÃO EXECUTADO | Nenhuma pessoa ou imagem enviada. |
| Atribuição e sincronização de catracas | NÃO EXECUTADO | Sem acesso à OpenAPI/terminais. HTTP 200 não provaria sincronização. |
| GitHub Actions/publicação | NÃO EXECUTADO | APK não foi validado no dispositivo; versão e publicação final permanecem pendentes. |

## Artefatos

- APK debug: `app/build/outputs/apk/debug/app-debug.apk` (2.357.516 bytes). **Não instalado no telefone.**
- JUnit XML: `app/build/test-results/testDebugUnitTest/`.
- Log da suíte: `qa/reports/run-20261021-225309.log`.
- Total: 3 testes aprovados, 0 reprovados, 0 ignorados. UI/câmera/API no telefone: não executadas.

## Conectividade e integração

No Windows, o DNS foi resolvido, mas o TCP à porta 37443 expirou. Não foi possível testar TLS/HTTPS nem identificar o serviço HTTP. A evidência não distingue firewall/NAT, rota ou serviço parado. O resultado no Android é desconhecido. Não há confirmação de que a porta publicada seja a OpenAPI Artemis. A documentação oficial Hikvision lista os endpoints de criação de pessoa, grupos de acesso, associação de pessoas e reaplicação de autorização, mas a versão/configuração instalada no servidor não foi informada: [OpenAPI V3.1.1](https://enpinfo.hikvision.com/hkwsen/unzip/20260723200832_95640_doc/index.html).

## Pendências para concluir

1. Desbloquear o telefone e aceitar o diálogo RSA **Permitir depuração USB** (marcando “Sempre permitir”). Se o diálogo não aparecer, habilitar Opções do desenvolvedor → Depuração USB, reconectar o cabo e executar `adb kill-server; adb start-server; adb devices -l`; revogar autorizações de depuração e aceitar o novo diálogo.
2. Configurar AppKey/AppSecret OpenAPI no app. Não enviar credenciais ao repositório ou relatório.
3. Confirmar porta/certificado Artemis no servidor e habilitar teste de leitura OpenAPI.
4. Para o teste ponta a ponta, usar ambiente HikCentral de teste e pessoa de teste presente, autorizada para captura consentida.
5. Conferir aplicação e sincronização nos terminais pelo HikCentral.
