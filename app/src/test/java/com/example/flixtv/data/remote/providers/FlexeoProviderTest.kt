package com.example.flixtv.data.remote.providers

import com.example.flixtv.data.remote.HttpFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlexeoProviderTest {

    private val provider = FlexeoProvider(HttpFetcher())

    @Test
    fun parsesCardsDropsAnimeAndDuplicatesAndIdless() {
        val html = """
            <a class="movie-card" href="/movie/533535"><img data-src="/p/a.jpg"/><h3>Deadpool</h3></a>
            <div class="film-item"><a href="/tv/94605-arcane"><img src="https://img/x.jpg"/></a><h3>Arcane</h3></div>
            <a class="movie-card" href="/movie/533535"><img src="https://img/dup.jpg"/><h3>Dup</h3></a>
            <a class="movie-card" href="/anime/tv/1234"><img src="https://img/n.jpg"/><h3>Some Anime</h3></a>
            <a class="movie-card" href="/about"><img src="https://img/n.jpg"/><h3>No Id</h3></a>
            <a class="movie-card" href="/movie/55"><h3>No Poster</h3></a>"""
        val items = provider.parseCards(html)
        assertEquals(listOf("flexeo_533535", "flexeo_94605"), items.map { it.id })
        assertEquals("https://flexeo.tv/p/a.jpg", items[0].posterUrl)
        assertEquals("Movie", items[0].category)
        assertEquals("TV Show", items[1].category)
        assertNull(items[0].streamUrl)
        assertTrue(items[1].embedUrl!!.endsWith("/embed/tv/94605/1/1"))
    }

    @Test
    fun searchSkipsAnimeAndPeopleAndKeepsTv() {
        val json = """{"results":[
            {"id":1,"title":"Movie","poster_path":"/m.jpg","release_date":"2024-05-01","vote_average":7.8,"genre_ids":[28]},
            {"id":2,"name":"Show","poster_path":"/s.jpg","first_air_date":"2023-01-01","media_type":"tv"},
            {"id":3,"name":"JP Cartoon","poster_path":"/a.jpg","genre_ids":[16],"original_language":"ja"},
            {"id":4,"title":"Pixar","poster_path":"/p.jpg","genre_ids":[16],"original_language":"en"},
            {"id":5,"name":"Actor","media_type":"person","poster_path":"/x.jpg"},
            {"id":6,"title":"Tagged","poster_path":"/t.jpg","media_type":"anime"},
            {"id":7,"title":"No Poster"}]}"""
        val items = provider.parseSearch(json)
        assertEquals(listOf("flexeo_1", "flexeo_2", "flexeo_4"), items.map { it.id })
        assertEquals("78% Match", items[0].rating)
        assertEquals("2024", items[0].releaseYear)
        assertEquals("TV Show", items[1].category)
        assertEquals("https://image.tmdb.org/t/p/w500/m.jpg", items[0].posterUrl)
    }
}
