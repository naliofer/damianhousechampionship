package com.campeonato.duplas
import com.campeonato.duplas.model.*
import com.campeonato.duplas.domain.ChampionshipEngine as E
import com.campeonato.duplas.data.ChampionshipCodec
import org.junit.Assert.*
import org.junit.Test
class WalkoverTest {
    private fun state(): Championship {
        val players = (1..12).map { Player("p$it", "Player $it", "Deck") }
        val teams = players.chunked(2).map { Team(it[0].name,it[1].name,it[0].id,it[1].id) }
        return Championship(players=players,teams=teams,started=true,created=true,teamsConfirmed=true,
            matches=listOf(Match(1,0,1),Match(1,2,3),Match(1,4,5),Match(2,0,2),Match(2,1,4),Match(2,3,5)))
    }
    @Test fun futureRoundsAndOldSnapshotsRemainCompatible() {
        var current = state().copy(matches=state().matches.take(3))
        current=E.setWalkovers(current,0,setOf("p1","p2"))
        for (index in current.matches.indices) current=E.confirmMatch(current,index,emptyList())
        val next=E.nextRound(current)
        assertTrue(next.matches.filter { it.round==2 }.all { it.walkovers.isEmpty() })
        assertEquals(current.players,next.players)
        val raw=org.json.JSONObject(ChampionshipCodec.encode(current))
        val matches=raw.getJSONArray("matches")
        for (i in 0 until matches.length()) matches.getJSONObject(i).remove("walkovers")
        assertTrue(ChampionshipCodec.decode(raw.toString()).matches.all { it.walkovers.isEmpty() })
    }
    @Test fun individualAndMultipleWalkoversAreLocalAndReversible() {
        val original=state()
        for (ids in listOf(setOf("p1"),setOf("p1","p2"))) {
            val next=E.setWalkovers(original,0,ids)
            assertEquals(ids,next.matches[0].walkovers)
            assertEquals(original.matches[1],next.matches[1])
            assertEquals(E.killerRanking(original),E.killerRanking(next))
            assertEquals(next,ChampionshipCodec.decode(ChampionshipCodec.encode(next)))
            assertEquals(original.players,next.players)
            assertEquals(original.teams,next.teams)
            assertEquals(original.matches[0],next.matches[0].copy(walkovers=emptySet()))
            assertEquals(E.standings(original.matches,6),E.standings(next.matches,6))
            assertEquals(original,E.setWalkovers(next,0,emptySet()))
        }
    }
}
