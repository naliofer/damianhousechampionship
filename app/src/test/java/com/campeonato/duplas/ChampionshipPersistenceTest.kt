package com.campeonato.duplas

import android.content.SharedPreferences
import com.campeonato.duplas.data.ChampionshipCodec
import com.campeonato.duplas.data.ChampionshipRepository
import com.campeonato.duplas.domain.ChampionshipEngine as Engine
import com.campeonato.duplas.model.*
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class ChampionshipPersistenceTest {
    private class MemoryPreferences {
        val values = mutableMapOf<String, Any?>()
        var commitWorks = true
        private val pending = mutableMapOf<String, Any?>()
        private val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "putString", "putInt", "putBoolean" -> { pending[args!![0] as String] = args[1]; proxy }
                "commit" -> { if (commitWorks) { values.putAll(pending); pending.clear() }; commitWorks }
                "apply" -> { values.putAll(pending); pending.clear(); null }
                else -> throw UnsupportedOperationException(method.name)
            }
        } as SharedPreferences.Editor
        val prefs = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString", "getInt", "getBoolean" -> values[args!![0] as String] ?: args[1]
                "contains" -> values.containsKey(args!![0] as String)
                "edit" -> editor
                else -> throw UnsupportedOperationException(method.name)
            }
        } as SharedPreferences
    }
    private fun populated(): Championship {
        val players = (0..11).map { Player("p$it","João $it","Deck ${it % 3}") }
        val teams = Engine.drawTeams(players)
        val state = Engine.nextRound(Engine.start(Championship(name = "Campeonato sábado", rounds = 5, players = players, teams = teams, teamsConfirmed = true, created = true)))
        val match = state.matches[0]
        return Engine.confirmMatch(state,0,listOf(Elimination(teams[match.a].playerA,teams[match.b].playerA)))
    }
    @Test fun roundTripRetainsAllFieldsAndStatistics() {
        val original = populated()
        val decoded = ChampionshipCodec.decode(ChampionshipCodec.encode(original))
        assertEquals(original, decoded)
        assertEquals(Engine.standings(original.matches,6),Engine.standings(decoded.matches,6))
        assertEquals(Engine.killerRanking(original),Engine.killerRanking(decoded))
    }
    @Test fun repositoryReloadsFromANewInstanceAfterCorrection() {
        val memory = MemoryPreferences()
        val first = populated()
        ChampionshipRepository(memory.prefs).save(first)
        val reopened = ChampionshipRepository(memory.prefs).load()
        assertEquals(first,reopened)
        val corrected = Engine.confirmMatch(reopened,0,emptyList())
        ChampionshipRepository(memory.prefs).save(corrected)
        val afterRestart = ChampionshipRepository(memory.prefs).load()
        assertEquals(corrected,afterRestart)
        assertEquals(0,Engine.killerRanking(afterRestart).sumOf { it.kills })
    }
    @Test fun draftSetupIsSavedWithoutRequiringCompleteNamesOrDecks() {
        val memory = MemoryPreferences()
        val draft = Championship(created = true, players = listOf(Player("1","Ana",""),Player("2")))
        ChampionshipRepository(memory.prefs).save(draft)
        assertEquals(draft,ChampionshipRepository(memory.prefs).load())
    }
    @Test fun migratesOldScoresWithoutInventingKillsAndKeepsOriginalData() {
        val memory = MemoryPreferences()
        val teams = """[{"a":"Ana","b":"Bia"},{"a":"Caio","b":"Davi"}]"""
        memory.values["teams"] = teams
        memory.values["matches"] = """[{"r":1,"a":0,"b":1,"v":0,"la":0,"lb":1}]"""
        val repo = ChampionshipRepository(memory.prefs)
        val migrated = repo.load()
        assertEquals(2,migrated.matches[0].result) // Old 1x0 now officially a draw.
        assertEquals("Ana",migrated.players[0].name)
        assertTrue(migrated.matches[0].legacyScore)
        assertEquals(0,Engine.killerRanking(migrated).sumOf { it.kills })
        assertEquals(1,Engine.standings(migrated.matches,2).first { it.team == 0 }.individualWins)
        repo.save(migrated)
        assertEquals(teams,memory.values["teams"])
        assertEquals(migrated,ChampionshipRepository(memory.prefs).load())
    }
    @Test fun corruptDataIsNotSilentlyReplaced() {
        val memory = MemoryPreferences()
        memory.values["championship_v2"] = "broken json"
        try { ChampionshipRepository(memory.prefs).load(); fail("Dados corrompidos aceitos") } catch (_: Exception) { }
        assertEquals("broken json",memory.values["championship_v2"])
    }
    @Test fun duplicatedVictimAndInconsistentScoresCannotBeSaved() {
        val s = populated()
        val game = s.matches[0]
        val duplicated = game.copy(eliminations = game.eliminations + game.eliminations)
        try { ChampionshipCodec.validate(s.copy(matches = listOf(duplicated))); fail("Duplicação aceita") } catch (_: IllegalArgumentException) { }
        try { ChampionshipCodec.validate(s.copy(matches = listOf(game.copy(lossesA = 2)))); fail("Placar inconsistente aceito") } catch (_: IllegalArgumentException) { }
    }
    @Test fun failingDiskWriteIsReported() {
        val memory = MemoryPreferences()
        memory.commitWorks = false
        try { ChampionshipRepository(memory.prefs).save(Championship()); fail("Falha de gravação ignorada") } catch (_: IllegalStateException) { }
    }
    @Test fun newChampionshipBackupPreservesPreviousSnapshot() {
        val memory = MemoryPreferences()
        val current = populated()
        val repo = ChampionshipRepository(memory.prefs)
        repo.backupCurrent(current)
        repo.save(Championship())
        val backup = memory.values.filterKeys { it.startsWith("archive_") }.values.single() as String
        assertEquals(current,ChampionshipCodec.decode(backup))
    }
}
