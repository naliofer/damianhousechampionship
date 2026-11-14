# Campeonato Damião — Android

Aplicativo Android offline em Kotlin, Jetpack Compose e Material 3. Os dados são armazenados localmente em SharedPreferences, como um snapshot JSON versionado.

## Cadastro e início

1. Criar o campeonato e definir o nome e a quantidade de rodadas (padrão: 4).
2. Cadastrar participantes e seus decks (padrão: 12 participantes).
3. Sortear duplas com decks diferentes, conferir e confirmar.
4. Iniciar o campeonato e sortear os confrontos.

São aceitos de 4 a 24 participantes, em múltiplos de 4, para manter um número par de duplas sem folgas. Nome e deck são obrigatórios. A comparação de decks ignora maiúsculas/minúsculas e espaços extras. O sorteio usa busca completa em ordem aleatória; quando um deck representa mais da metade dos participantes, não existe combinação válida.

Editar participantes antes do início invalida o sorteio e sua confirmação. Após iniciar, participantes, duplas e rodadas ficam bloqueados. Com seis duplas, são permitidas de uma a cinco rodadas.

## Resultados

- Vitória: 2×0, 0×2, 2×1 ou 1×2, conforme a decisão do organizador.
- Empate: 0×0, 1×0, 0×1, 1×1 ou 2×2.
- Vitória vale 1 ponto, empate vale 0,5 por dupla e derrota vale zero.
- Cada jogador pode ser eliminado apenas uma vez por confronto, por um dos adversários.
- Um jogador pode eliminar mais de um adversário, mesmo que também tenha sido eliminado naquela partida. O registro não impõe ordem temporal às eliminações.
- O placar é calculado pelos registros individuais. Todas as eliminações contam nas estatísticas, inclusive em empates.

## Sorteio e classificação

O sorteio de confrontos utiliza emparelhamento de custo mínimo por diferença de pontos, sem repetir adversários ou colocar uma dupla em mais de uma partida por rodada. Em caso de alternativas equivalentes, a escolha é aleatória. Se não existir uma rodada completa sem repetição, o aplicativo informa o impedimento.

Classificação geral: pontos, vitórias da dupla, menos mortes, mais kills, confronto direto e Buchholz. O confronto direto considera os pontos dentro de um grupo empatado quando todas as duplas desse grupo já se enfrentaram. Buchholz é a soma dos pontos dos adversários. Se todos os critérios empatarem, a ordem de cadastro das duplas dá estabilidade à apresentação.

Ranking Killer: mais kills, menos mortes e maior saldo. Empates compartilham a posição. As estatísticas são recalculadas a partir das partidas confirmadas, sem acumular contadores independentes.

Rodadas anteriores ficam recolhidas; a rodada atual abre por padrão. Resultados podem ser corrigidos com confirmação. Isso recalcula os rankings, mas não refaz confrontos já sorteados.

Ao terminar, o botão de compartilhamento prepara o pódio para envio pelo WhatsApp ou outro aplicativo instalado.

## Persistência e migração

Os dados antigos de nomes, duplas e placares são preservados e migrados. Resultados oficiais são recalculados conforme a nova regra. Como os dados antigos não indicam quem eliminou quem, esses placares mantêm os totais por dupla, mas não inventam estatísticas de jogadores. A interface informa essa limitação; editar e confirmar a partida com as eliminações reais substitui o placar antigo e completa o Ranking Killer.

O JSON atual é gravado atomicamente em uma única chave. Os dados legados originais não são apagados. Falhas de leitura são exibidas sem substituir o conteúdo salvo. Falhas de gravação são informadas sem atualizar o estado da tela. Ao criar outro campeonato, o atual é copiado para uma chave local de arquivo; a consulta visual ao histórico não faz parte desta versão. Essa cópia local não substitui um backup externo e pode ser perdida ao desinstalar ou limpar os dados do aplicativo.

## Validação e compilação

No PowerShell, com JAVA_HOME apontando para o JBR do Android Studio e GRADLE_USER_HOME para o cache do usuário:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

APK de teste: `app/build/outputs/apk/debug/app-debug.apk`.

Testes cobrem placares oficiais, pontuação, eliminações, correções, restrições de decks, sorteios, limite de rodadas, bloqueio após início, desempates, posições compartilhadas, migração e persistência.

O applicationId permanece `com.campeonato.duplas`. A versão foi elevada para 1.1 (versionCode 2). O APK debug usa a chave de desenvolvimento; para atualizar uma instalação release, gere a versão assinada com a mesma Keystore usando `build-release.ps1`, sem colocar as senhas no código.
