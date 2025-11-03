package dev.qilletni.lib.tidal.music.entities;

import dev.qilletni.api.auth.ServiceProvider;
import dev.qilletni.api.music.Playlist;
import dev.qilletni.api.music.User;

import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.sql.Date;
import java.util.Collections;
import java.util.Optional;

@Entity
public class TidalPlaylist implements Playlist {

    @Id
    private String id;
    private String title;
    private int trackCount;

    @ManyToOne(fetch = FetchType.EAGER)
    private TidalUser creator;

    @Embedded
    private TidalPlaylistIndex tidalPlaylistIndex;

    public TidalPlaylist() {}

    public TidalPlaylist(String id, String title, TidalUser creator, int trackCount) {
        this.id = id;
        this.title = title;
        this.creator = creator;
        this.trackCount = trackCount;
        this.tidalPlaylistIndex = new TidalPlaylistIndex(Collections.emptyList(), new Date(0));
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public User getCreator() {
        return creator;
    }

    @Override
    public int getTrackCount() {
        return trackCount;
    }

    @Override
    public Optional<ServiceProvider> getServiceProvider() {
        return Optional.empty();
    }

    public TidalPlaylistIndex getTidalPlaylistIndex() {
        return tidalPlaylistIndex;
    }

    public void setTidalPlaylistIndex(TidalPlaylistIndex tidalPlaylistIndex) {
        this.tidalPlaylistIndex = tidalPlaylistIndex;
    }

    @Override
    public String toString() {
        return "TidalPlaylist{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", creator=" + creator +
                '}';
    }
}
