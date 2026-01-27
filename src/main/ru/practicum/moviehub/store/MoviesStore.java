package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Хранилище фильмов
 */
public class MoviesStore {
    // Потокобезопасная мапа для хранения фильмов по ID
    private final Map<Integer, Movie> movies = new ConcurrentHashMap<>();

    // Атомарный счетчик для генерации уникальных ID
    private final AtomicInteger idCounter = new AtomicInteger(1);

    /**
     * Добавляет фильм в хранилище
     */
    /**
     * Добавляет фильм в хранилище
     */
    public Movie addMovie(Movie movie) {
        // Теперь проверка на ID=0 делается в валидации, поэтому здесь просто добавляем
        int id = idCounter.getAndIncrement();
        Movie newMovie = new Movie(id,
                movie.getName(),
                movie.getDescription(),
                movie.getReleaseDate(),
                movie.getDuration());
        movies.put(id, newMovie);
        return newMovie;
    }

    /**
     * Возвращает все фильмы
     */
    public List<Movie> getAllMovies() {
        return new ArrayList<>(movies.values());
    }

    /**
     * Ищет фильм по ID
     */
    public Optional<Movie> getMovieById(int id) {
        return Optional.ofNullable(movies.get(id));
    }

    /**
     * Удаляет фильм по ID
     */
    public boolean deleteMovie(int id) {
        return movies.remove(id) != null;
    }

    /**
     * Возвращает фильмы по году выпуска
     */
    public List<Movie> getMoviesByYear(int year) {
        return movies.values().stream()
                .filter(movie -> movie.getReleaseDate().getYear() == year)
                .toList();
    }

    /**
     * Очищает хранилище (для тестирования)
     */
    public void clear() {
        movies.clear();
        idCounter.set(1);
    }
}