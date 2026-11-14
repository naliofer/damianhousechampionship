# Camp D. House — PWA

Módulo independente em `web/`. O Android permanece inalterado. Não depende de servidor de dados, CDN ou serviços pagos. Requer Node.js 22+ para os comandos locais.

## Testar, compilar e executar

```powershell
cd web
npm test
npm run build
npm start
```

Abra http://localhost:8080 no computador. O build gera `web/dist`. Não abra via file://: módulos e Service Worker precisam de localhost ou HTTPS. O servidor local não é um endereço acessível pelo iPhone.

## Publicar no GitHub Pages

1. Quando desejar, envie o projeto a um repositório GitHub. Nenhum commit ou publicação foi realizado por este trabalho.
2. Em Settings → Pages → Source, escolha GitHub Actions.
3. O arquivo `.github/workflows/pages.yml` testa, compila e publica apenas `web/dist` em pushes na branch `main` ou manualmente pelo botão Run workflow em Actions. Ajuste a branch se necessário.
4. Abra a URL HTTPS apresentada pelo job, normalmente `https://USUARIO.github.io/REPOSITORIO/`.

Os caminhos relativos suportam a subpasta do repositório. Arquivos Android, APKs, Keystore e credenciais não são publicados pelo workflow.

## Instalar no iPhone

1. Abra a URL HTTPS no Safari.
2. Compartilhar → Adicionar à Tela de Início → Adicionar. Se houver a opção “Abrir como App”, deixe habilitada.
3. Abra **Camp D. House** pelo ícone e aguarde “Pronto para uso offline”.
4. Cadastre seu campeonato na versão instalada; depois teste fechar e reabrir sem internet.

Manifest standalone, ícone PNG 512×512, metadados Apple, safe areas e campos de 16px foram incluídos. Alvo: Safari/iOS 16.4+. Não houve teste em iPhone físico; a compatibilidade no aparelho precisa ser conferida após a publicação.

## Dados e offline

IndexedDB: banco `camp-d-house`, store `data`, chave `current`. Os arquivos do app são pré-armazenados pelo Service Worker. Após a primeira carga completa, cadastros, sorteios e resultados funcionam offline.

Exporte regularmente o backup JSON pela aba Cadastro e guarde em Arquivos. Restaurar substitui o campeonato atual mediante confirmação. Limpar dados do navegador, desinstalar ou restrições de armazenamento do sistema podem remover os dados. Safari e a PWA instalada podem ter armazenamentos separados; use exportação/restauração para transferir dados. Não há sincronização com o Android.

Atualizações: aumente `CACHE` em `sw.js`, publique e feche todas as janelas antigas para a nova versão assumir. O cache não contém os dados de IndexedDB.

## Regras

`engine.js` é uma portabilidade das regras do `ChampionshipEngine.kt`, mantendo o Android intacto e dispensando Kotlin/JS para hospedagem estática.

- 4 a 24 jogadores em múltiplos de 4; padrão 12. Nome e deck obrigatórios.
- Busca completa de duplas com ordem aleatória, normalizando caixa e espaços dos decks.
- Rodadas: padrão 4, máximo de duplas menos uma. Cadastro e configuração bloqueados após início.
- Sistema suíço minimiza diferenças de pontuação, sem repetir adversários ou duplas na mesma rodada. Informa impossibilidade de nova rodada completa.
- 2×0 e 2×1 valem vitória, assim como seus inversos. Demais placares, inclusive 2×2, são empate.
- Vitória = 1; empate = 0,5; derrota = 0. Cada eliminação é vinculada a autor e vítima. Apenas adversários podem eliminar, cada vítima uma vez por partida.
- Geral: pontos, vitórias, menos mortes, mais kills, confronto direto quando todo o grupo empatado se enfrentou e Buchholz.
- Killer: kills, menos mortes e saldo, com posições compartilhadas nos empates.
- Correções substituem eventos e recalculam estatísticas, sem refazer sorteios anteriores.
- Histórico expansível e pódio compartilhável pelo menu nativo ou cópia de texto.

## Conferência manual no iPhone

Cadastre os jogadores/decks; sorteie e confirme; confira bloqueio do cadastro; registre 2×1 e 1×0; corrija uma partida; confira classificação e Killer; consulte rodada anterior; feche e reabra; use modo avião; exporte e restaure backup; confira teclado, rolagem e compartilhamento pelo WhatsApp.

Referências: [WebKit — Tela de Início](https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/) e [GitHub Pages — workflows](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages).
