# Validação desta implementação

## Build e JAR verificados

- [Execução aprovada no GitHub Actions](https://github.com/nateriver31/reliquias/actions/runs/43346937640), código `95c2168`, Java 25 e Paper API `26.2.build.111-stable`.
- **27 testes aprovados**, incluindo 19 do núcleo e 8 de integração MockBukkit. O workflow exige a execução completa, sem falhas, erros ou testes ignorados, antes de publicar o JAR.
- [JAR 1.0.0 e checksum SHA-256](https://github.com/nateriver31/reliquias/releases/tag/build-3) publicados. O JAR foi baixado novamente no ambiente cloud, passou na verificação do checksum e contém as classes do plugin, `plugin.yml` e `config.yml` esperados, sem dependências de teste.
- Corrigido o carregamento em MockBukkit: a classe principal precisa permitir a subclasse usada pelo framework de integração.

## Instância cloud

- JDK Temurin 25.0.5+8 e Maven 3.9.9 instalados em `/workspace/.tools`, com SHA-256/SHA-512 oficiais verificados.
- Bootstrap e os **19 testes do núcleo passaram localmente**, sem testes ignorados ou abortados.
- O build completo **local** ainda está bloqueado por HTTP 403 ao acessar a API Paper. O resultado completo acima foi obtido no GitHub Actions, e não nesta máquina. O rascunho inclui os domínios oficiais do Paper e `api.github.com`; é necessário aplicar essa configuração para compilar localmente e usar a API GitHub. Depois da aplicação, executar `bash scripts/cloud-mvn.sh -B -ntp -U -C verify`.

## Testes em servidor

Não foi executado servidor Paper real nem clientes Java/Bedrock. O roteiro de aceitação com as versões reais de Geyser/Floodgate, CoreProtect e proteções está no README. A aprovação dos testes automatizados não substitui esse teste funcional. Nenhum mundo de produção foi acessado.
