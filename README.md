# Relíquias do Nether — Paper 26.2

Plugin Java 25 para liberar gradualmente um teto customizado do Nether. Usa somente `FLOWER_BANNER_PATTERN` e `CREEPER_BANNER_PATTERN`, com identificação persistente própria. Padrões vanilla comuns, mesmo renomeados, não ativam poderes. Não exige mod ou resource pack; recebe as interações Java e as traduzidas por Geyser/Floodgate.

## Compilar e testar

O workflow [Compilar plugin Paper 26.2](../../actions) roda em cada envio para `main` e também pode ser iniciado manualmente em **Actions → Run workflow**. Depois que os 27 testes passarem, ele disponibiliza o JAR como artefato da execução e em **Releases**, junto ao checksum SHA-256. Builds que falham ou ignoram testes não publicam o plugin.

Com JDK 25 e Maven 3.9.9 ou superior:

```sh
mvn -B -C verify
```

No ambiente Codex Cloud, que tem proxy e CA próprios:

```sh
bash scripts/bootstrap-cloud.sh
bash scripts/cloud-mvn.sh -B -ntp -C verify
```

Resultado esperado após compilação bem-sucedida: `target/reliquias-nether-1.0.0.jar`. Paper API e MockBukkit estão fixados respectivamente em `26.2.build.111-stable` e `4.117.0`. O JAR não inclui Paper nem dependências de teste.

`bash scripts/test-core.sh` executa os testes do algoritmo, fila, persistência e cooldown sem depender do repositório Paper. Não substitui a compilação completa nem o teste com servidor e clientes. Veja [VALIDATION.md](VALIDATION.md) para o estado efetivamente verificado.

## Instalar e obter os itens

Coloque o JAR compilado em `plugins/` de um servidor **Paper 26.2 com Java 25** e reinicie normalmente. O plugin cria `plugins/ReliquiasNether/config.yml` e `chunks-concluidos.log`.

```text
/pergaminho dar NomeDoJogador flor 1
/pergaminho dar NomeDoJogador creeper 1
```

O comando exige `reliquias.admin` (OP por padrão). Jogadores têm `reliquias.flor` e `reliquias.creeper` por padrão. A quantidade administrativa pode ser de 1 a 64; com inventário cheio a entrega é recusada.

Em servidores com múltiplos Nethers, preencha `mundos-permitidos` com o nome do Nether customizado. Uma lista vazia aceita qualquer mundo cuja dimensão seja `NETHER`; Overworld e End são sempre recusados.

## Comportamento

- **Flor:** consome imediatamente uma unidade, inclusive no Criativo; fixa X/Z e o chunk de origem; mostra 3 vermelho, 2 amarelo e 1 verde em intervalos de 20 ticks. Prepara 7×7 chunks e só inicia o teleporte depois dos 60 ticks **e** da conclusão dos 49 chunks. Andar durante a espera não altera o destino. Aguarda carregamento assíncrono e procura espaço livre nas mesmas X/Z, acima do novo chão, sem apagar obstáculos. Após um teleporte aceito, coloca um baú acessível próximo com 10 obsidianas e um isqueiro; nunca entrega esses materiais no inventário.
- **Creeper:** reutilizável, sem consumo ou teleporte, somente a partir de Y=221, sem limite artificial superior. Prepara 5×5 chunks em torno do chunk atual, independentemente da direção. Cooldown individual de 30 segundos de tempo monotônico, mantido ao reconectar; reiniciar o servidor encerra cooldowns.
- **Ambos:** constroem bedrock em Y=219 e Y=220, antes de remover apenas bedrock em Y=221–255. Não leem nem modificam camadas inferiores durante a transformação, nem removem outros materiais do teto.

O preenchimento do chão aceita **somente ar ou bedrock**. Qualquer outro bloco, incluindo blocos naturais, é um conflito conservador: não é sobrescrito, o chunk fica incompleto e seu teto não é removido. Uma proteção que negue alterações também impede a conclusão. Se houver conflito em qualquer um dos 49 chunks, a Flor não teleporta. Chunks já concluídos na operação são mantidos. A expansão informa quantos chunks ficaram prontos.

