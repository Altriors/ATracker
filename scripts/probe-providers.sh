#!/bin/bash
# Round 2: web-novel coverage, AniList novel filter, Wikidata, Open Library, Jikan retry.
UA="ATracker-personal/0.1 (probe)"
al() { curl -s -m 30 https://graphql.anilist.co -H 'Content-Type: application/json' -H "User-Agent: $UA" \
  -d "$(jq -nc --arg q "$1" --arg cc "$2" '{query:"query($q:String,$cc:CountryCode){Page(perPage:5){media(search:$q,type:MANGA,format:NOVEL,countryOfOrigin:$cc){id idMal format countryOfOrigin status chapters volumes title{romaji english} startDate{year} staff(perPage:2){edges{role node{name{full}}}} relations{edges{relationType node{id type format title{romaji}}}}}}}",variables:{q:$q,cc:(if $cc=="" then null else $cc end)}}')" \
  | jq -r '.errors // (.data.Page.media[] | "  AL#\(.id) mal=\(.idMal) \(.title.romaji) | \(.title.english) | \(.format) \(.countryOfOrigin) \(.status) ch=\(.chapters) vol=\(.volumes) staff=\([.staff.edges[]?|.node.name.full]|join("/")) rel=\([.relations.edges[]?|.relationType+":"+.node.format]|join(","))")' 2>&1 | head -6; }
echo "### AniList NOVEL-only, CN/KR web novels"
for t in "Reverend Insanity" "Gu Zhenren" "Guimi Zhi Zhu" "Lord of the Mysteries" "Omniscient Reader" "Solo Leveling" "Circle of Inevitability" "Release That Witch" "Coiling Dragon" "Overgeared" "The Beginning After the End" "Shadow Slave" "Trash of the Count" ; do echo "== $t"; al "$t" ""; done
echo "### Wikidata"
for t in "Lord of the Mysteries" "Reverend Insanity"; do
 echo "== $t"; curl -s -m 30 -A "$UA" -G "https://www.wikidata.org/w/api.php" --data-urlencode "action=wbsearchentities" --data-urlencode "search=$t" --data-urlencode "language=en" --data-urlencode "format=json" --data-urlencode "limit=4" | jq -r '.search[]? | "  \(.id) \(.label) - \(.description)"'
done
echo "### Open Library"
for t in "Lord of the Mysteries" "Reverend Insanity" "Overlord Maruyama" "Sword Art Online"; do echo "== $t"; curl -s -m 25 -A "$UA" -G "https://openlibrary.org/search.json" --data-urlencode "q=$t" --data-urlencode "limit=3" --data-urlencode "fields=key,title,author_name,first_publish_year,cover_i" | jq -c '.docs[]?' | head -3; done
echo "### Google Books (may be rate limited without key)"
curl -s -m 25 -A "$UA" -G "https://www.googleapis.com/books/v1/volumes" --data-urlencode "q=intitle:Overlord" --data-urlencode "maxResults=2" | head -c 300; echo
echo "### Jikan retry"
for u in https://api.jikan.moe/v4/manga?q=overlord\&limit=2 https://api.jikan.moe/v4/anime/1; do printf "%s -> " "$u"; curl -s -o /dev/null -m 40 -w "%{http_code} %{time_total}s\n" -A "$UA" "$u"; done
echo "### AniList idMal + MAL cover test"
curl -s -m 30 https://graphql.anilist.co -H 'Content-Type: application/json' -d '{"query":"{Media(id:125291){id idMal coverImage{large extraLarge} bannerImage description(asHtml:false) genres tags{name rank isMediaSpoiler} averageScore meanScore siteUrl synonyms}}"}' | jq -c '.data.Media | {id,idMal,cover:.coverImage.large,gen:.genres,score:.averageScore,syn:.synonyms,tags:[.tags[0:3][]|.name]}'
echo "### AniList rate-limit headers"
curl -s -D - -o /dev/null -m 20 https://graphql.anilist.co -H 'Content-Type: application/json' -d '{"query":"{Media(id:1){id}}"}' | grep -i -E "^(HTTP|x-ratelimit)"
