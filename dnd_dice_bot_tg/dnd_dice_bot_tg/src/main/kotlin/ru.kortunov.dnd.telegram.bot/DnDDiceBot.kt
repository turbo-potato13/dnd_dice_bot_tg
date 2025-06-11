package ru.kortunov.dnd.telegram.bot.dnd_tg_bot

import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

/**
 * Telegram бот для игры в D&D с виртуальными кубиками.
 * Позволяет создавать игровые комнаты, присоединяться к ним и бросать различные типы кубиков.
 *
 * Основные функции:
 * - Создание игровых комнат с ограничением по количеству игроков
 * - Присоединение к существующей комнате по коду
 * - Бросок различных типов кубиков (d4, d6, d8, d10, d12, d20, d100)
 * - Просмотр статистики бросков всех игроков в комнате
 * - Выход из комнаты
 */
class DnDDiceBot : TelegramLongPollingBot() {

    private val BOT_TOKEN = System.getenv("BOT_TOKEN") ?: error("BOT_TOKEN is not set in environment variables")
    private val BOT_USERNAME =  System.getenv("BOT_USERNAME") ?: error("BOT_USERNAME is not set in environment variables")

    /**
     * Карта состояний пользователей для отслеживания текущих операций
     * Ключ: ID пользователя, Значение: текущее состояние пользователя
     */
    private val userStates = mutableMapOf<Long, UserState>()

    /**
     * Карта ожидающих присоединений к комнате
     * Ключ: ID пользователя, Значение: код комнаты для присоединения
     */
    private val pendingJoins = mutableMapOf<Long, String>()

    private val seesionCodeByUserId: MutableMap<Long, String> = mutableMapOf()

    override fun getBotToken(): String = BOT_TOKEN


    override fun getBotUsername(): String = BOT_USERNAME

    val LOG = LoggerFactory.getLogger(javaClass.name)

