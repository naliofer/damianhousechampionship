package com.campeonato.duplas.model

data class Player(val id: String, val name: String = "", val deck: String = "")
data class Team(val personA: String, val personB: String, val playerA: String = "", val playerB: String = "") {
    val playerIds: List<String> get() = listOf(playerA, playerB)
}
data class Elimination(val killerId: String, val victimId: String)
data class Match(
    val round: Int, val a: Int, val b: Int, val result: Int = -1,
    val lossesA: Int = 0, val lossesB: Int = 0,
    val eliminations: List<Elimination> = emptyList(),
    // Legacy totals have no attribution to individual players.
    val legacyScore: Boolean = false
)
data class Championship(
    val name: String = "Damian House - Champ", val rounds: Int = 4,
    val players: List<Player> = (1..12).map { Player("player-$it") },
    val teams: List<Team> = emptyList(), val matches: List<Match> = emptyList(),
    val teamsConfirmed: Boolean = false, val started: Boolean = false, val created: Boolean = false
)
data class Standing(
    val team: Int, val points: Double, val wins: Int, val draws: Int,
    val individualLosses: Int, val buchholz: Double,
    val losses: Int = 0, val individualWins: Int = 0, val position: Int = 0
)
data class KillerStanding(val player: Player, val team: Int, val kills: Int, val deaths: Int, val position: Int = 0) {
    val balance: Int get() = kills - deaths
}
data class ChampionshipSummary(
    val teamCount: Int, val currentRound: Int, val maxRounds: Int,
    val playedMatches: Int, val totalMatches: Int, val nextMatches: List<Match>
)
