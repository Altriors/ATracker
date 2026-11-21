#!/bin/bash
# Round 3: capture real response shapes using the exact queries the app uses.
UA="ATracker-personal/0.1 (probe)"
FRAG=$(cat scripts/queries/anilist_media.graphql)
gql() { curl -s -m 30 https://graphql.anilist.co -H 'Content-Type: application/json' -H "User-Agent: $UA" -d "$(jq -nc --arg q "$1" --argjson v "$2" '{query:$q,variables:$v}')"; }
trim() { jq -c 'walk(if type=="string" and length>90 then .[0:90]+"…" else . end)'; }
echo "### ANILIST SEARCH (Lord of the Mysteries, MANGA)"
gql "$FRAG query(\$q:String,\$type:MediaType){Page(page:1,perPage:2){pageInfo{hasNextPage} media(search:\$q,type:\$type,isAdult:false,sort:SEARCH_MATCH){...M}}}" '{"q":"Lord of the Mysteries","type":"MANGA"}' | trim
echo "### ANILIST DETAIL anime airing (Lord of Mysteries ONA 137667)"
gql "$FRAG query(\$id:Int){Media(id:\$id){...M}}" '{"id":137667}' | trim
echo "### ANILIST NOVEL format search (no country filter)"
for t in "Solo Leveling" "Lord of the Mysteries" "Omniscient Reader"; do echo "== $t"; gql "query(\$q:String){Page(perPage:5){media(search:\$q,type:MANGA,format:NOVEL){id format countryOfOrigin title{romaji english}}}}" "{\"q\":\"$t\"}" | trim; done
echo "### ANILIST popular manhwa"
gql "$FRAG query{Page(page:1,perPage:2){media(type:MANGA,countryOfOrigin:KR,isAdult:false,sort:POPULARITY_DESC){...M}}}" '{}' | trim
echo "### MANGADEX SEARCH (Reverend Insanity)"
curl -s -m 30 -A "$UA" -G "https://api.mangadex.org/manga" --data-urlencode "title=Reverend Insanity" --data-urlencode "limit=2" --data-urlencode "includes[]=cover_art" --data-urlencode "includes[]=author" --data-urlencode "includes[]=artist" --data-urlencode "contentRating[]=safe" --data-urlencode "contentRating[]=suggestive" | trim
echo "### WIKIDATA ENTITY (Lord of Mysteries Q109342457)"
curl -s -m 30 -A "$UA" -G "https://www.wikidata.org/w/api.php" --data-urlencode "action=wbgetentities" --data-urlencode "ids=Q109342457|Q137441819" --data-urlencode "props=labels|descriptions|claims|aliases|sitelinks/urls" --data-urlencode "languages=en" --data-urlencode "format=json" | jq -c '.entities | map_values({label:.labels.en.value, desc:.descriptions.en.value, aliases:[.aliases.en[]?.value], claims:(.claims|to_entries|map({(.key):(.value[0].mainsnak.datavalue.value|if type=="object" then (.id // .time // .amount // .text // "obj") else . end)})|add), sitelinks:(.sitelinks|keys|length)})'
echo "### WIKIDATA ENTITY LABELS"
curl -s -m 30 -A "$UA" -G "https://www.wikidata.org/w/api.php" --data-urlencode "action=wbgetentities" --data-urlencode "ids=Q8261|Q7725634" --data-urlencode "props=labels" --data-urlencode "languages=en" --data-urlencode "format=json" | jq -c '.entities|map_values(.labels.en.value)'
echo "### MANGADEX rate/headers"
curl -s -D - -o /dev/null -m 20 -A "$UA" "https://api.mangadex.org/ping" | grep -i -E "^(HTTP|x-ratelimit)"
