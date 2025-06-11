package ru.kortunov.dnd.telegram.bot.dnd_tg_bot

import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault
import kotlin.random.Random

class BotUtils {

    companion object {
        fun registerBotCommands(bot: DnDDiceBot) {
            val commands = listOf(
                    BotCommand("/start", "Запустить бота"),
                    BotCommand("/create", "Создать комнату "),
                    BotCommand("/join", "Присоединиться к комнате (например: /join ABC123)"),
                    BotCommand("/stats", "Статистика"),
                    BotCommand("/leave", "Покинуть комнату"),
                    BotCommand("/help", "Показать помощь"),
                    BotCommand("/cancel", "Отмена")
            )

            val setMyCommands = SetMyCommands()
            setMyCommands.commands = commands
            setMyCommands.scope = BotCommandScopeDefault()
//            setMyCommands.languageCode = null // Можно указать "ru" для русского, если нужно

            bot.execute(setMyCommands)
        }

        fun roll(diceType: DiceType): Int {
            return Random.nextInt(1, diceType.sides + 1)
        }
    }
}