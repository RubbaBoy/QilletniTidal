package dev.qilletni.lib.tidal.music;

import com.tidal.sdk.tidalapi.generated.TidalApiClient;
import com.tidal.sdk.tidalapi.generated.models.AlbumsItemsMultiRelationshipDataDocument;
import com.tidal.sdk.tidalapi.generated.models.AlbumsResourceObject;
import com.tidal.sdk.tidalapi.generated.models.AlbumsSingleResourceDataDocument;
import com.tidal.sdk.tidalapi.generated.models.ArtistsResourceObject;
import com.tidal.sdk.tidalapi.generated.models.ArtistsSingleResourceDataDocument;
import com.tidal.sdk.tidalapi.generated.models.IncludedInner;
import com.tidal.sdk.tidalapi.generated.models.PlaylistsItemsMultiRelationshipDataDocument;
import com.tidal.sdk.tidalapi.generated.models.PlaylistsResourceObject;
import com.tidal.sdk.tidalapi.generated.models.PlaylistsSingleResourceDataDocument;
import com.tidal.sdk.tidalapi.generated.models.ResourceIdentifier;
import com.tidal.sdk.tidalapi.generated.models.SearchResultsSingleResourceDataDocument;
import com.tidal.sdk.tidalapi.generated.models.TracksMultiResourceDataDocument;
import com.tidal.sdk.tidalapi.generated.models.TracksResourceObject;
import com.tidal.sdk.tidalapi.generated.models.TracksSingleResourceDataDocument;
import com.tidal.sdk.tidalapi.generated.models.UserCollectionsPlaylistsMultiRelationshipDataDocument;
import com.tidal.sdk.tidalapi.generated.models.UserCollectionsPlaylistsResourceIdentifier;
import com.tidal.sdk.tidalapi.generated.models.UserCollectionsSingleResourceDataDocument;
import com.tidal.sdk.tidalapi.generated.models.UsersAttributes;
import com.tidal.sdk.tidalapi.generated.models.UsersResourceObject;
import dev.qilletni.api.music.Album;
import dev.qilletni.api.music.Artist;
import dev.qilletni.api.music.MusicFetcher;
import dev.qilletni.api.music.Playlist;
import dev.qilletni.api.music.Track;
import dev.qilletni.api.music.User;
import dev.qilletni.lib.tidal.CoroutineHelper;
import dev.qilletni.lib.tidal.api.helper.IncludedInnerWrapper;
import dev.qilletni.lib.tidal.api.helper.ModelHelper;
import dev.qilletni.lib.tidal.music.entities.TidalAlbum;
import dev.qilletni.lib.tidal.music.entities.TidalArtist;
import dev.qilletni.lib.tidal.music.entities.TidalPlaylist;
import dev.qilletni.lib.tidal.music.entities.TidalTrack;
import dev.qilletni.lib.tidal.music.entities.TidalUser;
import dev.qilletni.lib.tidal.music.entities.stubs.TidalAlbumStub;
import dev.qilletni.lib.tidal.music.entities.stubs.TidalArtistStub;
import dev.qilletni.lib.tidal.music.entities.stubs.TidalTrackStub;
import dev.qilletni.lib.tidal.music.entities.stubs.TidalUserStub;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Response;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TidalMusicFetcher implements MusicFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(TidalMusicFetcher.class);

    private final String countryCode;
    private final TidalApiClient tidalApi;
    private final UsersResourceObject currentUser;
    private final boolean prioritizeUserCollection = true;
    private final boolean caseSensitivePlaylist = true;

    public TidalMusicFetcher(String countryCode, TidalApiClient tidalApi, UsersResourceObject currentUser) {
        this.countryCode = countryCode;
        this.tidalApi = tidalApi;
        this.currentUser = currentUser;
    }

    /**
     * Checks if the given name is the logged in user's username, or "FirstName + LastName"
     *
     * @param name The name to check
     * @return If the given name represents the current user
     */
    private boolean isSelfUser(String name) {
        var attributes = currentUser.getAttributes();
        if (attributes == null) {
            return false;
        }

        return true;

//        return name.equalsIgnoreCase(attributes.getUsername()) ||
//                name.equalsIgnoreCase("%s %s".formatted(attributes.getFirstName(), attributes.getLastName()).trim());
    }

    private Optional<String> getErrorResponse(Response<?> response) {
        try (var errorBody = response.errorBody()) {
            if (errorBody != null) {
                return Optional.of(errorBody.string());
            }
        } catch (Exception ignored) {}

        return Optional.empty();
    }

    private String getFormatedErrorResponse(Response<?> response) {
        return getErrorResponse(response).map("\n%s"::formatted).orElse("");
    }

    @Override
    public Optional<Track> fetchTrack(String name, String artist) {
        LOGGER.debug("fetchTrack({}, {})", name, artist);

        try {
            Response<SearchResultsSingleResourceDataDocument> response =
                    CoroutineHelper.runSuspend(cont ->
                            tidalApi.createSearchResults().searchResultsIdGet(
                                    "%s %s".formatted(name, artist),
                                    countryCode,
                                    "include",
                                    List.of("tracks"),
                                    cont
                            ));

            if (!response.isSuccessful() || response.body() == null || response.body().getData().getRelationships() == null || response.body().getData().getRelationships().getTracks().getData() == null) {
                LOGGER.error("Failed to fetch track: {}", getFormatedErrorResponse(response));
                return Optional.empty();
            }

            var data = response.body().getData().getRelationships().getTracks().getData().getFirst();

            return fetchTrackById(data.getId());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Track> fetchTrackById(String id) {
        LOGGER.debug("fetchTrack({})", id);

        try {
            Response<TracksSingleResourceDataDocument> singleTrackResponse =
                    CoroutineHelper.runSuspend(cont ->
                            tidalApi.createTracks().tracksIdGet(
                                    id,
                                    countryCode,
                                    List.of("albums", "artists"),
                                    cont
                            ));

            if (!singleTrackResponse.isSuccessful() || singleTrackResponse.body() == null || singleTrackResponse.body().getData().getRelationships() == null) {
                LOGGER.error("Failed to fetch track by ID: {}", getFormatedErrorResponse(singleTrackResponse));
                return Optional.empty();
            }

            return createTrackEntity(singleTrackResponse.body());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Track> fetchTracks(List<TrackNameArtist> list) {
        throw new RuntimeException("fetchTracks(List<TrackNameArtist>) not supported!");
    }

    @Override
    public List<Track> fetchTracksById(List<String> list) {
        LOGGER.debug("fetchTracksById({})", String.join(", ", list));

        try {
            Response<TracksMultiResourceDataDocument> multiTrackResponse =
                    CoroutineHelper.runSuspend(cont ->
                            tidalApi.createTracks().tracksGet(
                                    countryCode,
                                    null,
                                    List.of("albums", "artists"),
                                    null,
                                    null,
                                    list,
                                    cont
                            ));

            if (!multiTrackResponse.isSuccessful() || multiTrackResponse.body() == null) {
                LOGGER.error("Failed to fetch track by ID: {}", getFormatedErrorResponse(multiTrackResponse));
                return Collections.emptyList();
            }

            var body = multiTrackResponse.body();

            return createTrackList(body.getData(), body.getIncluded());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Playlist> fetchPlaylist(String name, String author) { // testing
        LOGGER.debug("fetchPlaylist({}, {})", name, author);

        try {
            if (prioritizeUserCollection && isSelfUser(author)) {
                // Check user's collection first

                String nextLink;
                do {
                    Response<UserCollectionsPlaylistsMultiRelationshipDataDocument> response =
                            CoroutineHelper.runSuspend(cont ->
                                    tidalApi.createUserCollections().userCollectionsIdRelationshipsPlaylistsGet(
                                            currentUser.getId(),
                                            null,
                                            null,
                                            List.of("playlists"),
                                            cont
                                    ));

                    if (!response.isSuccessful() || response.body() == null /* || response.body().getData().getRelationships() == null || response.body().getData().getRelationships().getPlaylists().getData() == null */) {
                        LOGGER.error("Continuing to normal search: Failed to fetch user collection playlist: {}", getFormatedErrorResponse(response));
                        break;
                    }

                    var body = response.body();
                    var includedInnerWrapper = new IncludedInnerWrapper(body.getIncluded());

                    nextLink = body.getLinks().getNext();

                    Predicate<PlaylistsResourceObject> playlistPredicate;

                    if (caseSensitivePlaylist) {
                        playlistPredicate = playlist -> playlist.getAttributes().getName().equals(name);
                    } else {
                        playlistPredicate = playlist -> playlist.getAttributes().getName().equalsIgnoreCase(name);
                    }

                    var playlistMatches = body.getData().stream()
                            .map(playlist -> includedInnerWrapper.getInner(playlist.getId(), PlaylistsResourceObject.class))
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .filter(playlistPredicate)
                            .toList();

                    if (!playlistMatches.isEmpty()) {
                        return fetchPlaylistById(playlistMatches.getFirst().getId());
                    }
                } while (nextLink != null);
            }

            LOGGER.debug("Continuing to normal playlist search");

            Response<SearchResultsSingleResourceDataDocument> response =
                    CoroutineHelper.runSuspend(cont ->
                            tidalApi.createSearchResults().searchResultsIdGet(
                                    "%s %s".formatted(name, author),
                                    countryCode,
                                    "include",
                                    List.of("playlists"),
                                    cont
                            ));

            if (!response.isSuccessful() || response.body() == null || response.body().getData().getRelationships() == null || response.body().getData().getRelationships().getPlaylists().getData() == null) {
                LOGGER.error("Failed to fetch playlist: {}", getFormatedErrorResponse(response));
                return Optional.empty();
            }

            var data = response.body().getData().getRelationships().getPlaylists().getData().getFirst();

            return fetchPlaylistById(data.getId());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Playlist> fetchPlaylistById(String id) {
        LOGGER.debug("fetchPlaylistById({})", id);

        try {
            Response<PlaylistsSingleResourceDataDocument> singlePlaylistResponse =
                    CoroutineHelper.runSuspend(cont ->
                            tidalApi.createPlaylists().playlistsIdGet(
                                    id,
                                    countryCode,
                                    List.of("owners"),
                                    cont
                            ));

            if (!singlePlaylistResponse.isSuccessful()) {
                LOGGER.error("Failed to fetch playlist info: {}", getFormatedErrorResponse(singlePlaylistResponse));
                return Optional.empty();
            }

            return createCollectionEntity(singlePlaylistResponse.body());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Album> fetchAlbum(String name, String artist) {
        LOGGER.debug("fetchAlbum({}, {})", name, artist);

        try {
            Response<SearchResultsSingleResourceDataDocument> response =
                    CoroutineHelper.runSuspend(cont ->
                            tidalApi.createSearchResults().searchResultsIdGet(
                                    "%s %s".formatted(name, artist),
                                    countryCode,
                                    "include",
                                    List.of("albums"),
                                    cont
                            ));

            if (!response.isSuccessful() || response.body() == null || response.body().getData().getRelationships() == null || response.body().getData().getRelationships().getAlbums().getData() == null) {
                LOGGER.error("Failed to fetch artist: {}", getFormatedErrorResponse(response));
                return Optional.empty();
            }

            var data = response.body().getData().getRelationships().getAlbums().getData().getFirst();

            return fetchAlbumById(data.getId());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Album> fetchAlbumById(String id) {
        LOGGER.debug("fetchAlbumById({})", id);

        try {
            Response<AlbumsSingleResourceDataDocument> singleAlbumResponse =
                    CoroutineHelper.runSuspend(cont ->
                            tidalApi.createAlbums().albumsIdGet(
                                    id,
                                    countryCode,
                                    List.of("artists"),
                                    cont
                            ));

            if (!singleAlbumResponse.isSuccessful()) {
                LOGGER.error("Failed to fetch artist info: {}", getFormatedErrorResponse(singleAlbumResponse));
                return Optional.empty();
            }

            return createAlbumEntity(singleAlbumResponse.body())
                    .map(Album.class::cast);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Track> fetchAlbumTracks(Album album) {
        LOGGER.debug("fetchAlbumTracks({})", album.getId());

        try {
            Response<AlbumsSingleResourceDataDocument> singleAlbumResponse =
                    CoroutineHelper.runSuspend(cont ->
                            tidalApi.createAlbums().albumsIdGet(
                                    album.getId(),
                                    countryCode,
                                    List.of("artists"),
                                    cont
                            ));

            if (!singleAlbumResponse.isSuccessful()) {
                LOGGER.error("Failed to fetch album info: {}", getFormatedErrorResponse(singleAlbumResponse));
                return Collections.emptyList();
            }

            var albumEntity = createAlbumEntity(singleAlbumResponse.body()).get();

            Response<AlbumsItemsMultiRelationshipDataDocument> albumItemsResponse =
                    CoroutineHelper.runSuspend(cont ->
                            tidalApi.createAlbums().albumsIdRelationshipsItemsGet(
                                    album.getId(),
                                    countryCode,
                                    null,
                                    List.of("items"),
                                    cont
                            ));

            if (!albumItemsResponse.isSuccessful()) {
                LOGGER.error("Failed to fetch album tracks: {}", getFormatedErrorResponse(albumItemsResponse));
                return Collections.emptyList();
            }

            return createAlbumTrackList(albumEntity, albumItemsResponse.body());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Track> fetchPlaylistTracks(Playlist playlist) {
        LOGGER.debug("fetchPlaylistTracks({})", playlist.getId());

        try {
            Response<PlaylistsItemsMultiRelationshipDataDocument> playlistItemsResponse =
                    CoroutineHelper.runSuspend(cont ->
                            tidalApi.createPlaylists().playlistsIdRelationshipsItemsGet(
                                    playlist.getId(),
                                    countryCode,
                                    null,
                                    List.of("items"),
                                    cont
                            ));

            if (!playlistItemsResponse.isSuccessful()) {
                LOGGER.error("Failed to fetch album items: {}", getFormatedErrorResponse(playlistItemsResponse));
                return Collections.emptyList();
            }

            return createPlaylistTrackList(playlistItemsResponse.body());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Artist> fetchArtistById(String id) {
        LOGGER.debug("fetchArtistById({})", id);

        try {
            Response<ArtistsSingleResourceDataDocument> response =
                    CoroutineHelper.runSuspend(cont ->
                            tidalApi.createArtists().artistsIdGet(
                                    id,
                                    countryCode,
                                    List.of(),
                                    cont
                            ));

            if (!response.isSuccessful() || response.body() == null) {
                LOGGER.error("Failed to fetch artist: {}", getFormatedErrorResponse(response));
                return Optional.empty();
            }

            var artistsResourceObject = response.body().getData();

            return Optional.of(createArtistEntity(artistsResourceObject));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Artist> fetchArtistByName(String name) {
        LOGGER.debug("fetchArtistByName({})", name);

        try {
            Response<SearchResultsSingleResourceDataDocument> response =
                    CoroutineHelper.runSuspend(cont ->
                            tidalApi.createSearchResults().searchResultsIdGet(
                                    name,
                                    countryCode,
                                    "include",
                                    List.of("artists"),
                                    cont
                            ));

            if (!response.isSuccessful() || response.body() == null || response.body().getData().getRelationships() == null || response.body().getData().getRelationships().getArtists().getData() == null) {
                LOGGER.error("Failed to fetch artist: {}", getFormatedErrorResponse(response));
                return Optional.empty();
            }

            var data = response.body().getData().getRelationships().getArtists().getData().getFirst();

            return fetchArtistById(data.getId());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<Track> createTrackEntity(@Nullable TracksSingleResourceDataDocument track) {
        if (track == null) {
            return Optional.empty();
        }

        var trackData = track.getData();

        var includedInnerWrapper = new IncludedInnerWrapper(track.getIncluded());

        var artists = ModelHelper.collectIncludeInners(includedInnerWrapper, trackData.getRelationships().getArtists().getData(), ArtistsResourceObject.class);
        var albums = ModelHelper.collectIncludeInners(includedInnerWrapper, trackData.getRelationships().getAlbums().getData(), AlbumsResourceObject.class);

        return Optional.of(new TidalTrack(trackData.getId(),
                trackData.getAttributes().getTitle(),
                artists.stream().map(this::createArtistEntityStub).toList(),
                createAlbumEntityStub(albums.getFirst()),
                DurationConverter.parseDurationToSeconds(trackData.getAttributes().getDuration())));
    }

    private Optional<Playlist> createCollectionEntity(@Nullable PlaylistsSingleResourceDataDocument playlist) {
        if (playlist == null) {
            return Optional.empty();
        }

        var playlistData = playlist.getData();

        // This would be accessed directly, but I'm not sure if the included array is ordered or not,
        // so this is using the owner relationships and taking data from this
        var includedInnerWrapper = new IncludedInnerWrapper(playlist.getIncluded());

        // TODO: Qilletni only supports playlists owned by a single user (Qilletni issue #10)
        var owners = ModelHelper.collectIncludeInners(includedInnerWrapper, playlistData.getRelationships().getOwners().getData(), UsersResourceObject.class);
        var firstOwner = owners.getFirst();

        return Optional.of(new TidalPlaylist(playlistData.getId(), playlistData.getAttributes().getName(), createUserEntity(firstOwner), playlistData.getAttributes().getNumberOfItems()));
    }

    private TidalUser createUserEntity(UsersResourceObject user) {
        // TODO: See Qilletni issue #11
        return new TidalUser(user.getId(), user.getAttributes().getUsername());
    }

    private Optional<TidalAlbum> createAlbumEntity(@Nullable AlbumsSingleResourceDataDocument album) {
        if (album == null) {
            return Optional.empty();
        }

        var albumData = album.getData();

        var includedInnerWrapper = new IncludedInnerWrapper(album.getIncluded());

        var artists = ModelHelper.collectIncludeInners(includedInnerWrapper, albumData.getRelationships().getArtists().getData(), ArtistsResourceObject.class);

        return Optional.of(new TidalAlbum(albumData.getId(), albumData.getAttributes().getTitle(), artists.stream().map(this::createArtistEntity).toList()));
    }

    private List<Track> createAlbumTrackList(TidalAlbum album, @Nullable AlbumsItemsMultiRelationshipDataDocument albumItems) {
        if (albumItems == null) {
            return Collections.emptyList();
        }

        var albumItemsData = albumItems.getData();

        var includedInnerWrapper = new IncludedInnerWrapper(albumItems.getIncluded());

        return albumItemsData.stream().map(item -> includedInnerWrapper.getInner(item.getId(), AlbumsResourceObject.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(albumResource -> {
                    var artistData = albumResource.getRelationships().getArtists().getData();
                    return (Track) new TidalTrack(albumResource.getId(),
                            albumResource.getAttributes().getTitle(),
                            artistData.stream().map(this::createArtistEntityStub).toList(),
                            album,
                            DurationConverter.parseDurationToSeconds(albumResource.getAttributes().getDuration()));
        }).toList();
    }

    private List<Track> createPlaylistTrackList(PlaylistsItemsMultiRelationshipDataDocument playlistItems) {
        var playlistItemsData = playlistItems.getData();

        var includedInnerWrapper = new IncludedInnerWrapper(playlistItems.getIncluded());

        return playlistItemsData.stream()
                .map(item -> includedInnerWrapper.getInner(item.getId(), TracksResourceObject.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(item -> {
                    return (Track) new TidalTrackStub(item.getId());
                }).toList();
    }

    private List<Track> createTrackList(List<TracksResourceObject> tracks, List<IncludedInner> included) {
        var includedInnerWrapper = new IncludedInnerWrapper(included);

        return tracks.stream()
                .map(item -> {
                    var artistData = item.getRelationships().getArtists().getData().stream()
                            .map(resource -> {
                                var innerOptional = includedInnerWrapper.getInner(resource.getId(), ArtistsResourceObject.class);
                                if (innerOptional.isEmpty()) {
                                    LOGGER.warn("Artist resource not included in IncludedInner list: ID {}", resource.getId());
                                }

                                return innerOptional;
                            })
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .toList();

                    var albumData = item.getRelationships().getAlbums().getData().stream()
                            .map(resource -> {
                                var innerOptional = includedInnerWrapper.getInner(resource.getId(), AlbumsResourceObject.class);
                                if (innerOptional.isEmpty()) {
                                    LOGGER.warn("Album resource not included in IncludedInner list: ID {}", resource.getId());
                                }

                                return innerOptional;
                            })
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .toList();

                    LOGGER.debug("track title = {}", item.getAttributes().getTitle());
                    return (Track) new TidalTrack(
                            item.getId(),
                            item.getAttributes().getTitle(),
                            artistData.stream().map(this::createArtistEntityStub).toList(),
                            new TidalAlbumStub(albumData.getFirst().getId()),
                            DurationConverter.parseDurationToSeconds(item.getAttributes().getDuration())
                    );
                }).toList();
    }

    private TidalArtist createArtistEntity(ArtistsResourceObject artist) {
        return new TidalArtist(artist.getId(), artist.getAttributes().getName());
    }

    private TidalArtist createArtistEntityStub(ResourceIdentifier artistIdentifier) {
        return new TidalArtistStub(artistIdentifier.getId());
    }

    private TidalArtist createArtistEntityStub(ArtistsResourceObject artist) {
        return new TidalArtistStub(artist.getId());
    }

    private TidalAlbum createAlbumEntityStub(AlbumsResourceObject album) {
        return new TidalAlbumStub(album.getId());
    }
}
