package xyz.cssxsh.mirai.plugin.command

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.pixiv.data.app.IllustInfo

@Suppress("unused")
object PixivEro : SimpleCommand(
    PixivHelperPlugin,
    "ero", "色图",
    description = "色图指令",
    prefixOptional = true
), PixivHelperLogger {

    private fun PixivHelper.randomIllust(): IllustInfo = PixivCacheData.ero.random().takeIf { illust ->
        illust.pid !in historyQueue
    }?.also {
        if (historyQueue.remainingCapacity() == 0) historyQueue.take()
        historyQueue.put(it.pid)
    } ?: randomIllust()

    @Handler
    suspend fun CommandSenderOnMessage<MessageEvent>.handle() = getHelper().runCatching {
        buildMessage(randomIllust())
    }.onSuccess { list ->
        list.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess
}

















