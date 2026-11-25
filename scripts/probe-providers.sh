#!/bin/bash
# Research round 4: which providers know web novels / light novels / obscure titles? (no key used)
UA="ATracker-research/0.1"
trim() { jq -c 'walk(if type=="string" and length>70 then .[0:70]+"…" else . end)' 2>/dev/null || head -c 600; }
TITLES=("Reverend Insanity" "Lord of the Mysteries" "Omniscient Reader" "Overgeared" "The Beginning After the End" "Mushoku Tensei" "Re:Zero" "Shadow Slave")
echo "### MANGABAKA (no key) search"
for t in "${TITLES[@]}"; do echo "== $t"; curl -s -m 25 -w ' [HTTP %{http_code}]' -A "$UA" -G "https://api.mangabaka.org/v1/series" --data-urlencode "q=$t" --data-urlencode "limit=3" | head -c 1500; echo; done
echo "### MANGABAKA rate headers"
curl -s -D - -o /dev/null -m 20 -A "$UA" "https://api.mangabaka.org/v1/series?q=test&limit=1" | grep -i -E "^(HTTP|x-ratelimit|retry|content-type)"
echo "### MANGAUPDATES official v1 search"
for t in "${TITLES[@]}"; do echo "== $t"; curl -s -m 25 -w ' [HTTP %{http_code}]' -A "$UA" -H 'Content-Type: application/json' -d "{\"search\":\"$t\",\"perpage\":3}" "https://api.mangaupdates.com/v1/series/search" | jq -c '.results[]?.record|{series_id,title,type,year}' 2>/dev/null; echo; done
echo "### KITSU manga search"
for t in "Reverend Insanity" "Lord of the Mysteries" "Mushoku Tensei" "Shadow Slave"; do echo "== $t"; curl -s -m 25 -A "$UA" -G "https://kitsu.io/api/edge/manga" --data-urlencode "filter[text]=$t" --data-urlencode "page[limit]=3" | jq -c '.data[]?|{id,sub:.attributes.subtype,t:.attributes.canonicalTitle,ch:.attributes.chapterCount,vol:.attributes.volumeCount}' 2>/dev/null; done
echo "### JIKAN manga search"
for t in "Reverend Insanity" "Lord of the Mysteries" "Mushoku Tensei"; do echo "== $t"; curl -s -m 25 -w ' [HTTP %{http_code}]' -A "$UA" -G "https://api.jikan.moe/v4/manga" --data-urlencode "q=$t" --data-urlencode "limit=3" | jq -c '.data[]?|{id:.mal_id,type,t:.title}' 2>/dev/null; echo; sleep 1; done
echo "### ANILIST novels (format NOVEL, no country)"
for t in "Mushoku Tensei" "Lord of the Mysteries" "Shadow Slave" "Overgeared" "Reverend Insanity"; do echo "== $t"; curl -s -m 25 -A "$UA" -H 'Content-Type: application/json' -d "{\"query\":\"query(\$q:String){Page(perPage:3){media(search:\$q,type:MANGA){id format countryOfOrigin chapters volumes title{romaji english}}}}\",\"variables\":{\"q\":\"$t\"}}" https://graphql.anilist.co | jq -c '.data.Page.media'; done
echo "### ANILIST user-list public query (profile idea): a public user's stats"
curl -s -m 25 -A "$UA" -H 'Content-Type: application/json' -d '{"query":"{User(name:\"AniList\"){id name avatar{large} statistics{anime{count episodesWatched} manga{count chaptersRead}}}}"}' https://graphql.anilist.co | jq -c '.data'