    /**
     * Обрабатывает то что приходит от пользователя
     */
    override fun onUpdateReceived(update: Update) {
        try {
            if (update.hasMessage()) {
                handleMessage(update)
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update)
            }
        } catch (e: Exception) {
            LOG.error(e.message)
            e.printStackTrace()
        }
    }

    /**
     * Обрабатывает текстовые сообщения от пользователей
     * @param update объект обновления с сообщением
     */
    private fun handleMessage(update: Update) {
        val message = update.message
        val chatId = message.chatId
        val userId = message.from.id
        val text = message.text ?: return
        LOG.debug("User: $userId, send message $text")

        when {
            text == "/start" -> {
                sendWelcomeMessage(chatId)
                showDiceKeyboard(chatId)
            }

            text == "/help" -> {
                sendHelpMessage(chatId)
            }

            text == "/stats"  -> {
                sendStatistics(chatId, userId)
            }

            text == "/leave"  -> {
                leaveSession(chatId, userId)
            }

            text == "/create"  -> {
                userStates[userId] = UserState.WAITING_FOR_PLAYER_COUNT
                sendMessage(chatId, "👥 Напиши количество игроков для комнаты (от 1 до 20):")
            }

            text == "/cancel" -> {
                userStates.remove(userId)
                pendingJoins.remove(userId)
                sendMessage(chatId, "❌ Операция отменена")
                showDiceKeyboard(chatId)
            }

            text.startsWith("/join") -> {
                handleJoinCommand(chatId, userId, text)
            }

            userStates[userId] == UserState.WAITING_FOR_PLAYER_COUNT -> {
                handlePlayerCountInput(chatId, userId, text)
            }

            userStates[userId] == UserState.WAITING_FOR_NAME -> {
                handleNameInput(chatId, userId, text)
            }

            userStates[userId] == UserState.WAITING_FOR_JOIN_NAME -> {
                handleJoinNameInput(chatId, userId, text)
            }

            // Обработка нажатий кнопок кубиков
            DiceType.values().any { it.displayName == text } -> {
                handleDiceButtonPress(chatId, userId, text)
            }
        }
    }

    /**
     * Обрабатывает callback запросы
     */
    private fun handleCallbackQuery(update: Update) {
        // Убираем обработку callback query, так как используем обычные кнопки
    }

    /**
     * Отправляет приветственное сообщение с описанием команд
     * @param chatId ID чата для отправки сообщения
     */
    private fun sendWelcomeMessage(chatId: Long) {
        val text = """
            🎲 Добро пожаловать в DnD Dice Bot! 🎲
            
            Команды:
            /start - Запустить бота
            /create - Создать комнату 
            /join [код] - Присоединиться к комнате (например: /join ABC123)
            /stats - Показать последние броски
            /leave - Покинуть комнату
            /help - Показать помощь
            /cancel - Отмена последнего действия
        """.trimIndent()

        sendMessage(chatId, text)
    }

    /**
     * Отправляет подробную справку по использованию бота
     * @param chatId ID чата для отправки сообщения
     */
    private fun sendHelpMessage(chatId: Long) {
        val text = """
            📖 Как использовать бота:
            
            1️⃣ Создание комнаты:
            Отправь /create и укажи количество игроков и введи свое имя
            
            2️⃣ Подключение к комнате:
            Отправь /join [код комнаты]
            Пример: /join ABC123
            Затем введи своё имя
            
            3️⃣ Бросок кубиков:
            После присоединения используй кнопки внизу экрана
            Нажми на нужный кубик чтобы его бросить!
            Бросать кубики можно и без комнаты, но статистика сохранятся не будет
            
            4️⃣ Статистика:
            /stats - покажет последние броски всех игроков
            
            5️⃣ Выход из комнаты:
            /leave - покинуть комнату
            
            6️⃣ Отмена:
            /cancel - отменить текущую команду
        """.trimIndent()

        sendMessage(chatId, text)
    }

    /**
     * Обрабатывает ввод количества игроков при создании комнаты
     * @param chatId ID чата
     * @param userId ID пользователя
     * @param text введенный текст с количеством игроков
     */
    private fun handlePlayerCountInput(chatId: Long, userId: Long, text: String) {
        val maxPlayers = text.toIntOrNull()
        if (maxPlayers == null || maxPlayers < 1 || maxPlayers > 20) {
            sendMessage(chatId, "❌ Количество игроков должно быть числом от 1 до 20! Попробуй ещё раз или отправь /cancel для отмены:")
            return
        }

        val session = SessionManager.createSession(userId, maxPlayers)
        userStates.remove(userId)

        val responseText = """
            ✅ Комната создана!
            
            🔑 Код комнаты: ${session.code}
            👥 Максимум игроков: ${session.maxPlayers}
            
            Отправь эту команду другим игрокам для присоединения:
        """.trimIndent()

        sendMessage(chatId, responseText)
        seesionCodeByUserId[userId] = session.code

        LOG.info("User: $userId create session: ${session.code} for $maxPlayers players")
        // Отправляем копируемую команду
        sendMessage(chatId, "`/join ${session.code}`", parseMode = "Markdown")

        userStates[userId] = UserState.WAITING_FOR_NAME
        sendMessage(chatId, "👤 Теперь введи своё имя:")
    }

    /**
     * Обрабатывает команду присоединения к комнате
     * @param chatId ID чата
     * @param userId ID пользователя
     * @param text текст команды с кодом комнаты
     */
    private fun handleJoinCommand(chatId: Long, userId: Long, text: String) {
        val parts = text.split(" ", limit = 2)
        if (parts.size < 2) {
            sendMessage(chatId, "❌ Неправильный формат! Используй: /join [код комнаты]")
            return
        }

        val sessionCode = parts[1].trim()

        // Проверяем существование комнаты
        val session = SessionManager.getSession(sessionCode)
        if (session == null) {
            sendMessage(chatId, "❌ Комната с кодом `$sessionCode` не найдена! Проверь правильность кода.", parseMode = "Markdown")
            return
        }

        if (!session.isActive) {
            sendMessage(chatId, "❌ Комната неактивна!")
            return
        }

        if (session.players.size >= session.maxPlayers) {
            sendMessage(chatId, "❌ Комната переполнена!")
            return
        }

        if (session.hasPlayer(userId)) {
            sendMessage(chatId, "❌ Ты уже в этой комнате!")
            showDiceKeyboard(chatId)
            return
        }

        seesionCodeByUserId[userId] = sessionCode
        // Сохраняем код комнаты и просим имя
        pendingJoins[userId] = sessionCode
        userStates[userId] = UserState.WAITING_FOR_JOIN_NAME
        sendMessage(chatId, "✅ Комната найдена! 👤 Теперь введи своё имя:")
    }

    /**
     * Обрабатывает ввод имени при присоединении к комнате
     * @param chatId ID чата
     * @param userId ID пользователя
     * @param name введенное имя игрока
     */
    private fun handleJoinNameInput(chatId: Long, userId: Long, name: String) {
        val sessionCode = pendingJoins[userId]
        if (sessionCode == null) {
            sendMessage(chatId, "❌ Ошибка! Попробуй присоединиться заново командой /join")
            userStates.remove(userId)
            return
        }

        // Обрезаем имя до 50 символов
        val playerName = name.trim().take(50)
        if (playerName.isEmpty()) {
            sendMessage(chatId, "❌ Имя не может быть пустым! Введи своё имя:")
            return
        }

        val session = SessionManager.getSession(sessionCode)
        if (session == null) {
            sendMessage(chatId, "❌ Комната больше не существует!")
            userStates.remove(userId)
            pendingJoins.remove(userId)
            return
        }

        if (session.addPlayer(userId, playerName)) {
            sendMessage(chatId, "✅ Ты присоединился к комнате как $playerName!")

            userStates.remove(userId)
            pendingJoins.remove(userId)

            showDiceKeyboard(chatId)

            // Уведомляем всех игроков о новом участнике
            val allPlayers = SessionManager.getAllPlayersInSession(sessionCode)
            for (playerId in allPlayers) {
                if (playerId != userId) {
                    sendMessage(playerId, "👤 $playerName присоединился к игре!")
                }
            }
        } else {
            sendMessage(chatId, "❌ Не удалось присоединиться к комнате (возможно, она переполнена)")
            userStates.remove(userId)
            pendingJoins.remove(userId)
        }
    }

    /**
     * Обрабатывает ввод имени создателем комнате
     * @param chatId ID чата
     * @param userId ID пользователя
     * @param name введенное имя
     */
    private fun handleNameInput(chatId: Long, userId: Long, name: String) {
        val session = SessionManager.getUserSession(userId)
        if (session == null) {
            sendMessage(chatId, "❌ Сначала создай или присоединись к комнате!")
            return
        }

        // Обрезаем имя до 30 символов
        val playerName = name.trim().take(30)
        if (playerName.isEmpty()) {
            sendMessage(chatId, "❌ Имя не может быть пустым! Введи своё имя:")
            return
        }

        session.addPlayer(userId, playerName)
        userStates.remove(userId)

        sendMessage(chatId, "✅ Добро пожаловать в игру, $playerName!")
        showDiceKeyboard(chatId)
    }

    /**
     * Показывает клавиатуру с кубиками
     * @param chatId ID чата
     */
    private fun showDiceKeyboard(chatId: Long) {
        val keyboard = createDiceKeyboard()
        sendMessage(
                chatId = chatId,
                text = "🎲 Используй кнопки внизу для броска кубиков! \n" +
                        " Или напиши тип кубика по примеру: d6 \n" +
                        " Доступные типы кубиков: d4, d6, d8, d10, d12, d20, d100",
                keyboard = keyboard
        )
    }

    /**
     * Создает клавиатуру с кнопками кубиков
     * @return объект ReplyKeyboardMarkup с кнопками кубиков
     */
    private fun createDiceKeyboard(): ReplyKeyboardMarkup {
        val keyboard = ReplyKeyboardMarkup()
        keyboard.selective = true
        keyboard.resizeKeyboard = true
        keyboard.oneTimeKeyboard = false

        val rows = mutableListOf<KeyboardRow>()

        // Первый ряд: d4, d6, d8, d10
        val row1 = KeyboardRow()
        row1.add(KeyboardButton("d4"))
        row1.add(KeyboardButton("d6"))
        row1.add(KeyboardButton("d8"))
        row1.add(KeyboardButton("d10"))
        rows.add(row1)

        // Второй ряд: d12, d20, d100
        val row2 = KeyboardRow()
        row2.add(KeyboardButton("d12"))
        row2.add(KeyboardButton("d20"))
        row2.add(KeyboardButton("d100"))
        rows.add(row2)

        keyboard.keyboard = rows
        return keyboard
    }

