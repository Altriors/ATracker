#!/bin/bash
# Research round 5: MAL feasibility + id mapping for imports.
UA="ATracker-research/0.1"
echo "### MAL API reachability, no credentials"
curl -s -m 25 -w ' [HTTP %{http_code}]\n' -A "$UA" "https://api.myanimelist.net/v2/anime?q=naruto&limit=1" | head -c 400; echo
echo "### MAL API with a bogus client id"
curl -s -m 25 -w ' [HTTP %{http_code}]\n' -A "$UA" -H "X-MAL-CLIENT-ID: 00000000000000000000000000000000" "https://api.myanimelist.net/v2/anime?q=naruto&limit=1" | head -c 400; echo
echo "### MAL OAuth authorize endpoint reachability"
curl -s -m 25 -o /dev/null -w 'HTTP %{http_code}\n' -A "$UA" "https://myanimelist.net/v1/oauth2/authorize?response_type=code&client_id=x&code_challenge=$(printf 'a%.0s' {1..50})"
echo "### MAL XML export / profile pages are not API; skip"
echo "### ANILIST idMal batch lookup (resolve imported MAL ids to covers/metadata)"
curl -s -m 25 -A "$UA" -H 'Content-Type: application/json' -d '{"query":"{A:Page(perPage:5){media(idMal_in:[1,5114,16498],type:ANIME){id idMal title{romaji} coverImage{large} episodes}} M:Page(perPage:5){media(idMal_in:[2,13,44347],type:MANGA){id idMal format countryOfOrigin title{romaji} coverImage{large} chapters volumes}}}"}' https://graphql.anilist.co | jq -c '.' | head -c 1800; echo
echo "### MAL ids that AniList lacks (web novel?): Lord of the Mysteries novel MAL manga id?"
curl -s -m 25 -A "$UA" -H 'Content-Type: application/json' -d '{"query":"{Page(perPage:5){media(idMal_in:[132663,114229],type:MANGA){id idMal format title{romaji}}}}"}' https://graphql.anilist.co | jq -c '.data' | head -c 600; echo
echo "### ANILIST public list by username (alternative import)"
curl -s -m 25 -A "$UA" -H 'Content-Type: application/json' -d '{"query":"{MediaListCollection(userName:\"Josh\",type:ANIME,status:COMPLETED,perChunk:2,chunk:1){hasNextChunk lists{name entries{status score(format:POINT_10) progress media{id idMal title{romaji}}}}}}"}' https://graphql.anilist.co | jq -c '.' | head -c 900; echo
echo "### KITSU mapping from MAL manga id"
curl -s -m 25 -A "$UA" -G "https://kitsu.io/api/edge/mappings" --data-urlencode "filter[externalSite]=myanimelist/manga" --data-urlencode "filter[externalId]=44347" --data-urlencode "include=item" | jq -c '{data:[.data[]?|{id,site:.attributes.externalSite,ext:.attributes.externalId}],inc:[.included[]?|{id,t:.attributes.canonicalTitle,sub:.attributes.subtype}]}' | head -c 600; echo
echo "### KITSU can it be bulk-filtered by many MAL ids? (rate / limit)"
curl -s -m 25 -D - -o /dev/null -A "$UA" "https://kitsu.io/api/edge/manga?page%5Blimit%5D=1" | grep -i -E "^(HTTP|x-ratelimit|retry)"
