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
}
