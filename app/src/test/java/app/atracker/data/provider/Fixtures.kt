package app.atracker.data.provider

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** Trimmed copies of REAL responses captured from the live APIs by the probe workflow. */
object Fixtures {
    const val ANILIST_LOTM_MANGA = """{"data":{"Page":{"media":[{"id":125291,"idMal":null,"type":"MANGA","format":"MANGA","status":"FINISHED","countryOfOrigin":"CN","chapters":65,"volumes":null,"episodes":null,"duration":null,"description":"With the rising tide of steam power and machinery, who can come close to being a Beyonder?<br><br>(Source: Tencent)","genres":["Action","Drama","Fantasy","Mystery","Thriller"],"synonyms":[],"averageScore":75,"siteUrl":"https://anilist.co/manga/125291","isAdult":false,"coverImage":{"large":"https://s4.anilist.co/c-large.png","extraLarge":"https://s4.anilist.co/c-xl.png"},"startDate":{"year":2020,"month":1,"day":21},"endDate":{"year":2020,"month":null,"day":null},"title":{"romaji":"Guimi Zhi Zhu","english":"Lord of the Mysteries","native":"诡秘之主"},"studios":{"nodes":[]},"staff":{"edges":[{"role":"Story & Art","node":{"name":{"full":"V"}}},{"role":"Original Story","node":{"name":{"full":"Ai Qianshui de Wuzei"}}}]},"relations":{"edges":[{"relationType":"ALTERNATIVE","node":{"id":137667,"type":"ANIME","format":"ONA","countryOfOrigin":"CN","title":{"romaji":"Guimi Zhi Zhu: Xiaochou Pian","english":"Lord of Mysteries"}}}]},"nextAiringEpisode":null}]}}}"""

    const val ANILIST_LOTM_ANIME = """{"data":{"Media":{"id":137667,"idMal":49818,"type":"ANIME","format":"ONA","status":"FINISHED","countryOfOrigin":"CN","chapters":null,"volumes":null,"episodes":13,"duration":35,"description":"In a Victorian world","genres":["Action","Mystery"],"synonyms":["LOTM","诡秘之主"],"averageScore":85,"siteUrl":"https://anilist.co/anime/137667","isAdult":false,"coverImage":{"large":"https://s4.anilist.co/a.jpg","extraLarge":null},"startDate":{"year":2025,"month":6,"day":28},"endDate":{"year":2025,"month":9,"day":13},"title":{"romaji":"Guimi Zhi Zhu: Xiaochou Pian","english":"Lord of Mysteries","native":"诡秘之主 小丑篇​"},"studios":{"nodes":[{"name":"B.CMAY PICTURES"}]},"staff":{"edges":[{"role":"Original Story","node":{"name":{"full":"Ai Qianshui de Wuzei"}}},{"role":"Director","node":{"name":{"full":"Ke Xiong"}}}]},"relations":{"edges":[{"relationType":"SEQUEL","node":{"id":206874,"type":"ANIME","format":"ONA","countryOfOrigin":"CN","title":{"romaji":"Guimi Zhi Zhu: Wu Mian Ren Pian","english":null}}}]},"nextAiringEpisode":{"episode":9,"airingAt":1795788000}}}}"""

    const val ANILIST_MANHWA_UNKNOWN_TOTAL = """{"data":{"Media":{"id":119257,"idMal":132214,"type":"MANGA","format":"MANGA","status":"RELEASING","countryOfOrigin":"KR","chapters":null,"volumes":null,"episodes":null,"duration":null,"description":null,"genres":["Action"],"synonyms":["ORV"],"averageScore":86,"siteUrl":"https://anilist.co/manga/119257","coverImage":{"large":"https://s4.anilist.co/o.jpg"},"startDate":{"year":2020,"month":5,"day":26},"endDate":{"year":null,"month":null,"day":null},"title":{"romaji":"Jeonjijeok Dokja Sijeom","english":"Omniscient Reader","native":"전지적 독자 시점"},"studios":{"nodes":[]},"staff":{"edges":[{"role":"Story","node":{"name":{"full":"UMI"}}},{"role":"Art","node":{"name":{"full":"Sleepy-C"}}},{"role":"Original Story","node":{"name":{"full":"Singsyong"}}},{"role":"Original Character Design","node":{"name":{"full":"Geomnem"}}},{"role":"Translator (German)","node":{"name":{"full":"Jessica Walther"}}}]},"relations":{"edges":[]},"nextAiringEpisode":null}}}"""

