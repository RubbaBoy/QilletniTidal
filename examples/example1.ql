import "tidal:tidal.ql"

provider "spotify"

song godKnows = "God Knows" by "Knocked Loose"

printf("Spotify song ID: %s", [godKnows.getId()])

provider "tidal"

printf("Tidal song ID: %s", [godKnows.getId()])