Sem chegada e espaço para baú acessíveis, a Flor encerra com mensagem em vez de apagar construções. Baús existentes não são substituídos nem conectados ao novo baú. Se outro plugin cancelar o teleporte ou impedir posteriormente a colocação do baú, o jogador recebe o motivo. **Não existe devolução do pergaminho**, inclusive após falha, saída, morte, mudança de mundo ou reinício.

## Persistência e desempenho

A fila global compartilha uma única tarefa entre pedidos sobrepostos do mesmo chunk. O registro usa UUID do mundo e X/Z do chunk, checksum e escrita serializada fora da thread principal, com flush antes de sinalizar conclusão. Arquivo corrompido ou integração incompatível impede o funcionamento, sem apagar o registro. Chunks concluídos nunca são editados novamente, protegendo construções posteriores.

Por padrão, até dois chunks são carregados/editados por vez, com teto global de 4.096 verificações e orçamento cooperativo de 2 ms por tick. A geração/carregamento usa `getChunkAtAsync`; todas as consultas e alterações de blocos ficam na thread do servidor, em passos pequenos e sem física de blocos. O plugin só solicita coordenadas selecionadas; o gerador do Minecraft pode carregar vizinhos internamente para gerar um chunk. Não há varredura nem pré-geração do Nether inteiro. Configurações de desempenho não alteram as alturas ou dimensões das áreas.

Tickets mantêm chunks em uso carregados, com contagem de referências entre operações e liberação ao concluir/desativar. Falhas de disco suspendem novas edições. No reinício normal, o registro e os chunks são preservados. Não há transação atômica entre arquivos do plugin e arquivos de região do Minecraft: desligamentos abruptos exigem restaurar backups consistentes do mundo **e** do registro. Não apague o registro para tentar reprocessar construções prontas.

## CoreProtect e proteções

CoreProtect é opcional. Quando presente, sua API pública `logRemoval`/`logPlacement` com `BlockData` registra as remoções de bedrock, o novo chão e a colocação do baú, atribuídos ao jogador. A API deve estar habilitada; erros interrompem a edição. Não são disparados eventos vanilla artificiais, evitando registros duplicados ou efeitos de quebra/colocação de inventário. A integração não depende de FAWE.

WorldGuard é opcional e consultado para `build`, `block-break` e `block-place` em cada alteração, sem ativar bypass de administrador. Outros plugins de proteção podem cancelar o evento síncrono `RoofBlockChangeEvent`, disparado antes de cada alteração e do baú. Não existe uma API universal de proteção do Bukkit; **plugins de claims diferentes precisam de integração com esse evento** antes de liberar o uso nas áreas protegidas. Nenhuma proteção é desativada pelo plugin.

A compatibilidade completa depende das versões instaladas no servidor. CoreProtect, WorldGuard, Geyser e Floodgate reais ainda precisam do teste de aceitação abaixo.

## Aceitação no servidor de testes

Use uma cópia do Nether customizado, com backups do mundo e do registro. Confirme em Paper 26.2 e nas versões reais dos plugins:

1. Com Flor em pilha, clique, ande durante a contagem e confira consumo de uma unidade, cores, destino X/Z original e o baú físico com conteúdo exato.
2. Confira duas camadas em 219/220 e remoção só de bedrock até 255. Coloque uma construção na faixa do chão: o chunk deve ficar incompleto, sem apagar a construção nem remover o teto desse chunk.
3. Expanda em 221 e sobre uma construção alta. Verifique área centralizada, ausência de teleporte/consumo e cooldown de 30 segundos. Abaixo de 221, confira a mensagem exigida.
4. Dois jogadores ativam áreas sobrepostas. Após concluir, construa no teto, reinicie normalmente e repita: chunks registrados devem permanecer intocados.
5. Confira os logs/rollback de CoreProtect e negações do WorldGuard ou do plugin de claims integrado. Verifique cancelamento de teleporte e falta de espaço para o baú sem substituição de blocos.
6. Repita as interações com jogador Bedrock via Geyser/Floodgate e acompanhe MSPT/TPS durante a geração de chunks novos.
