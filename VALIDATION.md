# Validação desta implementação

- JDK Temurin 25.0.5+8 e Maven 3.9.9 instalados em `/workspace/.tools`, com SHA-256/SHA-512 oficiais verificados.
- `bash scripts/bootstrap-cloud.sh`: executado com sucesso. A CA pública do proxy é adicionada a um truststore local, preservando TLS.
- `bash scripts/test-core.sh`: **19 testes executados, 19 aprovados**, nenhum ignorado ou abortado. Verificam a transformação completa de chunks, preservação de construções, orçamento incremental, conflitos/proteções, deduplicação de áreas sobrepostas, limites da fila, coordenadas negativas, isolamento por mundo, persistência e corrupção de registros, e cooldown.
- `bash scripts/cloud-mvn.sh -B -ntp -C verify`: **bloqueado no download da API do Paper por HTTP 403 da política de rede**. O build completo e os 8 testes `ScrollIntegrationTest` com MockBukkit ainda não foram executados. Não há JAR compilado/validado para instalar.
- O rascunho do ambiente inclui os domínios oficiais do Paper. Após aplicar o acesso nas configurações, executar novamente o build Maven (usar `-U` na primeira tentativa, para invalidar o resultado de resolução em cache).
- Não foi executado servidor Paper real nem clientes Java/Bedrock. O roteiro de aceitação, incluindo CoreProtect e proteções, está no README. Nenhum mundo de produção foi acessado.

Os testes do núcleo não estabelecem a compilação ou compatibilidade de execução das classes que usam a API Paper. O ambiente e o plugin ainda precisam dessa validação após a liberação de rede.

## Build no GitHub

O workflow `.github/workflows/build.yml` compila e executa as duas suítes no GitHub Actions, com Java 25. Exige pelo menos 27 testes, sem falhas, erros ou testes ignorados, antes de disponibilizar o JAR. Uma execução verde e seu artefato comprovam o resultado desse build; a existência do workflow, por si só, não comprova compilação bem-sucedida. O teste de aceitação em servidor real permanece separado.
