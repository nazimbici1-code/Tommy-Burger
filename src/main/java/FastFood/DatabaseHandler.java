package FastFood;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHandler {

    // ================== RAILWAY MYSQL ==================
    private static final String URL =
            "jdbc:mysql://shuttle.proxy.rlwy.net:16929/railway?" +
            "useSSL=false&" +
            "allowPublicKeyRetrieval=true&" +
            "serverTimezone=UTC&" +
            "connectTimeout=10000&" + 
            "socketTimeout=10000";

    private static final String USER = "root";
    private static final String PASSWORD = "clzDOSLPsQXUqtPSRYbmDUhSoPrjSqHn";
    // ===================================================

    public static final String KEY_NB_TABLES = "nbTables";
    public static final String KEY_PRINTER_CLIENT = "printer_name_client";
    public static final String KEY_PRINTER_CUISINE = "printer_name_cuisine";
    public static final String KEY_NOM_RESTO = "nom_resto";

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static void setupDatabase() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {

            // ================= USERS =================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(255) UNIQUE NOT NULL,
                    password VARCHAR(255) NOT NULL,
                    role VARCHAR(100) NOT NULL
                )
            """);

            // ================= CATEGORIES =================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS categories (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    nom VARCHAR(255) UNIQUE NOT NULL,
                    image_path TEXT,
                    ordre INT DEFAULT 0
                )
            """);

            // ================= PRODUITS =================
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

            // ================= COMMANDES =================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS commandes (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    table_num VARCHAR(100),
                    total DOUBLE NOT NULL DEFAULT 0,
                    details LONGTEXT,
                    date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // ================= SETTINGS =================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS settings (
                    cle VARCHAR(100) PRIMARY KEY,
                    valeur TEXT
                )
            """);

            // ================= LOGS =================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS log_activity (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(255),
                    action VARCHAR(255) NOT NULL,
                    details TEXT,
                    date_action TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            ensureColumnExists(conn, "categories", "ordre", "INT DEFAULT 0");
            ensureColumnExists(conn, "produits", "ordre", "INT DEFAULT 0");
            ensureColumnExists(conn, "commandes", "details", "LONGTEXT");

            try (PreparedStatement p = conn.prepareStatement("SELECT COUNT(*) FROM users");
                 ResultSet rs = p.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO users (username, password, role) VALUES (?, ?, ?)")) {
                        ins.setString(1, "admin");
                        ins.setString(2, hashPassword("1234"));
                        ins.setString(3, "Admin");
                        ins.executeUpdate();
                    }
                }
            }

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
            throw new RuntimeException("Erreur setupDatabase()", e);
        }
    }

    private static void ensureColumnExists(Connection conn, String tableName, String columnName, String columnDef)
            throws SQLException {
        String checkSql = """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """;

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
                if (rs.next()) {
                    oldVal = rs.getString("valeur");
                }
            }
        }
        if (oldVal != null && !oldVal.isBlank()) {
            try (PreparedStatement ins = conn.prepareStatement("""
                    INSERT INTO settings (cle, valeur)
                    VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE valeur = VALUES(valeur)
                    """)) {
                ins.setString(1, newKey);
                ins.setString(2, oldVal);
                ins.executeUpdate();
            }
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM settings WHERE cle=?")) {
                del.setString(1, oldKey);
                del.executeUpdate();
            }
        }
    }

    // ================= SETTINGS =================

    public static String getSetting(String cle, String valeurParDefaut) {
        String sql = "SELECT valeur FROM settings WHERE cle = ?";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, cle);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("valeur");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return valeurParDefaut;
    }

    public static void setSetting(String cle, String valeur) {
        String v = (valeur == null) ? "" : valeur.trim();
        String sql = """
                INSERT INTO settings (cle, valeur)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE valeur = VALUES(valeur)
                """;
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, cle);
            p.setString(2, v);
            p.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static int getNbTables() {
        try {
            return Integer.parseInt(getSetting(KEY_NB_TABLES, "10"));
        } catch (Exception e) {
            return 10;
        }
    }

    public static void setNbTables(int nb) {
        if (nb < 0) nb = 0;
        setSetting(KEY_NB_TABLES, String.valueOf(nb));
    }

    // ================= USERS =================

    public static boolean checkLogin(String user, String pass, String role) {
        String sql = "SELECT 1 FROM users WHERE LOWER(username)=LOWER(?) AND password=? AND LOWER(role)=LOWER(?)";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, user == null ? "" : user.trim());
            p.setString(2, hashPassword(pass == null ? "" : pass));
            p.setString(3, role == null ? "" : role.trim());
            try (ResultSet rs = p.executeQuery()) {
                boolean ok = rs.next();
                if (ok) {
                    logAction(user, "login", "Connexion réussie avec rôle " + role);
                } else {
                    logAction(user, "login_failed", "Échec de connexion avec rôle " + role);
                }
                return ok;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public static void addUser(String username, String password, String role) {
        if (username == null || username.trim().isEmpty()) return;
        if (password == null || password.isBlank()) return;
        if (role == null || role.isBlank()) role = "Caissier";

        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, username.trim());
            p.setString(2, hashPassword(password));
            p.setString(3, role.trim());
            p.executeUpdate();
            logAction(username, "add_user", "Ajout d'un utilisateur avec rôle " + role);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void deleteUserById(int id) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, id);
            p.executeUpdate();
            logAction("system", "delete_user", "Suppression utilisateur id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void updateUser(int id, String newUsername, String newPasswordOrNull, String newRole) {
        if (newUsername == null || newUsername.trim().isEmpty()) return;
        if (newRole == null || newRole.isBlank()) newRole = "Caissier";

        try (Connection conn = connect()) {
            if (newPasswordOrNull != null && !newPasswordOrNull.isBlank()) {
                try (PreparedStatement p = conn.prepareStatement(
                        "UPDATE users SET username=?, password=?, role=? WHERE id=?")) {
                    p.setString(1, newUsername.trim());
                    p.setString(2, hashPassword(newPasswordOrNull));
                    p.setString(3, newRole.trim());
                    p.setInt(4, id);
                    p.executeUpdate();
                }
            } else {
                try (PreparedStatement p = conn.prepareStatement(
                        "UPDATE users SET username=?, role=? WHERE id=?")) {
                    p.setString(1, newUsername.trim());
                    p.setString(2, newRole.trim());
                    p.setInt(3, id);
                    p.executeUpdate();
                }
            }
            logAction(newUsername, "update_user", "Modification utilisateur id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<AdminInterface.Employe> getAllUsers() {
        List<AdminInterface.Employe> users = new ArrayList<>();
        String sql = "SELECT id, username, role FROM users ORDER BY id DESC";
        try (Connection conn = connect(); Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                users.add(new AdminInterface.Employe(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("role")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return users;
    }

    // ================= CATEGORIES =================

    public static void addCategorie(String nom, String imagePath) {
        if (nom == null || nom.trim().isEmpty()) return;
        int ordre = 0;
        String sqlOrdre = "SELECT COALESCE(MAX(ordre), -1) + 1 FROM categories";
        String sql = "INSERT INTO categories (nom, image_path, ordre) VALUES (?, ?, ?)";
        try (Connection conn = connect()) {
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sqlOrdre)) {
                if (rs.next()) ordre = rs.getInt(1);
            }
            try (PreparedStatement p = conn.prepareStatement(sql)) {
                p.setString(1, nom.trim());
                p.setString(2, imagePath);
                p.setInt(3, ordre);
                p.executeUpdate();
            }
            logAction("system", "add_categorie", "Ajout catégorie " + nom);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void deleteCategorieById(int id) {
        String sql = "DELETE FROM categories WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, id);
            p.executeUpdate();
            logAction("system", "delete_categorie", "Suppression catégorie id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void updateCategorie(int id, String newNom, String newImageOrNull) {
        if (newNom == null || newNom.trim().isEmpty()) return;
        try (Connection conn = connect()) {
            if (newImageOrNull != null && !newImageOrNull.isBlank()) {
                try (PreparedStatement p = conn.prepareStatement(
                        "UPDATE categories SET nom=?, image_path=? WHERE id=?")) {
                    p.setString(1, newNom.trim());
                    p.setString(2, newImageOrNull);
                    p.setInt(3, id);
                    p.executeUpdate();
                }
            } else {
                try (PreparedStatement p = conn.prepareStatement(
                        "UPDATE categories SET nom=? WHERE id=?")) {
                    p.setString(1, newNom.trim());
                    p.setInt(2, id);
                    p.executeUpdate();
                }
            }
            logAction("system", "update_categorie", "Modification catégorie id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ================= PRODUITS =================

    public static void addProduit(String nom, double prix, String imagePath, int categorieId) {
        if (nom == null || nom.trim().isEmpty()) return;
        if (prix < 0) prix = 0;
        int ordre = 0;
        String sqlOrdre = "SELECT COALESCE(MAX(ordre), -1) + 1 FROM produits WHERE categorie_id = ?";
        String sql = "INSERT INTO produits (nom, prix, image_path, categorie_id, ordre) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = connect()) {
            try (PreparedStatement po = conn.prepareStatement(sqlOrdre)) {
                po.setInt(1, categorieId);
                try (ResultSet rs = po.executeQuery()) {
                    if (rs.next()) ordre = rs.getInt(1);
                }
            }
            try (PreparedStatement p = conn.prepareStatement(sql)) {
                p.setString(1, nom.trim());
                p.setDouble(2, prix);
                p.setString(3, imagePath);
                p.setInt(4, categorieId);
                p.setInt(5, ordre);
                p.executeUpdate();
            }
            logAction("system", "add_produit", "Ajout produit " + nom + " (cat=" + categorieId + ")");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void deleteProduitById(int id) {
        String sql = "DELETE FROM produits WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, id);
            p.executeUpdate();
            logAction("system", "delete_produit", "Suppression produit id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean updateProduit(int id, String newNom, double newPrix, String newImageOrNull) {
        if (newNom == null || newNom.trim().isEmpty()) return false;
        if (newPrix < 0) newPrix = 0;
        try (Connection conn = connect()) {
            if (newImageOrNull != null && !newImageOrNull.isBlank()) {
                try (PreparedStatement p = conn.prepareStatement(
                        "UPDATE produits SET nom=?, prix=?, image_path=? WHERE id=?")) {
                    p.setString(1, newNom.trim());
                    p.setDouble(2, newPrix);
                    p.setString(3, newImageOrNull);
                    p.setInt(4, id);
                    p.executeUpdate();
                }
            } else {
                try (PreparedStatement p = conn.prepareStatement(
                        "UPDATE produits SET nom=?, prix=? WHERE id=?")) {
                    p.setString(1, newNom.trim());
                    p.setDouble(2, newPrix);
                    p.setInt(3, id);
                    p.executeUpdate();
                }
            }
            logAction("system", "update_produit", "Modification produit id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
		return false;
    }

    public static List<AdminInterface.Categorie> getAllCategories() {
        List<AdminInterface.Categorie> categories = new ArrayList<>();
        String sqlCat = "SELECT id, nom, image_path, ordre FROM categories ORDER BY ordre ASC, id DESC";
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sqlCat)) {
            while (rs.next()) {
                AdminInterface.Categorie c = new AdminInterface.Categorie(
                        rs.getInt("id"),
                        rs.getString("nom"),
                        rs.getString("image_path")
                );
                c.ordre = rs.getInt("ordre");
                c.produits = getProduitsByCategorie(conn, c.id);
                categories.add(c);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return categories;
    }

    private static List<AdminInterface.Produit> getProduitsByCategorie(Connection conn, int catId) throws SQLException {
        List<AdminInterface.Produit> produits = new ArrayList<>();
        String sql = "SELECT id, nom, prix, image_path, ordre FROM produits WHERE categorie_id = ? ORDER BY ordre ASC, id DESC";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, catId);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    AdminInterface.Produit prod = new AdminInterface.Produit(
                            rs.getInt("id"),
                            rs.getString("nom"),
                            rs.getDouble("prix"),
                            rs.getString("image_path")
                    );
                    prod.ordre = rs.getInt("ordre");
                    produits.add(prod);
                }
            }
        }
        return produits;
    }

    // ================= COMMANDES =================

    public static void enregistrerCommande(String table, double total, String details) {
        String sql = "INSERT INTO commandes (table_num, total, details) VALUES (?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, table);
            p.setDouble(2, total);
            p.setString(3, details);
            p.executeUpdate();
            logAction("system", "add_commande", "Nouvelle commande table=" + table + ", total=" + total);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void deleteCommandeById(int id) {
        String sql = "DELETE FROM commandes WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, id);
            p.executeUpdate();
            logAction("system", "delete_commande", "Suppression commande id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<CommandeData> getAllCommandes() {
        List<CommandeData> list = new ArrayList<>();
        String sql = "SELECT id, date, table_num, details, total FROM commandes ORDER BY id DESC";
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new CommandeData(
                        rs.getInt("id"),
                        rs.getString("date"),
                        rs.getString("table_num"),
                        rs.getString("details"),
                        rs.getDouble("total")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public static void deleteAllCommandes() {
        String sql = "DELETE FROM commandes";
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            logAction("system", "delete_all_commandes", "Suppression de toutes les commandes");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ================= STATS =================

    public static double getStatsPeriode(String periode) {
        String query = switch (periode) {
            case "Jour" -> "SELECT COALESCE(SUM(total),0) FROM commandes WHERE DATE(date) = CURDATE()";
            case "Semaine" -> "SELECT COALESCE(SUM(total),0) FROM commandes WHERE date >= NOW() - INTERVAL 7 DAY";
            case "SemainePrecedente" -> "SELECT COALESCE(SUM(total),0) FROM commandes WHERE date >= NOW() - INTERVAL 14 DAY AND date < NOW() - INTERVAL 7 DAY";
            case "Mois" -> "SELECT COALESCE(SUM(total),0) FROM commandes WHERE date >= NOW() - INTERVAL 1 MONTH";
            default -> "SELECT COALESCE(SUM(total),0) FROM commandes";
        };
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { throw new RuntimeException(e); }
        return 0.0;
    }

    public static int getNombreCommandesPeriode(String periode) {
        String query = switch (periode) {
            case "Jour" -> "SELECT COUNT(*) FROM commandes WHERE DATE(date) = CURDATE()";
            case "Semaine" -> "SELECT COUNT(*) FROM commandes WHERE date >= NOW() - INTERVAL 7 DAY";
            case "SemainePrecedente" -> "SELECT COUNT(*) FROM commandes WHERE date >= NOW() - INTERVAL 14 DAY AND date < NOW() - INTERVAL 7 DAY";
            case "Mois" -> "SELECT COUNT(*) FROM commandes WHERE date >= NOW() - INTERVAL 1 MONTH";
            default -> "SELECT COUNT(*) FROM commandes";
        };
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { throw new RuntimeException(e); }
        return 0;
    }

    // ================= MENU API =================

    public static List<MenuItem> getMenu() {
        List<MenuItem> menu = new ArrayList<>();
        String sql = """
            SELECT p.id, p.nom, p.prix, p.image_path, c.nom AS categorie
            FROM produits p
            JOIN categories c ON p.categorie_id = c.id
            ORDER BY c.ordre ASC, c.nom ASC, p.ordre ASC, p.nom ASC
        """;
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                menu.add(new MenuItem(rs.getInt("id"), rs.getString("nom"), rs.getDouble("prix"), rs.getString("categorie"), rs.getString("image_path")));
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return menu;
    }

    // ================= LOG ACTIVITY =================

    public static void logAction(String username, String action, String details) {
        String sql = "INSERT INTO log_activity (username, action, details) VALUES (?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, username); p.setString(2, action); p.setString(3, details);
            p.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static List<LogEntry> getLogs() {
        List<LogEntry> logs = new ArrayList<>();
        String sql = "SELECT id, username, action, details, date_action FROM log_activity ORDER BY id DESC";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                logs.add(new LogEntry(rs.getInt("id"), rs.getString("username"), rs.getString("action"), rs.getString("details"), rs.getString("date_action")));
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return logs;
    }

    public static int getNombreActionsAujourdHui() {
        String sql = "SELECT COUNT(*) FROM log_activity WHERE DATE(date_action) = CURDATE()";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { throw new RuntimeException(e); }
        return 0;
    }

    // ================= ORDRE =================

    public static void updateProductOrder(int id, int ordre) {
        String sql = "UPDATE produits SET ordre = ? WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, ordre); p.setInt(2, id); p.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public static void updateCategoryOrder(int id, int ordre) {
        String sql = "UPDATE categories SET ordre = ? WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, ordre); p.setInt(2, id); p.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    // ================= PRINT =================

    public static void printRecapTicket(String text) {
        PrinterService.printText(KEY_PRINTER_CLIENT, text);
    }

    // ================= HASH =================

    private static String hashPassword(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return s == null ? "" : s; }
    }

    // ================= CORRECTION DES MÉTHODES API (SANS SUPPRESSION) =================

    public static boolean addProduit(String nom, double prix, String categorieNom) {
        if (nom == null || nom.trim().isEmpty()) return false;
        String sqlCat = "SELECT id FROM categories WHERE nom = ?";
        String sqlInsert = "INSERT INTO produits (nom, prix, categorie_id, ordre) VALUES (?, ?, ?, ?)";
        try (Connection conn = connect()) {
            int catId = -1;
            try (PreparedStatement pCat = conn.prepareStatement(sqlCat)) {
                pCat.setString(1, categorieNom);
                try (ResultSet rs = pCat.executeQuery()) {
                    if (rs.next()) catId = rs.getInt("id");
                }
            }
            if (catId == -1) {
                // Création auto de la catégorie si absente
                try (PreparedStatement pNewCat = conn.prepareStatement("INSERT INTO categories (nom) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
                    pNewCat.setString(1, categorieNom);
                    pNewCat.executeUpdate();
                    try (ResultSet rs = pNewCat.getGeneratedKeys()) { if (rs.next()) catId = rs.getInt(1); }
                }
            }
            try (PreparedStatement p = conn.prepareStatement(sqlInsert)) {
                p.setString(1, nom.trim()); p.setDouble(2, prix); p.setInt(3, catId); p.setInt(4, 0);
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

    public static List<AdminInterface.Employe> getStaff() {
        return getAllUsers();
    }

    public static boolean addStaff(String user, String pass, String role) {
        try { addUser(user, pass, role); return true; } catch (Exception e) { return false; }
    }

    public static boolean deleteStaff(String user) {
        String sql = "DELETE FROM users WHERE username = ?";
        try (Connection conn = connect(); PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, user);
            return p.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    // ================= CLASSES DE DONNÉES =================

    public static class CommandeData {
        private final int id; private final String dateHeure; private final String numTable; private final String details; private final double montantTotal;
        public CommandeData(int id, String dateHeure, String numTable, String details, double montantTotal) {
            this.id = id; this.dateHeure = dateHeure; this.numTable = numTable; this.details = details; this.montantTotal = montantTotal;
        }
        public int getId() { return id; }
        public String getDateHeure() { return dateHeure; }
        public String getNumTable() { return numTable; }
        public String getDetails() { return details; }
        public double getMontantTotal() { return montantTotal; }
    }

    public static class MenuItem {
        private final int id; private final String nom; private final double prix; private final String categorie; private final String imagePath;
        public MenuItem(int id, String nom, double prix, String categorie, String imagePath) {
            this.id = id; this.nom = nom; this.prix = prix; this.categorie = categorie; this.imagePath = imagePath;
        }
        public int getId() { return id; }
        public String getNom() { return nom; }
        public double getPrix() { return prix; }
        public String getCategorie() { return categorie; }
        public String getImagePath() { return imagePath; }
    }

    public static class LogEntry {
        private final int id; private final String username; private final String action; private final String details; private final String dateAction;
        public LogEntry(int id, String username, String action, String details, String dateAction) {
            this.id = id; this.username = username; this.action = action; this.details = details; this.dateAction = dateAction;
        }
        public int getId() { return id; }
        public String getUsername() { return username; }
        public String getAction() { return action; }
        public String getDetails() { return details; }
        public String getDateAction() { return dateAction; }
    }
}
