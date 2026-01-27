package ru.practicum.moviehub.http;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.model.MovieCreateRequest;
import ru.practicum.moviehub.model.LocalDateAdapter;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Обработчик HTTP запросов для работы с фильмами
 * Реализует REST API для CRUD операций с фильмами
 */
public class MoviesHandler extends BaseHttpHandler {
    private static final Logger logger = Logger.getLogger(MoviesHandler.class.getName());
    private static final int MAX_REQUEST_SIZE = 1024 * 1024; // 1 МБ

    // Константы для валидации
    private static final int MIN_YEAR = 1888;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final int MAX_DURATION_HOURS = 24;
    private static final int MIN_DURATION = 1;

    private final MoviesStore moviesStore;
    private final Gson gson;

    public MoviesHandler(MoviesStore moviesStore) {
        this.moviesStore = moviesStore;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                .create();
    }

    /**
     * Основной метод обработки HTTP запросов
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            logger.info(() -> String.format("Обработка запроса: %s %s", method, path));

            // Маршрутизация запросов
            switch (method) {
                case "GET":
                    handleGetRequest(exchange, path);
                    break;
                case "POST":
                    if (path.equals("/movies")) {
                        handlePostMovie(exchange);
                    } else {
                        sendMethodNotAllowed(exchange, new String[]{"GET", "POST", "DELETE"});
                    }
                    break;
                case "DELETE":
                    handleDeleteRequest(exchange, path);
                    break;
                default:
                    sendMethodNotAllowed(exchange, new String[]{"GET", "POST", "DELETE"});
                    break;
            }
        } catch (Exception e) {
            logger.severe(() -> String.format("Необработанное исключение: %s", e.getMessage()));
            ErrorResponse error = new ErrorResponse("Внутренняя ошибка сервера", 500);
            sendJson(exchange, 500, gson.toJson(error));
        }
    }

    /**
     * Обработка GET запросов
     */
    private void handleGetRequest(HttpExchange exchange, String path) throws IOException {
        String[] parts = path.split("/");

        // GET /movies
        if (parts.length == 2) {
            handleGetAllMovies(exchange);
        }
        // GET /movies/{id}
        else if (parts.length == 3) {
            try {
                int id = Integer.parseInt(parts[2]);
                handleGetMovieById(exchange, id);
            } catch (NumberFormatException e) {
                ErrorResponse error = new ErrorResponse("ID должен быть числом", 400);
                sendJson(exchange, 400, gson.toJson(error));
            }
        } else {
            ErrorResponse error = new ErrorResponse("Неверный путь запроса", 404);
            sendJson(exchange, 404, gson.toJson(error));
        }
    }

    /**
     * Обработка DELETE запросов
     */
    private void handleDeleteRequest(HttpExchange exchange, String path) throws IOException {
        String[] parts = path.split("/");

        // DELETE /movies/{id}
        if (parts.length == 3 && parts[1].equals("movies")) {
            try {
                int id = Integer.parseInt(parts[2]);
                handleDeleteMovie(exchange, id);
            } catch (NumberFormatException e) {
                ErrorResponse error = new ErrorResponse("ID должен быть числом", 400);
                sendJson(exchange, 400, gson.toJson(error));
            }
        } else {
            sendMethodNotAllowed(exchange, new String[]{"GET", "POST", "DELETE"});
        }
    }

    /**
     * GET /movies - получение всех фильмов
     * GET /movies?year=YYYY - фильтрация по году
     */
    private void handleGetAllMovies(HttpExchange exchange) throws IOException {
        try {
            // Проверяем query параметры
            String query = exchange.getRequestURI().getQuery();
            List<Movie> movies;

            if (query != null && query.startsWith("year=")) {
                // Фильтрация по году
                String yearParam = query.substring(5);
                try {
                    int year = Integer.parseInt(yearParam);

                    if (year < 0) {
                        ErrorResponse error = new ErrorResponse("Год не может быть отрицательным", 400);
                        sendJson(exchange, 400, gson.toJson(error));
                        return;
                    }

                    // Для очень больших годов возвращаем пустой список
                    if (year > 10000) {
                        movies = Collections.emptyList();
                    } else {
                        movies = moviesStore.getMoviesByYear(year);
                    }
                } catch (NumberFormatException e) {
                    ErrorResponse error = new ErrorResponse("Год должен быть числом", 400);
                    sendJson(exchange, 400, gson.toJson(error));
                    return;
                }
            } else if (query != null && !query.isEmpty()) {
                // Неизвестные query параметры
                ErrorResponse error = new ErrorResponse("Неизвестный query параметр. Используйте ?year=YYYY",
                        400);
                sendJson(exchange, 400, gson.toJson(error));
                return;
            } else {
                // Все фильмы
                movies = moviesStore.getAllMovies();
            }

            String jsonResponse = gson.toJson(movies);
            sendJson(exchange, 200, jsonResponse);

        } catch (Exception e) {
            logger.severe(() -> String.format("Ошибка при получении фильмов: %s", e.getMessage()));
            ErrorResponse error = new ErrorResponse("Ошибка при обработке запроса", 500);
            sendJson(exchange, 500, gson.toJson(error));
        }
    }

