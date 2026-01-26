package ru.practicum.moviehub.model;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public int getDuration() {
        return duration;
    }

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

    //Адаптер для сериализации/десериализации LocalDate в JSON

    public static class LocalDateAdapter implements JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

        @Override
        public JsonElement serialize(LocalDate date, Type typeOfSrc, JsonSerializationContext context) {
            if (date == null) {
                return JsonNull.INSTANCE;
            }
            return new JsonPrimitive(date.format(FORMATTER));
        }

        @Override
        public LocalDate deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }
            try {
                return LocalDate.parse(json.getAsString(), FORMATTER);
            } catch (DateTimeParseException e) {
                throw new JsonParseException("Неверный формат даты. Ожидается YYYY-MM-DD", e);
            }
        }
    }

    //DTO для создания фильма (принимается от клиента)

    public static class CreateRequest {
        public String name;         // Название фильма
        public String description;  // Описание фильма
        public String releaseDate;  // Дата выпуска в формате YYYY-MM-DD
        public int duration;        // Продолжительность в минутах

        // Геттеры и сеттеры
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getReleaseDate() {
            return releaseDate;
        }

        public void setReleaseDate(String releaseDate) {
            this.releaseDate = releaseDate;
        }

        public int getDuration() {
            return duration;
        }

        public void setDuration(int duration) {
            this.duration = duration;
        }
    }
}