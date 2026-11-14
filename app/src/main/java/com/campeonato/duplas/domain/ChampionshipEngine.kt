package com.campeonato.duplas.domain

import com.campeonato.duplas.model.*
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

object ChampionshipEngine {
    fun teamLabel(index: Int, teams: List<Team>): String = teams.getOrNull(index)?.let {
        listOf(it.personA.trim(), it.personB.trim()).filter(String::isNotEmpty).joinToString(" + ")
    }?.ifBlank { "Dupla ${index + 1}" } ?: "Dupla ${index + 1}"

    fun normalizedDeck(deck: String): String = deck.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
    fun maxRounds(teamCount: Int): Int = (teamCount - 1).coerceAtLeast(1)
    fun derivedResult(match: Match): Int = when {
        match.lossesB == 2 && match.lossesA < 2 -> 0
        match.lossesA == 2 && match.lossesB < 2 -> 1
        else -> 2
    }

    fun participantError(players: List<Player>): String? = when {
        players.size !in 4..24 || players.size % 4 != 0 -> "Cadastre 4 a 24 participantes, em múltiplos de 4, para formar um número par de duplas."
        players.map { it.id }.distinct().size != players.size -> "Existem participantes duplicados."
        players.any { it.name.isBlank() } -> "Preencha o nome de todos os participantes."
        players.any { normalizedDeck(it.deck).isBlank() } -> "O deck é obrigatório para todos os participantes."
        else -> null
    }

    // Exhaustive backtracking with randomized traversal: cannot miss an existing pairing.
    fun drawTeams(players: List<Player>, random: Random = Random.Default): List<Team> {
        require(participantError(players) == null) { participantError(players)!! }
        fun search(remaining: List<Player>): List<Team>? {
            if (remaining.isEmpty()) return emptyList()
            val first = remaining.first()
            for (other in remaining.drop(1).shuffled(random)) {
                if (normalizedDeck(first.deck) == normalizedDeck(other.deck)) continue
                val tail = search(remaining.filter { it.id != first.id && it.id != other.id })
                if (tail != null) return listOf(Team(first.name, other.name, first.id, other.id)) + tail
            }
            return null
        }
        // A deck present in more than half the field makes a complete pairing impossible.
        require(players.groupingBy { normalizedDeck(it.deck) }.eachCount().values.max() <= players.size / 2) {
            "Não é possível formar todas as duplas: mais da metade dos participantes usa o mesmo deck."
        }
        return requireNotNull(search(players.shuffled(random))) { "Não há combinação de duplas com decks diferentes." }.shuffled(random)
    }

    fun validTeams(state: Championship): Boolean {
        if (participantError(state.players) != null || state.teams.size * 2 != state.players.size) return false
        val ids = state.teams.flatMap { it.playerIds }
        return ids.toSet() == state.players.map { it.id }.toSet() && ids.distinct().size == ids.size &&
            state.teams.all { team ->
                val pair = state.players.filter { it.id in team.playerIds }
                pair.size == 2 && normalizedDeck(pair[0].deck) != normalizedDeck(pair[1].deck)
            }
    }

    fun start(state: Championship): Championship {
        require(state.created && !state.started) { "O campeonato já começou ou ainda não foi criado." }
        require(state.teamsConfirmed && validTeams(state)) { "Confirme duplas válidas antes de iniciar." }
        require(state.rounds in 1..maxRounds(state.teams.size)) { "Quantidade de rodadas inválida." }
        return state.copy(started = true)
    }

    fun configure(state: Championship, name: String, rounds: Int): Championship {
        require(!state.started) { "A configuração está bloqueada após o início do campeonato." }
        require(name.isNotBlank()) { "Informe o nome do campeonato." }
        require(rounds in 1..maxRounds(state.players.size / 2)) { "Quantidade de rodadas inválida para o número de duplas." }
        return state.copy(name = name.trim(), rounds = rounds, created = true)
    }

    fun updatePlayers(state: Championship, players: List<Player>): Championship {
        require(!state.started) { "Os participantes estão bloqueados após o início do campeonato." }
        require(players.size <= 24 && players.map { it.id }.distinct().size == players.size)
        return state.copy(players = players, teams = emptyList(), teamsConfirmed = false)
    }

    // Minimum-cost perfect matching, memoized by remaining team bitmask.
    fun makePairs(previous: List<Match>, teamCount: Int, random: Random = Random.Default): List<Pair<Int, Int>>? {
        if (teamCount !in 2..12 || teamCount % 2 != 0) return null
        val used = previous.map { setOf(it.a, it.b) }.toSet()
        val scores = standings(previous, teamCount).associate { it.team to it.points }
        val memo = mutableMapOf<Int, Double>()
        fun cost(mask: Int): Double {
            if (mask == 0) return 0.0
            memo[mask]?.let { return it }
            val a = Integer.numberOfTrailingZeros(mask)
            val rest = mask xor (1 shl a)
            var best = Double.POSITIVE_INFINITY
            for (b in a + 1 until teamCount) {
                if (rest and (1 shl b) == 0 || setOf(a, b) in used) continue
                best = minOf(best, abs(scores.getValue(a) - scores.getValue(b)) + cost(rest xor (1 shl b)))
            }
            memo[mask] = best
            return best
        }
        var mask = (1 shl teamCount) - 1
        if (!cost(mask).isFinite()) return null
        val pairs = mutableListOf<Pair<Int, Int>>()
        while (mask != 0) {
            val a = Integer.numberOfTrailingZeros(mask)
            val rest = mask xor (1 shl a)
            val b = (a + 1 until teamCount).filter {
                rest and (1 shl it) != 0 && setOf(a, it) !in used &&
                    abs(scores.getValue(a) - scores.getValue(it)) + cost(rest xor (1 shl it)) == cost(mask)
            }.random(random)
            pairs += a to b
            mask = rest xor (1 shl b)
        }
        return pairs.shuffled(random)
    }

