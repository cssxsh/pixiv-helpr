@file:Suppress("unused")

package xyz.cssxsh.mirai.plugin

import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.PlainText
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.pixiv.client.*
import xyz.cssxsh.pixiv.data.AuthResult

/**
 * 助手实例
 */
class PixivHelper(val contact: Contact, ) : SimplePixivClient(
    parentCoroutineContext = PixivHelperPlugin.coroutineContext,
    config = PixivHelperData.config
), PixivHelperLogger {

    init {
        (config.refreshToken ?: authInfo?.refreshToken)?.let { token ->
            runBlocking {
                runCatching {
                    authInfo = refresh(token)
                    config = config.copy(refreshToken = token)
                }.onSuccess {
                    logger.info("${contact}的助手自动${requireNotNull(authInfo).user.name}登陆成功")
                }.onFailure { ree ->
                    logger.info("${contact}的助手自动登陆失败, ${ree.message}")
                }
            }
        }
    }

    override var config: PixivConfig
        get() = PixivHelperData.config
        set(value) { PixivHelperData.config = value }

    override var authInfo: AuthResult.AuthInfo?
        get() = PixivHelperData.authInfo
        set(value) {
            if (value != null) {
                PixivHelperData.authInfo = value
            }
        }

    val isLoggedIn: Boolean
        get() = authInfo != null

    override fun config(block: PixivConfig.() -> Unit) =
        config.apply(block).also { PixivHelperData.config = it }

    override suspend fun refresh(): AuthResult.AuthInfo =
        super.refresh().also { authInfo = it }

    override suspend fun refresh(token: String): AuthResult.AuthInfo =
        super.refresh(token).also { logger.info("Auth by RefreshToken: $token") }

    override suspend fun login(): AuthResult.AuthInfo =
        super.login().also { authInfo = it }

    override suspend fun login(mailOrPixivID: String, password: String): AuthResult.AuthInfo =
        super.login(mailOrPixivID, password).also { logger.info("Auth by Account: $mailOrPixivID") }

    /**
     * 给这个助手的联系人发送消息
     */
    @JvmSynthetic
    suspend fun reply(message: Message): MessageReceipt<Contact> =
        contact.sendMessage(message)

    /**
     * 给这个助手的联系人发送文本消息
     */
    @JvmSynthetic
    suspend fun reply(plain: String): MessageReceipt<Contact> =
        contact.sendMessage(PlainText(plain))
}