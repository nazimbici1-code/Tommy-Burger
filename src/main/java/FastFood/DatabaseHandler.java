package FastFood;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHandler {

    // ================== CONFIGURATION RAILWAY ==================
    private static final String URL =
            "jdbc:mysql://shuttle.proxy.rlwy.net:16929/railway?" +
            "useSSL=false&" +
            "allowPublicKeyRetrieval=true&" +
            "serverTimezone=UTC&" +
            "connectTimeout=10000&" +
            "socketTimeout=10000";

    private static final String USER = "root";
    private static final String PASSWORD = "clzDOSLPsQXUqtPSRYbmDUhSoPrjSqHn";

    public static final String KEY_NB_TABLES = "nbTables";
    public static final String KEY_PRINTER_CLIENT = "printer_name_client";
    public static final String KEY_PRINTER_CUISINE = "printer_name_cuisine";
    public static final String KEY_NOM_RESTO = "nom_resto";

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static void setupDatabase() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {

            // USERS
            stmt.execute("CREATE TABLE IF NOT EXISTS users (id INT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(255) UNIQUE NOT NULL, password VARCHAR(255) NOT NULL, role VARCHAR(100) NOT NULL)");

            // CATEGORIES
            stmt.execute("CREATE TABLE IF NOT EXISTS categories (id INT AUTO_INCREMENT PRIMARY KEY, nom VARCHAR(255) UNIQUE NOT NULL, image_path TEXT, ordre INT DEFAULT 0)");

            // PRODUITS
            stmt.execute("CREATE TABLE IF NOT EXISTS produits (id INT AUTO_INCREMENT PRIMARY KEY, nom VARCHAR(255) NOT NULL, prix DOUBLE NOT NULL, image_path TEXT, categorie_id INT NOT NULL, ordre INT DEFAULT 0, CONSTRAINT fk_produits_categories FOREIGN KEY (categorie_id) REFERENCES categories(id) ON DELETE CASCADE)");

            // COMMANDES
            stmt.execute("CREATE TABLE IF NOT EXISTS commandes (id INT AUTO_INCREMENT PRIMARY KEY, table_num VARCHAR(100), total DOUBLE NOT NULL DEFAULT 0, details LONGTEXT, date TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // SETTINGS & LOGS
            stmt.execute("CREATE TABLE IF NOT EXISTS settings (cle VARCHAR(100) PRIMARY KEY, valeur TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS log_activity (id INT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(255), action VARCHAR(255) NOT NULL, details TEXT, date_action TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // Vérification colonnes
            ensureColumnExists(conn, "categories", "ordre", "INT DEFAULT 0");
            ensureColumnExists(conn, "produits", "ordre", "INT DEFAULT 0");
            ensureColumnExists(conn, "commandes", "details", "LONGTEXT");

            // Admin par défaut
            try (PreparedStatement p = conn.prepareStatement("SELECT COUNT(*) FROM users");
                 ResultSet rs = p.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    addUser("admin", "1234", "Admin");
                }
            }

            // Settings par défaut
            String[][] defaultSettings = {
                    {KEY_NB_TABLES, "10"},
                    {KEY_PRINTER_CLIENT, "CLIENT_PRINTER"},
                    {KEY_PRINTER_CUISINE, "CUISINE_PRINTER"},
                    {KEY_NOM_RESTO, "TOMMY BURGER"}
            };

            try (PreparedStatement p = conn.prepareStatement("INSERT INTO settings (cle, valeur) VALUES (?, ?) ON DUPLICATE KEY UPDATE valeur = valeur")) {
                for (String[] s : defaultSettings) {
                    p.setString(1, s[0]);
                    p.setString(2, s[1]);
                    p.addBatch();
                }
                p.executeBatch();
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur setupDatabase()", e);
        }
    }

    // ================== LOGIQUE DES PRODUITS (CORRIGÉE) ==================

    public static boolean addProduit(String nom, double prix, String categorieNom) {
        String sqlCat = "SELECT id FROM categories WHERE nom = ?";
        String sqlIns = "INSERT INTO produits (nom, prix, categorie_id, ordre) VALUES (?, ?, ?, 0)";
        try (Connection conn = connect()) {
            int catId = -1;
            try (PreparedStatement pCat = conn.prepareStatement(sqlCat)) {
                pCat.setString(1, categorieNom);
                try (ResultSet rs = pCat.executeQuery()) {
                    if (rs.next()) catId = rs.getInt("id");
                }
            }
            if (catId == -1) return false;

            try (PreparedStatement p = conn.prepareStatement(sqlIns)) {
                p.setString(1, nom);
                p.setDouble(2, prix);
                p.setInt(3, catId);
                return p.executeUpdate() > 0;
            }
        } catch (SQLException e) { return false; }
    }

    public static boolean deleteProduit(int id) {
        String sql = "DELETE FROM produits WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, id);
            return p.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    // ================== LOGIQUE DES COMMANDES & STATS ==================

    public static List<MenuItem> getMenu() {
        List<MenuItem> menu = new ArrayList<>();
        String sql = "SELECT p.id, p.nom, p.prix, p.image_path, c.nom AS categorie FROM produits p JOIN categories c ON p.categorie_id = c.id ORDER BY c.ordre, p.ordre";
        try (Connection conn = connect(); Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                menu.add(new MenuItem(rs.getInt("id"), rs.getString("nom"), rs.getDouble("prix"), rs.getString("categorie"), rs.getString("image_path")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return menu;
    }

    public static double getStatsPeriode(String periode) {
        String sql = switch (periode) {
            case "Jour" -> "SELECT SUM(total) FROM commandes WHERE DATE(date) = CURDATE()";
            case "Semaine" -> "SELECT SUM(total) FROM commandes WHERE date >= NOW() - INTERVAL 7 DAY";
            case "SemainePrecedente" -> "SELECT SUM(total) FROM commandes WHERE date >= NOW() - INTERVAL 14 DAY AND date < NOW() - INTERVAL 7 DAY";
            default -> "SELECT SUM(total) FROM commandes";
        };
        try (Connection conn = connect(); Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0.0;
    }

    public static int getNombreCommandesPeriode(String periode) {
        String sql = "SELECT COUNT(*) FROM commandes WHERE date >= NOW() - INTERVAL 7 DAY";
        try (Connection conn = connect(); Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    // ================== GESTION UTILISATEURS ==================

    public static boolean checkLogin(String user, String pass, String role) {
        String sql = "SELECT 1 FROM users WHERE LOWER(username)=LOWER(?) AND password=? AND LOWER(role)=LOWER(?)";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, user);
            p.setString(2, hashPassword(pass));
            p.setString(3, role);
            return p.executeQuery().next();
        } catch (SQLException e) { return false; }
    }

    public static void addUser(String username, String password, String role) {
        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, username);
            p.setString(2, hashPassword(password));
            p.setString(3, role);
            p.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // ================== MÉTHODES UTILITAIRES CONSERVÉES ==================

    private static void ensureColumnExists(Connection conn, String tableName, String colName, String def) throws SQLException {
        ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, colName);
        if (!rs.next()) {
            try (Statement s = conn.createStatement()) {
                s.execute("ALTER TABLE " + tableName + " ADD COLUMN " + colName + " " + def);
            }
        }
    }

    public static void logAction(String user, String action, String details) {
        String sql = "INSERT INTO log_activity (username, action, details) VALUES (?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, user);
            p.setString(2, action);
            p.setString(3, details);
            p.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static List<LogEntry> getLogs() {
        List<LogEntry> logs = new ArrayList<>();
        try (Connection conn = connect(); Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM log_activity ORDER BY id DESC LIMIT 50")) {
            while (rs.next()) {
                logs.add(new LogEntry(rs.getInt("id"), rs.getString("username"), rs.getString("action"), rs.getString("details"), rs.getString("date_action")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return logs;
    }

    private static String hashPassword(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return s; }
    }

    // DÉFINITION DES CLASSES DE DONNÉES (POUR ÉVITER LES ERREURS DE COMPILATION)
    public record MenuItem(int id, String nom, double prix, String categorie, String imagePath) {}
    public record LogEntry(int id, String username, String action, String details, String dateAction) {}
}
