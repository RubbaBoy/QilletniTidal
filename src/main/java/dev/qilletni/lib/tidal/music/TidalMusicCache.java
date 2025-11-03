package dev.qilletni.lib.tidal.music;

import dev.qilletni.api.exceptions.InvalidURLOrIDException;
import dev.qilletni.api.music.Album;
import dev.qilletni.api.music.Artist;
import dev.qilletni.api.music.MusicCache;
import dev.qilletni.api.music.MusicFetcher;
import dev.qilletni.api.music.Playlist;
import dev.qilletni.api.music.Track;
import dev.qilletni.lib.tidal.database.EntityTransaction;
import dev.qilletni.lib.tidal.music.entities.TidalAlbum;
import dev.qilletni.lib.tidal.music.entities.TidalArtist;
import dev.qilletni.lib.tidal.music.entities.TidalTrack;
import dev.qilletni.lib.tidal.music.entities.TidalPlaylist;
import dev.qilletni.lib.tidal.music.entities.TidalPlaylistIndex;
import dev.qilletni.lib.tidal.music.entities.TidalUser;
import dev.qilletni.lib.tidal.music.entities.stubs.TidalTrackStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.criteria.Join;
import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class TidalMusicCache implements MusicCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(TidalMusicCache.class);

    private final TidalMusicFetcher tidalMusicFetcher;

    public TidalMusicCache(TidalMusicFetcher tidalMusicFetcher) {
        this.tidalMusicFetcher = tidalMusicFetcher;
    }

    @Override
    public Optional<Track> getTrack(String name, String artist) {
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            var builder = session.getCriteriaBuilder();
            var criteria = builder.createQuery(TidalTrack.class);
            var root = criteria.from(TidalTrack.class);

            Join<TidalTrack, TidalArtist> artistsJoin = root.join("artists");

            var trackNamePredicate = builder.equal(root.get("name"), name);
            var artistPredicate = builder.equal(artistsJoin.get("name"), artist);

            criteria.where(trackNamePredicate, artistPredicate);

            var tracks = session.createQuery(criteria).getResultList();

            if (!tracks.isEmpty()) {
                LOGGER.debug("Returning cached track by name and artist");
                return Optional.of(tracks.getFirst());
            }
        }

        // Fetch from API, resolve stubs, and store
        return tidalMusicFetcher.fetchTrack(name, artist)
                .map(this::storeTrack);
    }

    @Override
    public Optional<Track> getTrackById(String id) {
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            var trackOptional = Optional.<Track>ofNullable(session.find(TidalTrack.class, id));
            if (trackOptional.isPresent()) {
                LOGGER.debug("Returning cached track by ID");
                return trackOptional;
            }
        }

        // Fetch from API, resolve stubs, and store
        return tidalMusicFetcher.fetchTrackById(id)
                .map(this::storeTrack);
    }

    @Override
    public List<Track> getTracks(List<MusicFetcher.TrackNameArtist> list) {
        // Not yet supported - throws exception like fetcher does
        return tidalMusicFetcher.fetchTracks(list);
    }

    @Override
    public List<Track> getTracksById(List<String> trackIds) {
        var lookupTracks = new HashMap<Integer, String>();
        var foundTracks = new ArrayList<Track>(Collections.nCopies(trackIds.size(), null));

        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            // Check which tracks are in DB
            for (int i = 0; i < trackIds.size(); i++) {
                var id = trackIds.get(i);
                var found = session.find(TidalTrack.class, id);
                if (found != null) {
                    LOGGER.debug("Found track at index {}", i);
                    foundTracks.set(i, found);
                } else {
                    LOGGER.debug("Looking up track at index {}", i);
                    lookupTracks.put(i, id);
                }
            }
        }

        LOGGER.debug("Found {} tracks in DB, fetching {} missing tracks",
                foundTracks.stream().filter(Objects::nonNull).count(), lookupTracks.size());

        // Fetch and store missing tracks with full stub resolution
        if (!lookupTracks.isEmpty()) {
            var fetched = storeTracks(tidalMusicFetcher.fetchTracksById(new ArrayList<>(lookupTracks.values()))).allTracks();
            for (var entry : lookupTracks.entrySet()) {
                var index = entry.getKey();
                var id = entry.getValue();
                fetched.stream()
                        .filter(track -> track.getId().equals(id))
                        .findFirst()
                        .ifPresent(track -> foundTracks.set(index, track));
            }
        }

        // Remove nulls (in case some tracks weren't found)
        foundTracks.removeIf(Objects::isNull);

        return foundTracks;
    }

    @Override
    public Optional<Playlist> getPlaylist(String name, String author) {
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            var builder = session.getCriteriaBuilder();
            var criteria = builder.createQuery(TidalPlaylist.class);
            var root = criteria.from(TidalPlaylist.class);

            Join<TidalPlaylist, TidalUser> userJoin = root.join("creator");

            var playlistNamePredicate = builder.equal(root.get("title"), name);
            var creatorPredicate = builder.equal(userJoin.get("name"), author);

            criteria.where(playlistNamePredicate, creatorPredicate);

            var playlists = session.createQuery(criteria).getResultList();

            if (!playlists.isEmpty()) {
                LOGGER.debug("Returning cached playlist by name and author");
                return Optional.of(playlists.getFirst());
            }
        }

        // Fetch from API, resolve user stub, and store
        return tidalMusicFetcher.fetchPlaylist(name, author)
                .map(TidalPlaylist.class::cast)
                .map(this::storePlaylist);
    }

    @Override
    public Optional<Playlist> getPlaylistById(String id) {
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            var playlistOptional = Optional.<Playlist>ofNullable(session.find(TidalPlaylist.class, id));
            if (playlistOptional.isPresent()) {
                LOGGER.debug("Returning cached playlist by id");
                return playlistOptional;
            }
        }

        // Fetch from API, resolve user stub, and store
        return tidalMusicFetcher.fetchPlaylistById(id)
                .map(TidalPlaylist.class::cast)
                .map(this::storePlaylist);
    }

    @Override
    public Optional<Album> getAlbum(String name, String artist) {
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            var builder = session.getCriteriaBuilder();
            var criteria = builder.createQuery(TidalAlbum.class);
            var root = criteria.from(TidalAlbum.class);

            Join<TidalAlbum, TidalArtist> artistsJoin = root.join("artists");

            var albumNamePredicate = builder.equal(root.get("name"), name);
            var artistPredicate = builder.equal(artistsJoin.get("name"), artist);

            criteria.where(albumNamePredicate, artistPredicate);

            var albums = session.createQuery(criteria).getResultList();

            if (!albums.isEmpty()) {
                LOGGER.debug("Returning cached album by name");
                return Optional.of(albums.getFirst());
            }
        }

        // Fetch from API, resolve artist stubs, and store
        return tidalMusicFetcher.fetchAlbum(name, artist)
                .map(TidalAlbum.class::cast)
                .map(this::storeAlbum);
    }

    @Override
    public Optional<Album> getAlbumById(String id) {
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            var albumOptional = Optional.<Album>ofNullable(session.find(TidalAlbum.class, id));
            if (albumOptional.isPresent()) {
                LOGGER.debug("Returning cached album by id");
                return albumOptional;
            }
        }

        // Fetch from API, resolve artist stubs, and store
        return tidalMusicFetcher.fetchAlbumById(id)
                .map(TidalAlbum.class::cast)
                .map(this::storeAlbum);
    }

    @Override
    public List<Track> getAlbumTracks(Album album) {
        var tidalAlbum = (TidalAlbum) album;
        var albumTracks = tidalAlbum.getTracks();

        // Check if tracks are already populated
        if (albumTracks != null && !albumTracks.isEmpty()) {
            LOGGER.debug("Album {} tracks already cached", album.getId());
            return albumTracks.stream().map(Track.class::cast).toList();
        }

        LOGGER.debug("Fetching and caching tracks for album {}", album.getId());

        // Fetch from API - these will have stub artists
        var tracks = tidalMusicFetcher.fetchAlbumTracks(album);

        // Resolve all stubs and store tracks
        var storedTracks = storeTracks(tracks);
        var allTracks = storedTracks.allTracks();

        // Update album entity with tracks
        tidalAlbum.setTracks(allTracks.stream().map(TidalTrack.class::cast).toList());

        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();
            session.update(tidalAlbum);
        }

        return allTracks;
    }

    @Override
    public List<Track> getPlaylistTracks(Playlist playlist) {
        var tidalPlaylist = (TidalPlaylist) playlist;
        var playlistIndex = tidalPlaylist.getTidalPlaylistIndex();

        // Check if index is expired (7 days)
        var expires = Instant.ofEpochMilli(playlistIndex.getLastUpdatedIndex().getTime())
                .plus(7, ChronoUnit.DAYS);

        LOGGER.debug("Playlist {} index last updated: {}, expires: {}",
                playlist.getId(), playlistIndex.getLastUpdatedIndex(), expires);

        if (Instant.now().isAfter(expires) || playlistIndex.getTracks().isEmpty()) {
            LOGGER.debug("Playlist {} index expired or empty, fetching fresh tracks", playlist.getId());

            // Fetch track stubs
            var tracks = tidalMusicFetcher.fetchPlaylistTracks(playlist);

            // Resolve all stubs recursively and store
            var storedTracks = storeTracks(tracks);
            var allTracks = storedTracks.allTracks();

            // Update playlist index
            tidalPlaylist.setTidalPlaylistIndex(new TidalPlaylistIndex(
                    allTracks.stream().map(TidalTrack.class::cast).toList(),
                    new Date(System.currentTimeMillis())
            ));

            try (var entityTransaction = EntityTransaction.beginTransaction()) {
                var session = entityTransaction.getSession();
                session.update(tidalPlaylist);
            }

            return allTracks;
        }

        LOGGER.debug("Returning cached tracks for playlist {}", playlist.getId());
        return playlistIndex.getTracks().stream().map(Track.class::cast).toList();
    }

    @Override
    public Optional<Artist> getArtistById(String id) {
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            var artistOptional = Optional.<Artist>ofNullable(session.find(TidalArtist.class, id));
            if (artistOptional.isPresent()) {
                LOGGER.debug("Returning cached artist by id");
                return artistOptional;
            }
        }

        // Fetch from API and store (artists have no dependencies)
        return tidalMusicFetcher.fetchArtistById(id)
                .map(TidalArtist.class::cast)
                .map(this::storeArtist);
    }

    @Override
    public Optional<Artist> getArtistByName(String name) {
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            var builder = session.getCriteriaBuilder();
            var criteria = builder.createQuery(TidalArtist.class);
            var root = criteria.from(TidalArtist.class);

            criteria.where(builder.equal(root.get("name"), name));

            var artists = session.createQuery(criteria).getResultList();

            if (!artists.isEmpty()) {
                LOGGER.debug("Returning cached artist by name");
                return Optional.of(artists.getFirst());
            }
        }

        // Fetch from API and store (artists have no dependencies)
        return tidalMusicFetcher.fetchArtistByName(name)
                .map(TidalArtist.class::cast)
                .map(this::storeArtist);
    }

    /**
     * Resolve track stubs by fetching them from the API.
     * Returns a list of full TidalTrack objects (though they may still have stub references inside).
     */
    private List<TidalTrack> resolveTrackStubs(List<Track> tracks) {
        var fullTracks = new ArrayList<TidalTrack>();
        var stubIds = new ArrayList<String>();

        for (var track : tracks) {
            if (track instanceof TidalTrackStub) {
                stubIds.add(track.getId());
            } else {
                fullTracks.add((TidalTrack) track);
            }
        }

        if (!stubIds.isEmpty()) {
            LOGGER.debug("Batch fetching {} track stubs", stubIds.size());
            var fetched = tidalMusicFetcher.fetchTracksById(stubIds);
            for (var track : fetched) {
                fullTracks.add((TidalTrack) track);
            }
        }

        return fullTracks;
    }

    /**
     * Resolve all nested stubs (artists and albums) within tracks.
     * This is the critical method that ensures no stubs remain before storage.
     *
     * Order of operations:
     * 1. Extract artist IDs from tracks (NOT albums - they might be stubs!)
     * 2. Extract album IDs (safe - only calls getId())
     * 3. Resolve albums (which might have stub artists)
     * 4. Extract artist IDs from resolved albums
     * 5. Resolve all artists
     * 6. Reconstruct albums with full artist references
     * 7. Reconstruct tracks with full references
     *
     * @param tracks The tracks to resolve stubs for
     * @return Resolved track entities, in the same order as passed in
     */
    private List<TidalTrack> resolveNestedStubs(List<TidalTrack> tracks) {
        // Extract artist IDs from tracks only (albums might be stubs)
        var allArtistIds = new HashSet<String>();
        for (var track : tracks) {
            track.getArtists().forEach(a -> allArtistIds.add(a.getId()));
            // DON'T call getArtists() on album here, it might be a stub
        }

        // Extract all album IDs that could be stubs
        var albumIds = tracks.stream()
                .map(t -> t.getAlbum().getId())
                .distinct()
                .toList();

        LOGGER.debug("Resolving {} unique albums (which may have stub artists)", albumIds.size());

        // Resolve all albums (check DB, fetch missing)
        // Pass empty artistMap since we haven't resolved artists yet
        var albumMap = resolveAndFetchAlbums(albumIds, new HashMap<>());

        // Extract artist IDs from the non-stub albums
        for (var album : albumMap.values()) {
            album.getArtists().forEach(a -> allArtistIds.add(a.getId()));
        }

        LOGGER.debug("Resolving {} unique artists from tracks and albums", allArtistIds.size());

        // Resolve all artists (check DB, fetch missing)
        var artistMap = resolveAndFetchArtists(new ArrayList<>(allArtistIds));

        // Albums might have stub artists, reconstruct with full artist references
        var resolvedAlbumMap = new HashMap<String, TidalAlbum>();
        for (var album : albumMap.values()) {
            var resolvedAlbum = new TidalAlbum(
                    album.getId(),
                    album.getName(),
                    album.getArtists().stream()
                            .map(a -> artistMap.get(a.getId()))
                            .toList()
            );
            resolvedAlbumMap.put(album.getId(), resolvedAlbum);
        }

        // Reconstruct tracks with fully resolved references
        return tracks.stream()
                .map(track -> new TidalTrack(
                        track.getId(),
                        track.getName(),
                        track.getArtists().stream()
                                .map(a -> artistMap.get(a.getId()))
                                .toList(),
                        resolvedAlbumMap.get(track.getAlbum().getId()),
                        track.getDuration()
                ))
                .toList();
    }

    /**
     * Resolve and fetch artists. Checks database first, then fetches missing ones from API.
     * Returns a map of artist ID to TidalArtist entity.
     *
     * @param artistIds The IDs of the artists to resolve to real artists
     * @return The map of artist IDs and artist entities
     */
    private Map<String, TidalArtist> resolveAndFetchArtists(List<String> artistIds) {
        var artistMap = new HashMap<String, TidalArtist>();
        var missingIds = new ArrayList<>(artistIds);

        // Check DB for existing artists
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();
            for (var id : new ArrayList<>(missingIds)) {
                var found = session.find(TidalArtist.class, id);
                if (found != null) {
                    artistMap.put(id, found);
                    missingIds.remove(id);
                }
            }
        }

        LOGGER.debug("Found {} artists in DB, fetching {} missing artists", artistMap.size(), missingIds.size());

        // Fetch missing artists from API
        for (var id : missingIds) {
            var fetched = tidalMusicFetcher.fetchArtistById(id)
                    .map(TidalArtist.class::cast)
                    .orElseThrow(() -> new RuntimeException("Failed to fetch artist with ID: " + id));
            artistMap.put(id, fetched);
        }

        return artistMap;
    }

    /**
     * Resolve and fetch albums. Handles albums that might have stub artists themselves.
     * Returns a map of album ID to TidalAlbum entity with fully resolved artist references.
     *
     * @param albumIds The IDs of the albums to resolve
     * @param artistMap Known artists to use as a cache, of their IDs as a key and entities as values
     * @return Resolved album IDs and their entities
     */
    private Map<String, TidalAlbum> resolveAndFetchAlbums(List<String> albumIds, Map<String, TidalArtist> artistMap) {
        var albumMap = new HashMap<String, TidalAlbum>();
        var missingIds = new ArrayList<>(albumIds);

        // Check DB for existing albums
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();
            for (var id : new ArrayList<>(missingIds)) {
                var found = session.find(TidalAlbum.class, id);
                if (found != null) {
                    albumMap.put(id, found);
                    missingIds.remove(id);
                }
            }
        }

        LOGGER.debug("Found {} albums in DB, fetching {} missing albums", albumMap.size(), missingIds.size());

        // Fetch missing albums from API
        for (var id : missingIds) {
            var fetched = tidalMusicFetcher.fetchAlbumById(id)
                    .map(TidalAlbum.class::cast)
                    .orElseThrow(() -> new RuntimeException("Failed to fetch album with ID: " + id));

            // Album might have stub artists - resolve them!
            var albumArtistIds = fetched.getArtists().stream()
                    .map(Artist::getId)
                    .toList();

            // Check if we need to fetch any album artists not in our map
            for (var artistId : albumArtistIds) {
                if (!artistMap.containsKey(artistId)) {
                    LOGGER.debug("Album {} has artist {} not in our map, fetching it", id, artistId);
                    var artist = tidalMusicFetcher.fetchArtistById(artistId)
                            .map(TidalArtist.class::cast)
                            .orElseThrow(() -> new RuntimeException("Failed to fetch artist with ID: " + artistId));
                    artistMap.put(artistId, artist);
                }
            }

            // Reconstruct album with full artist references
            var resolvedAlbum = new TidalAlbum(
                    fetched.getId(),
                    fetched.getName(),
                    albumArtistIds.stream()
                            .map(artistMap::get)
                            .toList()
            );

            albumMap.put(id, resolvedAlbum);
        }

        return albumMap;
    }

    /**
     * Store artists in the database. Returns a map of artist ID to stored TidalArtist.
     *
     * @param artists The artists to store
     * @return The map of artist IDs and their entities that have been stored
     */
    private Map<String, TidalArtist> storeArtists(List<TidalArtist> artists) {
        var allArtists = new HashMap<String, TidalArtist>();
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            for (var artist : artists) {
                if (allArtists.containsKey(artist.getId())) {
                    continue;
                }

                var found = session.find(TidalArtist.class, artist.getId());
                if (found != null) {
                    LOGGER.debug("Artist already in DB: {}", found.getId());
                    allArtists.put(found.getId(), found);
                } else {
                    LOGGER.debug("Storing new artist: {}", artist.getId());
                    session.save(artist);
                    allArtists.put(artist.getId(), artist);
                }
            }
        }
        return allArtists;
    }

    /**
     * Store albums in the database with fully resolved artist references.
     * Returns a map of album ID to stored TidalAlbum.
     *
     * @param albums The albums to store
     * @param artistMap A map of artist IDs and their entities to use to lookup
     * @return The map of album IDs and their entities that have been stored
     */
    private Map<String, TidalAlbum> storeAlbums(List<TidalAlbum> albums, Map<String, TidalArtist> artistMap) {
        var allAlbums = new HashMap<String, TidalAlbum>();
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            for (var album : albums) {
                var found = session.find(TidalAlbum.class, album.getId());
                if (found != null) {
                    LOGGER.debug("Album already in DB: {}", found.getId());
                    allAlbums.put(found.getId(), found);
                } else {
                    // Ensure album uses artists from artistMap (from DB)
                    var newAlbum = new TidalAlbum(
                            album.getId(),
                            album.getName(),
                            album.getArtists().stream()
                                    .map(a -> artistMap.get(a.getId()))
                                    .toList()
                    );
                    LOGGER.debug("Storing new album: {}", newAlbum.getId());
                    session.save(newAlbum);
                    allAlbums.put(newAlbum.getId(), newAlbum);
                }
            }
        }
        return allAlbums;
    }

    /**
     * Store tracks with full stub resolution.
     * This method resolves ALL stubs recursively before storing.
     *
     * @param addingTracks The tracks to store
     * @return The tracks that have been stored in the database
     */
    private StoredTracks storeTracks(List<Track> addingTracks) {
        LOGGER.debug("Storing {} tracks", addingTracks.size());

        // First resolve ALL stubs recursively (tracks, then nested artists/albums)
        var resolvedTracks = resolveNestedStubs(resolveTrackStubs(addingTracks));

        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            // Filter out tracks already in DB
            var newTracks = resolvedTracks.stream()
                    .filter(track -> session.find(TidalTrack.class, track.getId()) == null)
                    .toList();

            LOGGER.debug("Found {} tracks already in DB, storing {} new tracks",
                    resolvedTracks.size() - newTracks.size(), newTracks.size());

            // Collect all distinct artists (from tracks and albums)
            var distinctArtists = resolvedTracks.stream()
                    .flatMap(track -> Stream.concat(
                            track.getArtists().stream(),
                            track.getAlbum().getArtists().stream()
                    ))
                    .distinct()
                    .map(TidalArtist.class::cast)
                    .toList();

            var artistMap = storeArtists(distinctArtists);

            // Collect all distinct albums
            var distinctAlbums = resolvedTracks.stream()
                    .map(TidalTrack::getAlbum)
                    .distinct()
                    .map(TidalAlbum.class::cast)
                    .toList();

            var albumMap = storeAlbums(distinctAlbums, artistMap);

            // Store new tracks with DB references
            var fetchedTracks = new ArrayList<Track>();
            var allTracks = resolvedTracks.stream().map(track -> {
                if (newTracks.contains(track)) {
                    var storedTrack = new TidalTrack(
                            track.getId(),
                            track.getName(),
                            track.getArtists().stream()
                                    .map(a -> artistMap.get(a.getId()))
                                    .toList(),
                            albumMap.get(track.getAlbum().getId()),
                            track.getDuration()
                    );
                    LOGGER.debug("Storing new track: {}", storedTrack.getId());
                    session.save(storedTrack);
                    fetchedTracks.add(storedTrack);
                    return storedTrack;
                } else {
                    return (Track) session.find(TidalTrack.class, track.getId());
                }
            }).toList();

            return new StoredTracks(fetchedTracks, allTracks);
        }
    }

    /**
     * Store a single artist.
     *
     * @param artist The artist to store
     * @return The stored artist entity
     */
    private TidalArtist storeArtist(TidalArtist artist) {
        return storeArtists(List.of(artist)).values().iterator().next();
    }

    /**
     * Store a single album.
     *
     * @param album The album to store
     * @return The stored album entity
     */
    private TidalAlbum storeAlbum(TidalAlbum album) {
        var artistMap = storeArtists(album.getArtists().stream().distinct().map(TidalArtist.class::cast).toList());
        return storeAlbums(List.of(album), artistMap).values().iterator().next();
    }

    /**
     * Store a single track.
     *
     * @param track The track to store
     * @return The stored track entity
     */
    private Track storeTrack(Track track) {
        return storeTracks(List.of(track)).allTracks().getFirst();
    }

    /**
     * Store a playlist with resolved user reference.
     *
     * @param playlist The playlist to store
     * @return The stored playlist entity
     */
    private TidalPlaylist storePlaylist(TidalPlaylist playlist) {
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            // Check if playlist already exists in DB
            var existingPlaylist = session.find(TidalPlaylist.class, playlist.getId());
            if (existingPlaylist != null) {
                LOGGER.debug("Playlist already in DB: {}", existingPlaylist.getId());
                return existingPlaylist;
            }

            var user = (TidalUser) playlist.getCreator();
            var databaseUser = session.find(TidalUser.class, user.getId());
            if (databaseUser == null) {
                LOGGER.debug("Storing new user: {}", user.getId());
                session.save(databaseUser = user);
            } else {
                LOGGER.debug("User already in DB: {}", user.getId());
            }

            var newPlaylist = new TidalPlaylist(playlist.getId(), playlist.getTitle(), databaseUser, playlist.getTrackCount());
            LOGGER.debug("Storing new playlist: {}", newPlaylist.getId());
            session.save(newPlaylist);
            return newPlaylist;
        }
    }

    /**
     * Store a user.
     *
     * @param user The user to store
     * @return The stored user entity
     */
    private TidalUser storeUser(TidalUser user) {
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            var found = session.find(TidalUser.class, user.getId());
            if (found != null) {
                return found;
            }

            session.save(user);
            return user;
        }
    }

    /**
     * Record to hold results from storeTracks.
     */
    private record StoredTracks(List<Track> fetchedTracks, List<Track> allTracks) {}

    @Override
    public String getIdFromString(String idOrUrl) {
        // Regular expression to match Tidal track URLs or an ID
        var pattern = Pattern.compile("(^|tidal\\.com/.*?/)(\\d{9}|\\w{8}-\\w{4}-\\w{4}-\\w{4}-\\w{12})");
        var matcher = pattern.matcher(idOrUrl);

        if (matcher.find()) {
            if (matcher.groupCount() == 2) {
                return matcher.group(2);
            }
        }

        throw new InvalidURLOrIDException(String.format("Invalid URL or ID: \"%s\"", idOrUrl));
    }
}
