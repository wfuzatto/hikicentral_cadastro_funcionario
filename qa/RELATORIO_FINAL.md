# Relatório de validação — HikCentral Cadastro

Data: 2026-10-21

## Resultado geral

**PARCIALMENTE VALIDADO; integração HikCentral bloqueada.** A versão 1.0.1 está instalada e foi testada no Android físico. Não houve cadastro, envio de face ou alteração de configurações. Após a correção do IP, TCP `37443` está acessível, mas TLS falha porque a cadeia do certificado não é confiável no Windows nem no Android. A OpenAPI e catracas ainda não puderam ser validadas.

## Ambiente e dispositivo

- Repositório: `wfuzatto/hikicentral_cadastro_funcionario`, branch `main`.
- Commit base: `da01cbd`; primeiro commit da suíte QA: `8d38211`.
- Java Temurin 17.0.20.1; Gradle 8.9; Android SDK 35.
- Motorola Moto G34 5G, Android 15 / API 35, 720×1600, 280 dpi.
- ADB autorizado: `ZF524P9TF6`; Wi-Fi `PRODATAS-5G`.
- Versão no aparelho: `versionName=1.0.1`, `versionCode=2`.

## Matriz de requisitos

| Requisito | Estado | Evidência / limite |
|---|---|---|
| Análise estática do app/configuração | APROVADO | Manifesto, UI, API, assinatura, armazenamento e utilitários revisados. |
| Testes JVM e build debug | APROVADO | 3 testes JVM aprovados, 0 falhas. `qa/run-all.ps1 -SkipDevice` concluiu `testDebugUnitTest` e `assembleDebug`. |
| Instalação/versão 1.0.1 no dispositivo | APROVADO | `dumpsys package` confirma versão instalada pelo usuário. |
| Atualização local preservando app 1.0.0 | BLOQUEADO | `adb install -r` foi recusado por incompatibilidade de assinatura. O QA não desinstalou nem limpou o app. |
| Abrir app e verificar tela | APROVADO | App 1.0.1 abriu; screenshot avaliado sem crash. Barra de status não cobre o título. |
| Engrenagem e campos servidor/porta | APROVADO | Automação abriu configurações e encontrou Servidor/Porta HTTPS; não salvou alterações. |
| Campo nome ausente | APROVADO | Envio vazio permaneceu local, marcou o campo e mostrou tooltip “Informe o nome” ao abrir o indicador. Nenhum Person foi criado. |
| Câmera | APROVADO | Câmera abriu e retornou após cancelar; nenhuma foto foi capturada. |
| Captura/qualidade/envio de face | NÃO EXECUTADO | Exige captura presencial consentida; nenhuma imagem foi enviada. |
| Validações restantes, rotação e teclado | NÃO EXECUTADO | Sem cobertura automatizada completa desses cenários. |
| DNS no Windows e Android | APROVADO | Ambos resolveram `608905397dde.sn.mynetname.net` para `200.149.203.116`. |
| ICMP no Android | APROVADO | Ping ao IP respondeu. |
| TCP Android/Windows para 37443 | APROVADO (reteste após correção do IP) | Ambos estabelecem TCP para `200.149.203.116:37443`. |
| HTTPS/TLS e identificação do serviço | REPROVADO | Windows: `SEC_E_UNTRUSTED_ROOT`; Android: `unable to get local issuer certificate`. TLS confiável não se estabelece; HTTP/OpenAPI não foram acessados. |
| Autenticação, endpoints e JSON OpenAPI | BLOQUEADO | TLS falha antes da autenticação; AppKey/AppSecret não foram enviados. Login admin não foi usado. |
| Criar Person, face e permissões | NÃO EXECUTADO | TLS bloqueia a chamada; nenhuma chamada mutável foi enviada. |
| Sincronização de catracas | NÃO EXECUTADO | Sem acesso OpenAPI/terminais. |
| GitHub Actions | APROVADO | Workflow “Android APK” do commit `8d38211` concluiu com sucesso (run `39708366446`). |