//    /**
//     * Создает пустую клавиатуру (убирает все кнопки)
//     * @return пустой объект ReplyKeyboardMarkup
//     */
//    private fun createEmptyKeyboard(): ReplyKeyboardMarkup {
//        val keyboard = ReplyKeyboardMarkup()
//        keyboard.selective = true
//        keyboard.resizeKeyboard = true
//        keyboard.oneTimeKeyboard = false
//
//        val rows = mutableListOf<KeyboardRow>()
//        keyboard.keyboard = rows
//        return keyboard
//    }

    /**
     * Выполняет бросок кубика и отправляет результат всем игрокам в комнате
     * @param userId ID пользователя
     * @param diceType тип кубика для броска
     */
    private fun rollDice(userId: Long, diceType: DiceType, sessionCode: String) {
        val session = SessionManager.getSession(sessionCode) ?: return
        val player = session.getPlayer(userId) ?: return

        val rollResult = player.roll(diceType, 1)

        val resultText = "\uD83C\uDFB2 ${player.userName} бросил кубик ${diceType.displayName} и выпало: $rollResult"

        // Отправляем результат всем игрокам в комнате
        session.players.forEach { (userId, _) -> sendMessage(userId, resultText) }

    }

    /**
     * Отправляет статистику бросков всех игроков в комнате
     * @param chatId ID чата
     * @param userId ID пользователя
     */
    private fun sendStatistics(chatId: Long, userId: Long) {
        val sessionCode = seesionCodeByUserId[userId]
        val session = sessionCode?.let { SessionManager.getSession(it) } ?: SessionManager.getUserSession(userId)
        if (session == null) {
            sendMessage(chatId, "❌ Ты не в комнате! Присоединись к игре командой /join")
            return
        }

        val stats = StringBuilder("📊 Последние броски:\n\n")

        for (player in session.players.values) {
            stats.append("👤 ${player.userName}: ")
            if (player.lastRoll != null) {
                val roll = player.lastRoll!!
                stats.append("${roll.diceType.displayName} → ${roll.formatResult()}")
            } else {
                stats.append("пока не бросал кубики")
            }
            stats.append("\n")
        }

        sendMessage(chatId, stats.toString())
    }

    /**
     * Обрабатывает выход пользователя из комнаты
     * @param chatId ID чата
     * @param userId ID пользователя
     */
    private fun leaveSession(chatId: Long, userId: Long) {
        val sessionCode = seesionCodeByUserId[userId]
        logger.info("User: $userId leave session:$sessionCode")
        if (sessionCode != null) {
            val session = SessionManager.getSession(sessionCode)
            val leaverName = session?.players?.get(userId)?.userName
            SessionManager.leaveSession(sessionCode, userId)
            userStates.remove(userId)
            pendingJoins.remove(userId)

            val allPlayers = SessionManager.getAllPlayersInSession(sessionCode)
            for (playerId in allPlayers) {
                if (playerId != userId) {
                    sendMessage(playerId, "👤 $leaverName вышел из комнаты!")
                }
            }
            sendMessage(chatId, "✅ Ты покинул комнату!")
        } else {
            sendMessage(chatId, "❌ Ты не в комнате!")
        }
    }

    /**
     * Отправляет сообщение в указанный чат
     * @param chatId ID чата получателя
     * @param text текст сообщения
     * @param keyboard клавиатура для отображения (необязательно)
     * @param parseMode режим разбора текста (необязательно)
     */
    private fun sendMessage(chatId: Long, text: String, keyboard: ReplyKeyboardMarkup? = null, parseMode: String? = null) {
        val message = SendMessage().apply {
            this.chatId = chatId.toString()
            this.text = text
            if (keyboard != null) {
                this.replyMarkup = keyboard
            }
            if (parseMode != null) {
                this.parseMode = parseMode
            }
        }

        try {
            execute(message)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    /**
     * Обрабатывает нажатие кнопок кубиков
     * @param chatId ID чата
     * @param userId ID пользователя
     * @param diceText текст кнопки кубика
     */
    private fun handleDiceButtonPress(chatId: Long, userId: Long, diceText: String) {
        val sessionCode = seesionCodeByUserId[userId]
        val session = sessionCode?.let { SessionManager.getSession(it) } ?: SessionManager.getUserSession(userId)
        val diceType = DiceType.values().find { it.displayName == diceText }

        if (session == null || !session.hasPlayer(userId)) {
            val rollResult = diceType?.let { BotUtils.roll(it).toString() } ?: "Такого кубика нет"
            sendMessage(chatId, rollResult)
            return
        }

        if (diceType != null) {
            rollDice(userId, diceType, session.code)
        }

    }

    /**
     * Перечисление состояний пользователя для отслеживания текущих операций
     */
    enum class UserState {
        /** Ожидание ввода имени игрока */
        WAITING_FOR_NAME,

        /** Ожидание ввода количества игроков для новой комнаты */
        WAITING_FOR_PLAYER_COUNT,

        /** Ожидание ввода имени при присоединении к комнате */
        WAITING_FOR_JOIN_NAME
    }
}