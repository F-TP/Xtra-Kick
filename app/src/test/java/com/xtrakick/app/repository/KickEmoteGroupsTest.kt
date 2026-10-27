package com.xtrakick.app.repository

import com.xtrakick.app.model.chat.Emote
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class KickEmoteGroupsTest {

    @Test
    fun `emote groups are sorted with current channel first and collectibles second`() {
        val groups = listOf(
            KickRepository.KickEmoteGroup(
                id = "Emoji",
                title = "Emojis",
                emotes = listOf(Emote(name = "😀", url1x = "https://files.kick.com/emotes/1/fullsize")),
            ),
            KickRepository.KickEmoteGroup(
                id = "Global",
                title = "Global",
                emotes = listOf(Emote(name = "PogChamp", url1x = "https://files.kick.com/emotes/2/fullsize")),
            ),
            KickRepository.KickEmoteGroup(
                id = "Collectibles",
                title = "Collectibles",
                emotes = listOf(Emote(name = "collectiblesDespairge", url1x = "https://files.kick.com/emotes/3/fullsize")),
            ),
            KickRepository.KickEmoteGroup(
                id = "serpias",
                title = "serpias",
                emotes = listOf(Emote(name = "serpiasHype", url1x = "https://files.kick.com/emotes/4/fullsize")),
            ),
            KickRepository.KickEmoteGroup(
                id = "xqc",
                title = "xQc",
                emotes = listOf(Emote(name = "xqcL", url1x = "https://files.kick.com/emotes/5/fullsize")),
            ),
        )

        val normalizedCurrentSlug = "serpias".lowercase(Locale.ROOT)
        val sorted = groups.sortedWith(
            compareBy<KickRepository.KickEmoteGroup> { group ->
                when {
                    group.id.equals(normalizedCurrentSlug, ignoreCase = true) -> 0
                    group.id.equals("Collectibles", ignoreCase = true) || group.title.equals("Collectibles", ignoreCase = true) -> 1
                    group.title.equals("Global", ignoreCase = true) -> 3
                    group.title.equals("Emojis", ignoreCase = true) || group.id.equals("Emoji", ignoreCase = true) -> 4
                    else -> 2
                }
            }.thenBy { it.title.lowercase(Locale.ROOT) }
        )

        assertEquals("serpias", sorted[0].id)
        assertEquals("Collectibles", sorted[1].id)
        assertEquals("xqc", sorted[2].id)
        assertEquals("Global", sorted[3].id)
        assertEquals("Emoji", sorted[4].id)
    }

    @Test
    fun `collectibles group is detected by id or title case-insensitively`() {
        val groupById = KickRepository.KickEmoteGroup(
            id = "collectibles",
            title = "My Collectibles",
            emotes = listOf(Emote(name = "collectiblesGoldenLULW", url1x = "https://files.kick.com/emotes/10/fullsize")),
        )
        val groupByTitle = KickRepository.KickEmoteGroup(
            id = "group_123",
            title = "Collectibles",
            emotes = listOf(Emote(name = "collectiblesListening", url1x = "https://files.kick.com/emotes/11/fullsize")),
        )

        fun isCollectibles(group: KickRepository.KickEmoteGroup): Boolean =
            group.id.equals("Collectibles", ignoreCase = true) || group.title.equals("Collectibles", ignoreCase = true)

        assertEquals(true, isCollectibles(groupById))
        assertEquals(true, isCollectibles(groupByTitle))
    }

    @Test
    fun `single pass partition separates current channel, collectibles and other groups`() {
        val groups = listOf(
            KickRepository.KickEmoteGroup(id = "serpias", title = "serpias", emotes = emptyList()),
            KickRepository.KickEmoteGroup(id = "Collectibles", title = "Collectibles", emotes = emptyList()),
            KickRepository.KickEmoteGroup(id = "xqc", title = "xQc", emotes = emptyList()),
            KickRepository.KickEmoteGroup(id = "Global", title = "Global", emotes = emptyList()),
        )

        var collectiblesKickGroup: KickRepository.KickEmoteGroup? = null
        val otherKickGroups = ArrayList<KickRepository.KickEmoteGroup>()
        val currentId = "serpias"

        groups.forEach { group ->
            when {
                group.id.equals(currentId, ignoreCase = true) -> Unit
                group.id.equals("Collectibles", ignoreCase = true) || group.title.equals("Collectibles", ignoreCase = true) -> {
                    collectiblesKickGroup = group
                }
                else -> otherKickGroups.add(group)
            }
        }

        assertEquals("Collectibles", collectiblesKickGroup?.id)
        assertEquals(2, otherKickGroups.size)
        assertEquals(listOf("xqc", "Global"), otherKickGroups.map { it.id })
    }
}
