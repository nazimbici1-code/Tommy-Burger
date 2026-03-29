package FastFood;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHandler {

    // ================== CONFIGURATION RAILWAY ==================
    // Note : Il est recommandé d'utiliser des variables d'environnement en production
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

    /**
     * Initialise la structure de la base de données au démarrage.
     */
    public static void setupDatabase() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {

            // Table UTILISATEURS
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(255) UNIQUE NOT NULL,
                    password VARCHAR(255) NOT NULL,
                    role VARCHAR(100) NOT NULL
                )
            """);

            // Table CATÉGORIES
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS categories (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    nom VARCHAR(255) UNIQUE NOT NULL,
                    image_path TEXT,
                    ordre INT DEFAULT 0
                )
            """);

            // Table PRODUITS
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS produits (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    nom VARCHAR(255) NOT NULL,
                    prix DOUBLE NOT NULL,
                    image_path TEXT,
                    categorie_id INT NOT NULL,
                    ordre INT DEFAULT 0,
                    CONSTRAINT fk_produits_categories
                        FOREIGN KEY (categorie_id) REFERENCES categories(id)
                        ON DELETE CASCADE
                )
            """);

            // Table COMMANDES
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS commandes (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    table_num VARCHAR(100),
                    total DOUBLE NOT NULL DEFAULT 0,
                    details LONGTEXT,
                    date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Table RÉGLAGES
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS settings (
                    cle VARCHAR(100) PRIMARY KEY,
                    valeur TEXT
                )
            """);

            // Table LOGS
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS log_activity (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(255),
                    action VARCHAR(255) NOT NULL,
                    details TEXT,
                    date_action TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Vérification des colonnes pour les mises à jour de version
            ensureColumnExists(conn, "categories", "ordre", "INT DEFAULT 0");
            ensureColumnExists(conn, "produits", "ordre", "INT DEFAULT 0");
            ensureColumnExists(conn, "commandes", "details", "LONGTEXT");

            // Création de l'admin par défaut
            try (PreparedStatement p = conn.prepareStatement("SELECT COUNT(*) FROM users");
                 ResultSet rs = p.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    addUser("admin", "1234", "Admin");
                }
            }

            // Paramètres par défaut
            String[][] defaultSettings = {
                    {KEY_NB_TABLES, "10"},
                    {KEY_PRINTER_CLIENT, "CLIENT_PRINTER"},
                    {KEY_PRINTER_CUISINE, "CUISINE_PRINTER"},
                    {KEY_NOM_RESTO, "TOMMY BURGER"}
            };

            try (PreparedStatement p = conn.prepareStatement("""
                    INSERT INTO settings (cle, valeur)
                    VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE valeur = valeur
                    """)) {
                for (String[] s : defaultSettings) {
                    p.setString(1, s[0]);
                    p.setString(2, s[1]);
                    p.addBatch();
                }
                p.executeBatch();
            }

            migrateSettingIfExists(conn, "printer_ip_client", KEY_PRINTER_CLIENT);
            migrateSettingIfExists(conn, "printer_ip_cuisine", KEY_PRINTER_CUISINE);

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Erreur lors de l'initialisation de la base de données", e);
        }
    }

    private static void ensureColumnExists(Connection conn, String tableName, String columnName, String columnDef) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    try (Statement st = conn.createStatement()) {
                        st.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDef);
                    }
                }
            }
        }
    }

    private static void migrateSettingIfExists(Connection conn, String oldKey, String newKey) throws SQLException {
        String oldVal = null;
        try (PreparedStatement p = conn.prepareStatement("SELECT valeur FROM settings WHERE cle=?")) {
            p.setString(1, oldKey);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) oldVal = rs.getString("valeur");
            }
        }
        if (oldVal != null && !oldVal.isBlank()) {
            setSetting(newKey, oldVal);
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM settings WHERE cle=?")) {
                del.setString(1, oldKey);
                del.executeUpdate();
            }
        }
    }

    // ================= GESTION DES RÉGLAGES =================

    public static String getSetting(String cle, String valeurParDefaut) {
        String sql = "SELECT valeur FROM settings WHERE cle = ?";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, cle);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) return rs.getString("valeur");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return valeurParDefaut;
    }

    public static void setSetting(String cle, String valeur) {
        String sql = "INSERT INTO settings (cle, valeur) VALUES (?, ?) ON DUPLICATE KEY UPDATE valeur = VALUES(valeur)";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, cle);
            p.setString(2, (valeur == null) ? "" : valeur.trim());
            p.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erreur mise à jour setting: " + cle, e);
        }
    }

    // ================= GESTION DES UTILISATEURS =================

    public static boolean checkLogin(String user, String pass, String role) {
        String sql = "SELECT 1 FROM users WHERE LOWER(username)=LOWER(?) AND password=? AND LOWER(role)=LOWER(?)";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, (user == null) ? "" : user.trim());
            p.setString(2, hashPassword(pass == null ? "" : pass));
            p.setString(3, (role == null) ? "" : role.trim());
            try (ResultSet rs = p.executeQuery()) {
                boolean success = rs.next();
                logAction(user, success ? "login" : "login_failed", "Tentative de connexion en tant que " + role);
                return success;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void addUser(String username, String password, String role) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) return;
        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, username.trim());
            p.setString(2, hashPassword(password));
            p.setString(3, (role == null) ? "Caissier" : role.trim());
            p.executeUpdate();
            logAction("system", "add_user", "Utilisateur créé: " + username);
        } catch (SQLException e) {
            throw new RuntimeException("Erreur ajout utilisateur", e);
        }
    }

    public static void deleteUserById(int id) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, id);
            p.executeUpdate();
            logAction("system", "delete_user", "Suppression utilisateur ID: " + id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ================= GESTION DES PRODUITS =================

    public static boolean updateProduit(int id, String newNom, double newPrix, String newImageOrNull) {
        if (newNom == null || newNom.isBlank()) return false;
        String sql = (newImageOrNull != null && !newImageOrNull.isBlank()) 
            ? "UPDATE produits SET nom=?, prix=?, image_path=? WHERE id=?" 
            : "UPDATE produits SET nom=?, prix=? WHERE id=?";
        
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, newNom.trim());
            p.setDouble(2, Math.max(0, newPrix));
            if (newImageOrNull != null && !newImageOrNull.isBlank()) {
                p.setString(3, newImageOrNull);
                p.setInt(4, id);
            } else {
                p.setInt(3, id);
            }
            int rows = p.executeUpdate();
            if (rows > 0) logAction("system", "update_produit", "Produit ID " + id + " modifié");
            return rows > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void addProduit(String nom, double prix, String imagePath, int categorieId) {
        if (nom == null || nom.isBlank()) return;
        String sql = "INSERT INTO produits (nom, prix, image_path, categorie_id, ordre) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = connect()) {
            int nextOrdre = 0;
            try (PreparedStatement po = conn.prepareStatement("SELECT COALESCE(MAX(ordre), -1) + 1 FROM produits WHERE categorie_id = ?")) {
                po.setInt(1, categorieId);
                try (ResultSet rs = po.executeQuery()) { if (rs.next()) nextOrdre = rs.getInt(1); }
            }
            try (PreparedStatement p = conn.prepareStatement(sql)) {
                p.setString(1, nom.trim());
                p.setDouble(2, Math.max(0, prix));
                p.setString(3, imagePath);
                p.setInt(4, categorieId);
                p.setInt(5, nextOrdre);
                p.executeUpdate();
            }
            logAction("system", "add_produit", "Produit " + nom + " ajouté");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ================= STATISTIQUES =================

    public static double getStatsPeriode(String periode) {
        String query = switch (periode) {
            case "Jour" -> "SELECT COALESCE(SUM(total),0) FROM commandes WHERE DATE(date) = CURDATE()";
            case "Semaine" -> "SELECT COALESCE(SUM(total),0) FROM commandes WHERE date >= NOW() - INTERVAL 7 DAY";
            case "Mois" -> "SELECT COALESCE(SUM(total),0) FROM commandes WHERE date >= NOW() - INTERVAL 1 MONTH";
            default -> "SELECT COALESCE(SUM(total),0) FROM commandes";
        };
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        } catch (SQLException e) { 
            e.printStackTrace();
            return 0.0;
        }
    }

    // ================= UTILITAIRES =================

    public static void logAction(String username, String action, String details) {
        String sql = "INSERT INTO log_activity (username, action, details) VALUES (?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, (username == null) ? "system" : username);
            p.setString(2, action);
            p.setString(3, details);
            p.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static String hashPassword(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return s;
        }
    }

    // Ajout d'une méthode pour fermer proprement si nécessaire (optionnel avec Try-with-resources)
    public static void deleteAllCommandes() {
        String sql = "TRUNCATE TABLE commandes"; // Plus rapide que DELETE pour vider une table
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            logAction("system", "delete_all_commandes", "Toutes les commandes ont été supprimées");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
