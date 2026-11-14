package com.campeonato.duplas.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.campeonato.duplas.data.ChampionshipRepository
import com.campeonato.duplas.domain.ChampionshipEngine as Engine
import com.campeonato.duplas.model.*
import com.campeonato.duplas.ui.components.*
import com.campeonato.duplas.ui.theme.AppChampion
import com.campeonato.duplas.ui.theme.AppLoss
import com.campeonato.duplas.ui.theme.AppWin
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChampionshipApp(repository: ChampionshipRepository) {
    val loaded = remember { runCatching { repository.load() } }
    if (loaded.isFailure) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Não foi possível abrir os dados", style = MaterialTheme.typography.titleLarge)
                Text("Os dados locais foram preservados. Não será criado um campeonato por cima deles.")
                Text(loaded.exceptionOrNull()?.message ?: "Erro de leitura")
            }
        }
        return
    }
    var state by remember { mutableStateOf(loaded.getOrThrow()) }
    var draftName by rememberSaveable(state.created) { mutableStateOf(state.name) }
    var draftRounds by rememberSaveable(state.created) { mutableStateOf(state.rounds.toString()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var editMatch by rememberSaveable { mutableStateOf<Int?>(null) }
    var requestEdit by remember { mutableStateOf<Int?>(null) }
    var newChampionship by remember { mutableStateOf(false) }
    fun update(transform: (Championship) -> Championship) {
        try {
            val next = transform(state)
            repository.save(next)
            state = next
        } catch (e: Exception) { error = e.message ?: "Não foi possível salvar a alteração." }
    }
    val current = state.matches.maxOfOrNull { it.round } ?: 0
    val finished = current == state.rounds && state.matches.isNotEmpty() && state.matches.all { it.result != -1 }
    val standings = Engine.standings(state.matches, state.teams.size)
    val tabs = listOf("Cadastro", "Duplas", "Confrontos", "Classificação", "Ranking Killer")
    Scaffold(topBar = {
        Column {
            CenterAlignedTopAppBar(title = { Text(state.name, maxLines = 1) })
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                tabs.forEachIndexed { index, title -> Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) }) }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (tab) {
                0 -> {
                    item {
                        SectionTitle(if (state.created) "Configuração do campeonato" else "Criar campeonato", "Dados salvos automaticamente neste celular")
                        ChampionshipSettings(state, draftName, draftRounds, { draftName = it }, { draftRounds = it }) { name, rounds ->
                            update { Engine.configure(it, name, rounds) }
                        }
                    }
                    if (state.created) {
                        item {
                            SectionTitle("Participantes (${state.players.size})", "Padrão: 12 jogadores e 6 duplas. Cada jogador precisa de nome e deck.")
                            if (!state.started) Text("Editar participantes desfaz o sorteio e a confirmação das duplas.")
                        }
                        items(state.players, key = { it.id }) { player ->
                            PlayerEditor(player, !state.started, onChange = { updated ->
                                update { Engine.updatePlayers(it, it.players.map { p -> if (p.id == updated.id) updated else p }) }
                            }, onRemove = {
                                update { Engine.updatePlayers(it, it.players.filterNot { p -> p.id == player.id }) }
                            })
                        }
                        if (!state.started) item {
                            OutlinedButton(onClick = {
                                update { Engine.updatePlayers(it, it.players + Player(UUID.randomUUID().toString())) }
                            }, enabled = state.players.size < 24) { Text("Adicionar participante") }
                            Button(onClick = {
                                update { it.copy(teams = Engine.drawTeams(it.players), teamsConfirmed = false) }
                                if (state.teams.isNotEmpty()) tab = 1
                            }) { Text(if (state.teams.isEmpty()) "Sortear duplas" else "Refazer sorteio das duplas") }
                        }
                    }
                    item {
                        HorizontalDivider()
                        OutlinedButton(onClick = { newChampionship = true }, enabled = state.created) { Text("Novo campeonato") }
                    }
                }
                1 -> {
                    item { SectionTitle("Duplas", if (state.started) "Duplas confirmadas — campeonato iniciado" else "Confira integrantes e decks antes de iniciar") }
                    if (state.teams.isEmpty()) item { EmptyStateCard("Cadastre os participantes e sorteie as duplas na aba Cadastro.") }
                    items(state.teams.indices.toList()) { index ->
                        val team = state.teams[index]
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Dupla ${index + 1}", style = MaterialTheme.typography.titleMedium)
                                team.playerIds.forEach { id -> state.players.find { it.id == id }?.let { Text("${it.name.ifBlank { "Nome pendente" }} • ${it.deck.ifBlank { "Deck não informado" }}") } }
                            }
                        }
                    }
                    if (!state.started && state.teams.isNotEmpty()) item {
                        OutlinedButton(onClick = { update { it.copy(teams = Engine.drawTeams(it.players), teamsConfirmed = false) } }) { Text("Refazer sorteio") }
                        Button(onClick = {
                            update { require(Engine.validTeams(it)) { "Duplas inválidas. Confira nomes, decks e participantes." }; it.copy(teamsConfirmed = true) }
                        }, enabled = !state.teamsConfirmed) { Text(if (state.teamsConfirmed) "Duplas confirmadas" else "Confirmar duplas") }
                        Button(onClick = { update(Engine::start); if (state.started) tab = 2 }, enabled = state.teamsConfirmed) { Text("Iniciar campeonato") }
                    }
                }
                2 -> {
                    item {
                        SectionTitle("Confrontos", "$current de ${state.rounds} rodadas • adversários sem repetição")
                        Text("Vitória: 2×0, 0×2, 2×1 ou 1×2. Demais placares são empate, inclusive 2×2.")
                        Button(onClick = { update(Engine::nextRound) }, enabled = state.started && current < state.rounds && state.matches.all { it.result != -1 }) {
                            Text(if (current == 0) "Sortear 1ª rodada" else "Sortear próxima rodada")
                        }
                        if (!state.started) Text("Confirme as duplas e inicie o campeonato na aba Duplas.")
                        if (finished) Text("Campeonato encerrado", color = AppChampion)
                    }
                    if (state.matches.isEmpty()) item { EmptyStateCard("Nenhum confronto sorteado.") }
                    state.matches.groupBy { it.round }.toSortedMap().forEach { (round, games) ->
                        item(key = "round-$round") {
                            RoundAccordion(round, games, state, current) { match ->
                                val index = state.matches.indexOf(match)
                                if (match.result == -1) editMatch = index else requestEdit = index
                            }
                        }
                    }
                    item { Text("Corrigir uma rodada anterior recalcula as estatísticas, mas mantém os confrontos já sorteados.", style = MaterialTheme.typography.bodySmall) }
                }
                3 -> {
                    item { SectionTitle("Classificação geral", "Pontos → vitórias → menos mortes → kills → confronto direto → Buchholz") }
                    if (standings.isEmpty()) item { EmptyStateCard("A classificação ficará disponível após o sorteio das duplas.") }
                    items(standings, key = { it.team }) { standing ->
                        StandingCard(standing.position - 1, standing, Engine.teamLabel(standing.team, state.teams))
                    }
                    if (finished) item { SharePodium(state, standings) }
                    item { Text("Confronto direto é aplicado quando todas as duplas do grupo empatado já se enfrentaram.", style = MaterialTheme.typography.bodySmall) }
                }
                4 -> {
                    item {
                        SectionTitle("Ranking Killer", "Kills → menos mortes → saldo. Empates compartilham a posição.")
                        if (state.matches.any { it.legacyScore && it.result != -1 }) Text("Partidas antigas sem autoria das eliminações não entram no Ranking Killer. Edite-as para informar os registros individuais.")
                    }
                    if (!state.started) item { EmptyStateCard("Inicie o campeonato para acompanhar as estatísticas individuais.") }
                    else items(Engine.killerRanking(state), key = { it.player.id }) { standing ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (standing.position == 1) Text(if (finished) "Killer do Campeonato" else "Líder Killer • parcial", color = AppChampion)
                                Text("${standing.position}º • ${standing.player.name}", style = MaterialTheme.typography.titleMedium)
                                Text("${Engine.teamLabel(standing.team, state.teams)} • Deck: ${standing.player.deck.ifBlank { "Não informado" }}")
                                Text("${standing.kills} kills • ${standing.deaths} mortes • saldo ${standing.balance}")
                            }
                        }
                    }
                }
            }
        }
    }
    if (error != null) AlertDialog(onDismissRequest = { error = null }, title = { Text("Confira os dados") }, text = { Text(error!!) }, confirmButton = { TextButton(onClick = { error = null }) { Text("Entendi") } })
    requestEdit?.let { index -> AlertDialog(onDismissRequest = { requestEdit = null }, title = { Text("Corrigir resultado?") },
        text = { Text("Ao confirmar a correção, a classificação e o Ranking Killer serão recalculados. Os próximos confrontos já sorteados serão mantidos.") },
        confirmButton = { TextButton(onClick = { editMatch = index; requestEdit = null }) { Text("Corrigir") } },
        dismissButton = { TextButton(onClick = { requestEdit = null }) { Text("Cancelar") } }) }
    editMatch?.let { index -> MatchEditor(state, index, onDismiss = { editMatch = null }, onConfirm = { events ->
        val before = state
        update { Engine.confirmMatch(it, index, events) }
        if (state != before || error == null) editMatch = null
    }) }
    if (newChampionship) AlertDialog(onDismissRequest = { newChampionship = false }, title = { Text("Criar outro campeonato?") },
        text = { Text("O campeonato atual será substituído. Uma cópia dos dados atuais será mantida no armazenamento local; a consulta ao histórico ainda não está disponível.") },
        confirmButton = { TextButton(onClick = {
            try { repository.backupCurrent(state); update { Championship() }; tab = 0; newChampionship = false }
            catch (e: Exception) { error = e.message; newChampionship = false }
        }) { Text("Criar novo") } }, dismissButton = { TextButton(onClick = { newChampionship = false }) { Text("Cancelar") } })
}

