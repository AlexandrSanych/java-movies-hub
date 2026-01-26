package ru.practicum.moviehub.http;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

//Обработчик HTTP запросов для работы с фильмами

public class MoviesHandler extends BaseHttpHandler {
    private static final Logger logger = Logger.getLogger(MoviesHandler.class.getName());
    private static final int MAX_REQUEST_SIZE = 1024 * 1024; // 1 МБ

    private final MoviesStore moviesStore;
    private final Gson gson;

    public MoviesHandler(MoviesStore moviesStore) {
        this.moviesStore = moviesStore;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDate.class, new Movie.LocalDateAdapter())
                .create();
    }

    //Основной метод обработки HTTP запросов

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            // Логирование входящих запросов
            logger.info(() -> String.format("Обработка запроса: %s %s", method, path));

            // Валидация пути
            if (!isValidPath(path)) {
                ErrorResponse error = new ErrorResponse("Неверный путь", 400);
                sendJson(exchange, 400, error.toJson());
                return;
            }

            String query = exchange.getRequestURI().getRawQuery();

            // Обработка GET /movies или GET /movies?year=YYYY
            if ("GET".equals(method) && path.matches("/movies(/.*)?")) {
                if (path.equals("/movies")) {
                    if (query != null && query.contains("year=")) {
                        handleGetMoviesByYear(exchange, query);
                    } else {
                        handleGetAllMovies(exchange);
                    }
                } else {
                    handleGetMovieById(exchange, path);
                }
            }
            // Обработка POST /movies
            else if ("POST".equals(method) && path.equals("/movies")) {
                handlePostMovie(exchange);
            }
            // Обработка DELETE /movies/{id}
            else if ("DELETE".equals(method) && path.matches("/movies/\\d+")) {
                handleDeleteMovie(exchange, path);
            }
            // Неподдерживаемый метод или путь
            else {
                ErrorResponse error = new ErrorResponse("Метод не поддерживается", 405);
                exchange.getResponseHeaders().set("Allow", "GET, POST, DELETE");
                sendJson(exchange, 405, error.toJson());
            }
        } catch (Exception e) {
            logger.severe(() -> String.format("Непредвиденная ошибка для %s %s: %s",
                    method, path, e.getMessage()));
            ErrorResponse error = new ErrorResponse("Внутренняя ошибка сервера", 500);
            sendJson(exchange, 500, error.toJson());
        }
    }

    // ========== Основные методы обработки запросов ==========

    //GET /movies - получение всех фильмов

    private void handleGetAllMovies(HttpExchange exchange) throws IOException {
        List<Movie> movies = moviesStore.getAllMovies();
        String jsonResponse = gson.toJson(movies);
        sendJson(exchange, 200, jsonResponse);
    }

    //GET /movies?year=YYYY - фильтрация по году

    private void handleGetMoviesByYear(HttpExchange exchange, String query) throws IOException {
        try {
            Map<String, String> params = parseQuery(query);
            String yearStr = params.get("year");

            if (yearStr == null || yearStr.isEmpty()) {
                ErrorResponse error = new ErrorResponse("Отсутствует параметр 'year'", 400);
                sendJson(exchange, 400, error.toJson());
                return;
            }

            int year = Integer.parseInt(yearStr);

            // Валидация года - проверка минимального значения
            if (year < 1888) {
                ErrorResponse error = new ErrorResponse("Год должен быть не раньше 1888", 400);
                sendJson(exchange, 400, error.toJson());
                return;
            }

            List<Movie> filteredMovies = moviesStore.getMoviesByYear(year);
            String jsonResponse = gson.toJson(filteredMovies);
            sendJson(exchange, 200, jsonResponse);

        } catch (NumberFormatException e) {
            ErrorResponse error = new ErrorResponse("Неверный параметр года. Должен быть числом.", 400);
            sendJson(exchange, 400, error.toJson());
        }
    }

    //GET /movies/{id} - получение фильма по ID
    private void handleGetMovieById(HttpExchange exchange, String path) throws IOException {
        try {
            int id = extractIdFromPath(path);
            Optional<Movie> movie = moviesStore.getMovieById(id);

            if (movie.isPresent()) {
                String jsonResponse = gson.toJson(movie.get());
                sendJson(exchange, 200, jsonResponse);
            } else {
                ErrorResponse error = new ErrorResponse("Фильм не найден", 404);
                sendJson(exchange, 404, error.toJson());
            }
        } catch (NumberFormatException e) {
            ErrorResponse error = new ErrorResponse("Неверный ID фильма: " + e.getMessage(), 400);
            sendJson(exchange, 400, error.toJson());
        }
    }

    //POST /movies - добавление нового фильма
    private void handlePostMovie(HttpExchange exchange) throws IOException {
        // Проверка Content-Type
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("application/json")) {
            ErrorResponse error = new ErrorResponse(
                    "Неподдерживаемый тип данных. Используйте application/json", 415);
            exchange.getResponseHeaders().set("Accept", "application/json");
            sendJson(exchange, 415, error.toJson());
            return;
        }

        // Проверка размера контента
        String contentLengthStr = exchange.getRequestHeaders().getFirst("Content-Length");
        if (contentLengthStr != null) {
            try {
                long contentLength = Long.parseLong(contentLengthStr);
                if (contentLength > MAX_REQUEST_SIZE) {
                    ErrorResponse error = new ErrorResponse(
                            "Слишком большой запрос", 413);
                    sendJson(exchange, 413, error.toJson());
                    return;
                }
            } catch (NumberFormatException e) {
                // Игнорируем, если Content-Length некорректен
            }
        }

        // Чтение тела запроса
        String requestBody = readRequestBody(exchange);
        if (requestBody.length() > MAX_REQUEST_SIZE) {
            ErrorResponse error = new ErrorResponse("Слишком большой запрос", 413);
            sendJson(exchange, 413, error.toJson());
            return;
        }

        logger.fine(() -> String.format("Тело запроса: %s", requestBody));

        try {
            // Предварительная проверка JSON
            if (!isValidJson(requestBody)) {
                ErrorResponse error = new ErrorResponse("Неверный формат JSON", 400);
                sendJson(exchange, 400, error.toJson());
                return;
            }

            // Парсинг JSON в DTO
            Movie.CreateRequest request = gson.fromJson(requestBody, Movie.CreateRequest.class);

            if (request == null) {
                ErrorResponse error = new ErrorResponse("Тело запроса не может быть пустым", 400);
                sendJson(exchange, 400, error.toJson());
                return;
            }

            // Валидация
            List<String> errors = validateMovieRequest(request);
            if (!errors.isEmpty()) {
                ErrorResponse error = new ErrorResponse("Ошибка валидации", 422, errors);
                sendJson(exchange, 422, error.toJson());
                return;
            }

            // Преобразование строки в LocalDate
            LocalDate releaseDate = LocalDate.parse(request.getReleaseDate());

            // Создание фильма
            Movie movie = new Movie(0,
                    request.getName().trim(),
                    request.getDescription() != null ? request.getDescription().trim() : "",
                    releaseDate,
                    request.getDuration());

            Movie createdMovie = moviesStore.addMovie(movie);
            String jsonResponse = gson.toJson(createdMovie);
            sendJson(exchange, 201, jsonResponse);

        } catch (JsonSyntaxException e) {
            logger.warning(() -> String.format("Ошибка парсинга JSON: %s", e.getMessage()));
            ErrorResponse error = new ErrorResponse("Неверный формат JSON", 400);
            sendJson(exchange, 400, error.toJson());
        }
    }

    //DELETE /movies/{id} - удаление фильма

    private void handleDeleteMovie(HttpExchange exchange, String path) throws IOException {
        try {
            int id = extractIdFromPath(path);
            boolean deleted = moviesStore.deleteMovie(id);

            if (deleted) {
                sendNoContent(exchange);
            } else {
                ErrorResponse error = new ErrorResponse("Фильм не найден", 404);
                sendJson(exchange, 404, error.toJson());
            }
        } catch (NumberFormatException e) {
            ErrorResponse error = new ErrorResponse("Неверный ID фильма: " + e.getMessage(), 400);
            sendJson(exchange, 400, error.toJson());
        }
    }

    // ========== Вспомогательные методы ==========

    //Проверяет валидность пути
    private boolean isValidPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        // Проверяем на наличие попыток directory traversal
        if (path.contains("..") || path.contains("//")) {
            return false;
        }

        return true;
    }

    //Проверяет валидность JSON строки
    private boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }

        try {
            JsonParser parser = new JsonParser();
            parser.parse(json);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    //Извлечение ID фильма из пути

    private int extractIdFromPath(String path) {
        String[] parts = path.split("/");
        String idStr = parts[parts.length - 1];

        try {
            long id = Long.parseLong(idStr);

            if (id <= 0) {
                throw new NumberFormatException("ID должен быть положительным");
            }
            if (id > Integer.MAX_VALUE) {
                throw new NumberFormatException("ID слишком большой");
            }

            return (int) id;
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Неверный ID фильма: " + idStr);
        }
    }

    //Чтение тела запроса

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = is.read(buffer)) != -1) {
                totalBytes += bytesRead;
                if (totalBytes > MAX_REQUEST_SIZE) {
                    throw new IOException("Слишком большой запрос");
                }
                baos.write(buffer, 0, bytesRead);
            }

            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    //Парсит query строку в Map параметров
    private Map<String, String> parseQuery(String query) {
        if (query == null || query.isEmpty()) {
            return Map.of();
        }

        return Arrays.stream(query.split("&"))
                .filter(param -> !param.isEmpty())
                .map(param -> param.split("=", 2))
                .collect(Collectors.toMap(
                        arr -> decode(arr[0]),
                        arr -> arr.length > 1 ? decode(arr[1]) : "",
                        (first, second) -> first // Обрабатываем дубликаты
                ));
    }

    //Декодирует URL-encoded строку
    private String decode(String encoded) {
        try {
            return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            logger.warning(() -> String.format("Ошибка декодирования параметра: %s", encoded));
            return encoded;
        }
    }

    //Валидация данных для создания фильма

    private List<String> validateMovieRequest(Movie.CreateRequest request) {
        List<String> errors = new ArrayList<>();

        // Валидация названия
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            errors.add("Название обязательно");
        } else {
            String trimmedName = request.getName().trim();
            if (trimmedName.length() > 100) {
                errors.add("Название должно быть не более 100 символов");
            }
            // Проверка на недопустимые символы
            if (trimmedName.contains("\0") || trimmedName.contains("\r") || trimmedName.contains("\n")) {
                errors.add("Название содержит недопустимые символы");
            }
        }

        // Валидация описания
        if (request.getDescription() != null) {
            String description = request.getDescription().trim();
            if (description.length() > 2000) {
                errors.add("Описание должно быть не более 2000 символов");
            }
        }

        // Валидация даты
        if (request.getReleaseDate() == null || request.getReleaseDate().trim().isEmpty()) {
            errors.add("Дата выпуска обязательна");
        } else {
            try {
                LocalDate releaseDate = LocalDate.parse(request.getReleaseDate().trim());
                LocalDate now = LocalDate.now();

                if (releaseDate.getYear() < 1888) {
                    errors.add("Год выпуска должен быть не раньше 1888");
                }
                if (releaseDate.getYear() > now.getYear() + 1) {
                    errors.add("Год выпуска не может быть в далеком будущем");
                }
                if (releaseDate.isAfter(now.plusYears(1))) {
                    errors.add("Дата выпуска не может быть больше чем на 1 год в будущем");
                }
            } catch (DateTimeParseException e) {
                errors.add("Неверный формат даты. Используйте YYYY-MM-DD");
            }
        }

        // Валидация продолжительности
        if (request.getDuration() <= 0) {
            errors.add("Продолжительность должна быть положительной");
        } else if (request.getDuration() > 24 * 60) { // 24 часа в минутах
            errors.add("Продолжительность не может превышать 24 часа");
        }

        return errors;
    }
}