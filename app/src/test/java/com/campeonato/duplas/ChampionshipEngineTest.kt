package com.campeonato.duplas

import com.campeonato.duplas.domain.ChampionshipEngine as Engine
import com.campeonato.duplas.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ChampionshipEngineTest {
    private fun players() = (0 until 12).map { Player("p$it", "Jogador $it", "Deck ${it % 4}") }
    private fun state(): Championship {
        val people = players()
        val teams = people.chunked(2).map { Team(it[0].name, it[1].name, it[0].id, it[1].id) }
        return Championship(players = people, teams = teams, teamsConfirmed = true, started = true, created = true)
    }
    private fun score(a: Int, b: Int) = Match(1, 0, 1, lossesA = b, lossesB = a)
    private fun confirmed(a: Int, b: Int, teamA: Int = 0, teamB: Int = 1) = score(a,b).copy(a = teamA, b = teamB, result = Engine.derivedResult(score(a,b)), legacyScore = true)
    private fun expectInvalid(action: () -> Unit) {
        try { action(); fail("Operação inválida aceita") } catch (_: IllegalArgumentException) { }
    }

    @Test fun officialResultForEveryPossibleScore() {
        for (a in 0..2) for (b in 0..2) {
            val expected = when { a == 2 && b < 2 -> 0; b == 2 && a < 2 -> 1; else -> 2 }
            assertEquals("$a x $b", expected, Engine.derivedResult(score(a,b)))
        }
    }
    @Test fun pointsAndEliminationsCountEvenInDraws() {
        val ranking = Engine.standings(listOf(confirmed(1,0), confirmed(2,1,0,2)), 3)
        val a = ranking.first { it.team == 0 }
        assertEquals(1.5, a.points, 0.0)
        assertEquals(1, a.wins); assertEquals(1, a.draws); assertEquals(0, a.losses)
        assertEquals(3, a.individualWins); assertEquals(1, a.individualLosses)
        assertEquals(0.5, ranking.first { it.team == 1 }.points, 0.0)
        assertEquals(0.0, ranking.first { it.team == 2 }.points, 0.0)
    }
    @Test fun individualKillsDeathsAndCorrectionReplaceOldEvents() {
        val initial = state().copy(matches = listOf(Match(1,0,1)))
        val events = listOf(Elimination("p0","p2"), Elimination("p0","p3"), Elimination("p2","p1"))
        val saved = Engine.confirmMatch(initial, 0, events)
        assertEquals(0, saved.matches[0].result) // User-confirmed rule: 2x1 is victory.
        val killer = Engine.killerRanking(saved).first()
        assertEquals("p0", killer.player.id); assertEquals(2, killer.kills); assertEquals(0, killer.deaths)
        val opponent = Engine.killerRanking(saved).first { it.player.id == "p2" }
        assertEquals(1, opponent.kills); assertEquals(1, opponent.deaths)
        val corrected = Engine.confirmMatch(saved, 0, listOf(Elimination("p3", "p0")))
        assertEquals(2, corrected.matches[0].result)
        assertEquals(0, Engine.killerRanking(corrected).first { it.player.id == "p0" }.kills)
        assertEquals(1, Engine.killerRanking(corrected).sumOf { it.kills })
        assertEquals(1, Engine.killerRanking(corrected).sumOf { it.deaths })
        assertEquals(0.5, Engine.standings(corrected.matches, 6).first { it.team == 0 }.points, 0.0)
        val again = Engine.confirmMatch(corrected, 0, listOf(Elimination("p3", "p0")))
        assertEquals(Engine.killerRanking(corrected), Engine.killerRanking(again))
    }
    @Test fun rejectsSelfPartnerRepeatedVictimAndForeignPlayer() {
        val s = state().copy(matches = listOf(Match(1,0,1)))
        listOf(
            listOf(Elimination("p0","p0")), listOf(Elimination("p0","p1")),
            listOf(Elimination("p0","p2"), Elimination("p1","p2")), listOf(Elimination("unknown","p2"))
        ).forEach { events -> expectInvalid { Engine.confirmMatch(s,0,events) } }
    }
    @Test fun deckNormalizationAndCompletePairing() {
        val people = (0 until 12).map { Player("p$it", "P$it", if (it < 6) "  Blue   Eyes " else "Dark Magician") }
        assertEquals("blue eyes", Engine.normalizedDeck(" BLUE   eyes "))
        repeat(40) { seed ->
            val pairs = Engine.drawTeams(people, Random(seed))
            assertEquals(6, pairs.size)
            assertEquals(12, pairs.flatMap { it.playerIds }.distinct().size)
            assertTrue(Engine.validTeams(Championship(players = people, teams = pairs)))
        }
    }
    @Test fun impossibleDeckDistributionAndMissingFieldsRejected() {
        val people = (0 until 12).map { Player("p$it", "P$it", if (it < 7) "Deck A" else "Deck B") }
        expectInvalid { Engine.drawTeams(people) }
        expectInvalid { Engine.drawTeams(players().map { it.copy(deck = " ") }) }
        expectInvalid { Engine.drawTeams(players().map { it.copy(name = "") }) }
        expectInvalid { Engine.drawTeams(players().dropLast(2)) }
    }
    @Test fun pairsAreRandomizedAcrossSeeds() {
        val variants = (0..10).map { Engine.drawTeams(players(), Random(it)).map { t -> t.playerIds.toSet() }.toSet() }.toSet()
        assertTrue(variants.size > 1)
    }
    @Test fun fiveRoundsHaveNoRepeatedOpponentsAndOneGamePerTeam() {
        // Use all draws to exercise full round-robin generation without score bias.
        var history = emptyList<Match>()
        for (round in 1..5) {
            val pairs = requireNotNull(Engine.makePairs(history, 6, Random(round)))
            assertEquals(6, pairs.flatMap { listOf(it.first,it.second) }.distinct().size)
            history = history + pairs.map { Match(round,it.first,it.second,2,legacyScore = true) }
        }
        assertEquals(15, history.map { setOf(it.a,it.b) }.distinct().size)
        assertNull(Engine.makePairs(history,6))
        assertNull(Engine.makePairs(emptyList(),3))
    }
    @Test fun swissMatchingMinimizesScoreDifferences() {
        val history = listOf(confirmed(2,0,0,1),confirmed(2,0,2,3),confirmed(2,0,4,5))
        val pairs = Engine.makePairs(history,6,Random(3))!!
        val scores = Engine.standings(history,6).associate { it.team to it.points }
        assertEquals(1.0, pairs.sumOf { kotlin.math.abs(scores.getValue(it.first) - scores.getValue(it.second)) }, 0.0)
        assertTrue(pairs.none { pair -> history.any { setOf(it.a,it.b) == setOf(pair.first,pair.second) } })
    }
    @Test fun configuredRoundsAndUnfinishedGamesAreRespected() {
        var s = Engine.nextRound(state().copy(rounds = 1))
        expectInvalid { Engine.nextRound(s) }
        s.matches.indices.forEach { s = Engine.confirmMatch(s,it,emptyList()) }
        expectInvalid { Engine.nextRound(s) }
        expectInvalid { Engine.nextRound(state().copy(started = false)) }
        assertEquals(3,Engine.summary(s.matches,6,1).totalMatches)
        assertEquals(5,Engine.maxRounds(6))
    }
    @Test fun cannotStartWithoutValidConfirmedTeamsAndValidRounds() {
        val s = state().copy(started = false)
        assertTrue(Engine.start(s).started)
        expectInvalid { Engine.start(s.copy(teamsConfirmed = false)) }
        expectInvalid { Engine.start(s.copy(rounds = 6)) }
        expectInvalid { Engine.start(s.copy(players = s.players.map { it.copy(deck = "same") })) }
    }
    @Test fun configurationAndParticipantsAreLockedAfterStart() {
        expectInvalid { Engine.configure(state(), "Novo nome", 5) }
        expectInvalid { Engine.updatePlayers(state(), players().dropLast(4)) }
        val draft = state().copy(started = false)
        assertEquals(5, Engine.configure(draft, "Teste", 5).rounds)
        expectInvalid { Engine.configure(draft, "Teste", 6) }
        val edited = Engine.updatePlayers(draft, players().dropLast(4))
        assertTrue(edited.teams.isEmpty())
        assertFalse(edited.teamsConfirmed)
    }
    @Test fun reportsWhenRemainingOpponentsCannotFormACompleteRound() {
        // Remaining edges form two odd triangles; every team has opponents but no perfect matching exists.
        val used = (0..2).flatMap { a -> (3..5).map { b -> Match(1, a, b, 2, legacyScore = true) } }
        assertNull(Engine.makePairs(used, 6))
    }
    @Test fun standingsUseWinsThenDeathsThenKills() {
        val games = listOf(confirmed(2,0,0,1),confirmed(0,2,0,2),confirmed(0,0,3,4),confirmed(0,0,3,5))
        // Team 0 and team 3 have one point; the win takes precedence over fewer deaths.
        val rank = Engine.standings(games,6)
        assertTrue(rank.indexOfFirst { it.team == 0 } < rank.indexOfFirst { it.team == 3 })
        val draws = Engine.standings(listOf(confirmed(1,0,0,1),confirmed(0,0,2,3)),4)
        assertTrue(draws.indexOfFirst { it.team == 0 } < draws.indexOfFirst { it.team == 2 })
    }
    @Test fun headToHeadBreaksAnOtherwiseEqualTwoTeamTie() {
        val games = listOf(confirmed(2,0,0,1),confirmed(0,2,0,2),confirmed(2,0,1,3),confirmed(0,0,2,3))
        val rank = Engine.standings(games,4)
        assertTrue(rank.indexOfFirst { it.team == 0 } < rank.indexOfFirst { it.team == 1 })
    }
    @Test fun killerTiesSharePositionsAndLegacyScoresAreNotInvented() {
        val s = state().copy(matches = listOf(Match(1,0,1)))
        val withKills = Engine.confirmMatch(s,0,listOf(Elimination("p0","p2"),Elimination("p1","p3")))
        val rank = Engine.killerRanking(withKills)
        assertEquals(1, rank.first { it.player.id == "p0" }.position)
        assertEquals(1, rank.first { it.player.id == "p1" }.position)
        assertEquals(0, Engine.killerRanking(s.copy(matches = listOf(confirmed(2,0)))).sumOf { it.kills })
    }
}
