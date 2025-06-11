package ru.kortunov.dnd.telegram.bot.dnd_dice_tg_bot


import org.slf4j.LoggerFactory

object SessionManager {
    private val sessions = mutableMapOf<String, GameSession>()
    private val userSessions = mutableMapOf<Long, String>() // userId -> sessionCode
    val LOG = LoggerFactory.getLogger(javaClass.name)

    fun createSession(creatorId: Long, maxPlayers: Int): GameSession {
        var code: String
        do {
            code = generateSessionCode()
        } while (sessions.containsKey(code))

        val session = GameSession(code, maxPlayers, creatorId)
        sessions[code] = session
        userSessions[creatorId] = code

        LOG.info("Session created, sessions size: ${sessions.size}")
        return session
    }

    fun getSession(code: String): GameSession? {
        return sessions[code]
    }

    fun getUserSession(userId: Long): GameSession? {
        val sessionCode = userSessions[userId] ?: return null
        return sessions[sessionCode]
    }

    fun leaveSession(sessionCode: String, userId: Long) {
        val session = sessions[sessionCode] ?: return

        session.players.remove(userId)
        userSessions.remove(userId)

        // Если комната пустая, удаляем её
        if (session.players.isEmpty()) {
            sessions.remove(sessionCode)
        }
    }

    fun getAllPlayersInSession(sessionCode: String): List<Long> {
        val session = sessions[sessionCode] ?: return emptyList()
        return session.players.keys.toList()
    }

    private fun generateSessionCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6)
                .map { chars.random() }
                .joinToString("")
    }

//    fun joinSession(code: String, userId: Long, userName: String): JoinResult {
//        val session = sessions[code] ?: return JoinResult.SESSION_NOT_FOUND
//
//        if (!session.isActive) return JoinResult.SESSION_INACTIVE
//
//        if (session.hasPlayer(userId)) return JoinResult.ALREADY_IN_SESSION
//
//        if (session.getPlayersCount() >= session.maxPlayers) {
//            return JoinResult.SESSION_FULL
//        }
//
//        session.addPlayer(userId, userName)
//        userSessions[userId] = code
//        return JoinResult.SUCCESS
//    }

//    enum class JoinResult {
//        SUCCESS,
//        SESSION_NOT_FOUND,
//        SESSION_FULL,
//        SESSION_INACTIVE,
//        ALREADY_IN_SESSION
//    }
}