package com.juying.tv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed interface Screen {
    data object Home : Screen
    data object Library : Screen
    data object Sources : Screen
    data object Settings : Screen
    data object KidsManage : Screen
    data object HiddenManage : Screen
    data class SeriesDetail(val seriesId: String) : Screen
    data class CollectionDetail(val collectionId: String) : Screen
    data class CollectionEdit(val collectionId: String) : Screen
    data class CollectionPicker(val collectionId: String) : Screen
    data class AddSource(val type: String, val sourceId: String? = null) : Screen
    data class Player(val mediaId: String, val seriesId: String, val fromStart: Boolean = false) : Screen

    val saveKey: String
        get() = when (this) {
            Home -> "home"
            Library -> "library"
            Sources -> "sources"
            Settings -> "settings"
            KidsManage -> "kids"
            HiddenManage -> "hidden"
            is SeriesDetail -> "series:$seriesId"
            is CollectionDetail -> "collection:$collectionId"
            is CollectionEdit -> "collection-edit:$collectionId"
            is CollectionPicker -> "collection-picker:$collectionId"
            is AddSource -> "add-source:$type:${sourceId.orEmpty()}"
            is Player -> "player:$mediaId"
        }
}

/** 记住最后一次聚焦的卡片，返回列表时回到原位。 */
object NavFocusMemory {
    var seriesId by mutableStateOf<String?>(null)
    var collectionId by mutableStateOf<String?>(null)
    var collectionSeriesId by mutableStateOf<String?>(null)

    /** 从合集页进入编辑/添加页时记录的触发入口，返回时焦点回到对应按钮。 */
    var collectionAction by mutableStateOf<String?>(null)
    var sourceId by mutableStateOf<String?>(null)

    /** 最近一次播放的单集，返回详情页时焦点回到这一集。 */
    var episodeId by mutableStateOf<String?>(null)
    var episodeSeriesId by mutableStateOf<String?>(null)

    /** 首页返回时的触发控件：hero-primary / hero-details / continue / series。 */
    var homeTarget by mutableStateOf<String?>(null)
    var homeSeriesId by mutableStateOf<String?>(null)
    var homeMediaId by mutableStateOf<String?>(null)
    var homeShelfKey by mutableStateOf<String?>(null)
}