@Composable
private fun ChampionshipSettings(state: Championship, name: String, rounds: String, onNameChange: (String) -> Unit, onRoundsChange: (String) -> Unit, onSave: (String, Int) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    val max = Engine.maxRounds(state.players.size / 2)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, onNameChange, label = { Text("Nome do campeonato") }, enabled = !state.started, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(rounds, { onRoundsChange(it.filter(Char::isDigit).take(2)) }, label = { Text("Rodadas (1 a $max)") }, enabled = !state.started, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (state.started) Text("Rodadas bloqueadas após o início: ${state.rounds}.")
            else Button(onClick = {
                val count = rounds.toIntOrNull()
                if (name.isBlank()) error = "Informe o nome do campeonato."
                else if (count == null || count !in 1..max) error = "Escolha de 1 a $max rodadas para a quantidade atual de participantes."
                else { error = null; onSave(name.trim(), count) }
            }) { Text(if (state.created) "Salvar configuração" else "Criar campeonato") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun PlayerEditor(player: Player, enabled: Boolean, onChange: (Player) -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(player.name, { onChange(player.copy(name = it)) }, label = { Text("Nome do participante") }, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(player.deck, { onChange(player.copy(deck = it)) }, label = { Text("Deck (obrigatório)") }, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (enabled) TextButton(onClick = onRemove) { Text("Remover participante") }
        }
    }
}

@Composable
private fun RoundAccordion(round: Int, games: List<Match>, state: Championship, current: Int, onEdit: (Match) -> Unit) {
    val completed = games.all { it.result != -1 }
    var expanded by rememberSaveable(round, current) { mutableStateOf(round == current) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Rodada $round • ${if (completed) "Finalizada" else "Em andamento"} ${if (expanded) "▴" else "▾"}",
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp))
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    games.forEach { game ->
                        HorizontalDivider()
                        val a = Engine.teamLabel(game.a, state.teams)
                        val b = Engine.teamLabel(game.b, state.teams)
                        val official = if (game.result == -1) -1 else Engine.derivedResult(game)
                        Text("$a × $b", style = MaterialTheme.typography.titleMedium)
                        Text("Placar: ${game.lossesB} × ${game.lossesA}", style = MaterialTheme.typography.titleLarge)
                        Text(when (official) { 0 -> "Vitória: $a"; 1 -> "Vitória: $b"; 2 -> "Empate"; else -> "Resultado pendente" }, color = when (official) { 0, 1 -> AppWin; 2 -> AppChampion; else -> MaterialTheme.colorScheme.onSurface })
                        if (official in 0..1) Text("Derrota: ${if (official == 0) b else a}", color = AppLoss)
                        game.eliminations.forEach { event -> Text("${state.players.first { it.id == event.killerId }.name} eliminou ${state.players.first { it.id == event.victimId }.name}") }
                        if (game.legacyScore) Text("Placar antigo: autoria das eliminações não informada.")
                        OutlinedButton(onClick = { onEdit(game) }) { Text(if (game.result == -1) "Registrar eliminações e resultado" else "Corrigir resultado") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchEditor(state: Championship, index: Int, onDismiss: () -> Unit, onConfirm: (List<Elimination>) -> Unit) {
    val match = state.matches[index]
    var events by rememberSaveable(index, stateSaver = listSaver<List<Elimination>, String>(
        save = { it.flatMap { event -> listOf(event.killerId, event.victimId) } },
        restore = { it.chunked(2).map { pair -> Elimination(pair[0], pair[1]) } }
    )) { mutableStateOf(match.eliminations) }
    var discard by remember { mutableStateOf(false) }
    val players = (state.teams[match.a].playerIds + state.teams[match.b].playerIds).map { id -> state.players.first { it.id == id } }
    val draft = match.copy(lossesA = events.count { it.victimId in state.teams[match.a].playerIds }, lossesB = events.count { it.victimId in state.teams[match.b].playerIds })
    val result = Engine.derivedResult(draft)
    fun close() { if (events != match.eliminations) discard = true else onDismiss() }
    AlertDialog(onDismissRequest = { close() }, title = { Text(if (match.result == -1) "Registrar partida" else "Corrigir partida") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Placar: ${draft.lossesB} × ${draft.lossesA}", style = MaterialTheme.typography.titleLarge)
                Text(when (result) { 0 -> "Vitória: ${Engine.teamLabel(match.a, state.teams)}"; 1 -> "Vitória: ${Engine.teamLabel(match.b, state.teams)}"; else -> "Empate" }, color = if (result == 2) AppChampion else AppWin)
                Text("Para cada jogador, informe quem o eliminou ou marque que não foi eliminado. O placar é calculado pelos registros.")
                if (match.legacyScore) Text("Ao salvar, os registros abaixo substituirão o placar antigo.")
            }
            items(players, key = { it.id }) { victim ->
                val opponentTeam = if (victim.id in state.teams[match.a].playerIds) state.teams[match.b] else state.teams[match.a]
                val selected = events.find { it.victimId == victim.id }?.killerId
                Column {
                    HorizontalDivider()
                    Text(victim.name, style = MaterialTheme.typography.titleMedium)
                    Text("Deck: ${victim.deck.ifBlank { "Não informado" }}")
                    Row(Modifier.fillMaxWidth().clickable { events = events.filterNot { it.victimId == victim.id } }) {
                        RadioButton(selected == null, onClick = { events = events.filterNot { it.victimId == victim.id } })
                        Text("Não foi eliminado", Modifier.padding(top = 12.dp))
                    }
                    opponentTeam.playerIds.forEach { id ->
                        val killer = state.players.first { it.id == id }
                        val choose = { events = events.filterNot { it.victimId == victim.id } + Elimination(id, victim.id) }
                        Row(Modifier.fillMaxWidth().clickable { choose() }) {
                            RadioButton(selected == id, onClick = { choose() })
                            Text("Eliminado por ${killer.name}", Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { onConfirm(events) }) { Text(if (match.result == -1) "Confirmar resultado" else "Confirmar correção") } }, dismissButton = { TextButton(onClick = { close() }) { Text("Cancelar") } })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Descartar alterações?") }, text = { Text("Os registros ainda não confirmados serão descartados.") }, confirmButton = { TextButton(onClick = onDismiss) { Text("Descartar") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Continuar editando") } })
}

@Composable
private fun SharePodium(state: Championship, standings: List<Standing>) {
    val context = LocalContext.current
    var error by remember { mutableStateOf(false) }
    Button(onClick = {
        val text = buildString {
            appendLine("🏆 ${state.name}"); appendLine("Resultado final — melhores colocados"); appendLine()
            standings.take(3).forEachIndexed { index, s ->
                appendLine("${listOf("🥇", "🥈", "🥉")[index]} ${index + 1}º: ${Engine.teamLabel(s.team, state.teams)} — ${String.format(Locale.forLanguageTag("pt-BR"), "%.1f", s.points)} ponto(s)")
            }
        }
        try { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Compartilhar resultado")) }
        catch (_: ActivityNotFoundException) { error = true }
    }, modifier = Modifier.fillMaxWidth()) { Text("Compartilhar melhores colocados") }
    if (error) Text("Nenhum aplicativo disponível para compartilhar.", color = MaterialTheme.colorScheme.error)
}