    const val ANILIST_ERROR = """{"errors":[{"message":"Too Many Requests.","status":429}],"data":null}"""

    const val MANGADEX_REVEREND = """{"result":"ok","response":"collection","data":[{"id":"e1809b04-f7dd-4327-9669-ca83884a1403","type":"manga","attributes":{"title":{"en":"Reverend Insanity Remake"},"altTitles":[{"zh":"蛊真人"},{"vi":"Cổ Chân Nhân"}],"description":{"en":"A story of a villain, **Fang Yuan**"},"links":{"mu":"i2saq3p"},"originalLanguage":"zh","lastVolume":"","lastChapter":"","status":"ongoing","year":2025,"contentRating":"suggestive","tags":[{"attributes":{"name":{"en":"Action"},"group":"genre"}},{"attributes":{"name":{"en":"Reincarnation"},"group":"theme"}},{"attributes":{"name":{"en":"Full Color"},"group":"format"}}]},"relationships":[{"id":"a","type":"author","attributes":{"name":"Gu Zhen Ren"}},{"id":"b","type":"artist","attributes":{"name":"LenHC"}},{"id":"c","type":"cover_art","attributes":{"fileName":"a635e356.png"}}]},{"id":"bb71e1b9-8773-4b44-85b2-68ed44d124eb","type":"manga","attributes":{"title":{"en":"Reverend Insanity"},"altTitles":[{"en":"Gu Daoist Master"},{"zh":"蛊真人"}],"description":{"en":"Fang Yuan was reborn"},"links":{"al":"108050","mu":"cog95lb"},"originalLanguage":"zh","lastVolume":"","lastChapter":"96","status":"cancelled","year":2018,"tags":[]},"relationships":[{"id":"x","type":"author","attributes":{"name":"Gu Zhen Ren"}}]}]}"""

    const val WIKIDATA_SEARCH_LOTM = """{"search":[{"id":"Q109342457","label":"Lord of Mysteries","description":"2018 Chinese web novel series"},{"id":"Q136027036","label":"Lord of Mysteries","description":"2025 Chinese animated web series based on the web novel series"},{"id":"Q136028097","label":"Lord of Mysteries: The Clown","description":"first season of the 2025 Chinese animated series"}]}"""

    const val WIKIDATA_ENTITY_LOTM = """{"entities":{"Q109342457":{"labels":{"en":{"language":"en","value":"Lord of Mysteries"}},"descriptions":{"en":{"language":"en","value":"2018 Chinese web novel series"}},"aliases":{"en":[{"value":"LotM"},{"value":"Lord of the Mysteries"},{"value":"Guimi zhi zhu"}]},"claims":{"P50":[{"mainsnak":{"datavalue":{"value":{"id":"Q110107880"}}}}],"P577":[{"mainsnak":{"datavalue":{"value":{"time":"+2020-00-00T00:00:00Z","precision":9}}}}],"P1476":[{"mainsnak":{"datavalue":{"value":{"text":"诡秘之主","language":"zh"}}}}]}}}}"""

    const val WIKIDATA_AUTHOR = """{"entities":{"Q110107880":{"labels":{"en":{"value":"Cuttlefish That Loves Diving"}}}}}"""

    const val WIKIDATA_ENTITY_BARE = """{"entities":{"Q137441819":{"labels":{"en":{"value":"Reverend Insanity"}},"descriptions":{"en":{"value":"Chinese xianxia novel by Gu Zhen Ren"}},"claims":{"P31":[{"mainsnak":{"datavalue":{"value":{"id":"Q47461344"}}}}]}}}}"""

    /** An OkHttp client that answers from a handler instead of the network. */
    fun client(handler: (Request) -> Pair<Int, String>): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val (code, body) = handler(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fake")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build()

    fun http(handler: (Request) -> Pair<Int, String>) =
        ApiHttp(RateLimiter(0), client(handler), maxAttempts = 3, backoffMs = 0, maxRetryAfterMs = 0)
}
