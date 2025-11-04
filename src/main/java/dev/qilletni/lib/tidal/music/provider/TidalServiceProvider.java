package dev.qilletni.lib.tidal.music.provider;

import dev.qilletni.api.auth.ServiceProvider;
import dev.qilletni.api.exceptions.config.ConfigInitializeException;
import dev.qilletni.api.lib.persistence.PackageConfig;
import dev.qilletni.api.music.ConsolePlayActor;
import dev.qilletni.api.music.MusicCache;
import dev.qilletni.api.music.MusicFetcher;
import dev.qilletni.api.music.MusicTypeConverter;
import dev.qilletni.api.music.StringIdentifier;
import dev.qilletni.api.music.factories.AlbumTypeFactory;
import dev.qilletni.api.music.factories.CollectionTypeFactory;
import dev.qilletni.api.music.factories.SongTypeFactory;
import dev.qilletni.api.music.orchestration.TrackOrchestrator;
import dev.qilletni.api.music.play.DefaultRoutablePlayActor;
import dev.qilletni.api.music.play.PlayActor;
import dev.qilletni.lib.tidal.api.TidalApiSingleton;
import dev.qilletni.lib.tidal.api.oauth.TidalOAuthAuthorizer;
import dev.qilletni.lib.tidal.database.HibernateUtil;
import dev.qilletni.lib.tidal.music.TidalMusicCache;
import dev.qilletni.lib.tidal.music.TidalMusicFetcher;
import dev.qilletni.lib.tidal.music.TidalMusicTypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class TidalServiceProvider implements ServiceProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(TidalServiceProvider.class);

    private PackageConfig packageConfig;
    private TidalOAuthAuthorizer authorizer;
    private TidalMusicFetcher musicFetcher;
    private TidalMusicCache musicCache;
    private TrackOrchestrator trackOrchestrator;
    private TidalMusicTypeConverter musicTypeConverter;
    private PlayActor playActor;

    private static ServiceProvider serviceProviderInstance;

    @Override
    public CompletableFuture<Void> initialize(BiFunction<PlayActor, MusicCache, TrackOrchestrator> defaultTrackOrchestratorFunction, PackageConfig packageConfig) {
        this.packageConfig = packageConfig;
        populateInitialConfig();
        initConfig();

        // Or with explicit credentials
        authorizer = new TidalOAuthAuthorizer(packageConfig, packageConfig.getOrThrow("clientId"), packageConfig.getOrThrow("redirectUri"));

        // Authorize (async)
        return authorizer.authorizeTidal().thenAccept(tidalApi -> {
            TidalApiSingleton.setTidalApi(tidalApi);

            musicFetcher = new TidalMusicFetcher("US", tidalApi, authorizer.getCurrentUser().orElseThrow());
            musicCache = new TidalMusicCache(musicFetcher);
            playActor = new DefaultRoutablePlayActor(new ConsolePlayActor());
            trackOrchestrator = defaultTrackOrchestratorFunction.apply(playActor, musicCache);
            musicTypeConverter = new TidalMusicTypeConverter(musicCache);

            serviceProviderInstance = this;
        });
    }

    @Override
    public void shutdown() {
        authorizer.shutdown();

        if (TidalApiSingleton.getTidalApi() != null) {
            TidalApiSingleton.getTidalApi().shutdown();
        }
    }

    @Override
    public String getName() {
        return "Tidal";
    }

    @Override
    public MusicCache getMusicCache() {
        return Objects.requireNonNull(musicCache, "ServiceProvider#initialize must be invoked to initialize MusicCache");
    }

    @Override
    public MusicFetcher getMusicFetcher() {
        return Objects.requireNonNull(musicFetcher, "ServiceProvider#initialize must be invoked to initialize MusicFetcher");
    }

    @Override
    public TrackOrchestrator getTrackOrchestrator() {
        return Objects.requireNonNull(trackOrchestrator, "ServiceProvider#initialize must be invoked to initialize TrackOrchestrator");
    }

    @Override
    public MusicTypeConverter getMusicTypeConverter() {
        return Objects.requireNonNull(musicTypeConverter, "ServiceProvider#initialize must be invoked to initialize MusicTypeConverter");
    }

    @Override
    public StringIdentifier getStringIdentifier(SongTypeFactory songTypeFactory, CollectionTypeFactory collectionTypeFactory, AlbumTypeFactory albumTypeFactory) {
        return null;
    }

    @Override
    public PlayActor getPlayActor() {
        return Objects.requireNonNull(playActor, "ServiceProvider#initialize must be invoked to initialize PlayActor");
    }

    /**
     * Populate the config if it's empty (i.e. first run).
     */
    private void populateInitialConfig() {
        packageConfig.loadConfig();

        if (packageConfig.get("dbUrl").isEmpty() && packageConfig.get("dbUsername").isEmpty() && packageConfig.get("dbPassword").isEmpty()) {
            LOGGER.debug("Tidal config is empty, populating with default values");

            packageConfig.set("dbUrl", "jdbc:postgresql://localhost:5435/qilletni_tidal");
            packageConfig.set("dbUsername", "qilletni");
            packageConfig.set("dbPassword", "pass");
            packageConfig.saveConfig();
        } else {
            LOGGER.debug("Tidal config already populated, skipping");
        }
    }

    private void initConfig() {
        packageConfig.loadConfig();

        var requiredOptions = List.of("clientId", "redirectUri", "dbUrl", "dbUsername", "dbPassword");
        var allFound = true;

        for (var option : requiredOptions) {
            if (packageConfig.get(option).isEmpty()) {
                allFound = false;
                LOGGER.error("Required config value '{}' not found in Tidal config", option);
            }
        }

        if (!allFound) {
            throw new ConfigInitializeException("Tidal config is missing required options, aborting");
        }

        HibernateUtil.initializeSessionFactory(packageConfig.getOrThrow("dbUrl"), packageConfig.getOrThrow("dbUsername"), packageConfig.getOrThrow("dbPassword"));
    }

    public static ServiceProvider getServiceProviderInstance() {
        return Objects.requireNonNull(serviceProviderInstance, "ServiceProvider#initialize must be invoked to initialize ServiceProvider");
    }
}
