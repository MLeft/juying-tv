package com.juying.tv.data.source

import com.juying.tv.data.db.SeriesKind

object MediaGrouper {

    fun group(files: List<ScannedFile>): List<LibraryGroup> {
        val videosByFolder = files.filter { it.kind == ScannedKind.VIDEO }
            .groupBy { it.parentPath }
        val imagesByFolder = files.filter { it.kind == ScannedKind.IMAGE }
            .groupBy { it.parentPath }

        val builders = LinkedHashMap<String, GroupBuilder>()

        videosByFolder.forEach { (folder, videos) ->
            val folderName = folder.trim('/').substringAfterLast('/')
            val parentFolderName = folder.trim('/').split('/').dropLast(1).lastOrNull().orEmpty()
            val seasonFolder = isSeasonFolder(folderName)
            val effectiveName = if (seasonFolder) parentFolderName else folderName
            val folderTitle = MediaNameParser.cleanFolderName(effectiveName)
            val parsedPairs = videos.map { it to MediaNameParser.parse(it.name) }

            val coverImages = buildList {
                addAll(imagesByFolder[folder].orEmpty())
                if (seasonFolder) addAll(imagesByFolder[parentOf(folder)].orEmpty())
            }
            val cover = usableCover(pickCover(coverImages))

            // 通用下载目录里可能同时存放多部动画，需要按剧名再分组；
            // 有明确文件夹名的目录则默认整个目录属于同一部剧。
            val candidateGroups = if (folderTitle == null) {
                parsedPairs.groupBy { (file, parsed) ->
                    parsed.groupTitle.lowercase().trim().ifBlank { file.remoteId }
                }
            } else {
                mapOf(folderTitle to parsedPairs)
            }

            candidateGroups.forEach { (candidateName, pairs) ->
                val explicitCount = pairs.count { it.second.explicitEpisode }
                val markerCount = pairs.count { it.second.hasEpisodeMarker }
                val distinctGroupTitles = pairs.map { it.second.groupTitle.lowercase().trim() }.distinct()
                val numericPrefixCount = pairs.count { (file, _) ->
                    Regex("""^\d{1,3}(?:[.\s_-]|(?=\p{L})|$)""").containsMatchIn(file.name)
                }
                val titlePrefix = commonPrefix(pairs.map { it.second.groupTitle })
                val folderSuggestsSeries = folderTitle != null && (
                    folderTitle.contains("集") ||
                        folderTitle.contains("季") ||
                        folderTitle.contains("合集") ||
                        folderTitle.contains("系列") ||
                        Regex("""(?i)(season|episode|ep\s*\d+)""").containsMatchIn(folderTitle)
                    )

                // 同一命名文件夹中的「001.xxx」「002.xxx」通常是一季剧集；
                // 数字前缀 + 文件夹名带“集/季/合集”等提示时按剧集归并，避免每集都变成电影。
                val isSeries = when {
                    pairs.size >= 2 && explicitCount >= 2 -> true
                    pairs.size >= 2 && folderTitle != null && titlePrefix.length >= 6 -> true
                    pairs.size >= 2 && folderTitle != null && folderSuggestsSeries -> true
                    pairs.size >= 2 && folderTitle != null &&
                        numericPrefixCount >= 5 && numericPrefixCount * 10 >= pairs.size * 6 -> true
                    pairs.size >= 2 && explicitCount == 0 && markerCount >= 2 && distinctGroupTitles.size == 1 -> true
                    pairs.size == 1 && pairs.first().second.explicitEpisode -> true
                    pairs.size >= 2 && explicitCount >= 1 && distinctGroupTitles.size == 1 -> true
                    else -> false
                }

                if (isSeries) {
                    val parsedTitle = pairs.firstOrNull()?.second?.groupTitle.orEmpty()
                    val title = folderTitle ?: parsedTitle.ifBlank { candidateName.ifBlank { "未命名剧集" } }
                    val key = when {
                        seasonFolder && folderTitle != null -> "${parentOf(folder)}::$title"
                        folderTitle == null -> "$folder::$title"
                        else -> folder
                    }
                    val builder = builders.getOrPut(key) { GroupBuilder(key, SeriesKind.SERIES) }
                    if (builder.title.isBlank()) builder.title = title
                    if (builder.year == null) {
                        builder.year = pairs.firstOrNull { it.second.year != null }?.second?.year
                    }
                    if (builder.coverRef == null) builder.coverRef = cover
                    pairs.forEach { (file, parsed) ->
                        if (builder.files.none { it.file.remoteId == file.remoteId }) {
                            builder.files += GroupedFile(file, parsed)
                        }
                    }
                } else {
                    pairs.forEach { (file, parsed) ->
                        val numericTitle = parsed.title.isBlank() || parsed.title.all { it.isDigit() || it.isWhitespace() }
                        val numericPrefix = Regex("""^\d{1,3}(?:[.\s_-]|$)""").containsMatchIn(parsed.title)
                        val title = when {
                            folderTitle != null && (numericTitle || numericPrefix) -> folderTitle
                            !numericTitle -> parsed.title
                            folderTitle != null -> folderTitle
                            else -> file.name.substringBeforeLast('.')
                        }
                        val key = "$folder::${file.remoteId}"
                        val builder = GroupBuilder(key, SeriesKind.MOVIE)
                        builder.title = title
                        builder.year = parsed.year
                        builder.coverRef = cover
                        builder.files += GroupedFile(file, parsed)
                        builders[key] = builder
                    }
                }
            }
        }

        return builders.values.map { builder ->
            val ordered = builder.files.sortedWith(
                compareBy({ it.parsed.season }, { it.parsed.episode }, { it.file.name.lowercase() })
            )
            LibraryGroup(
                key = builder.key,
                title = builder.title.ifBlank { "未命名" },
                kind = builder.kind,
                year = builder.year,
                coverRef = builder.coverRef,
                files = ordered
            )
        }.sortedWith(compareBy({ it.title.lowercase() }, { it.year ?: 0 }))
    }

    private fun commonPrefix(values: List<String>): String {
        if (values.isEmpty()) return ""
        var prefix = values.first().trim()
        for (value in values.drop(1)) {
            var index = 0
            val other = value.trim()
            while (index < prefix.length && index < other.length &&
                prefix[index].equals(other[index], ignoreCase = true)
            ) {
                index++
            }
            prefix = prefix.substring(0, index).trim()
            if (prefix.length < 3) break
        }
        return prefix.trim()
    }

    private fun parentOf(folder: String): String {
        val trimmed = folder.trim('/')
        if (!trimmed.contains('/')) return "/"
        return "/" + trimmed.substringBeforeLast('/')
    }

    private fun pickCover(images: List<ScannedFile>): ScannedFile? {
        val keywords = listOf("poster", "folder", "cover", "fanart", "thumb", "banner")
        return images
            .filter { image ->
                val name = image.name.lowercase()
                keywords.any { name.contains(it) }
            }
            .sortedBy { image ->
                val name = image.name.lowercase()
                keywords.indexOfFirst { name.contains(it) }.let { if (it < 0) 99 else it }
            }
            .firstOrNull()
    }

    private fun usableCover(file: ScannedFile?): String? {
        val ref = file?.playRef ?: return null
        return if (ref.startsWith("content://") || ref.startsWith("file://")) ref else null
    }

    private class GroupBuilder(val key: String, val kind: String) {
        var title: String = ""
        var year: Int? = null
        var coverRef: String? = null
        val files = mutableListOf<GroupedFile>()
    }
}
