package com.example.newtube.database

import com.example.newtube.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ShortsFeedMappingTest {
    @Test fun `entity carries metadata only and round-trips to Video`() {
        val video = Video("id1", "Title", "Chan", "UC1", "https://thumb", 42L, 100L, "2 d", false, "avatar")
        val entity = video.toShortsFeedEntity(position = 3, cachedAt = 1234L)
        assertEquals(3, entity.position)
        assertEquals(1234L, entity.cachedAt)
        assertEquals(video, entity.toVideo())
        // Controller ruling: no kotlin-reflect; Java reflection on declared fields.
        assertFalse(
            ShortsFeedEntity::class.java.declaredFields.any { field ->
                field.name.contains("url", true) && field.name.contains("stream", true)
            },
        )
    }
}
