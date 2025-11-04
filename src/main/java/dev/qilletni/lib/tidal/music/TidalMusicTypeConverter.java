package dev.qilletni.lib.tidal.music;

import dev.qilletni.api.music.Album;
import dev.qilletni.api.music.Artist;
import dev.qilletni.api.music.MusicTypeConverter;
import dev.qilletni.api.music.Playlist;
import dev.qilletni.api.music.Track;
import dev.qilletni.api.music.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class TidalMusicTypeConverter implements MusicTypeConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TidalMusicTypeConverter.class);

    private final TidalMusicCache musicCache;

    public TidalMusicTypeConverter(TidalMusicCache musicCache) {
        this.musicCache = musicCache;
    }

    @Override
    public Optional<Track> convertTrack(List<Track> tracks) {
        // Try all tracks
        for (var track : tracks) {
            var artistName = track.getArtist().getName();
            if (artistName == null) {
                continue; // Just in case, for some reason this is null
            }

            var trackOptional = musicCache.getTrack(track.getName(), artistName);
            if (trackOptional.isPresent()) {
                return trackOptional;
            }
        }

        return Optional.empty();
    }

    @Override
    public Optional<Album> convertAlbum(List<Album> albums) {
        // Try all albums
        for (var album : albums) {
            var artistName = album.getArtist().getName();
            if (artistName == null) {
                continue; // Just in case, for some reason this is null
            }

            var albumOptional = musicCache.getAlbum(album.getName(), artistName);
            if (albumOptional.isPresent()) {
                return albumOptional;
            }
        }

        return Optional.empty();
    }

    @Override
    public Optional<Artist> convertArtist(List<Artist> artists) {
        // Try all artists
        for (var artist : artists) {
            var artistOptional = musicCache.getArtistByName(artist.getName());
            if (artistOptional.isPresent()) {
                return artistOptional;
            }
        }

        return Optional.empty();
    }

    @Override
    public Optional<Playlist> convertPlaylist(List<Playlist> playlists) {
        // We don't support playlist conversion in this package
        LOGGER.warn("Playlist conversion is not supported in the Tidal service provider");
        return Optional.empty();
    }

    @Override
    public Optional<User> convertUser(List<User> users) {
        // We don't support playlist conversion in this package
        LOGGER.warn("User conversion is not supported in the Tidal service provider");
        return Optional.empty();
    }
}
