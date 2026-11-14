package com.campeonato.duplas.data

import android.content.SharedPreferences
import com.campeonato.duplas.domain.ChampionshipEngine
import com.campeonato.duplas.model.*
import org.json.JSONArray
import org.json.JSONObject

/** One versioned JSON snapshot keeps setup and results atomic. Original legacy keys are retained. */
class ChampionshipRepository(private val prefs: SharedPreferences) {
    fun backupCurrent(state: Championship) {
        check(prefs.edit().putString("archive_${System.currentTimeMillis()}", ChampionshipCodec.encode(state)).commit()) {
            "Não foi possível preservar o campeonato anterior."
        }
    }
    fun load(): Championship {
        val snapshot = prefs.getString("championship_v2", null)
        return if (snapshot != null) ChampionshipCodec.decode(snapshot) else migrateLegacy()
    }

    fun save(state: Championship) {
        ChampionshipCodec.validate(state)
        check(prefs.edit().putString("championship_v2", ChampionshipCodec.encode(state)).commit()) {
            "Não foi possível salvar no celular. Tente novamente antes de sair."
        }
    }

    private fun migrateLegacy(): Championship {
        val rawTeams = prefs.getString("teams", null)
        val rawNames = prefs.getString("names", null)
        val rawMatches = prefs.getString("matches", null)
        if (rawTeams == null && rawNames == null && rawMatches == null) return Championship()
        val people = mutableListOf<Player>()
        val teams = if (rawTeams != null) {
            val arr = JSONArray(rawTeams)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                val a = Player("legacy-${i}-a", j.optString("a"))
                val b = Player("legacy-${i}-b", j.optString("b"))
                people += a; people += b
                Team(a.name, b.name, a.id, b.id)
            }
        } else {
            val arr = JSONArray(rawNames ?: "[]")
            (0 until arr.length()).map { i ->
                val a = Player("legacy-${i}-a", arr.getString(i))
                val b = Player("legacy-${i}-b")
                people += a; people += b
                Team(a.name, b.name, a.id, b.id)
            }
        }
        val arr = JSONArray(rawMatches ?: "[]")
        val matches = (0 until arr.length()).map { i ->
            val j = arr.getJSONObject(i)
            val match = Match(j.getInt("r"), j.getInt("a"), j.getInt("b"), j.getInt("v"),
                j.optInt("la").coerceIn(0, 2), j.optInt("lb").coerceIn(0, 2), legacyScore = true)
            if (match.result == -1) match else match.copy(result = ChampionshipEngine.derivedResult(match))
        }
        val rounds = maxOf(minOf(4, ChampionshipEngine.maxRounds(teams.size)), matches.maxOfOrNull { it.round } ?: 1)
        return Championship(rounds = rounds, players = people, teams = teams, matches = matches,
            teamsConfirmed = matches.isNotEmpty(), started = matches.isNotEmpty(), created = true)
            .also { ChampionshipCodec.validate(it) }
    }
}

object ChampionshipCodec {
    fun encode(state: Championship): String = JSONObject().apply {
        put("version", 2)
        put("name", state.name); put("rounds", state.rounds)
        put("created", state.created); put("started", state.started); put("confirmed", state.teamsConfirmed)
        put("players", JSONArray().apply { state.players.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("deck", it.deck)) } })
        put("teams", JSONArray().apply { state.teams.forEach { put(JSONObject().put("a", it.playerA).put("b", it.playerB)) } })
        put("matches", JSONArray().apply { state.matches.forEach { m -> put(JSONObject().apply {
            put("round", m.round); put("a", m.a); put("b", m.b); put("result", m.result)
            put("lossesA", m.lossesA); put("lossesB", m.lossesB); put("legacy", m.legacyScore)
            put("events", JSONArray().apply { m.eliminations.forEach { put(JSONObject().put("killer", it.killerId).put("victim", it.victimId)) } })
        }) } })
    }.toString()

    fun decode(raw: String): Championship {
        val root = JSONObject(raw)
        require(root.getInt("version") == 2) { "Versão de dados não suportada. Os dados salvos foram preservados." }
        val players = root.getJSONArray("players").mapObjects { Player(it.getString("id"), it.getString("name"), it.getString("deck")) }
        val byId = players.associateBy { it.id }
        val teams = root.getJSONArray("teams").mapObjects {
            val a = byId.getValue(it.getString("a")); val b = byId.getValue(it.getString("b"))
            Team(a.name, b.name, a.id, b.id)
        }
        val matches = root.getJSONArray("matches").mapObjects { m ->
            Match(m.getInt("round"), m.getInt("a"), m.getInt("b"), m.getInt("result"), m.getInt("lossesA"), m.getInt("lossesB"),
                m.getJSONArray("events").mapObjects { Elimination(it.getString("killer"), it.getString("victim")) }, m.getBoolean("legacy"))
        }
        return Championship(root.getString("name"), root.getInt("rounds"), players, teams, matches,
            root.getBoolean("confirmed"), root.getBoolean("started"), root.getBoolean("created")).also(::validate)
    }

    fun validate(state: Championship) {
        require(state.players.map { it.id }.distinct().size == state.players.size && state.players.all { it.id.isNotBlank() })
        require(state.rounds in 1..11)
        val ids = state.players.map { it.id }.toSet()
        require(state.teams.flatMap { it.playerIds }.all { it in ids })
        require(state.teams.flatMap { it.playerIds }.distinct().size == state.teams.size * 2)
        require(!state.started || state.teams.size * 2 == state.players.size)
        require(!state.started || state.teamsConfirmed)
        require(state.matches.isEmpty() || state.started)
        val pairs = state.matches.map { setOf(it.a, it.b) }
        require(pairs.distinct().size == pairs.size) { "Confrontos duplicados nos dados salvos." }
        state.matches.groupBy { it.round }.forEach { (_, games) ->
            require(games.size == state.teams.size / 2) { "Rodada incompleta nos dados salvos." }
            require(games.flatMap { listOf(it.a, it.b) }.distinct().size == games.size * 2)
        }
        val lastRound = state.matches.maxOfOrNull { it.round } ?: 0
        require(state.matches.map { it.round }.distinct().sorted() == (1..lastRound).toList())
        state.matches.forEach { m ->
            require(m.round in 1..state.rounds && m.a in state.teams.indices && m.b in state.teams.indices && m.a != m.b)
            require(m.lossesA in 0..2 && m.lossesB in 0..2 && m.result in -1..2)
            ChampionshipEngine.validateEliminations(m, state.teams, m.eliminations)
            if (!m.legacyScore) {
                require(m.lossesA == m.eliminations.count { it.victimId in state.teams[m.a].playerIds })
                require(m.lossesB == m.eliminations.count { it.victimId in state.teams[m.b].playerIds })
            }
            require(m.result == -1 || m.result == ChampionshipEngine.derivedResult(m))
        }
    }

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> = (0 until length()).map { transform(getJSONObject(it)) }
}