    fun nextRound(state: Championship): Championship {
        require(state.started) { "Inicie o campeonato antes de sortear os confrontos." }
        require(state.matches.all { it.result != -1 }) { "Confirme todos os resultados antes de sortear." }
        val round = (state.matches.maxOfOrNull { it.round } ?: 0) + 1
        require(round <= state.rounds) { "Todas as rodadas configuradas já foram realizadas." }
        val pairs = requireNotNull(makePairs(state.matches, state.teams.size)) { "Não é possível gerar novos confrontos sem repetir adversários." }
        return state.copy(matches = state.matches + pairs.map { Match(round, it.first, it.second) })
    }

    fun validateEliminations(match: Match, teams: List<Team>, events: List<Elimination>) {
        require(match.a != match.b && match.a in teams.indices && match.b in teams.indices) { "Confronto inválido." }
        val a = teams[match.a].playerIds.toSet()
        val b = teams[match.b].playerIds.toSet()
        require(events.map { it.victimId }.distinct().size == events.size) { "Cada jogador pode ser eliminado apenas uma vez por confronto." }
        require(events.all { (it.killerId in a && it.victimId in b) || (it.killerId in b && it.victimId in a) }) {
            "Uma eliminação deve ocorrer entre adversários; não é permitido eliminar a si mesmo ou seu parceiro."
        }
    }

    fun confirmMatch(state: Championship, index: Int, events: List<Elimination>): Championship {
        require(state.started) { "O campeonato ainda não começou." }
        val old = state.matches[index]
        validateEliminations(old, state.teams, events)
        val updated = old.copy(
            lossesA = events.count { it.victimId in state.teams[old.a].playerIds },
            lossesB = events.count { it.victimId in state.teams[old.b].playerIds },
            eliminations = events.toList(), legacyScore = false
        )
        return state.copy(matches = state.matches.toMutableList().also { it[index] = updated.copy(result = derivedResult(updated)) })
    }

    fun standings(matches: List<Match>, teamCount: Int): List<Standing> {
        val confirmed = matches.filter { it.result != -1 }
        fun result(m: Match) = derivedResult(m)
        fun points(team: Int): Double = confirmed.filter { it.a == team || it.b == team }.sumOf {
            when { result(it) == 2 -> 0.5; result(it) == 0 && it.a == team || result(it) == 1 && it.b == team -> 1.0; else -> 0.0 }
        }
        val base = (0 until teamCount).map { i ->
            val games = confirmed.filter { it.a == i || it.b == i }
            val wins = games.count { result(it) == 0 && it.a == i || result(it) == 1 && it.b == i }
            val draws = games.count { result(it) == 2 }
            Standing(i, points(i), wins, draws,
                games.sumOf { if (it.a == i) it.lossesA else it.lossesB },
                games.sumOf { points(if (it.a == i) it.b else it.a) }, games.size - wins - draws,
                games.sumOf { if (it.a == i) it.lossesB else it.lossesA })
        }
        val primary = compareByDescending<Standing> { it.points }.thenByDescending { it.wins }
            .thenBy { it.individualLosses }.thenByDescending { it.individualWins }
        // Direct encounter applies to an entire tied group only when all its members have met.
        val ordered = base.groupBy { listOf(it.points, it.wins, it.individualLosses, it.individualWins) }
            .values.sortedWith { a, b -> primary.compare(a.first(), b.first()) }.flatMap { group ->
                val ids = group.map { it.team }.toSet()
                val internal = confirmed.filter { it.a in ids && it.b in ids }
                val applicable = ids.size > 1 && internal.map { setOf(it.a, it.b) }.distinct().size == ids.size * (ids.size - 1) / 2
                fun direct(i: Int) = if (!applicable) 0.0 else internal.filter { it.a == i || it.b == i }.sumOf {
                    when { result(it) == 2 -> 0.5; result(it) == 0 && it.a == i || result(it) == 1 && it.b == i -> 1.0; else -> 0.0 }
                }
                group.sortedWith(compareByDescending<Standing> { direct(it.team) }.thenByDescending { it.buchholz }.thenBy { it.team })
            }
        return ordered.mapIndexed { index, s -> s.copy(position = index + 1) }
    }

    fun killerRanking(state: Championship): List<KillerStanding> {
        val events = state.matches.filter { it.result != -1 && !it.legacyScore }.flatMap { it.eliminations }
        val sorted = state.players.map { player -> KillerStanding(player,
            state.teams.indexOfFirst { player.id in it.playerIds },
            events.count { it.killerId == player.id }, events.count { it.victimId == player.id })
        }.sortedWith(compareByDescending<KillerStanding> { it.kills }.thenBy { it.deaths }.thenByDescending { it.balance })
        var position = 1
        return sorted.mapIndexed { index, s ->
            if (index > 0 && (s.kills != sorted[index - 1].kills || s.deaths != sorted[index - 1].deaths)) position = index + 1
            s.copy(position = position)
        }
    }

    fun summary(matches: List<Match>, teamCount: Int, rounds: Int = minOf(4, maxRounds(teamCount))): ChampionshipSummary {
        val current = matches.maxOfOrNull { it.round } ?: 0
        return ChampionshipSummary(teamCount, current, rounds, matches.count { it.result != -1 }, rounds * (teamCount / 2),
            matches.filter { it.round == current && it.result == -1 })
    }
}
