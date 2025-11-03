import dev.qilletni.lib.tidal.music.provider.TidalServiceProvider;

module qilletni.lib.tidal.main {
    requires qilletni.api;
    requires java.desktop;
    requires java.net.http;
    requires com.google.gson;
    requires java.persistence;
    requires jdk.jfr;
    requires org.hibernate.orm.core;
    requires com.tidal.sdk.tidalapi;
    requires org.jetbrains.annotations;
    requires jdk.httpserver;
    requires retrofit2;
    requires kotlinx.coroutines.core;
    requires java.naming;
    requires java.sql;

    provides dev.qilletni.api.auth.ServiceProvider
            with TidalServiceProvider;
}
