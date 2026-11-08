package app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    // подключение к базе в оперативной памяти
    private static final String URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASS = "";

    public static void main(String[] args) {
        // try-with-resources автоматически закроет соединение после работы
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS)) {
            System.out.println("Успешное подключение к базе данных");

            // создаем схему study
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE SCHEMA IF NOT EXISTS study");
            }

            // читаем скрипт из файла music-create.sql и сохраняем его
            String sqlScript = Files.readString(Path.of("music-create.sql"));
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sqlScript);
            }

            System.out.println("\n----- Задание №1. Список композиций -----");
            String sql = "SELECT * FROM study.music";

            try (Statement stmt = conn.createStatement(); // отправляется в базу
                 ResultSet rs = stmt.executeQuery(sql)) { // читает информацию от туда

                while (rs.next()) {
                    System.out.println(rs.getInt("id") + ". " + rs.getString("name"));
                }
            }

        } catch (SQLException | IOException e) {
            System.err.println("Ошибка работы с базой данных или файлом: " + e.getMessage());
        }
    }
}
