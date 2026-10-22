# HikCentral Cadastro — Android e Backend seguro

Sistema de cadastro rápido de funcionários para as catracas Acqua Vale.

## Desenvolvimento: branch isolada

Nesta branch, o fluxo **câmera → nome → sexo → setor → cadastrar** foi preservado, mas o Android não autentica mais na Artemis e não contém AppKey ou AppSecret. A integração passa por backend Java 17 / Spring Boot com HTTPS e token individual por aparelho.

- [Backend, endpoints, configuração Windows e CLI](backend/README.md)
- [Suite de QA Android existente](qa/README.md)
- Build Android: `gradle :app:assembleDebug`
- Testes de backend: `cd backend && mvn clean verify`

**Não há serviço instalado no HikCentral.** Nenhum cadastro/alteração remota foi efetuado. A porta backend, CA, token e conectividade entre redes precisam ser provisionados por administrador autorizado.

Os seis setores disponíveis são REFEITORIO, DESENVOLVIMENTO, LOJA ACQUAVALE, CATRACAS SABIA, SAIDA ACQUAVALE e ENTRADA ACQUAVALE. O backend rejeita outros níveis mesmo se um cliente modificar requisições.

Não publicar uma release nem fazer merge na main até a validação offline e autorização operacional.
