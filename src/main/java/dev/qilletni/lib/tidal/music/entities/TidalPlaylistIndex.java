package dev.qilletni.lib.tidal.music.entities;

import javax.persistence.Embeddable;
import javax.persistence.ManyToMany;
import java.sql.Date;
import java.util.List;

/**
 * An embeddable entity that stores a cached index of tracks in a playlist.
 * This index is meant to be regularly updated and includes an expiration timestamp.
 */
@Embeddable
public class TidalPlaylistIndex {

    @ManyToMany
    private List<TidalTrack> tracks;

    private Date lastUpdatedIndex;

    public TidalPlaylistIndex() {}

    public TidalPlaylistIndex(List<TidalTrack> tracks, Date lastUpdatedIndex) {
        this.tracks = tracks;
        this.lastUpdatedIndex = lastUpdatedIndex;
    }

    public List<TidalTrack> getTracks() {
        return tracks;
    }

    public Date getLastUpdatedIndex() {
        return lastUpdatedIndex;
    }

    @Override
    public String toString() {
        return "TidalPlaylistIndex{" +
                "tracks=" + tracks +
                ", lastUpdatedIndex=" + lastUpdatedIndex +
                '}';
    }
}
