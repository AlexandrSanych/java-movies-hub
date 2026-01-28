package ru.practicum.moviehub.model;

import com.google.gson.annotations.SerializedName;

//DTO для создания фильма (принимается от клиента)

public class MovieCreateRequest {
    private String name;         // Название фильма
    private String description;  // Описание фильма
    private String releaseDate;  // Дата выпуска в формате YYYY-MM-DD
    private int duration;        // Продолжительность в минутах

    @SerializedName("id")
    private Integer clientId;    // ID, который может прислать клиент (но должен игнорироваться)

    // Пустой конструктор для Gson
    public MovieCreateRequest() {
    }

    // Конструктор с параметрами
    public MovieCreateRequest(String name, String description, String releaseDate, int duration) {
        this.name = name;
        this.description = description;
        this.releaseDate = releaseDate;
        this.duration = duration;
    }

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

    public Integer getClientId() {
        return clientId;
    }

    public void setClientId(Integer clientId) {
        this.clientId = clientId;
    }

    @Override
    public String toString() {
        return "MovieCreateRequest{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", releaseDate='" + releaseDate + '\'' +
                ", duration=" + duration +
                ", clientId=" + clientId +
                '}';
    }
}