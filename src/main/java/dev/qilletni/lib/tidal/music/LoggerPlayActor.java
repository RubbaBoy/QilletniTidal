package dev.qilletni.lib.tidal.music;

import dev.qilletni.api.music.Track;
import dev.qilletni.api.music.play.PlayActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class LoggerPlayActor implements PlayActor {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggerPlayActor.class);

    @Override
    public CompletableFuture<PlayResult> playTrack(Track track) {
        LOGGER.info("Playing: {} - {}", track.getName(), track.getArtist().getName());

        return CompletableFuture.completedFuture(PlayResult.SUCCESS);
    }
}
