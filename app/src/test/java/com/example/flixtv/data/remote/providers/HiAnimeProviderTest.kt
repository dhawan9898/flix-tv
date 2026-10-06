package com.example.flixtv.data.remote.providers

import com.example.flixtv.data.remote.HttpFetcher
import com.example.flixtv.domain.models.MediaItem
import com.example.flixtv.domain.models.SkipRange
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HiAnimeProviderTest {

    private val provider = HiAnimeProvider(HttpFetcher())

    private fun card(href: String, title: String, poster: String, extra: String = "") = """
        <div class="flw-item">
          <div class="film-poster"><img data-src="$poster" src="data:image/gif;base64,xx"/></div>
          <div class="film-detail">
            <h3 class="film-name"><a href="$href" title="$title">$title</a></h3>
            <div class="fd-infor"><span class="fdi-item">TV</span>$extra</div>
          </div>
        </div>"""

    @Test
    fun parsesCardsAndDropsJunk() {
        val html = """
            <html><body>
            ${card("/solo-leveling-19413", "Solo Leveling", "https://img.example/a.jpg", "<div class=\"tick-eps\">12</div>")}
            ${card("/solo-leveling-19413", "Solo Leveling (clone)", "https://img.example/a.jpg")}
            ${card("/load-more", "Load more", "https://img.example/b.jpg")}
            ${card("/no-poster-5", "No Poster", "")}
            ${card("/movie-one-77", "Movie One", "//img.example/c.jpg")}
            <div id="main-sidebar">${card("/sidebar-show-9", "Sidebar Show", "https://img.example/s.jpg")}</div>
            </body></html>"""
        val items = provider.parseCards(html)
        assertEquals(listOf("hianime_solo-leveling-19413", "hianime_movie-one-77"), items.map { it.id })
        assertEquals("12 Episodes", items[0].durationOrEpisodes)
        assertEquals("https://img.example/a.jpg", items[0].posterUrl)
        assertEquals("https://img.example/c.jpg", items[1].posterUrl)
        assertNull(items[0].streamUrl)
        assertEquals("Anime", items[0].category)
    }

    @Test
    fun slugOnlyForSiteItems() {
        val site = MediaItem(id = "hianime_naruto-677", title = "Naruto", posterUrl = "", provider = "HiAnime")
        val legacy = MediaItem(id = "hianime_solo_leveling_18721", title = "Solo", posterUrl = "", provider = "HiAnime")
        val anilist = MediaItem(id = "anime_1", title = "X", posterUrl = "", category = "Anime", provider = "Flexeo")
        assertEquals("naruto-677", HiAnimeProvider.slugOf(site))
        assertNull(HiAnimeProvider.slugOf(legacy))
        assertNull(HiAnimeProvider.slugOf(anilist))
        assertTrue(HiAnimeProvider.isAnime(anilist))
    }

    @Test
    fun picksServersOfRequestedModeFirstThenAnyAndCapsAtFour() {
        val html = """
            <div data-type="sub" data-hash="s1"></div><div data-type="dub" data-hash="d1"></div>
            <div data-type="sub" data-hash="s2"></div><div data-type="sub" data-hash="s1"></div>
            <div data-type="sub" data-hash="s3"></div><div data-type="sub" data-hash="s4"></div>
            <div data-type="sub" data-hash="s5"></div><div data-type="sub"></div>"""
        assertEquals(listOf("s1", "s2", "s3", "s4"), provider.pickServerHashes(html, "sub"))
        assertEquals(listOf("d1"), provider.pickServerHashes(html, "dub"))
        val subOnly = """<div data-type="sub" data-hash="s1"></div>"""
        assertEquals(listOf("s1"), provider.pickServerHashes(subOnly, "dub"))
        assertTrue(provider.pickServerHashes("", "sub").isEmpty())
    }

    @Test
    fun parsesSkipRangesInAllShapes() {
        assertEquals(SkipRange(85_000, 175_000), provider.parseSkipRange(JSONObject("""{"start":85,"end":175}""")))
        assertEquals(SkipRange(1_500, 9_000), provider.parseSkipRange(JSONArray("[1.5, \"9\"]")))
        assertNull(provider.parseSkipRange(JSONObject("""{"start":0,"end":0}""")))
        assertNull(provider.parseSkipRange(JSONObject("""{"start":50,"end":10}""")))
        assertNull(provider.parseSkipRange(null))
    }
}
