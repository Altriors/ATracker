package app.atracker.data.provider

import app.atracker.data.mal.MalApi
import app.atracker.data.mal.MalKind
import app.atracker.domain.LibraryStatus
import app.atracker.domain.MediaType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewProvidersTest {
    private val kitsuNovels = """{"data":[
      {"id":"2334","attributes":{"canonicalTitle":"Gu Zhenren","titles":{"en":"Reverend Insanity","zh_cn":"蛊真人"},"subtype":"novel","status":"finished","chapterCount":2334,"volumeCount":0,"startDate":"2013-01-01","posterImage":{"large":"https://k/p.jpg"},"averageRating":"82.5","synopsis":"A schemer."}},
      {"id":"9","attributes":{"canonicalTitle":"Mushoku Tensei","titles":{"en_jp":"Mushoku Tensei","ja_jp":"無職転生"},"subtype":"novel","status":"finished","chapterCount":330,"volumeCount":26}},
      {"id":"5","attributes":{"canonicalTitle":"Some Doujin","titles":{"en":"Some Doujin"},"subtype":"doujin"}}
    ]}"""

    @Test fun kitsuSplitsNovelsByOriginalLanguageAndNeverInventsCounts() = runBlocking {
        val p = KitsuProvider(Fixtures.http { 200 to kitsuNovels })
        val r = p.search("novel", null)
        assertEquals(2, r.size) // doujin is not a tracked category
        val web = r.first { it.externalId == "2334" }
        assertEquals(MediaType.WEB_NOVEL, web.mediaType); assertEquals(2334, web.totalChapters)
        assertNull("volumeCount 0 means unknown on Kitsu", web.totalVolumes)
        assertEquals(82, web.averageScore)
        val ln = r.first { it.externalId == "9" }
        assertEquals(MediaType.LIGHT_NOVEL, ln.mediaType); assertEquals(26, ln.totalVolumes)
    }

    @Test fun kitsuRequestedTypeFiltersResults() = runBlocking {
        val p = KitsuProvider(Fixtures.http { 200 to kitsuNovels })
        assertEquals(listOf("9"), p.search("x", MediaType.LIGHT_NOVEL).map { it.externalId })
    }

    private val mu = """{"results":[
      {"record":{"series_id":55,"title":"Overgeared (Novel)","url":"https://mu/55","description":"Grid.","image":{"url":{"original":"https://mu/o.jpg"}},"type":"Novel","year":"2014","bayesian_rating":8.1,"genres":[{"genre":"Fantasy"}]}},
      {"record":{"series_id":56,"title":"Some Doujin","type":"Doujinshi","genres":[]}},
      {"record":{"series_id":57,"title":"Adult One","type":"Manga","genres":[{"genre":"Adult"}]}}
    ]}"""

    @Test fun mangaUpdatesKeepsNovelsAndDropsDoujinshiAndAdult() = runBlocking {
        val p = MangaUpdatesProvider(Fixtures.http { 200 to mu })
        val r = p.search("overgeared", null)
        assertEquals(1, r.size)
        assertEquals(MediaType.WEB_NOVEL, r[0].mediaType); assertEquals("55", r[0].externalId); assertEquals(81, r[0].averageScore)
        assertNull(r[0].totalChapters)
    }

    @Test fun malApiNodeKeepsEveryProvidedField() {
        val json = """{"node":{"id":44347,"title":"One-Punch Man","num_chapters":0,"num_volumes":30,"media_type":"manga"},
          "list_status":{"status":"on_hold","score":8,"num_volumes_read":10,"num_chapters_read":90,"is_rereading":false,"num_times_reread":2,"start_date":"2021-03-01","tags":["action"],"comments":"slow"}}"""
        val r = MalApi.parseNode(MalKind.MANGA, lenientJson.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject)!!
        assertEquals(44347, r.malId); assertEquals(LibraryStatus.ON_HOLD, r.status); assertEquals(90, r.progress); assertEquals(10, r.volumes)
        assertEquals(8, r.score); assertEquals(2, r.repeatCount); assertEquals("2021-03-01", r.startDate); assertNull(r.finishDate)
        assertEquals(listOf("action"), r.tags); assertEquals("slow", r.comments); assertNull(r.total); assertEquals(30, r.totalVolumes)
        assertTrue(r.externalId == "manga:44347")
    }
}
