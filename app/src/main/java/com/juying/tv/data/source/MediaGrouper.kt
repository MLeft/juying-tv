package com.juying.tv.data.source

import com.juying.tv.data.db.SeriesKind

object MediaGrouper {

    fun group(files: List<ScannedFile>): List<LibraryGroup> {
        val videosByFolder = files.filter { it.kind == ScannedKind.VIDEO }
            .groupBy { it.parentPath }
        val imagesByFolder = files.filter { it.kind == ScannedKind.IMAGE }
            .groupBy { it.parentPath }

        val builders = LinkedHashMap<String, GroupBuilder>()

        // 课程结构识别：某些目录下不是“多部剧”，而是同一课程的多个级别/季。
        // 例如 Wow English 的“完整版无字幕/第一级…第八级”，应是一部剧的 8 季。
        val allFolders = linkedSetOf<String>()
        videosByFolder.keys.forEach { leaf ->
            var current = leaf
            while (true) {
                allFolders += current
                if (current == "/") break
                current = parentOf(current)
            }
        }
        val childrenByFolder = HashMap<String, MutableList<String>>()
        allFolders.forEach { folder ->
            if (folder != "/") {
                childrenByFolder.getOrPut(parentOf(folder)) { mutableListOf() }.add(folder)
            }
        }

        val courseRoots = LinkedHashMap<String, CourseCandidate>()
        allFolders.forEach { folder ->
            val children = childrenByFolder[folder].orEmpty()
            if (children.size < 2) return@forEach
            val folderName = nameOf(folder)
            val title = MediaNameParser.cleanFolderName(folderName) ?: folderName
            val courseKeyword = listOf("材料", "课程", "教程", "教材", "分级")
                .any { title.contains(it) }
            val rawLevels = LinkedHashMap<String, Int>()
            children.forEach { child ->
                val childName = nameOf(child)
                val parsedLevel = levelNumberFromName(childName)
                if (parsedLevel != null) {
                    rawLevels[child] = parsedLevel
                } else if (courseKeyword) {
                    val leading = leadingNumber(childName)
                    if (leading != null) rawLevels[child] = leading
                }
            }
            if (rawLevels.size >= 2 && rawLevels.size * 10 >= children.size * 6) {
                val used = mutableSetOf<Int>()
                val levels = LinkedHashMap<String, Int>()
                rawLevels.forEach { (child, raw) ->
                    var season = raw.coerceAtLeast(1)
                    while (!used.add(season)) season++
                    levels[child] = season
                }
                courseRoots[folder] = CourseCandidate(
                    path = folder,
                    title = title.ifBlank { folderName },
                    levelSeasons = levels,
                    coverRef = coverFor(folder, imagesByFolder)
                )
            }
        }

        val assignments = HashMap<String, Pair<CourseCandidate, Int>>()
        videosByFolder.keys.forEach { leaf ->
            val candidate = ancestorsOf(leaf)
                .filter { courseRoots.containsKey(it) }
                .maxByOrNull { it.length }
            if (candidate != null) {
                val course = courseRoots[candidate]!!
                val relative = leaf.removePrefix(candidate).trim('/')
                val first = relative.substringBefore('/')
                val season = if (first.isBlank()) 1
                else course.levelSeasons[candidate + "/" + first] ?: 1
                assignments[leaf] = course to season
            }
        }

        assignments.forEach { (leaf, pair) ->
            val (course, season) = pair
            val builder = builders.getOrPut("course::${course.path}") {
                GroupBuilder("course::${course.path}", SeriesKind.SERIES).apply {
                    title = course.title.ifBlank { nameOf(course.path) }
                    coverRef = course.coverRef
                }
            }
            val parsedPairs = videosByFolder[leaf].orEmpty().map {
                it to MediaNameParser.parse(it.name).copy(season = season)
            }
            parsedPairs.forEach { (file, parsed) ->
                if (builder.files.none { it.file.remoteId == file.remoteId }) {
                    builder.files += GroupedFile(file, parsed)
                }
            }
        }

        videosByFolder.filterKeys { it !in assignments.keys }.forEach { (folder, videos) ->
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

    private fun nameOf(path: String): String = path.trim('/').substringAfterLast('/')

    private fun ancestorsOf(path: String): List<String> {
        val result = mutableListOf<String>()
        var current = path
        while (true) {
            result += current
            if (current == "/") break
            current = parentOf(current)
        }
        return result
    }

    private fun levelNumberFromName(name: String): Int? {
        Regex("""第\s*([0-9一二三四五六七八九十百]+)\s*[级季]""").find(name)?.let {
            return parseChineseNumber(it.groupValues[1])
        }
        Regex("""(?i)\b(?:season|level)\s*(\d{1,2})""").find(name)?.let {
            return it.groupValues[1].toIntOrNull()
        }
        return null
    }

    private fun leadingNumber(name: String): Int? =
        Regex("""^0*(\d{1,2})(?:[.\s_-]|$)""").find(name)?.groupValues?.get(1)?.toIntOrNull()

    private fun parseChineseNumber(value: String): Int? {
        if (value.all { it.isDigit() }) return value.toIntOrNull()
        val digits = mapOf(
            '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5,
            '六' to 6, '七' to 7, '八' to 8, '九' to 9
        )
        if (value == "十") return 10
        if ('十' in value) {
            val parts = value.split('十')
            val tens = if (parts[0].isBlank()) 1 else digits[parts[0].firstOrNull()] ?: return null
            val ones = if (parts.size > 1 && parts[1].isNotBlank()) {
                digits[parts[1].firstOrNull()] ?: 0
            } else 0
            return tens * 10 + ones
        }
        return digits[value.firstOrNull()] ?: null
    }

    private fun coverFor(folder: String, imagesByFolder: Map<String, List<ScannedFile>>): String? {
        val images = imagesByFolder
            .filterKeys { it == folder || it.startsWith("$folder/") }
            .values.flatten()
        return usableCover(pickCover(images))
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

    private data class CourseCandidate(
        val path: String,
        val title: String,
        val levelSeasons: Map<String, Int>,
        val coverRef: String?
    )

    private class GroupBuilder(val key: String, val kind: String) {
        var title: String = ""
        var year: Int? = null
        var coverRef: String? = null
        val files = mutableListOf<GroupedFile>()
    }
}
