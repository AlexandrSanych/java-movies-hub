package ru.practicum.moviehub.api;

import java.util.List;

//Ответ с ошибкой для API

public class ErrorResponse {
    private final String message;     // Сообщение об ошибке
    private final int status;         // HTTP статус код
    private final List<String> details; // Детали ошибок (опционально)

    //Конструктор для простых ошибок без деталей

    public ErrorResponse(String message, int status) {
        this.message = message;
        this.status = status;
        this.details = null;
    }

    //Конструктор для ошибок с деталями

    public ErrorResponse(String message, int status, List<String> details) {
        this.message = message;
        this.status = status;
        this.details = details;
    }


    // Геттеры
    public String getMessage() {
        return message;
    }

    public int getStatus() {
        return status;
    }

    public List<String> getDetails() {
        return details;
    }
}