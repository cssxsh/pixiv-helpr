package xyz.cssxsh.mirai.plugin.tools

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import net.mamoe.mirai.utils.*
import org.apache.poi.xssf.usermodel.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.PixivHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.useArtWorkInfoMapper
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Suppress("unused")
object PoiTool {

    private fun xlsxFile(name: String) = File(
        PixivHelperSettings.backupFolder,
        "${name}(${nowTimeText()}).xlsx"
    )

    private fun nowTimeText() =
        OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))

    private fun XSSFSheet.writeHeader(header: List<String>) = apply {
        createRow(0).apply {
            header.forEachIndexed { column, text ->
                createCell(column).setCellValue(text)
                autoSizeColumn(column)
            }
        }
    }

    private val PIXIV_CACHE_DATA_HEADER = listOf(
        "PID",
        "TITLE",
        "CREATE_DATE",
        "PAGE_COUNT",
        "SANITY_LEVEL",
        "TYPE",
        "IS_R18",
        "WIDTH",
        "HEIGHT",
        "USER_ID",
        "USER_NAME",
        "TOTAL_BOOKMARKS"
    )

    private val PIXIV_TAG_DATA_HEADER = listOf("TAG", "TOTAL")

    private val PIXIV_STATISTICAL_DATA_HEADER = listOf("QQ", "ERO", "TAG")

    private val PIXIV_ALIAS_DATA_HEADER = listOf("NAME", "UID")

    fun saveCacheAsync(): Deferred<File> = PixivHelperPlugin.async(Dispatchers.IO) {
        XSSFWorkbookFactory.createWorkbook().use { workbook ->
            workbook.apply {
                createSheet("PIXIV_CACHE_DATA").apply {
                    val dateStyle = createCellStyle().apply {
                        dataFormat = workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss")
                    }
                    // TODO
                    useArtWorkInfoMapper { it.eroRandom(100) }.sortedByDescending {
                        it.totalBookmarks
                    }.forEachIndexed { row, info ->
                        createRow(row + 1).apply {
                            createCell(PIXIV_CACHE_DATA_HEADER.indexOf("PID")).setCellValue(info.pid.toDouble())
                            createCell(PIXIV_CACHE_DATA_HEADER.indexOf("TITLE")).setCellValue(info.title)
                            createCell(PIXIV_CACHE_DATA_HEADER.indexOf("CREATE_DATE")).apply {
                                setCellValue(info.createAt)
                                cellStyle = dateStyle
                            }
                            createCell(PIXIV_CACHE_DATA_HEADER.indexOf("PAGE_COUNT")).setCellValue(info.pageCount.toDouble())
                            createCell(PIXIV_CACHE_DATA_HEADER.indexOf("SANITY_LEVEL")).setCellValue(info.sanityLevel.toDouble())
                            createCell(PIXIV_CACHE_DATA_HEADER.indexOf("TYPE")).setCellValue(info.type)
                            createCell(PIXIV_CACHE_DATA_HEADER.indexOf("IS_R18")).setCellValue(info.isR18)
                            createCell(PIXIV_CACHE_DATA_HEADER.indexOf("WIDTH")).setCellValue(info.width.toDouble())
                            createCell(PIXIV_CACHE_DATA_HEADER.indexOf("HEIGHT")).setCellValue(info.height.toDouble())
                            createCell(PIXIV_CACHE_DATA_HEADER.indexOf("USER_ID")).setCellValue(info.uid.toDouble())
                            createCell(PIXIV_CACHE_DATA_HEADER.indexOf("TOTAL_BOOKMARKS")).setCellValue(info.totalBookmarks.toDouble())
                        }
                    }
                    writeHeader(PIXIV_CACHE_DATA_HEADER)
                }
                logger.verbose { "PIXIV_CACHE_DATA 已写入到 XLSX" }
                createSheet("PIXIV_STATISTICAL_DATA").apply {
                    PixivStatisticalData.getMap().entries.forEachIndexed { row, (qq, data) ->
                        createRow(row + 1).apply {
                            createCell(PIXIV_STATISTICAL_DATA_HEADER.indexOf("QQ")).setCellValue(qq.toDouble())
                            createCell(PIXIV_STATISTICAL_DATA_HEADER.indexOf("ERO")).setCellValue(data.eroCount.toDouble())
                            data.tagCount.entries.sortedByDescending {
                                it.value
                            }.forEachIndexed { row, (tag, total) ->
                                createCell(row + PIXIV_STATISTICAL_DATA_HEADER.size - 1).setCellValue("$tag: $total")
                            }
                        }
                    }
                    writeHeader(PIXIV_STATISTICAL_DATA_HEADER)
                }
                logger.verbose { "PIXIV_STATISTICAL_DATA 已写入到 XLSX" }
                createSheet("PIXIV_ALIAS_DATA").apply {
                    PixivAliasData.aliases.toMap().entries.forEachIndexed { row, (name, uid) ->
                        createRow(row + 1).apply {
                            createCell(PIXIV_ALIAS_DATA_HEADER.indexOf("NAME")).setCellValue(name)
                            createCell(PIXIV_ALIAS_DATA_HEADER.indexOf("UID")).setCellValue(uid.toDouble())
                        }
                    }
                    writeHeader(PIXIV_ALIAS_DATA_HEADER)
                }
                logger.verbose { "PIXIV_ALIAS_DATA 已写入到 XLSX" }
            }
            xlsxFile("CACHE").apply {
                outputStream().use {
                    workbook.write(it)
                }
                logger.info { "数据将保存至${absolutePath}" }
            }
        }
    }
}