package com.juying.tv

import com.juying.tv.data.ai.AiJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiJsonTest {

    @Test
    fun parsesSeriesPlanWithCodeFence() {
        val content = """
            ```json
            {
              "series_title": "数字积木",
              "episodes": [
                {"index": 1, "season": 1, "episode": 1, "title": "一"},
                {"index": 2, "season": 1, "episode": 2, "title": "二"}
              ]
            }
            ```
        """.trimIndent()
        val plan = AiJson.parseSeriesPlan(content, 2)
        assertNotNull(plan)
        assertEquals("数字积木", plan?.seriesTitle)
        assertEquals(2, plan?.episodes?.size)
        assertEquals(2, plan?.episodes?.get(1)?.episode)
    }

    @Test
    fun rejectsInvalidIndexWhenNoFileName() {
        val content = """{"episodes":[{"index":99,"season":1,"episode":1,"title":"x"}]}"""
        val plan = AiJson.parseSeriesPlan(content, 3)
        assertEquals(null, plan)
    }

    @Test
    fun parsesPosterJson() {
        val poster = AiJson.parsePoster(
            """{"image_url":"https://img.example.com/a.jpg","page_url":"https://example.com/a","confidence":0.9}"""
        )
        assertNotNull(poster)
        assertTrue(poster?.imageUrl?.endsWith("a.jpg") == true)
        assertEquals(0.9, poster?.confidence ?: 0.0, 0.001)
    }
}
