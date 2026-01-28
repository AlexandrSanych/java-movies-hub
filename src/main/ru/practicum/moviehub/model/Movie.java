package ru.practicum.moviehub.model;

import java.time.LocalDate;
import java.util.Objects;

//Модель фильма

public class Movie {
    private final int id;            // Уникальный идентификатор
    private final String name;       // Название фильма
    private final String description; // Описание фильма
    private final LocalDate releaseDate; // Дата выпуска
    private final int duration;      // Продолжительность в минутах

    //Конструктор фильма

    public Movie(int id, String name, String description, LocalDate releaseDate, int duration) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.releaseDate = releaseDate;
        this.duration = duration;
    }

    // Геттеры
    public int getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public LocalDate getReleaseDate() { return releaseDate; }
    public int getDuration() { return duration; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Movie movie = (Movie) o;
        return id == movie.id && duration == movie.duration &&
                Objects.equals(name, movie.name) &&
                Objects.equals(description, movie.description) &&
                Objects.equals(releaseDate, movie.releaseDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, releaseDate, duration);
    }
}