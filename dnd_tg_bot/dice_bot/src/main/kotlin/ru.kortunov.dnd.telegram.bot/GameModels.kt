package ru.kortunov.dnd.telegram.bot.dnd_tg_bot


enum class DiceType(val sides: Int, val displayName: String) {
    D4(4, "d4"),
    D6(6, "d6"),
    D8(8, "d8"),
    D10(10, "d10"),
    D12(12, "d12"),
    D20(20, "d20"),
    D100(100, "d100")
}

data class Player(
        val userId: Long,
        val userName: String,
        var lastRoll: RollResult? = null
) {
    fun roll(diceType: DiceType, count: Int = 1): Int {
        val rollResult = BotUtils.roll(diceType)
        lastRoll = RollResult(diceType, rollResult, count)
        return rollResult
    }
}

data class RollResult(
        val diceType: DiceType,
        val result: Int,
        val count: Int
) {
    fun formatResult(): String {
        return "$result"
    }
}

data class GameSession(
        val code: String,
        val maxPlayers: Int,
        val creatorId: Long,
        val players: MutableMap<Long, Player> = mutableMapOf(),
        var isActive: Boolean = true
) {
    fun addPlayer(userId: Long, userName: String): Boolean {
        if (players.size >= maxPlayers) return false
        players[userId] = Player(userId, userName)
        return true
    }

    fun getPlayer(userId: Long): Player? = players[userId]

    fun hasPlayer(userId: Long): Boolean = players.containsKey(userId)

    fun getPlayersCount(): Int = players.size
}