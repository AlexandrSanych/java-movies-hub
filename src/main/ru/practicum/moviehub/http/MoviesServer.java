package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpServer;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.InetSocketAddress;

//HTTP сервер для MovieHub API
public class MoviesServer {
    private final HttpServer server;
    private final MoviesStore moviesStore;

    public MoviesServer() throws IOException {
        this.moviesStore = new MoviesStore();
        server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/movies", new MoviesHandler(moviesStore));

        // Устанавливаем executor (null означает использование дефолтного)
        server.setExecutor(null);
    }

    //Очищает хранилище фильмов
    public void clearStore() {
        moviesStore.clear();
    }

    //Запускает сервер
    public void start() {
        server.start();
        System.out.println("MovieHub сервер запущен на порту 8080");
    }

    //Останавливает сервер
    public void stop() {
        server.stop(0);
        System.out.println("Сервер остановлен");
    }
}