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

            System.out.println("\n----- Задание №2. Композиции без букв 'm' и 't' -----");

            // LOWER() переводит в нижний регистр, NOT LIKE ищет отсутствие буквы
            String sqlFiltered = "SELECT * FROM study.music " +
                    "WHERE LOWER(name) NOT LIKE '%m%' " +
                    "AND LOWER(name) NOT LIKE '%t%'";

            try (Statement stmt = conn.createStatement(); // отправляется в базу
                 ResultSet rs = stmt.executeQuery(sqlFiltered)) { // читает информацию от туда

                while (rs.next()) {
                    System.out.println(rs.getInt("id") + ". " + rs.getString("name"));
                }
            }

            System.out.println("\n----- Задание №3. Добавление своей композиции -----");

            // пишем запрос с вопросами вместо значений, заглушки
            String sqlInsert = "INSERT INTO study.music (id, name) VALUES (?, ?)";

            try (java.sql.PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
                ps.setInt(1, 21); // ID новой песни
                ps.setString(2, "Loser");
                ps.executeUpdate(); // выполняем вставку
            }

            // gроверим, что песня добавилась
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM study.music WHERE id = 21")) {
                if (rs.next()) {
                    System.out.println("Успешно добавлена: " + rs.getInt("id") + ". " + rs.getString("name"));
                }
            }

        } catch (SQLException | IOException e) {
            System.err.println("Ошибка работы с базой данных или файлом: " + e.getMessage());
        }
    }
}
