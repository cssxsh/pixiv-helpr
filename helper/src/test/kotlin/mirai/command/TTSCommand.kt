package mirai.command

import mirai.tools.TTS
import net.mamoe.mirai.console.command.CommandOwner
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.permission.Permission
import net.mamoe.mirai.console.permission.PermissionId
import net.mamoe.mirai.console.permission.RootPermission
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.message.GroupMessageEvent
import net.mamoe.mirai.message.uploadAsGroupVoice

object TTSCommand : SimpleCommand(
    owner = TTSCommandOwner,
    "tts", "say", "说",
    description = "TTS指令",
    prefixOptional = true
) {

    private object TTSCommandOwner : CommandOwner {
        override val parentPermission: Permission
            get() = RootPermission

        override fun permissionId(name: String): PermissionId =
            PermissionId("tts", name)
    }

    @ConsoleExperimentalApi
    @Handler
    suspend fun CommandSenderOnMessage<GroupMessageEvent>.handle(text: String) {
        reply(TTS.getAmrFile(text.takeIf {
            it.length < 256
        } ?: "太长不说").inputStream().uploadAsGroupVoice(fromEvent.group))
    }
}