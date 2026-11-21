#!/bin/bash
# Probes the real metadata APIs with sample titles. Runs in CI only (the dev sandbox has no access).
UA="ATracker-personal/0.1 (probe)"
anilist() { # $1=search $2=type(ANIME|MANGA)
  curl -s -m 30 https://graphql.anilist.co -H 'Content-Type: application/json' -H "User-Agent: $UA" \
   -d "$(jq -nc --arg q "$1" --arg t "$2" '{query:"query($q:String,$t:MediaType){Page(perPage:4){media(search:$q,type:$t){id format countryOfOrigin status chapters volumes episodes startDate{year} title{romaji english native} relations{edges{relationType node{id type format title{romaji}}}}}}}",variables:{q:$q,t:$t}}')" \
  | jq -r '.errors // (.data.Page.media[] | "  AL#\(.id) \(.title.romaji) | \(.title.english) | fmt=\(.format) cc=\(.countryOfOrigin) st=\(.status) ch=\(.chapters) vol=\(.volumes) ep=\(.episodes) yr=\(.startDate.year) rel=\(.relations.edges|length)")' 2>&1 | head -6
}
jikan() { # $1=q $2=endpoint(anime|manga)
  curl -s -m 30 -A "$UA" "https://api.jikan.moe/v4/$2?q=$(jq -rn --arg q "$1" '$q|@uri')&limit=3" \
  | jq -r '(.data // [])[] | "  MAL#\(.mal_id) \(.title) | type=\(.type) st=\(.status) ch=\(.chapters) vol=\(.volumes) ep=\(.episodes)"' 2>&1 | head -4
  sleep 1.2
}
mangadex() {
  curl -s -m 30 -A "$UA" -G "https://api.mangadex.org/manga" --data-urlencode "title=$1" --data-urlencode "limit=3" --data-urlencode "includes[]=cover_art" \
  | jq -r '(.data // [])[] | "  MD#\(.id[0:8]) \(.attributes.title|to_entries[0].value) | lang=\(.attributes.originalLanguage) st=\(.attributes.status) lastCh=\(.attributes.lastChapter) lastVol=\(.attributes.lastVolume) cover=\([.relationships[]|select(.type=="cover_art")|.attributes.fileName][0])"' 2>&1 | head -4
}
echo "### reachability"
for u in https://graphql.anilist.co https://api.jikan.moe/v4/anime/1 https://api.mangadex.org/ping https://uploads.mangadex.org https://openlibrary.org https://www.googleapis.com/books/v1/volumes?q=test; do
  printf "%s -> " "$u"; curl -s -o /dev/null -m 20 -w "%{http_code}\n" -A "$UA" "$u"; done
for t in "Reverend Insanity" "Lord of the Mysteries" "Omniscient Reader" "Tower of God" "Eleceed" "Mushoku Tensei" "Kino no Tabi" "Boogiepop" "Spice and Wolf" "Frieren"; do
  echo; echo "=== $t"
  echo " anilist ANIME:"; anilist "$t" ANIME
  echo " anilist MANGA(+novels/manhwa/manhua):"; anilist "$t" MANGA
  echo " jikan manga:"; jikan "$t" manga
  echo " mangadex:"; mangadex "$t"
done
echo; echo "### anilist airing schedule + popular sample"
curl -s -m 30 https://graphql.anilist.co -H 'Content-Type: application/json' -d '{"query":"{Page(perPage:3){media(type:ANIME,status:RELEASING,sort:POPULARITY_DESC){id title{romaji} nextAiringEpisode{episode airingAt}}}}"}' | jq -c '.data.Page.media[]?' | head -3
echo "### openlibrary sample"; curl -s -m 20 "https://openlibrary.org/search.json?q=sword+art+online&limit=2&fields=key,title,author_name,first_publish_year" | jq -c '.docs[]?' | head -2
echo "### google books sample"; curl -s -m 20 "https://www.googleapis.com/books/v1/volumes?q=intitle:overlord+light+novel&maxResults=2" | jq -c '(.items // [])[] | {t:.volumeInfo.title,a:.volumeInfo.authors}' | head -2
