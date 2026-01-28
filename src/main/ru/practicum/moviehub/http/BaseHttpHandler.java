package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

//Базовый обработчик HTTP запросов

public abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";

    /**
     * Отправляет JSON ответ
     *
     * @param exchange HTTP обмен
     * @param status   HTTP статус код
     * @param json     JSON строка для отправки
     */
    protected void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE_JSON);
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, response.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        } finally {
            exchange.close();
        }
    }

    //Отправляет ответ без содержимого (204 No Content)

    protected void sendNoContent(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }
}