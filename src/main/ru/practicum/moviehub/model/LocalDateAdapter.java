package ru.practicum.moviehub.model;

import com.google.gson.*;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

//Адаптер для сериализации/десериализации LocalDate в JSON

public class LocalDateAdapter implements JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
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