    /**
     * GET /movies/{id} - получение фильма по ID
     */
    private void handleGetMovieById(HttpExchange exchange, int id) throws IOException {
        try {
            Optional<Movie> movieOpt = moviesStore.getMovieById(id);

            if (movieOpt.isPresent()) {
                String jsonResponse = gson.toJson(movieOpt.get());
                sendJson(exchange, 200, jsonResponse);
            } else {
                ErrorResponse error = new ErrorResponse("Фильм не найден", 404);
                sendJson(exchange, 404, gson.toJson(error));
            }
        } catch (Exception e) {
            logger.severe(() -> String.format("Ошибка при получении фильма: %s", e.getMessage()));
            ErrorResponse error = new ErrorResponse("Ошибка при обработке запроса", 500);
            sendJson(exchange, 500, gson.toJson(error));
        }
    }

    /**
     * POST /movies - добавление нового фильма
     */
    /**
     * POST /movies - добавление нового фильма
     */
    private void handlePostMovie(HttpExchange exchange) throws IOException {
        // Проверяем Content-Type
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("application/json")) {
            ErrorResponse error = new ErrorResponse("Требуется Content-Type: application/json", 415);
            sendJson(exchange, 415, gson.toJson(error));
            return;
        }


        String requestBody = null;

        try {
            // Читаем тело запроса с ограничением размера
            try (InputStream is = exchange.getRequestBody();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;

                while ((bytesRead = is.read(buffer)) != -1) {
                    totalBytes += bytesRead;
                    if (totalBytes > MAX_REQUEST_SIZE) {
                        ErrorResponse error = new ErrorResponse("Размер запроса превышает 1 МБ", 413);
                        sendJson(exchange, 413, gson.toJson(error));
                        return;
                    }
                    baos.write(buffer, 0, bytesRead);
                }

                requestBody = baos.toString(StandardCharsets.UTF_8.name());
            } catch (IOException e) {
                ErrorResponse error = new ErrorResponse("Ошибка чтения тела запроса", 400);
                sendJson(exchange, 400, gson.toJson(error));
                return;
            }

            // Проверяем, что тело не пустое
            if (requestBody == null || requestBody.trim().isEmpty()) {
                ErrorResponse error = new ErrorResponse("Тело запроса не может быть пустым", 400);
                sendJson(exchange, 400, gson.toJson(error));
                return;
            }

            // Парсинг JSON в DTO за один раз
            MovieCreateRequest request = gson.fromJson(requestBody, MovieCreateRequest.class);

            if (request == null) {
                ErrorResponse error = new ErrorResponse("Тело запроса не может быть пустым", 400);
                sendJson(exchange, 400, gson.toJson(error));
                return;
            }

            // Валидация данных из DTO (включая проверку на наличие ID)
            List<String> errors = validateMovieRequest(request);
            if (!errors.isEmpty()) {
                ErrorResponse error = new ErrorResponse("Ошибка валидации", 422, errors);
                sendJson(exchange, 422, gson.toJson(error));
                return;
            }

            // Преобразование строки в LocalDate
            LocalDate releaseDate = LocalDate.parse(request.getReleaseDate());

            // Создание фильма - ВСЕГДА указываем ID=0, чтобы сервер сам его сгенерировал
            Movie movie = new Movie(0,
                    request.getName().trim(),
                    request.getDescription() != null ? request.getDescription().trim() : "",
                    releaseDate,
                    request.getDuration());

            // Добавление фильма в хранилище
            Movie createdMovie = moviesStore.addMovie(movie);

            // Успешный ответ
            String jsonResponse = gson.toJson(createdMovie);
            sendJson(exchange, 201, jsonResponse);
            logger.info(() -> String.format("Создан фильм: ID=%d, Название='%s'",
                    createdMovie.getId(), createdMovie.getName()));

        } catch (JsonSyntaxException e) {
            String requestBodyForLog = requestBody != null ?
                    requestBody.substring(0, Math.min(requestBody.length(), 200)) : "null";

            logger.warning(() -> String.format("Ошибка парсинга JSON: %s. Тело: %s",
                    e.getMessage(), requestBodyForLog));
            ErrorResponse error = new ErrorResponse("Неверный формат JSON", 400);
            sendJson(exchange, 400, gson.toJson(error));
        } catch (DateTimeParseException e) {
            logger.warning(() -> String.format("Ошибка парсинга даты: %s", e.getMessage()));
            ErrorResponse error = new ErrorResponse(
                    "Неверный формат даты. Используйте формат YYYY-MM-DD (например: 2023-12-31)",
                    400
            );
            sendJson(exchange, 400, gson.toJson(error));
        } catch (Exception e) {
            // Общая обработка непредвиденных ошибок
            logger.severe(() -> String.format("Неожиданная ошибка при создании фильма: %s", e.getMessage()));
            e.printStackTrace();
            ErrorResponse error = new ErrorResponse("Внутренняя ошибка сервера при создании фильма",
                    500);
            sendJson(exchange, 500, gson.toJson(error));
        }
    }

    /**
     * DELETE /movies/{id} - удаление фильма
     */
    private void handleDeleteMovie(HttpExchange exchange, int id) throws IOException {
        try {
            // Проверяем, существует ли фильм
            Optional<Movie> movieOpt = moviesStore.getMovieById(id);

            if (movieOpt.isPresent()) {
                boolean deleted = moviesStore.deleteMovie(id);
                if (deleted) {
                    sendNoContent(exchange);
                } else {
                    ErrorResponse error = new ErrorResponse("Ошибка при удалении фильма", 500);
                    sendJson(exchange, 500, gson.toJson(error));
                }
            } else {
                ErrorResponse error = new ErrorResponse("Фильм не найден", 404);
                sendJson(exchange, 404, gson.toJson(error));
            }
        } catch (Exception e) {
            logger.severe(() -> String.format("Ошибка при удалении фильма: %s", e.getMessage()));
            ErrorResponse error = new ErrorResponse("Ошибка при обработке запроса", 500);
            sendJson(exchange, 500, gson.toJson(error));
        }
    }

    /**
     * Валидирует данные для создания фильма
     */
    private List<String> validateMovieRequest(MovieCreateRequest request) {
        List<String> errors = new ArrayList<>();

        // Проверка: клиент не должен указывать ID
        if (request.getClientId() != null) {
            errors.add("ID фильма не должен быть указан при создании. ID генерируется автоматически сервером.");
        }

        // Валидация названия
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            errors.add("Название обязательно");
        } else {
            String trimmedName = request.getName().trim();
            if (trimmedName.length() > MAX_NAME_LENGTH) {
                errors.add(String.format("Название должно быть не более %d символов", MAX_NAME_LENGTH));
            }
            // Проверка на недопустимые символы
            if (trimmedName.contains("\0") || trimmedName.contains("\r") || trimmedName.contains("\n")) {
                errors.add("Название содержит недопустимые символы");
            }
        }

        // Валидация описания
        if (request.getDescription() != null) {
            String description = request.getDescription().trim();
            if (description.length() > MAX_DESCRIPTION_LENGTH) {
                errors.add(String.format("Описание должно быть не более %d символов", MAX_DESCRIPTION_LENGTH));
            }
        }

        // Валидация даты
        if (request.getReleaseDate() == null || request.getReleaseDate().trim().isEmpty()) {
            errors.add("Дата выпуска обязательна");
        } else {
            try {
                LocalDate releaseDate = LocalDate.parse(request.getReleaseDate().trim());
                LocalDate now = LocalDate.now();

                if (releaseDate.getYear() < MIN_YEAR) {
                    errors.add(String.format("Год выпуска должен быть не раньше %d", MIN_YEAR));
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
        if (request.getDuration() < MIN_DURATION) {
            errors.add(String.format("Продолжительность должна быть не менее %d минуты", MIN_DURATION));
        } else if (request.getDuration() > MAX_DURATION_HOURS * 60) {
            errors.add(String.format("Продолжительность не может превышать %d часов", MAX_DURATION_HOURS));
        }

        return errors;
    }

    /**
     * Отправляет ошибку 405 Method Not Allowed
     */
    private void sendMethodNotAllowed(HttpExchange exchange, String[] allowedMethods) throws IOException {
        String allowed = String.join(", ", allowedMethods);
        exchange.getResponseHeaders().set("Allow", allowed);
        ErrorResponse error = new ErrorResponse(
                String.format("Метод %s не разрешен для данного ресурса. Разрешены: %s",
                        exchange.getRequestMethod(), allowed),
                405);
        sendJson(exchange, 405, gson.toJson(error));
    }
}