package com.juying.tv.ui

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
    var seriesId: String? = null
    var collectionId: String? = null
}
