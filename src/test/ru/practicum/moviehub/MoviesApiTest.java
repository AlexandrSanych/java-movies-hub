package ru.practicum.moviehub;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;
import ru.practicum.moviehub.http.MoviesServer;
import ru.practicum.moviehub.model.Movie;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static MoviesServer server;
    private static HttpClient client;
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, new Movie.LocalDateAdapter())
            .create();

    @BeforeAll
    static void beforeAll() throws IOException, InterruptedException {
        System.out.println("=== ЗАПУСК СЕРВЕРА ===");
        server = new MoviesServer();
        server.start();

        Thread.sleep(2000);

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        System.out.println("Сервер запущен на " + BASE);
        System.out.println("========================\n");
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        if (server != null) {
            server.clearStore();
            Thread.sleep(100);
        }
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            System.out.println("\n=== ОСТАНОВКА СЕРВЕРА ===");
            server.stop();
            System.out.println("Сервер остановлен");
        }
    }

    // ==================== БАЗОВЫЕ ТЕСТЫ ====================

    @Test
    @Order(1)
    @DisplayName("1. GET /movies возвращает пустой список при первом запуске")
    void test01_getMoviesEmpty() throws Exception {
        System.out.println("Тест 1: GET /movies (пустой)");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("  Статус: " + resp.statusCode());
        System.out.println("  Тело: " + resp.body());

        assertEquals(200, resp.statusCode(), "Должен вернуть 200 OK");
        assertEquals("[]", resp.body().trim(), "Должен вернуть пустой массив");

        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.contains("application/json"),
                "Content-Type должен быть application/json. Получено: " + contentType);

        System.out.println("  ✓ Тест пройден\n");
    }

    @Test
    @Order(2)
    @DisplayName("2. POST /movies создает фильм с валидными данными")
    void test02_postMovieValid() throws Exception {
        System.out.println("Тест 2: POST /movies (валидные данные)");

        String movieJson = createMovieJson("Побег из Шоушенка",
                "Драма о надежде в тюрьме", "1994-09-23", 142);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("  Статус: " + resp.statusCode());
        System.out.println("  Тело: " + resp.body());

        assertEquals(201, resp.statusCode(), "Должен вернуть 201 Created");

        Movie movie = gson.fromJson(resp.body(), Movie.class);
        assertNotNull(movie, "Ответ должен содержать объект Movie");
        assertEquals(1, movie.getId(), "Первый фильм должен иметь ID=1");
        assertEquals("Побег из Шоушенка", movie.getName());
        assertEquals("Драма о надежде в тюрьме", movie.getDescription());
        assertEquals(LocalDate.parse("1994-09-23"), movie.getReleaseDate());
        assertEquals(142, movie.getDuration());

        System.out.println("  Создан фильм: ID=" + movie.getId() + ", Название=" + movie.getName());
        System.out.println("  ✓ Тест пройден\n");
    }

    @Test
    @Order(3)
    @DisplayName("3. GET /movies возвращает добавленные фильмы")
    void test03_getMoviesAfterPost() throws Exception {
        System.out.println("Тест 3: GET /movies (после добавления)");

        String movieJson = createMovieJson("Начало",
                "Фильм о сновидениях", "2010-07-16", 148);
        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();

        HttpResponse<String> postResp = client.send(postReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(201, postResp.statusCode());

        Thread.sleep(100);

        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> getResp = client.send(getReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("  Статус GET: " + getResp.statusCode());

        assertEquals(200, getResp.statusCode(), "Должен вернуть 200 OK");

        Movie[] movies = gson.fromJson(getResp.body(), Movie[].class);
        assertEquals(1, movies.length, "Должен быть 1 фильм");
        assertEquals("Начало", movies[0].getName());
        assertEquals(1, movies[0].getId(), "Фильм должен иметь ID=1");

        System.out.println("  Найдено фильмов: " + movies.length);
        System.out.println("  ✓ Тест пройден\n");
    }

    @Test
    @Order(4)
    @DisplayName("4. GET /movies/{id} возвращает фильм по существующему ID")
    void test04_getMovieByIdValid() throws Exception {
        System.out.println("Тест 4: GET /movies/{id} (существующий ID)");

        String movieJson = createMovieJson("Крестный отец",
                "Мафиозная сага", "1972-03-24", 175);
        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();

        HttpResponse<String> postResp = client.send(postReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(201, postResp.statusCode());

        Movie createdMovie = gson.fromJson(postResp.body(), Movie.class);
        System.out.println("  Создан фильм с ID=" + createdMovie.getId());

        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + createdMovie.getId()))
                .GET()
                .build();

        HttpResponse<String> getResp = client.send(getReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("  Статус GET: " + getResp.statusCode());
        System.out.println("  Тело: " + getResp.body());

        assertEquals(200, getResp.statusCode(), "Должен вернуть 200 OK");

        Movie retrievedMovie = gson.fromJson(getResp.body(), Movie.class);
        assertEquals(createdMovie.getId(), retrievedMovie.getId());
        assertEquals("Крестный отец", retrievedMovie.getName());
        assertEquals("Мафиозная сага", retrievedMovie.getDescription());
        assertEquals(175, retrievedMovie.getDuration());

        System.out.println("  ✓ Тест пройден\n");
    }

    @Test
    @Order(5)
    @DisplayName("5. GET /movies/{id} возвращает 404 для несуществующего ID")
    void test05_getMovieByIdNotFound() throws Exception {
        System.out.println("Тест 5: GET /movies/{id} (несуществующий ID)");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("  Статус: " + resp.statusCode());
        System.out.println("  Тело: " + resp.body());

        assertEquals(404, resp.statusCode(), "Должен вернуть 404 Not Found");

        assertTrue(resp.body().contains("\"status\":404"),
                "Ответ должен содержать статус 404");
        assertTrue(resp.body().contains("Фильм не найден"),
                "Ответ должен содержать сообщение 'Фильм не найден'");

        System.out.println("  ✓ Тест пройден\n");
    }

    @Test
    @Order(6)
    @DisplayName("6. DELETE /movies/{id} удаляет фильм и возвращает 204")
    void test06_deleteMovieValid() throws Exception {
        System.out.println("Тест 6: DELETE /movies/{id} (удаление)");

        String movieJson = createMovieJson("Криминальное чтиво",
                "Неллинейный криминальный фильм", "1994-10-14", 154);
        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();

        HttpResponse<String> postResp = client.send(postReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(201, postResp.statusCode());

        Movie createdMovie = gson.fromJson(postResp.body(), Movie.class);
        System.out.println("  Создан фильм с ID=" + createdMovie.getId());

        HttpRequest deleteReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + createdMovie.getId()))
                .DELETE()
                .build();

        HttpResponse<String> deleteResp = client.send(deleteReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("  Статус DELETE: " + deleteResp.statusCode());

        assertEquals(204, deleteResp.statusCode(), "Должен вернуть 204 No Content");
        assertTrue(deleteResp.body().isEmpty(), "Тело ответа должно быть пустым");

        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + createdMovie.getId()))
                .GET()
                .build();

        HttpResponse<String> getResp = client.send(getReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, getResp.statusCode(), "После удаления должен возвращать 404");

        System.out.println("  ✓ Тест пройден\n");
    }

    @Test
    @Order(7)
    @DisplayName("7. DELETE /movies/{id} возвращает 404 для несуществующего фильма")
    void test07_deleteMovieNotFound() throws Exception {
        System.out.println("Тест 7: DELETE /movies/{id} (несуществующий)");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .DELETE()
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("  Статус: " + resp.statusCode());
        System.out.println("  Тело: " + resp.body());

        assertEquals(404, resp.statusCode(), "Должен вернуть 404 Not Found");

        System.out.println("  ✓ Тест пройден\n");
    }

    @Test
    @Order(8)
    @DisplayName("8. GET /movies?year=YYYY фильтрует по году выпуска")
    void test08_getMoviesByYear() throws Exception {
        System.out.println("Тест 8: GET /movies?year=2000 (фильтрация)");

        String[] movies = {
                createMovieJson("Фильм 2000", "Описание 2000", "2000-01-01", 120),
                createMovieJson("Фильм 2005", "Описание 2005", "2005-06-15", 130),
                createMovieJson("Еще 2000", "Еще описание", "2000-12-31", 140),
                createMovieJson("Фильм 2010", "Описание 2010", "2010-03-20", 150)
        };

        for (int i = 0; i < movies.length; i++) {
            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/movies"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(movies[i]))
                    .build();

            HttpResponse<String> postResp = client.send(postReq,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(201, postResp.statusCode(), "Фильм " + i + " должен создаться");
            Thread.sleep(50);
        }

        System.out.println("  Добавлено 4 фильма разных годов");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2000"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("  Статус: " + resp.statusCode());

        assertEquals(200, resp.statusCode(), "Должен вернуть 200 OK");

        Movie[] filteredMovies = gson.fromJson(resp.body(), Movie[].class);
        assertEquals(2, filteredMovies.length, "Должно быть 2 фильма 2000 года");

        for (Movie movie : filteredMovies) {
            assertEquals(2000, movie.getReleaseDate().getYear(),
                    "Все фильмы должны быть 2000 года");
        }

        System.out.println("  Найдено фильмов 2000 года: " + filteredMovies.length);
        System.out.println("  ✓ Тест пройден\n");
    }

    @Test
    @Order(9)
    @DisplayName("9. GET /movies?year= с неверным параметром возвращает 400")
    void test09_getMoviesByYearInvalid() throws Exception {
        System.out.println("Тест 9: GET /movies?year=abc (невалидный год)");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=abc"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("  Статус: " + resp.statusCode());
        System.out.println("  Тело: " + resp.body());

        assertEquals(400, resp.statusCode(), "Должен вернуть 400 Bad Request");

        System.out.println("  ✓ Тест пройден\n");
    }

    @Test
    @Order(10)
    @DisplayName("10. Неподдерживаемый метод возвращает 405")
    void test10_unsupportedMethod() throws Exception {
        System.out.println("Тест 10: PUT /movies (неподдерживаемый метод)");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("  Статус: " + resp.statusCode());
        System.out.println("  Тело: " + resp.body());

        assertEquals(405, resp.statusCode(), "Должен вернуть 405 Method Not Allowed");

        String allowHeader = resp.headers().firstValue("Allow").orElse("");
        assertTrue(allowHeader.contains("GET") && allowHeader.contains("POST") &&
                        allowHeader.contains("DELETE"),
                "Заголовок Allow должен содержать GET, POST, DELETE");

        System.out.println("  Заголовок Allow: " + allowHeader);
        System.out.println("  ✓ Тест пройден\n");
    }

    @Test
    @Order(11)
    @DisplayName("11. POST /movies с неправильным Content-Type возвращает 415")
    void test11_postWrongContentType() throws Exception {
        System.out.println("Тест 11: POST /movies (text/plain вместо JSON)");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("просто текст"))
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("  Статус: " + resp.statusCode());
        System.out.println("  Тело: " + resp.body());

        assertEquals(415, resp.statusCode(), "Должен вернуть 415 Unsupported Media Type");

        System.out.println("  ✓ Тест пройден\n");
    }

    @Test
    @Order(12)
    @DisplayName("12. POST /movies с невалидным JSON возвращает 400")
    void test12_postInvalidJson() throws Exception {
        System.out.println("Тест 12: POST /movies (невалидный JSON)");

        // Действительно невалидный JSON - незакрытая фигурная скобка
        String invalidJson = "{\"name\": \"Фильм\", \"releaseDate\": \"2023-01-01\", \"duration\": 120";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(invalidJson))
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("  Статус: " + resp.statusCode());
        System.out.println("  Тело: " + resp.body());

        assertEquals(400, resp.statusCode(), "Должен вернуть 400 Bad Request");
        assertTrue(resp.body().contains("Неверный формат JSON"),
                "Должен содержать сообщение 'Неверный формат JSON'");

        System.out.println("  ✓ Тест пройден\n");
    }

    @Test
    @Order(13)
    @DisplayName("13. POST /movies с невалидными данными возвращает 422")
    void test13_postInvalidData() throws Exception {
        System.out.println("Тест 13: POST /movies (невалидные данные)");

        String invalidJson = "{\"name\":\"\", \"releaseDate\":\"2023-01-01\", \"duration\":-10}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(invalidJson))
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("  Статус: " + resp.statusCode());
        System.out.println("  Тело: " + resp.body());

        assertEquals(422, resp.statusCode(), "Должен вернуть 422 Unprocessable Entity");

        assertTrue(resp.body().contains("\"details\""),
                "Ответ должен содержать детали ошибок");
        assertTrue(resp.body().contains("Название обязательно") ||
                        resp.body().contains("Продолжительность должна быть положительной"),
                "Должен содержать конкретные ошибки валидации на русском");

        System.out.println("  ✓ Тест пройден\n");
    }

    @Test
    @Order(14)
    @DisplayName("14. Несколько последовательных добавлений (проверка автоинкремента)")
    void test14_multipleAdditions() throws Exception {
        System.out.println("Тест 14: Множественные добавления (автоинкремент)");

        Set<Integer> ids = new HashSet<>();

        for (int i = 1; i <= 5; i++) {
            String movieJson = createMovieJson("Фильм " + i,
                    "Описание " + i, "200" + i + "-01-01", 100 + i);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/movies"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                    .build();

            HttpResponse<String> resp = client.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            assertEquals(201, resp.statusCode(), "Фильм " + i + " должен создаться");

            Movie movie = gson.fromJson(resp.body(), Movie.class);
            ids.add(movie.getId());

            System.out.println("  Добавлен фильм: ID=" + movie.getId() +
                    ", Название=" + movie.getName());

            Thread.sleep(50);
        }

        // Проверяем, что все ID уникальны и идут по порядку
        assertEquals(5, ids.size(), "Должно быть 5 уникальных ID");
        assertTrue(ids.contains(1) && ids.contains(2) && ids.contains(3) &&
                ids.contains(4) && ids.contains(5), "ID должны быть от 1 до 5");

        // Проверяем общее количество фильмов
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> getResp = client.send(getReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        Movie[] allMovies = gson.fromJson(getResp.body(), Movie[].class);
        assertEquals(5, allMovies.length, "Должно быть 5 фильмов в хранилище");

        System.out.println("  Всего фильмов в хранилище: " + allMovies.length);
        System.out.println("  ✓ Тест пройден\n");
    }

    @Test
    @Order(15)
    @DisplayName("15. Проверка очистки хранилища между тестами")
    void test15_storeClearBetweenTests() throws Exception {
        System.out.println("Тест 15: Проверка очистки хранилища");

        // Проверяем, что хранилище пустое (должно быть очищено @BeforeEach)
        HttpRequest getEmptyReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> emptyResp = client.send(getEmptyReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("  Проверка пустого хранилища:");
        System.out.println("    Статус: " + emptyResp.statusCode());
        System.out.println("    Тело: " + emptyResp.body());

        assertEquals(200, emptyResp.statusCode());
        assertEquals("[]", emptyResp.body().trim(),
                "Хранилище должно быть пустым перед тестом");

        // Добавляем фильм
        String movieJson = createMovieJson("Тестовый фильм",
                "Проверка очистки", "2023-01-01", 120);

        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();

        HttpResponse<String> postResp = client.send(postReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("  Добавлен фильм:");
        System.out.println("    Статус: " + postResp.statusCode());
        System.out.println("    Тело: " + postResp.body());

        assertEquals(201, postResp.statusCode());

        Movie movie = gson.fromJson(postResp.body(), Movie.class);
        assertEquals(1, movie.getId(), "Фильм должен получить ID=1");

        // Проверяем, что фильм добавлен
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> getResp = client.send(getReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        Movie[] movies = gson.fromJson(getResp.body(), Movie[].class);
        assertEquals(1, movies.length, "Должен быть 1 фильм");

        System.out.println("  ✓ Хранилище корректно очищается между тестами\n");
    }

    @Test
    @Order(16)
    @DisplayName("16. Параллельные запросы на добавление")
    void test16_concurrentPosts() throws Exception {
        System.out.println("Тест 16: Параллельные POST запросы (5 потоков)");

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        Set<Integer> ids = new HashSet<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    String movieJson = createMovieJson("Параллельный " + index,
                            "Поток " + index, "2023-01-01", 100 + index);

                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(BASE + "/movies"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                            .build();

                    HttpResponse<String> resp = client.send(req,
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                    if (resp.statusCode() == 201) {
                        Movie movie = gson.fromJson(resp.body(), Movie.class);
                        synchronized (ids) {
                            ids.add(movie.getId());
                            System.out.println("    Поток " + index + ": создан фильм ID=" + movie.getId());
                        }
                    } else {
                        System.err.println("    Поток " + index + ": ошибка " + resp.statusCode());
                    }
                } catch (Exception e) {
                    System.err.println("    Поток " + index + ": исключение " + e.getMessage());
                }
            });
        }

        executor.shutdown();
        // Ждем завершения всех задач
        while (!executor.isTerminated()) {
            Thread.sleep(100);
        }

        System.out.println("  Всего создано уникальных ID: " + ids.size());

        // Проверяем результаты
        assertEquals(threadCount, ids.size(),
                "Все " + threadCount + " параллельных запросов должны создать уникальные ID");

        // Проверяем общее количество фильмов
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> getResp = client.send(getReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        Movie[] allMovies = gson.fromJson(getResp.body(), Movie[].class);
        assertEquals(threadCount, allMovies.length,
                "Должно быть " + threadCount + " фильмов");

        System.out.println("  Всего фильмов в хранилище: " + allMovies.length);
        System.out.println("  ✓ Тест пройден\n");
    }

    @Test
    @Order(17)
    @DisplayName("17. Очень длинное название (100 символов)")
    void test17_longName() throws Exception {
        System.out.println("Тест 17: POST /movies с названием 100 символов");

        String longName = "A".repeat(100);
        String movieJson = createMovieJson(longName,
                "Фильм с очень длинным названием", "2023-01-01", 120);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("  Статус: " + resp.statusCode());

        if (resp.statusCode() == 201) {
            Movie movie = gson.fromJson(resp.body(), Movie.class);
            assertEquals(100, movie.getName().length(), "Название должно быть 100 символов");
            System.out.println("  ✓ Создан фильм с названием 100 символов");
        } else {
            System.out.println("  Тело: " + resp.body());
            // Если возвращает 422 - это тоже допустимо (ограничение валидации)
            assertEquals(422, resp.statusCode(),
                    "Может вернуть 201 или 422 (если есть ограничение в 100 символов)");
        }

        System.out.println("  ✓ Тест пройден\n");
    }

    @Test
    @Order(18)
    @DisplayName("18. Пустое описание (допустимо)")
    void test18_emptyDescription() throws Exception {
        System.out.println("Тест 18: POST /movies с пустым описанием");

        String movieJson = createMovieJson("Фильм без описания",
                "", "2023-01-01", 120);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("  Статус: " + resp.statusCode());

        if (resp.statusCode() == 201) {
            Movie movie = gson.fromJson(resp.body(), Movie.class);
            assertEquals("", movie.getDescription(), "Описание должно быть пустым");
            System.out.println("  ✓ Создан фильм с пустым описанием");
        } else {
            System.out.println("  Тело: " + resp.body());
            fail("Пустое описание должно быть допустимо");
        }

        System.out.println("  ✓ Тест пройден\n");
    }

    @Test
    @Order(19)
    @DisplayName("19. Фильтр по несуществующему году")
    void test19_filterNonExistentYear() throws Exception {
        System.out.println("Тест 19: GET /movies?year=2100 (год в далеком будущем)");

        // Хранилище пустое

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2100"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("  Статус: " + resp.statusCode());
        System.out.println("  Тело: " + resp.body());

        // Исправленная логика: должен вернуть 200 с пустым массивом
        assertEquals(200, resp.statusCode(), "Должен вернуть 200 OK");
        assertEquals("[]", resp.body().trim(), "Должен вернуть пустой массив");

        System.out.println("  ✓ Вернул 200 с пустым массивом");
        System.out.println("  ✓ Тест пройден\n");
    }

    @Test
    @Order(20)
    @DisplayName("20. Фильтр по году с существующими фильмами, но не по этому году")
    void test20_filterYearWithOtherMovies() throws Exception {
        System.out.println("Тест 20: GET /movies?year=2000 (есть фильмы, но не 2000 года)");

        // Очищаем хранилище
        server.clearStore();
        Thread.sleep(100);

        // Добавляем фильмы НЕ 2000 года
        String movie1 = createMovieJson("Фильм 1999", "1999 год", "1999-01-01", 100);
        String movie2 = createMovieJson("Фильм 2001", "2001 год", "2001-01-01", 110);

        client.send(HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movie1))
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Thread.sleep(50);

        client.send(HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movie2))
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Thread.sleep(100);

        // Фильтруем по 2000 году
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2000"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("  Статус: " + resp.statusCode());

        assertEquals(200, resp.statusCode(), "Должен вернуть 200 OK");
        Movie[] movies = gson.fromJson(resp.body(), Movie[].class);
        assertEquals(0, movies.length, "Должен вернуть пустой массив (нет фильмов 2000 года)");
        System.out.println("  ✓ Вернул 200 с пустым массивом (0 фильмов 2000 года)");
        System.out.println("  ✓ Тест пройден\n");
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private String createMovieJson(String name, String description, String releaseDate, int duration) {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("description", description != null ? description : "");
        json.addProperty("releaseDate", releaseDate);
        json.addProperty("duration", duration);
        return gson.toJson(json);
    }
}