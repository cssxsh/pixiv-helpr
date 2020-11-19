package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.contact.recall
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.QuoteReply
import net.mamoe.mirai.utils.verbose
import xyz.cssxsh.mirai.plugin.PixivHelperLogger
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin

object PixivRecallCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "recall", "撤回",
    description = "撤回指令"
), PixivHelperLogger {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    @Handler
    @Suppress("unused")
    suspend fun CommandSenderOnMessage<MessageEvent>.handle() {
        message[QuoteReply]?.runCatching {
            logger.verbose { "尝试对${source}进行撤回" }
            fromEvent.subject.recall(source)
        }
    }
}