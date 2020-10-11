package xyz.cssxsh.mirai.plugin.command

import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.message.MessageEvent
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.PixivCacheData
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import xyz.cssxsh.pixiv.WorkContentType
import xyz.cssxsh.pixiv.RankMode
import xyz.cssxsh.pixiv.api.app.*
import xyz.cssxsh.pixiv.data.app.IllustInfo
import xyz.cssxsh.pixiv.data.app.UserDetail
import java.io.File

@Suppress("unused")
object PixivCacheCommand : CompositeCommand(
    PixivHelperPlugin,
    "cache",
    description = "缓存指令",
    prefixOptional = true
), PixivHelperLogger {
    /**
     * timeMillis
     */
    private var delayTime: Long
        get() = PixivHelperSettings.delayTime
        set(value) {
            PixivHelperSettings.delayTime = value
        }

    private var cacheJob: Job? = null

    private suspend fun PixivHelper.getRank(modes: Array<RankMode> = RankMode.values()) = buildList {
        modes.map { mode ->
            runCatching {
                illustRanking(mode = mode).illusts
            }.onSuccess {
                add(PixivCacheData.filter(it).values)
                logger.verbose("加载排行榜[${mode}]{${it.size}}成功")
            }.onFailure {
                logger.verbose("加载排行榜[${mode}]失败, ${it.message}")
            }
        }
    }

    private suspend fun PixivHelper.getFollow(limit: Long = 10_000) = buildList {
        (0 until limit step 30).forEach { offset ->
            runCatching {
                illustFollow(offset = offset).illusts
            }.onSuccess {
                if (it.isEmpty()) return@buildList
                add(PixivCacheData.filter(it).values)
                logger.verbose("加载关注用户作品时间线第${offset / 30}页{${it.size}}成功")
            }.onFailure {
                logger.verbose("加载关注用户作品时间线第${offset / 30}页失败, $it")
            }
        }
    }

    private suspend fun PixivHelper.getUserPreviews(uid: Long, limit: Long = 10_000) = buildList {
        (0 until limit step 30).forEach { offset ->
            runCatching {
                userFollowing(uid = uid, offset = offset).userPreviews.flatMap { it.illusts }
            }.onSuccess {
                if (it.isEmpty()) return@buildList
                add(PixivCacheData.filter(it).values)
                logger.verbose("加载关注用户作品预览第${offset / 30}页{${it.size}}成功")
            }.onFailure {
                logger.verbose("加载关注用户作品预览第${offset / 30}页失败, $it")
            }
        }
    }

    private suspend fun PixivHelper.getRecommended(limit: Long = 10_000) = buildList {
        (0 until limit step 30).forEach { offset ->
            runCatching {
                userRecommended(offset = offset).userPreviews.flatMap { it.illusts }
            }.onSuccess {
                if (it.isEmpty()) return@buildList
                add(PixivCacheData.filter(it).values)
                logger.verbose("加载推荐用户预览第${offset / 30}页{${it.size}}成功")
            }.onFailure {
                logger.verbose("加载推荐用户预览第${offset / 30}页失败, $it")
            }
        }
    }

    private suspend fun CommandSenderOnMessage<MessageEvent>.doCache(
        timeMillis: Long = delayTime,
        block: suspend PixivHelper.() -> List<IllustInfo>
    ) = getHelper().runCatching {
        check(cacheJob?.isActive != true) { "正在缓存中, ${cacheJob}..." }
        launch {
            runCatching {
                PixivCacheData.filter(block()).values.also { list ->
                    logger.verbose("共 ${list.size} 个作品信息将会被尝试添加")
                }.count { illust: IllustInfo ->
                    isActive && illust.pid !in PixivCacheData && runCatching {
                        getImages(illust)
                    }.onSuccess {
                        delay(timeMillis)
                    }.onFailure {
                        logger.verbose("获取作品(${illust.pid})[${illust.title}]错误", it)
                    }.isSuccess
                }
            }.onSuccess {
                quoteReply("缓存完毕共${it}个新作品")
            }.onFailure {
                quoteReply("缓存失败, ${it.message}")
            }
        }.also {
            cacheJob = it
        }
    }.onSuccess {
        quoteReply("添加任务完成${it}")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 缓存排行榜和关注列表
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.all() = doCache {
        (getFollow() + getRank() + getUserPreviews(getAuthInfoOrThrow().user.uid)).flatten().apply {
            forEach { illust ->
                illust.writeTo(File(PixivHelperSettings.imagesFolder(illust.pid), "${illust.pid}.json"))
            }
        }
    }

    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.recommended() = doCache {
        getRecommended().flatten().filter { illust ->
            illust.totalBookmarks ?: 0 >= 10_000 && illust.type == WorkContentType.ILLUST
        }.apply {
            forEach { illust ->
                illust.writeTo(File(PixivHelperSettings.imagesFolder(illust.pid), "${illust.pid}.json"))
            }
        }
    }

    /**
     * 缓存指定用户关注的用户的预览作品
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.preview(uid: Long) = doCache {
        getUserPreviews(uid).flatten().filter { illust ->
            illust.totalBookmarks ?: 0 >= 10_000 && illust.type == WorkContentType.ILLUST
        }.apply {
            forEach { illust ->
                illust.writeTo(File(PixivHelperSettings.imagesFolder(illust.pid), "${illust.pid}.json"))
            }
        }
    }


    /**
     * 从文件夹中加载信息
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.load() = doCache(0) {
        PixivHelperSettings.cacheFolder.also {
            logger.verbose("从 ${it.absolutePath} 加载作品信息")
        }.walk().mapNotNull { file ->
            if (file.isDirectory && file.name.matches("""^[0-9]+$""".toRegex())) {
                file.name.toLong()
            } else {
                null
            }
        }.toList().map { pid ->
            getIllustInfo(pid)
        }
    }

    /**
     * 从用户详情加载信息
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.user(uid: Long) = doCache {
        val detail: UserDetail = userDetail(uid)
        logger.verbose("用户(${detail.user.id})[${detail.user.name}], 共有${detail.profile.totalIllusts} 个作品")

        (0 .. detail.profile.totalIllusts step 30).mapNotNull { offset ->
            runCatching {
                userIllusts(uid = uid, offset = offset).illusts
            }.onSuccess {
                logger.verbose("加载用户作品第${offset / 30}页{${it.size}}成功")
            }.onFailure {
                logger.verbose("加载用户作品第${offset / 30}页失败, $it")
            }.getOrNull()
        }.flatten().apply {
            forEach { illust ->
                illust.writeTo(File(PixivHelperSettings.imagesFolder(illust.pid), "${illust.pid}.json"))
            }
        }
    }

    /**
     * 强制停止缓存
     */
    @SubCommand("cancel", "stop")
    suspend fun CommandSenderOnMessage<MessageEvent>.cancel() = runCatching {
        cacheJob?.cancelAndJoin()
    }.onSuccess {
        quoteReply("任务${cacheJob}已停止")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 检查当前缓存中不可读，删除并重新下载
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.check() = getHelper().runCatching {
        PixivCacheData.values().also {
            logger.verbose("共有 ${it.size} 个作品需要检查")
        }.count { (pid, data) ->
            runCatching {
                val illust = requireNotNull(data) { "没有数据" }
                val dir = PixivHelperSettings.imagesFolder(pid)
                (0 until illust.pageCount).forEach { index ->
                    File(dir, "${illust.pid}-origin-${index}.jpg").apply {
                        if (canRead().not()) {
                            delete().let {
                                logger.warning("$absolutePath 不可读， 文件将删除重新下载，删除结果：${it}")
                            }
                            httpClient().use { client ->
                                client.get<ByteArray>(illust.getOriginUrl()[index]) {
                                    headers[HttpHeaders.Referrer] = url.buildString()
                                }
                            }.let {
                                writeBytes(it)
                            }
                        }
                    }
                }
            }.onFailure {
                logger.warning("作品(${pid})缓存出错, ${it.message}")
            }.isFailure
        }
    }.onSuccess {
        quoteReply("检查缓存完毕，无法修复错误数: $it")
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 色图之王
     */
    @SubCommand
    suspend fun CommandSenderOnMessage<MessageEvent>.king() = getHelper().runCatching {
        (PixivCacheData.eros + PixivCacheData.r18s).values.maxByOrNull {
            it.totalBookmarks ?: 0
        }.let {
            buildMessage(requireNotNull(it) { "缓存为空" })
        }
    }.onSuccess { lists ->
        lists.forEach { quoteReply(it) }
    }.onFailure {
        quoteReply(it.toString())
    }.isSuccess

    /**
     * 设置缓存目录 cache path /storage/emulated/0/PixivCache
     * @param path 缓存目录
     */
    @SubCommand
    fun ConsoleCommandSender.path(path: String) {
        runCatching {
            if (File(path).exists().not()) File(PixivHelperSettings.cachePath).renameTo(File(path))
        }
        PixivHelperSettings.cachePath = path
    }

    /**
     * 设置缓存延迟时间
     */
    @SubCommand
    fun ConsoleCommandSender.delay(timeMillis: Long) {
        logger.info("delay: $delayTime -> $timeMillis")
        delayTime = timeMillis
    }

    /**
     * 设置缓存延迟时间
     */
    @SubCommand
    fun ConsoleCommandSender.remove(pid: Long) {
        PixivCacheData.eros.remove(pid)?.let {
            logger.info("色图作品(${it.pid})[${it.title}]信息将从{色图}移除, 目前共${PixivCacheData.eros.size}条色图")
        }
    }
}