## Contagem dos testes

- JVM: 3 aprovados, 0 reprovados.
- Dispositivo (1.0.1): 4 cenários aprovados — abrir/ver tela, configurações, validação de nome vazio e abrir/cancelar câmera; nenhum Person enviado.
- Rede: DNS aprovado em 2 origens; ICMP e TCP 37443 aprovados no Android; TLS reprovado em Windows e Android por emissor não confiável.
- Crashes: nenhum `FATAL EXCEPTION` detectado nos logs coletados.

## Assinatura e preservação

A tentativa inicial de atualizar o app 1.0.0 com o APK debug local foi recusada por certificados diferentes. SHA-256 do certificado 1.0.0 instalado anteriormente: `a3fff6b7b7947ae193c66c063c334570fc358c18f4292e2c132047f8ba3fca49`; APK local 1.0.1: `47ee111702b5d58a41e1f745016357acc5876bdf420efd3d698ad7de9fa2dde7`. Após o usuário instalar 1.0.1, o certificado atual é `6b17a4b4f17b2c18e0dad6260085c385f37fa172797b1daa49f917427a6a2a6b`. A chave privada correspondente não está no checkout nem no debug keystore local. O QA não apagou dados. A preservação de configurações durante a instalação manual do usuário não pôde ser confirmada.

## Artefatos

- APK debug local 1.0.1: `app/build/outputs/apk/debug/app-debug.apk` (2.357.516 bytes); assinado com certificado diferente do APK atualmente instalado.
- Relatórios JUnit XML: `app/build/test-results/testDebugUnitTest/`.
- Tela inicial: `qa/reports/device-20261021-230327/screen.png`.
- Configurações sem alteração: `qa/reports/device-20261021-230327/settings.png`.
- Validação de nome vazio: `qa/reports/device-20261021-230934/empty-validation.png`.
- Prévia da câmera, sem captura: `qa/reports/camera-20261021-230341/camera-preview.png`.
- Diagnóstico de rede anterior: `qa/reports/connectivity-20261021-231046.txt`.
- Diagnóstico após correção do IP: `qa/reports/connectivity-20261021-233717.txt` (37443 TCP OK; certificado não confiável).
- Teste comparativo 443/8443: `qa/reports/connectivity-20261021-231458.txt` e `qa/reports/connectivity-20261021-231514.txt` (conexão recusada).
- Relatório/log da suíte local: `qa/reports/run-20261021-230823.log`.

## Diagnóstico e pendências

Após o usuário corrigir o IP, DNS, ICMP e TCP para `200.149.203.116:37443` funcionam do Android; TCP também funciona no Windows. O erro atual é TLS: Windows relata `SEC_E_UNTRUSTED_ROOT` e Android relata `unable to get local issuer certificate`. Isso indica uma cadeia de certificados que os clientes não confiam. Corrigir a cadeia/certificado no servidor para um certificado válido para o hostname, com intermediários completos e raiz confiável. Não desativar validação TLS. Portas 443 e 8443 foram recusadas nos testes; isso não prova que 37443 seja Artemis. Ainda é necessária confirmação de que o serviço nessa porta é a OpenAPI. A [documentação oficial Hikvision OpenAPI V3.1.1](https://enpinfo.hikvision.com/hkwsen/unzip/20260723200832_95640_doc/index.html) lista os endpoints relevantes, mas não confirma que estejam ativos nesta instalação.

A execução completa futura de `run-all.ps1` precisa do keystore que assinou o APK 1.0.1 instalado para que `adb install -r` seja compatível. Também falta corrigir TLS e executar o teste somente leitura com AppKey/AppSecret da OpenAPI. O teste ponta a ponta requer uma pessoa de teste presente e autorizada; a sincronização deve ser conferida nos terminais.
