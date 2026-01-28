package ru.practicum.moviehub;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;
import ru.practicum.moviehub.http.MoviesServer;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.model.LocalDateAdapter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class MoviesApiTest {
    private static final String BASE_URL = "http://localhost:8080";
    private static MoviesServer server;
    private static HttpClient client;
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
            .create();

    @BeforeAll
    static void beforeAll() throws IOException {
        server = new MoviesServer();
        server.start();

        // Даем серверу время на запуск с помощью поллинга
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // Ждем пока сервер не станет доступен
        waitForServerStart();
    }

    private static void waitForServerStart() {
        long deadline = System.currentTimeMillis() + 3000; // 3 секунды на запуск

        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest testRequest = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/movies"))
                        .GET()
                        .timeout(Duration.ofMillis(100)) // Короткий таймаут
                        .build();

                client.send(testRequest,
                        HttpResponse.BodyHandlers.discarding()); // Не читаем тело

                return; // Если запрос успешен, сервер запущен
            } catch (Exception e) {

            }
        }

        throw new RuntimeException("Сервер не запустился за 3 секунды");
    }

    @BeforeEach
    void setUp() {
        if (server != null) {
            server.clearStore();
        }
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("GET /movies возвращает пустой список при первом запуске")
    void getMovies_shouldReturnEmptyList_whenNoMoviesAdded() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "Должен вернуть 200 OK");
        assertEquals("[]", resp.body().trim(), "Должен вернуть пустой массив");

        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.contains("application/json"),
                "Content-Type должен быть application/json. Получено: " + contentType);
    }

    @Test
    @DisplayName("POST /movies создает фильм с валидными данными")
    void postMovie_shouldCreateMovie_whenValidData() throws Exception {
        String movieJson = createMovieJson("Побег из Шоушенка",
                "Драма о надежде в тюрьме", "1994-09-23", 142);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(201, resp.statusCode(), "Должен вернуть 201 Created");

        Movie movie = gson.fromJson(resp.body(), Movie.class);
        assertNotNull(movie, "Ответ должен содержать объект Movie");
        assertTrue(movie.getId() > 0, "Фильм должен получить уникальный ID");
        assertEquals("Побег из Шоушенка", movie.getName());
        assertEquals("Драма о надежде в тюрьме", movie.getDescription());
        assertEquals(LocalDate.parse("1994-09-23"), movie.getReleaseDate());
        assertEquals(142, movie.getDuration());
    }

    @Test
    @DisplayName("GET /movies возвращает добавленные фильмы")
    void getMovies_shouldReturnMovies_whenMoviesAdded() throws Exception {
        // Сначала создаем фильм
        String movieJson = createMovieJson("Начало",
                "Фильм о сновидениях", "2010-07-16", 148);
        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();

        HttpResponse<String> postResp = client.send(postReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(201, postResp.statusCode(), "Фильм должен быть создан");

        // Затем получаем все фильмы
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .GET()
                .build();

        HttpResponse<String> getResp = client.send(getReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, getResp.statusCode(), "Должен вернуть 200 OK");

        Movie[] movies = gson.fromJson(getResp.body(), Movie[].class);
        assertEquals(1, movies.length, "Должен быть 1 фильм");
        assertEquals("Начало", movies[0].getName());
    }

    @Test
    @DisplayName("GET /movies/{id} возвращает фильм по существующему ID")
    void getMovieById_shouldReturnMovie_whenIdExists() throws Exception {
        // Сначала создаем фильм
        String movieJson = createMovieJson("Крестный отец",
                "Мафиозная сага", "1972-03-24", 175);
        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();

        HttpResponse<String> postResp = client.send(postReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(201, postResp.statusCode());

        Movie createdMovie = gson.fromJson(postResp.body(), Movie.class);

        // Затем получаем фильм по ID
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies/" + createdMovie.getId()))
                .GET()
                .build();

        HttpResponse<String> getResp = client.send(getReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, getResp.statusCode(), "Должен вернуть 200 OK");

        Movie retrievedMovie = gson.fromJson(getResp.body(), Movie.class);
        assertEquals(createdMovie.getId(), retrievedMovie.getId());
        assertEquals("Крестный отец", retrievedMovie.getName());
        assertEquals("Мафиозная сага", retrievedMovie.getDescription());
        assertEquals(175, retrievedMovie.getDuration());
    }

    @Test
    @DisplayName("GET /movies/{id} возвращает 404 для несуществующего ID")
    void getMovieById_shouldReturn404_whenIdNotExists() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies/999"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode(), "Должен вернуть 404 Not Found");

        assertTrue(resp.body().contains("\"status\":404"),
                "Ответ должен содержать статус 404");
        assertTrue(resp.body().contains("Фильм не найден"),
                "Ответ должен содержать сообщение 'Фильм не найден'");
    }

    @Test
    @DisplayName("DELETE /movies/{id} удаляет фильм и возвращает 204")
    void deleteMovie_shouldDeleteMovie_whenIdExists() throws Exception {
        // Сначала создаем фильм
        String movieJson = createMovieJson("Криминальное чтиво",
                "Неллинейный криминальный фильм", "1994-10-14", 154);
        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();

        HttpResponse<String> postResp = client.send(postReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(201, postResp.statusCode());

        Movie createdMovie = gson.fromJson(postResp.body(), Movie.class);

        // Затем удаляем его
        HttpRequest deleteReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies/" + createdMovie.getId()))
                .DELETE()
                .build();

        HttpResponse<String> deleteResp = client.send(deleteReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(204, deleteResp.statusCode(), "Должен вернуть 204 No Content");
        assertTrue(deleteResp.body().isEmpty(), "Тело ответа должно быть пустым");

        // Проверяем, что фильм действительно удален
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies/" + createdMovie.getId()))
                .GET()
                .build();

        HttpResponse<String> getResp = client.send(getReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, getResp.statusCode(), "После удаления должен возвращать 404");
    }

    @Test
    @DisplayName("DELETE /movies/{id} возвращает 404 для несуществующего фильма")
    void deleteMovie_shouldReturn404_whenIdNotExists() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies/999"))
                .DELETE()
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode(), "Должен вернуть 404 Not Found");
    }

    @Test
    @DisplayName("GET /movies?year=YYYY фильтрует по году выпуска")
    void getMovies_shouldFilterByYear_whenYearParameterProvided() throws Exception {
        // Создаем несколько фильмов разных годов
        String[] movies = {
                createMovieJson("Фильм 2000", "Описание 2000", "2000-01-01", 120),
                createMovieJson("Фильм 2005", "Описание 2005", "2005-06-15", 130),
                createMovieJson("Еще 2000", "Еще описание", "2000-12-31", 140),
                createMovieJson("Фильм 2010", "Описание 2010", "2010-03-20", 150)
        };

        for (int i = 0; i < movies.length; i++) {
            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/movies"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(movies[i]))
                    .build();

            HttpResponse<String> postResp = client.send(postReq,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(201, postResp.statusCode(), "Фильм " + i + " должен создаться");
        }

        // Фильтруем по 2000 году
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies?year=2000"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "Должен вернуть 200 OK");

        Movie[] filteredMovies = gson.fromJson(resp.body(), Movie[].class);
        assertEquals(2, filteredMovies.length, "Должно быть 2 фильма 2000 года");

        for (Movie movie : filteredMovies) {
            assertEquals(2000, movie.getReleaseDate().getYear(),
                    "Все фильмы должны быть 2000 года");
        }
    }

    @Test
    @DisplayName("GET /movies?year= с неверным параметром возвращает 400")
    void getMovies_shouldReturn400_whenInvalidYearParameter() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies?year=abc"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode(), "Должен вернуть 400 Bad Request");
    }

    @Test
    @DisplayName("Неподдерживаемый метод возвращает 405")
    void request_shouldReturn405_whenUnsupportedMethod() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(405, resp.statusCode(), "Должен вернуть 405 Method Not Allowed");

        String allowHeader = resp.headers().firstValue("Allow").orElse("");
        assertTrue(allowHeader.contains("GET") && allowHeader.contains("POST") &&
                        allowHeader.contains("DELETE"),
                "Заголовок Allow должен содержать GET, POST, DELETE");
    }

    @Test
    @DisplayName("POST /movies с неправильным Content-Type возвращает 415")
    void postMovie_shouldReturn415_whenWrongContentType() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("просто текст"))
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, resp.statusCode(), "Должен вернуть 415 Unsupported Media Type");
    }

    @Test
    @DisplayName("POST /movies с невалидным JSON возвращает 400")
    void postMovie_shouldReturn400_whenInvalidJson() throws Exception {
        String invalidJson = "{\"name\": \"Фильм\", \"releaseDate\": \"2023-01-01\", \"duration\": 120";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(invalidJson))
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode(), "Должен вернуть 400 Bad Request");
        assertTrue(resp.body().contains("Неверный формат JSON"),
                "Должен содержать сообщение 'Неверный формат JSON'");
    }

    @Test
    @DisplayName("POST /movies с невалидными данными возвращает 422")
    void postMovie_shouldReturn422_whenInvalidData() throws Exception {
        String invalidJson = "{\"name\":\"\", \"releaseDate\":\"2023-01-01\", \"duration\":-10}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(invalidJson))
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode(), "Должен вернуть 422 Unprocessable Entity");

        assertTrue(resp.body().contains("\"details\""),
                "Ответ должен содержать детали ошибок");
        assertTrue(resp.body().contains("Название обязательно") ||
                        resp.body().contains("Продолжительность должна быть"),
                "Должен содержать конкретные ошибки валидации на русском");
    }

    @Test
    @DisplayName("Множественные добавления (проверка автоинкремента)")
    void postMovie_shouldAutoIncrementId_whenMultipleMoviesAdded() throws Exception {
        Set<Integer> ids = new HashSet<>();

        for (int i = 1; i <= 5; i++) {
            String movieJson = createMovieJson("Фильм " + i,
                    "Описание " + i, "200" + i + "-01-01", 100 + i);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/movies"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                    .build();

            HttpResponse<String> resp = client.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            assertEquals(201, resp.statusCode(), "Фильм " + i + " должен создаться");

            Movie movie = gson.fromJson(resp.body(), Movie.class);
            ids.add(movie.getId());
        }

        // Проверяем, что все ID уникальны
        assertEquals(5, ids.size(), "Должно быть 5 уникальных ID");

        // Проверяем общее количество фильмов
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .GET()
                .build();

        HttpResponse<String> getResp = client.send(getReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        Movie[] allMovies = gson.fromJson(getResp.body(), Movie[].class);
        assertEquals(5, allMovies.length, "Должно быть 5 фильмов в хранилище");
    }

    @Test
    @DisplayName("POST /movies с указанием ID возвращает ошибку")
    void postMovie_shouldReturnError_whenIdSpecified() throws Exception {
        // Попытка создать фильм с указанием ID (что запрещено)
        String movieJsonWithId = "{\"id\":100, \"name\":\"Фильм с ID\", " +
                "\"description\":\"Тест\", \"releaseDate\":\"2023-01-01\", \"duration\":120}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJsonWithId))
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Проверяем, что сервер возвращает ошибку валидации при указании ID
        assertEquals(422, resp.statusCode(),
                "Должен вернуть 422 Unprocessable Entity при указании ID");
        assertTrue(resp.body().contains("ID фильма не должен быть указан"),
                "Должен содержать сообщение о том, что ID не должен быть указан");
        assertTrue(resp.body().contains("\"details\""),
                "Ответ должен содержать детали ошибок");
    }

    @Test
    @DisplayName("Параллельные запросы на добавление")
    void postMovie_shouldHandleConcurrentRequests_whenMultipleThreads() throws Exception {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        Set<Integer> ids = new HashSet<>();
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                // Создаем отдельный HttpClient для каждого потока
                try (HttpClient threadClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build()) {

                    String movieJson = createMovieJson("Параллельный " + index,
                            "Поток " + index, "2023-01-01", 100 + index);

                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/movies"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                            .build();

                    HttpResponse<String> resp = threadClient.send(req,
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                    if (resp.statusCode() == 201) {
                        Movie movie = gson.fromJson(resp.body(), Movie.class);
                        synchronized (ids) {
                            ids.add(movie.getId());
                        }
                    } else {
                        exceptions.add(new RuntimeException("Неожиданный статус: " + resp.statusCode()));
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                }
            });
        }

        executor.shutdown();
        boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
        assertTrue(terminated, "Все потоки должны завершиться за 10 секунд");

        // Проверяем, что не было исключений
        assertTrue(exceptions.isEmpty(),
                "Не должно быть исключений в параллельных запросах: " +
                        exceptions.stream()
                                .map(e -> e.getClass().getSimpleName() + ": " + e.getMessage())
                                .collect(Collectors.joining(", ")));

        // Проверяем результаты
        assertEquals(threadCount, ids.size(),
                "Все " + threadCount + " параллельных запросов должны создать уникальные ID. Создано: " + ids);

        // Проверяем общее количество фильмов
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .GET()
                .build();

        HttpResponse<String> getResp = client.send(getReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        Movie[] allMovies = gson.fromJson(getResp.body(), Movie[].class);
        assertEquals(threadCount, allMovies.length,
                "Должно быть " + threadCount + " фильмов. Найдено: " + allMovies.length);
    }
    @Test
    @DisplayName("POST /movies с очень длинным названием")
    void postMovie_shouldHandleLongName_whenNameIs100Characters() throws Exception {
        String longName = "A".repeat(100);
        String movieJson = createMovieJson(longName,
                "Фильм с очень длинным названием", "2023-01-01", 120);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (resp.statusCode() == 201) {
            Movie movie = gson.fromJson(resp.body(), Movie.class);
            assertEquals(100, movie.getName().length(), "Название должно быть 100 символов");
        } else if (resp.statusCode() == 422) {
            // Если сервер ограничивает длину названия
            assertTrue(resp.body().contains("Название должно быть не более"),
                    "Должен содержать сообщение об ограничении длины");
        } else {
            fail("Неожиданный статус: " + resp.statusCode());
        }
    }

    @Test
    @DisplayName("POST /movies с пустым описанием")
    void postMovie_shouldAcceptEmptyDescription_whenDescriptionIsEmpty() throws Exception {
        String movieJson = createMovieJson("Фильм без описания",
                "", "2023-01-01", 120);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(201, resp.statusCode(), "Пустое описание должно быть допустимо");

        Movie movie = gson.fromJson(resp.body(), Movie.class);
        assertEquals("", movie.getDescription(), "Описание должно быть пустым");
    }

    @Test
    @DisplayName("GET /movies?year= с годом в далеком будущем возвращает пустой массив")
    void getMovies_shouldReturnEmptyArray_whenYearInDistantFuture() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies?year=2100"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "Должен вернуть 200 OK");
        assertEquals("[]", resp.body().trim(), "Должен вернуть пустой массив");
    }

    @Test
    @DisplayName("GET /movies?year= с отрицательным годом возвращает ошибку")
    void getMovies_shouldReturn400_whenNegativeYear() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies?year=-100"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode(), "Должен вернуть 400 Bad Request при отрицательном годе");
    }

    @Test
    @DisplayName("GET /movies?year= с фильмами, но не по этому году")
    void getMovies_shouldReturnEmptyArray_whenNoMoviesForYear() throws Exception {
        // Добавляем фильмы НЕ 2000 года
        String movie1 = createMovieJson("Фильм 1999", "1999 год", "1999-01-01", 100);
        String movie2 = createMovieJson("Фильм 2001", "2001 год", "2001-01-01", 110);

        client.send(HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movie1))
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        client.send(HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movie2))
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // Фильтруем по 2000 году
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies?year=2000"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "Должен вернуть 200 OK");
        Movie[] movies = gson.fromJson(resp.body(), Movie[].class);
        assertEquals(0, movies.length, "Должен вернуть пустой массив (нет фильмов 2000 года)");
    }

    // Вспомогательный метод
    private String createMovieJson(String name, String description, String releaseDate, int duration) {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("description", description != null ? description : "");
        json.addProperty("releaseDate", releaseDate);
        json.addProperty("duration", duration);
        return gson.toJson(json);
    }
}