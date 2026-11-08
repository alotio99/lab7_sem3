package app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.List;

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

            // проверим, что песня добавилась
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM study.music WHERE id = 21")) {
                if (rs.next()) {
                    System.out.println("Успешно добавлена: " + rs.getInt("id") + ". " + rs.getString("name"));
                }
            }

            System.out.println("\n----- Задание №4. Загрузка данных из JSON -----");

            // создаем таблицы (связь через isbn для книг и телефон для посетителей)
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS study.books (" +
                        "isbn VARCHAR(20) PRIMARY KEY, " +
                        "name VARCHAR(255), " +
                        "author VARCHAR(255), " +
                        "publishingYear INT)");

                stmt.execute("CREATE TABLE IF NOT EXISTS study.visitors (" +
                        "phone VARCHAR(20), " +
                        "name VARCHAR(100), " +
                        "surname VARCHAR(100), " +
                        "subscribed BOOLEAN, " +
                        "PRIMARY KEY (phone, name, surname))");

                // таблица связи, кто какие книги любит
                stmt.execute("CREATE TABLE IF NOT EXISTS study.visitor_books (" +
                        "phone VARCHAR(20), " +
                        "name VARCHAR(100), " + // добавили имя
                        "surname VARCHAR(100), " + // добавили фамилию
                        "isbn VARCHAR(20), " +
                        "PRIMARY KEY (phone, name, surname, isbn), " +
                        "FOREIGN KEY (isbn) REFERENCES study.books(isbn))");
            }

            // читаем JSON с помощью Gson
            Gson gson = new Gson();
            List<Visitor> visitors;
            try (FileReader reader = new FileReader("books.json")) {
                Type listType = new TypeToken<List<Visitor>>() {}.getType();
                visitors = gson.fromJson(reader, listType);
            }

            // MERGE INTO: если ключ (isbn/phone) уже есть, H2 просто пропустит вставку
            String insertBook = "MERGE INTO study.books (isbn, name, author, publishingYear) " +
                    "KEY(isbn) VALUES (?, ?, ?, ?)";
            String insertVisitor = "MERGE INTO study.visitors (phone, name, surname, subscribed) " +
                    "KEY(phone, name, surname) VALUES (?, ?, ?, ?)";
            String insertLink = "INSERT INTO study.visitor_books (phone, name, surname, isbn) VALUES (?, ?, ?, ?)";

            try (java.sql.PreparedStatement psBook = conn.prepareStatement(insertBook);
                 java.sql.PreparedStatement psVisitor = conn.prepareStatement(insertVisitor);
                 java.sql.PreparedStatement psLink = conn.prepareStatement(insertLink)) {

                for (Visitor v : visitors) {
                    // добавляем посетителя
                    psVisitor.setString(1, v.phone);
                    psVisitor.setString(2, v.name);
                    psVisitor.setString(3, v.surname);
                    psVisitor.setBoolean(4, v.subscribed);
                    psVisitor.executeUpdate();

                    // добавляем его книги
                    for (Book b : v.favoriteBooks) {
                        psBook.setString(1, b.isbn);
                        psBook.setString(2, b.name);
                        psBook.setString(3, b.author);
                        psBook.setInt(4, b.publishingYear);
                        psBook.executeUpdate();

                        // связываем посетителя и книгу
                        psLink.setString(1, v.phone);
                        psLink.setString(2, v.name);
                        psLink.setString(3, v.surname);
                        psLink.setString(4, b.isbn);
                        try {
                            psLink.executeUpdate();
                        } catch (SQLException e) {
                            // если связь уже есть (повтор), игнорируем ошибку
                        }
                    }
                }
            }

            // проверяем, сколько уникальных книг и посетителей
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as cnt FROM study.books")) {
                if (rs.next()) System.out.println("Уникальных книг в базе данных: " + rs.getInt("cnt"));
            }
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as cnt FROM study.visitors")) {
                if (rs.next()) System.out.println("Уникальных посетителей в базе данных: " + rs.getInt("cnt"));
            }

            System.out.println("\n----- Задание №5. Сортировка книг по году издания -----");

            // запрос с ORDER BY (по умолчанию сортирует по возрастанию)
            String sqlSortedBooks = "SELECT name, author, publishingYear FROM study.books ORDER BY publishingYear";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sqlSortedBooks)) {

                while (rs.next()) {
                    System.out.println(rs.getInt("publishingYear") + " - " +
                            rs.getString("name") + " (" + rs.getString("author") + ")");
                }
            }

        } catch (SQLException | IOException e) {
            System.err.println("Ошибка работы с базой данных или файлом: " + e.getMessage());
        }
    }
}
