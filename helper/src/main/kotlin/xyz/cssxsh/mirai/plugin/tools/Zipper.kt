package xyz.cssxsh.mirai.plugin.tools

import kotlinx.coroutines.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.data.PixivHelperSettings
import xyz.cssxsh.pixiv.model.ArtWorkInfo
import java.io.BufferedOutputStream
import java.nio.file.attribute.FileTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Zipper {

    private val charMap = mapOf(
        "\\" to "＼",
        "/" to "／",
        ":" to "：",
        "*" to "＊",
        "?" to "？",
        "\"" to "＂",
        "<" to "＜",
        ">" to "＞",
        "|" to "｜"
    )

    private const val BUFFER_SIZE = 64 * 1024 * 1024

    private fun ArtWorkInfo.toText() =
        "(${pid})[${getFullWidthTitle()}]{${pageCount}}"

    private fun nowTimeText() =
        OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))

    private fun ArtWorkInfo.getFullWidthTitle() = title.replace("""[\\/:*?"<>|]""".toRegex()) {
        charMap.getOrDefault(it.value, "")
    }

    private fun zipFile(type: String) =
        PixivHelperSettings.backupFolder.resolve("${type.toUpperCase()}(${nowTimeText()}).zip")

    fun compressAsync(list: List<ArtWorkInfo>, type: String) = PixivHelperPlugin.async(Dispatchers.IO) {
        zipFile(type).apply {
            createNewFile()
            logger.verbose { "共${list.size}个作品将写入文件${absolutePath}" }
            ZipOutputStream(BufferedOutputStream(outputStream(), BUFFER_SIZE)).use { zipOutputStream ->
                zipOutputStream.setLevel(Deflater.BEST_COMPRESSION)
                list.forEach { info ->
                    PixivHelperSettings.imagesFolder(info.pid).listFiles()?.forEach { file ->
                        zipOutputStream.putNextEntry(ZipEntry("[${info.pid}](${info.getFullWidthTitle()})/${file.name}").apply {
                            creationTime = FileTime.from(info.createAt.toInstant(ZoneOffset.ofHours(9)))
                            lastModifiedTime = FileTime.from(info.createAt.toInstant(ZoneOffset.ofHours(9)))
                        })
                        zipOutputStream.write(file.readBytes())
                    }
                    logger.verbose { "${info.toText()}已写入${name}" }
                }
                zipOutputStream.flush()
            }
            logger.info { "${absolutePath}压缩完毕！" }
        }
    }

    fun backupAsync() = PixivHelperPlugin.async(Dispatchers.IO) {
        mapOf(
            PixivHelperPlugin.dataFolder to zipFile("data").apply { createNewFile() },
            PixivHelperPlugin.configFolder to zipFile("config").apply { createNewFile() }
        ).map { (dir, zip) ->
            zip.apply {
                logger.verbose { "将备份数据目录${dir.absolutePath}到${absolutePath}" }
                ZipOutputStream(BufferedOutputStream(zip.outputStream(), BUFFER_SIZE)).use { zipOutputStream ->
                    zipOutputStream.setLevel(Deflater.BEST_COMPRESSION)
                    dir.listFiles { file -> file.isFile }?.forEach { file ->
                        zipOutputStream.putNextEntry(ZipEntry(file.name).apply {
                            lastModifiedTime = FileTime.fromMillis(file.lastModified())
                        })
                        zipOutputStream.write(file.readBytes())
                        logger.verbose { "${file.name}已写入${name}" }
                    }
                    zipOutputStream.flush()
                }
                logger.info { "${absolutePath}压缩完毕！" }
            }
        }
    }
}