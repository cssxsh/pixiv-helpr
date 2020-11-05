package xyz.cssxsh.mirai.plugin.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    @SerialName("similarity")
    val similarity: Double,
    @SerialName("content")
    val content: String,
    @SerialName("pid")
    val pid: Long,
    @SerialName("uid")
    val uid: Long
)