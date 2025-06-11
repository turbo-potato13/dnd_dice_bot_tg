package ru.kortunov.dnd.telegram.bot.dnd_dice_tg_bot

import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import ru.kortunov.dnd.telegram.bot.dnd_tg_bot.BotUtils
import ru.kortunov.dnd.telegram.bot.dnd_tg_bot.DnDDiceBot

val logger = LoggerFactory.getLogger("Main")


fun main() {
    try {
        val telegramBots = TelegramBotsApi(DefaultBotSession::class.java)
        val bot = DnDDiceBot()
        BotUtils.registerBotCommands(bot)
        telegramBots.registerBot(bot)
        logger.info("DnD Dice Bot запущен успешно!")
    } catch (e: TelegramApiException) {
        e.printStackTrace()
    }
}