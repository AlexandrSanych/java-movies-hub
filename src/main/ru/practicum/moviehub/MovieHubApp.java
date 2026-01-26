package ru.practicum.moviehub;

import ru.practicum.moviehub.http.MoviesServer;

import java.util.Scanner;

public class MovieHubApp {
    public static void main(String[] args) {
        try {
            MoviesServer server = new MoviesServer();
            server.start();

            System.out.println(" MovieHub сервер запущен");
            System.out.println(" http://localhost:8080/movies");
            System.out.println("\nДля остановки введите 'стоп':");

            Scanner scanner = new Scanner(System.in);
            while (true) {
                String input = scanner.nextLine();
                if ("стоп".equalsIgnoreCase(input.trim())) {
                    System.out.println("Останавливаю сервер...");
                    server.stop();
                    break;
                }
            }
            scanner.close();

        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }
}