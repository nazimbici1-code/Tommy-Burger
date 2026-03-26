package FastFood;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminInterface extends Application {

    private StackPane root;
    private BorderPane content;

    private final ObservableList<Categorie> categoriesData = FXCollections.observableArrayList();
    private final ObservableList<Employe> staffData = FXCollections.observableArrayList();

    private int nbTables = 10;

    private final String IMAGE_DIR = "images/";
    private String tempImgPath = null;

    private final Map<String, Image> imageCache = new HashMap<>();
    private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "db-worker");
        t.setDaemon(true);
        return t;
    });

    private final String GRAD_ORANGE = "linear-gradient(to bottom right, #ff9f43, #ee5253)";
    private final String GRAD_BLUE   = "linear-gradient(to bottom right, #48dbfb, #2e86de)";
    private final String GRAD_PURPLE = "linear-gradient(to bottom right, #a29bfe, #6c5ce7)";
    private final String GRAD_GREEN  = "linear-gradient(to bottom right, #55efc4, #10ac84)";
    private final String GRAD_RED    = "linear-gradient(to bottom right, #ff7675, #d63031)";
    private final String GRAD_DARK   = "linear-gradient(to bottom right, #535c68, #2f3640)";
    private final String GRAD_CYAN   = "linear-gradient(to bottom right, #00d2d3, #0abde3)";

    private final String GLASS_DARK =
            "-fx-background-color: rgba(12,12,12,0.88);" +
            "-fx-background-radius: 25;" +
            "-fx-border-color: rgba(255,215,0,0.35);" +
            "-fx-border-width: 2;";

    private final String LIST_DARK =
            "-fx-background-color: rgba(10,10,10,0.75);" +
            "-fx-control-inner-background: rgba(10,10,10,0.75);" +
            "-fx-background-insets: 0;" +
            "-fx-padding: 10;";

    @Override
    public void start(Stage stage) {
        DatabaseHandler.setupDatabase();
        nbTables = DatabaseHandler.getNbTables();

        root = new StackPane();
        content = new BorderPane();

        try {
            Image bg = new Image(getClass().getResource("/FastFood/background.jpg").toExternalForm(), true);
            BackgroundImage bgImage = new BackgroundImage(
                    bg,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.CENTER,
                    new BackgroundSize(100, 100, true, true, false, true)
            );
            root.setBackground(new Background(bgImage));
        } catch (Exception e) {
            root.setStyle("-fx-background-color: #0b0f14;");
        }

        Region overlay = new Region();
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.60);");
        overlay.setEffect(new GaussianBlur(6));

        root.getChildren().addAll(overlay, content);

        try { Files.createDirectories(Paths.get(IMAGE_DIR)); } catch (Exception ignored) {}

        Scene scene = new Scene(root, 1400, 900);
        stage.setTitle("TOMMY BURGER – ADMIN");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();

        stage.setOnCloseRequest(e -> dbExec.shutdownNow());

        reloadAllAsync(this::showHome);
    }

    private static class Snapshot {
        final List<Employe> users;
        final List<Categorie> cats;
        final int nt;

        Snapshot(List<Employe> users, List<Categorie> cats, int nt) {
            this.users = users;
            this.cats = cats;
            this.nt = nt;
        }
    }

    private void reloadAllAsync(Runnable after) {
        Task<Snapshot> task = new Task<>() {
            @Override protected Snapshot call() {
                List<Employe> users = DatabaseHandler.getAllUsers();
                List<Categorie> cats = DatabaseHandler.getAllCategories();
                int nt = DatabaseHandler.getNbTables();
                return new Snapshot(users, cats, nt);
            }
        };

        task.setOnSucceeded(e -> {
            Snapshot s = task.getValue();
            staffData.setAll(s.users);
            categoriesData.setAll(s.cats);
            nbTables = s.nt;
            if (after != null) after.run();
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            if (ex != null) ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Erreur chargement base de données").show();
        });

        dbExec.execute(task);
    }

    private void setCenter(Node node) {
        content.setCenter(node);
    }

    private void showHome() {
        VBox menuPrincipal = new VBox(30);
        menuPrincipal.setAlignment(Pos.CENTER);
        menuPrincipal.setPadding(new Insets(40));

        Label title = new Label("TABLEAU DE BORD");
        title.setTextFill(Color.web("#f5f6fa"));
        title.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 52));

        GridPane grid = new GridPane();
        grid.setHgap(40);
        grid.setVgap(40);
        grid.setAlignment(Pos.CENTER);

        grid.add(createMenuBtn("📦 MENU", GRAD_ORANGE, e -> showCategoryManager()), 0, 0);
        grid.add(createMenuBtn("🪑 SALLE", GRAD_BLUE, e -> showTableManager()), 1, 0);
        grid.add(createMenuBtn("👥 PERSONNEL", GRAD_PURPLE, e -> showStaffManager()), 0, 1);
        grid.add(createMenuBtn("📊 HISTORIQUE", GRAD_GREEN, e -> showStats()), 1, 1);
        grid.add(createMenuBtn("💰 CHIFFRE D'AFFAIRES", GRAD_CYAN, e -> showBeneficeStats()), 0, 2);
        grid.add(createMenuBtn("🔑 COMPTES", GRAD_DARK, e -> showAccountManager()), 1, 2);
        grid.add(createMenuBtn("⚙️ MATÉRIEL", GRAD_DARK, e -> showHardwareSettings()), 0, 3);

        Button btnLogout = createBtn("🚪 DÉCONNEXION", GRAD_RED);
        btnLogout.setPrefWidth(260);
        btnLogout.setOnAction(e -> ((Stage) root.getScene().getWindow()).close());

        menuPrincipal.getChildren().addAll(title, grid, btnLogout);
        setCenter(menuPrincipal);
    }

    private VBox createPageBase(String title, Runnable backAction) {
        Button back = createBtn("⬅ RETOUR", GRAD_DARK);
        back.setOnAction(e -> backAction.run());

        Label t = new Label(title.toUpperCase());
        t.setTextFill(Color.web("#f5f6fa"));
        t.setFont(Font.font("System", FontWeight.BOLD, 38));

        VBox v = new VBox(25, back, t);
        v.setPadding(new Insets(35));
        v.setAlignment(Pos.TOP_CENTER);
        return v;
    }

    private VBox createGlass(String title, Node contentNode) {
        Label l = new Label(title);
        l.setTextFill(Color.web("#fbc531"));
        l.setFont(Font.font("System", FontWeight.SEMI_BOLD, 15));

        VBox v = new VBox(15, l, contentNode);
        v.setPadding(new Insets(20));
        v.setStyle(GLASS_DARK);
        return v;
    }

    private Label labelW(String s) {
        Label l = new Label(s);
        l.setTextFill(Color.web("#f5f6fa"));
        l.setFont(Font.font("System", FontWeight.BOLD, 14));
        return l;
    }

    private void showHardwareSettings() {
        VBox view = createPageBase("Paramètres Matériel", this::showHome);

        TextField printerClientName = createField("Nom imprimante Ticket (Client)", 380);
        printerClientName.setText(DatabaseHandler.getSetting("printer_name_client", "CLIENT_PRINTER"));

        TextField printerCuisineName = createField("Nom imprimante Bon (Cuisine)", 380);
        printerCuisineName.setText(DatabaseHandler.getSetting("printer_name_cuisine", "CUISINE_PRINTER"));

        TextField nameField = createField("Nom de l'établissement", 380);
        nameField.setText(DatabaseHandler.getSetting("nom_resto", "TOMMY BURGER"));

        Button btnSave = createBtn("ENREGISTRER", GRAD_GREEN);
        btnSave.setOnAction(e -> {
            Task<Void> t = new Task<>() {
                @Override protected Void call() {
                	DatabaseHandler.setSetting("printer_name_client", printerClientName.getText());
                	DatabaseHandler.setSetting("printer_name_cuisine", printerCuisineName.getText());
                	DatabaseHandler.setSetting("nom_resto", nameField.getText());

                    return null;
                }
            };
            t.setOnSucceeded(ev -> new Alert(Alert.AlertType.INFORMATION, "Configuration sauvegardée !").show());
            dbExec.execute(t);
        });

        VBox box = new VBox(12,
        		labelW("NOM IMPRIMANTE TICKET CLIENT :"), printerClientName,
        		labelW("NOM IMPRIMANTE BON CUISINE :"), printerCuisineName,
                labelW("NOM SUR LE TICKET :"), nameField,
                btnSave
        );
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(10));

        view.getChildren().add(createGlass("Gestion des Périphériques", box));
        setCenter(view);
    }

    private void showBeneficeStats() {
        VBox view = createPageBase("Analyse des Revenus", this::showHome);

        Label lblResultat = new Label("0.00 DA");
        lblResultat.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 60));
        lblResultat.setTextFill(Color.web("#fbc531"));

        ComboBox<String> periodeCombo = new ComboBox<>(FXCollections.observableArrayList("Jour", "Semaine", "Mois"));
        periodeCombo.setPromptText("Choisir la période");
        periodeCombo.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-text-fill: white; -fx-font-size: 18;");

        Button btnCalculer = createBtn("CALCULER", GRAD_GREEN);
        btnCalculer.setOnAction(e -> {
            String p = periodeCombo.getValue();
            if (p == null) return;

            Task<Double> t = new Task<>() {
                @Override protected Double call() {
                    return DatabaseHandler.getStatsPeriode(p);
                }
            };
            t.setOnSucceeded(ev -> lblResultat.setText(String.format("%.2f DA", t.getValue())));
            dbExec.execute(t);
        });

        VBox box = new VBox(18, labelW("SÉLECTIONNEZ UNE PÉRIODE :"), periodeCombo, btnCalculer, lblResultat);
        box.setAlignment(Pos.CENTER);

        view.getChildren().add(createGlass("Calculateur de Profit", box));
        setCenter(view);
    }

    private void showStaffManager() {
        VBox view = createPageBase("Gestion du Personnel", this::showHome);

        TextField nom = createField("Nom de l'employé", 250);
        TextField role = createField("Rôle (Admin / Caissier)", 220);
        Button add = createBtn("AJOUTER", GRAD_PURPLE);

        add.setOnAction(e -> {
            String n = nom.getText().trim();
            String r = role.getText().trim();
            if (n.isEmpty() || r.isEmpty()) return;

            Task<Void> t = new Task<>() {
                @Override protected Void call() {
                    DatabaseHandler.addUser(n, "1234", r);
                    return null;
                }
            };
            t.setOnSucceeded(ev -> reloadAllAsync(this::showStaffManager));
            dbExec.execute(t);
        });

        HBox form = new HBox(12, nom, role, add);
        form.setAlignment(Pos.CENTER);

        ListView<Employe> list = new ListView<>(staffData);
        list.setStyle(LIST_DARK);

        list.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Employe emp, boolean empty) {
                super.updateItem(emp, empty);
                if (empty || emp == null) { setGraphic(null); return; }

                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setStyle("-fx-background-color: rgba(0,0,0,0.55); -fx-background-radius: 18; -fx-padding: 12;");

                Label name = new Label(emp.username);
                name.setTextFill(Color.web("#f5f6fa"));
                name.setFont(Font.font("System", FontWeight.BOLD, 16));

                Label r = new Label("Rôle: " + emp.role);
                r.setTextFill(Color.web("#00d2d3"));

                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);

                Button edit = createSmallBtn("✏", "#feca57");
                edit.setOnAction(e -> editEmploye(emp));

                Button del = createSmallBtn("✕", "#ff7675");
                del.setOnAction(e -> {
                    Task<Void> t = new Task<>() {
                        @Override protected Void call() {
                            DatabaseHandler.deleteUserById(emp.id);
                            return null;
                        }
                    };
                    t.setOnSucceeded(ev -> reloadAllAsync(AdminInterface.this::showStaffManager));
                    dbExec.execute(t);
                });

                row.getChildren().addAll(name, r, sp, edit, del);
                setGraphic(createGlass("", row));
            }
        });

        view.getChildren().addAll(createGlass("Nouvel Employé", form), list);
        setCenter(view);
    }

    private void showAccountManager() {
        VBox view = createPageBase("Gestion des Comptes", this::showHome);

        Button btnAddAccount = createBtn("➕ CRÉER ACCÈS", GRAD_CYAN);
        btnAddAccount.setOnAction(e -> showAccountCreationPopup());

        ListView<Employe> list = new ListView<>(staffData);
        list.setStyle(LIST_DARK);

        list.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Employe emp, boolean empty) {
                super.updateItem(emp, empty);
                if (empty || emp == null) { setGraphic(null); return; }

                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setStyle("-fx-background-color: rgba(0,0,0,0.55); -fx-background-radius: 18; -fx-padding: 12;");

                Label name = new Label(emp.username);
                name.setTextFill(Color.web("#f5f6fa"));
                name.setFont(Font.font("System", FontWeight.BOLD, 16));

                Label role = new Label("Niveau: " + emp.role);
                role.setTextFill(Color.web("#00d2d3"));

                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);

                Button edit = createSmallBtn("✏", "#feca57");
                edit.setOnAction(e -> editEmploye(emp));

                Button del = createSmallBtn("✕", "#ff7675");
                del.setOnAction(e -> {
                    Task<Void> t = new Task<>() {
                        @Override protected Void call() {
                            DatabaseHandler.deleteUserById(emp.id);
                            return null;
                        }
                    };
                    t.setOnSucceeded(ev -> reloadAllAsync(AdminInterface.this::showAccountManager));
                    dbExec.execute(t);
                });

                row.getChildren().addAll(name, role, sp, edit, del);
                setGraphic(createGlass("", row));
            }
        });

        view.getChildren().addAll(createGlass("Accès Logiciel", new HBox(btnAddAccount)), list);
        setCenter(view);
    }

    private void showAccountCreationPopup() {
        Stage popup = new Stage();
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.setTitle("Nouveau Compte");

        VBox layout = new VBox(12);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-background-color: #0b0f14; -fx-border-color: #00d2d3; -fx-border-width: 2;");

        TextField nom = createField("Identifiant", 220);

        PasswordField mdp = new PasswordField();
        mdp.setPromptText("Mot de passe");
        mdp.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-text-fill: white; -fx-padding: 10; -fx-background-radius: 10;");

        ComboBox<String> roleCombo = new ComboBox<>(FXCollections.observableArrayList("Admin", "Caissier"));
        roleCombo.setValue("Caissier");

        Button save = createBtn("ACTIVER", GRAD_GREEN);
        save.setOnAction(e -> {
            String u = nom.getText().trim();
            String p = mdp.getText();
            String r = roleCombo.getValue();
            if (u.isEmpty() || p.isBlank()) return;

            Task<Void> t = new Task<>() {
                @Override protected Void call() {
                    DatabaseHandler.addUser(u, p, r);
                    return null;
                }
            };
            t.setOnSucceeded(ev -> {
                popup.close();
                reloadAllAsync(this::showAccountManager);
            });
            dbExec.execute(t);
        });

        Label lbl = new Label("INFOS DE CONNEXION");
        lbl.setTextFill(Color.web("#f5f6fa"));

        layout.getChildren().addAll(lbl, nom, mdp, roleCombo, save);
        popup.setScene(new Scene(layout, 340, 420));
        popup.show();
    }

    private void editEmploye(Employe emp) {
        Stage popup = new Stage();
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.setTitle("Modifier Compte");

        VBox layout = new VBox(12);
        layout.setPadding(new Insets(18));
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-background-color: #0b0f14; -fx-border-color: #feca57; -fx-border-width: 2;");

        TextField username = createField("Identifiant", 240);
        username.setText(emp.username);

        PasswordField pass = new PasswordField();
        pass.setPromptText("Nouveau mot de passe (optionnel)");
        pass.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-text-fill: white; -fx-padding: 10; -fx-background-radius: 10;");

        ComboBox<String> roleCombo = new ComboBox<>(FXCollections.observableArrayList("Admin", "Caissier"));
        roleCombo.setValue(emp.role == null ? "Caissier" : emp.role);

        Button save = createBtn("ENREGISTRER", GRAD_GREEN);
        save.setOnAction(e -> {
            String u = username.getText().trim();
            String p = pass.getText();
            String r = roleCombo.getValue();
            if (u.isEmpty()) return;

            Task<Void> t = new Task<>() {
                @Override protected Void call() {
                    DatabaseHandler.updateUser(emp.id, u, (p == null || p.isBlank()) ? null : p, r);
                    return null;
                }
            };
            t.setOnSucceeded(ev -> {
                popup.close();
                reloadAllAsync(this::showAccountManager);
            });
            dbExec.execute(t);
        });

        Button cancel = createBtn("ANNULER", GRAD_RED);
        cancel.setOnAction(e -> popup.close());

        layout.getChildren().addAll(labelW("MODIFIER COMPTE"), username, pass, roleCombo, new HBox(12, cancel, save));
        popup.setScene(new Scene(layout, 380, 420));
        popup.show();
    }

    private Button createBtn(String t, String grad) {
        Button b = new Button(t);
        b.setStyle("-fx-background-color: " + grad + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12; -fx-padding: 10 25; -fx-cursor: hand;");
        return b;
    }

    private Button createSmallBtn(String t, String color) {
        Button b = new Button(t);
        b.setFocusTraversable(false);
        b.setStyle("-fx-background-color: " + color + "; -fx-text-fill: black; -fx-font-weight: bold; -fx-background-radius: 12; -fx-padding: 6 10; -fx-cursor: hand;");
        return b;
    }

    private TextField createField(String p, int w) {
        TextField t = new TextField();
        t.setPromptText(p);
        t.setPrefWidth(w);
        t.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-text-fill: white; -fx-prompt-text-fill: rgba(255,255,255,0.55); -fx-background-radius: 10; -fx-padding: 10;");
        return t;
    }

    private Button createImageBtn() {
        Button b = new Button("📷 PHOTO");
        b.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;");
        b.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
            File f = fc.showOpenDialog(root.getScene().getWindow());
            if (f != null) tempImgPath = f.getAbsolutePath();
        });
        return b;
    }

    private VBox createMenuBtn(String t, String grad, javafx.event.EventHandler<javafx.scene.input.MouseEvent> e) {
        Label l = new Label(t);
        l.setTextFill(Color.web("#f5f6fa"));
        l.setFont(Font.font("System", FontWeight.BOLD, 22));

        VBox v = new VBox(l);
        v.setAlignment(Pos.CENTER);
        v.setPrefSize(280, 150);

        String borderColor = "#ffffff";
        int idx = grad.indexOf("#");
        if (idx >= 0 && idx + 7 <= grad.length()) borderColor = grad.substring(idx, idx + 7);

        v.setStyle(
                "-fx-background-color: rgba(0,0,0,0.55);" +
                "-fx-border-color: " + borderColor + ";" +
                "-fx-border-width: 3;" +
                "-fx-background-radius: 30;" +
                "-fx-border-radius: 30;" +
                "-fx-cursor: hand;"
        );
        v.setOnMouseClicked(e);
        return v;
    }

    private String copyImageToDir(String s) {
        if (s == null || s.isBlank()) return "";
        try {
            Path src = Paths.get(s);
            String fileName = System.currentTimeMillis() + "_" + src.getFileName();
            Path dst = Paths.get(IMAGE_DIR + fileName);
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            return dst.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private Image loadImage(String path, double w, double h) {
        String key = (path == null ? "" : path) + "|" + w + "x" + h;
        Image cached = imageCache.get(key);
        if (cached != null) return cached;

        Image img;
        try {
            if (path != null && !path.isBlank() && new File(path).exists()) {
                img = new Image(new File(path).toURI().toString(), w, h, true, true, true);
            } else {
                img = new Image("https://cdn-icons-png.flaticon.com/512/1147/1147832.png", w, h, true, true, true);
            }
        } catch (Exception e) {
            img = new Image("https://cdn-icons-png.flaticon.com/512/1147/1147832.png", w, h, true, true, true);
        }

        imageCache.put(key, img);
        return img;
    }

    public static class Categorie {
        public int id;
        public String nom;
        public String img;
        public List<Produit> produits;
		public int ordre;

        public Categorie(int id, String nom, String img) {
            this.id = id;
            this.nom = nom;
            this.img = img;
        }
    }

    public static class Produit {
        public int id;
        public String nom;
        public double prix;
        public String img;
		public int ordre;

        public Produit(int id, String nom, double prix, String img) {
            this.id = id;
            this.nom = nom;
            this.prix = prix;
            this.img = img;
        }
    }

    public static class Employe {
        public int id;
        public String username;
        public String role;

        public Employe(int id, String username, String role) {
            this.id = id;
            this.username = username;
            this.role = role;
        }
    }

    public static void main(String[] args) { launch(args); }

   

    private void showCategoryManager() {
        VBox view = createPageBase("Gestion des Catégories", this::showHome);

        TextField nom = createField("Nom de la catégorie...", 280);
        Button imgBtn = createImageBtn();
        Button add = createBtn("AJOUTER", GRAD_ORANGE);

        add.setOnAction(e -> {
            String n = nom.getText().trim();
            if (n.isEmpty()) return;

            String img = copyImageToDir(tempImgPath);
            tempImgPath = null;

            Task<Void> t = new Task<>() {
                @Override protected Void call() {
                    DatabaseHandler.addCategorie(n, img);
                    return null;
                }
            };
            t.setOnSucceeded(ev -> reloadAllAsync(this::showCategoryManager));
            dbExec.execute(t);
        });

        HBox form = new HBox(12, nom, imgBtn, add);
        form.setAlignment(Pos.CENTER);

        ListView<Categorie> list = new ListView<>(categoriesData);
        list.setStyle(LIST_DARK);

        list.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Categorie c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) { setGraphic(null); return; }

                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setStyle("-fx-background-color: rgba(0,0,0,0.55); -fx-background-radius: 18; -fx-padding: 12;");

                ImageView iv = new ImageView(loadImage(c.img, 70, 55));
                iv.setFitWidth(70);
                iv.setFitHeight(55);
                iv.setPreserveRatio(true);

                Label name = new Label(c.nom);
                name.setTextFill(Color.web("#f5f6fa"));
                name.setFont(Font.font("System", FontWeight.BOLD, 16));

                int count = (c.produits == null) ? 0 : c.produits.size();
                Label sub = new Label(count + " produits");
                sub.setTextFill(Color.web("#00d2d3"));

                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);

                Button open = createSmallBtn("📦", "#2e86de");
                open.setOnAction(e -> showProductManager(c));

                Button edit = createSmallBtn("✏", "#feca57");
                edit.setOnAction(e -> editCategory(c));

                Button del = createSmallBtn("✕", "#ff7675");
                del.setOnAction(e -> {
                    Task<Void> t = new Task<>() {
                        @Override protected Void call() {
                            DatabaseHandler.deleteCategorieById(c.id);
                            return null;
                        }
                    };
                    t.setOnSucceeded(ev -> reloadAllAsync(AdminInterface.this::showCategoryManager));
                    dbExec.execute(t);
                });

                row.getChildren().addAll(iv, name, sub, sp, open, edit, del);
                setGraphic(createGlass("", row));
            }
        });

        view.getChildren().addAll(createGlass("Nouvelle Catégorie", form), list);
        setCenter(view);
    }

    private void editCategory(Categorie c) {
        Stage popup = new Stage();
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.setTitle("Modifier Catégorie");

        VBox layout = new VBox(12);
        layout.setPadding(new Insets(18));
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-background-color: #0b0f14; -fx-border-color: #feca57; -fx-border-width: 2;");

        TextField name = createField("Nom", 260);
        name.setText(c.nom);

        Button imgBtn = createImageBtn();
        imgBtn.setText("📷 Nouvelle Photo");

        Button save = createBtn("ENREGISTRER", GRAD_GREEN);
        save.setOnAction(e -> {
            String n = name.getText().trim();
            if (n.isEmpty()) return;

            String img = copyImageToDir(tempImgPath);
            tempImgPath = null;

            Task<Void> t = new Task<>() {
                @Override protected Void call() {
                    DatabaseHandler.updateCategorie(c.id, n, (img == null || img.isBlank()) ? null : img);
                    return null;
                }
            };
            t.setOnSucceeded(ev -> {
                popup.close();
                reloadAllAsync(this::showCategoryManager);
            });
            dbExec.execute(t);
        });

        Button cancel = createBtn("ANNULER", GRAD_RED);
        cancel.setOnAction(e -> popup.close());

        layout.getChildren().addAll(labelW("MODIFIER CATÉGORIE"), name, imgBtn, new HBox(12, cancel, save));
        popup.setScene(new Scene(layout, 380, 300));
        popup.show();
    }

    private void showProductManager(Categorie cat) {
        VBox view = createPageBase("Produits : " + cat.nom, this::showCategoryManager);

        TextField nom = createField("Produit", 220);
        TextField prix = createField("Prix (DA)", 120);
        Button imgBtn = createImageBtn();
        Button add = createBtn("AJOUTER", GRAD_GREEN);

        add.setOnAction(e -> {
            String n = nom.getText().trim();
            String pr = prix.getText().trim();
            if (n.isEmpty() || pr.isEmpty()) return;

            double val;
            try { val = Double.parseDouble(pr.replace(",", ".")); }
            catch (Exception ex) { new Alert(Alert.AlertType.ERROR, "Prix invalide").show(); return; }

            String img = copyImageToDir(tempImgPath);
            tempImgPath = null;

            Task<Void> t = new Task<>() {
                @Override protected Void call() {
                    DatabaseHandler.addProduit(n, val, img, cat.id);
                    return null;
                }
            };

            t.setOnSucceeded(ev -> reloadAllAsync(() -> {
                Categorie updated = categoriesData.stream().filter(x -> x.id == cat.id).findFirst().orElse(cat);
                showProductManager(updated);
            }));
            dbExec.execute(t);
        });

        HBox form = new HBox(12, nom, prix, imgBtn, add);
        form.setAlignment(Pos.CENTER);

        ObservableList<Produit> products = FXCollections.observableArrayList(cat.produits == null ? List.of() : cat.produits);

        ListView<Produit> list = new ListView<>(products);
        list.setStyle(LIST_DARK);

        list.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Produit p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) { setGraphic(null); return; }

                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setStyle("-fx-background-color: rgba(0,0,0,0.55); -fx-background-radius: 18; -fx-padding: 12;");

                ImageView iv = new ImageView(loadImage(p.img, 70, 55));
                iv.setFitWidth(70);
                iv.setFitHeight(55);
                iv.setPreserveRatio(true);

                Label name = new Label(p.nom);
                name.setTextFill(Color.web("#f5f6fa"));
                name.setFont(Font.font("System", FontWeight.BOLD, 16));

                Label sub = new Label(String.format("%.2f DA", p.prix));
                sub.setTextFill(Color.web("#fbc531"));

                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);

                Button edit = createSmallBtn("✏", "#feca57");
                edit.setOnAction(e -> editProduct(cat, p));

                Button del = createSmallBtn("✕", "#ff7675");
                del.setOnAction(e -> {
                    Task<Void> t = new Task<>() {
                        @Override protected Void call() {
                            DatabaseHandler.deleteProduitById(p.id);
                            return null;
                        }
                    };
                    t.setOnSucceeded(ev -> reloadAllAsync(() -> {
                        Categorie updated = categoriesData.stream().filter(x -> x.id == cat.id).findFirst().orElse(cat);
                        showProductManager(updated);
                    }));
                    dbExec.execute(t);
                });

                row.getChildren().addAll(iv, name, sub, sp, edit, del);
                setGraphic(createGlass("", row));
            }
        });

        view.getChildren().addAll(createGlass("Ajouter un produit", form), list);
        setCenter(view);
    }

    private void editProduct(Categorie cat, Produit p) {
        Stage popup = new Stage();
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.setTitle("Modifier Produit");

        VBox layout = new VBox(12);
        layout.setPadding(new Insets(18));
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-background-color: #0b0f14; -fx-border-color: #feca57; -fx-border-width: 2;");

        TextField name = createField("Nom", 260);
        name.setText(p.nom);

        TextField price = createField("Prix (DA)", 260);
        price.setText(String.valueOf(p.prix));

        Button imgBtn = createImageBtn();
        imgBtn.setText("📷 Nouvelle Photo");

        Button save = createBtn("ENREGISTRER", GRAD_GREEN);
        save.setOnAction(e -> {
            String n = name.getText().trim();
            String pr = price.getText().trim();
            if (n.isEmpty() || pr.isEmpty()) return;

            double val;
            try { val = Double.parseDouble(pr.replace(",", ".")); }
            catch (Exception ex) { new Alert(Alert.AlertType.ERROR, "Prix invalide").show(); return; }

            String img = copyImageToDir(tempImgPath);
            tempImgPath = null;

            Task<Void> t = new Task<>() {
                @Override protected Void call() {
                    DatabaseHandler.updateProduit(p.id, n, val, (img == null || img.isBlank()) ? null : img);
                    return null;
                }
            };

            t.setOnSucceeded(ev -> {
                popup.close();
                reloadAllAsync(() -> {
                    Categorie updated = categoriesData.stream().filter(x -> x.id == cat.id).findFirst().orElse(cat);
                    showProductManager(updated);
                });
            });

            dbExec.execute(t);
        });

        Button cancel = createBtn("ANNULER", GRAD_RED);
        cancel.setOnAction(e -> popup.close());

        layout.getChildren().addAll(labelW("MODIFIER PRODUIT"), name, price, imgBtn, new HBox(12, cancel, save));
        popup.setScene(new Scene(layout, 400, 360));
        popup.show();
    }

   

    private void showTableManager() {
        VBox view = createPageBase("Plan de Salle", this::showHome);

        Button m = createBtn("- TABLE", GRAD_RED);
        Button p = createBtn("+ TABLE", GRAD_BLUE);

        Label info = new Label("Nombre de tables : " + nbTables);
        info.setTextFill(Color.web("#f5f6fa"));
        info.setFont(Font.font("System", FontWeight.BOLD, 18));

        m.setOnAction(e -> {
            if (nbTables <= 0) return;
            saveNbTablesAsync(nbTables - 1, this::showTableManager);
        });

        p.setOnAction(e -> saveNbTablesAsync(nbTables + 1, this::showTableManager));

        HBox ctrl = new HBox(20, m, p, info);
        ctrl.setAlignment(Pos.CENTER);

        FlowPane plan = new FlowPane(20, 20);
        plan.setAlignment(Pos.CENTER);
        plan.setPadding(new Insets(25));

        for (int i = 1; i <= nbTables; i++) {
            VBox t = new VBox(new Label("T" + i));
            t.setAlignment(Pos.CENTER);
            t.setPrefSize(100, 100);
            t.setStyle("-fx-background-color: rgba(0,0,0,0.55); -fx-border-color: #00d2d3; -fx-border-radius: 50; -fx-background-radius: 50; -fx-border-width: 2;");
            Label lb = (Label) t.getChildren().get(0);
            lb.setTextFill(Color.web("#f5f6fa"));
            lb.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 18));
            plan.getChildren().add(t);
        }

        view.getChildren().addAll(createGlass("Gestion des Tables", ctrl), createGlass("Plan", plan));
        setCenter(view);
    }

    private void saveNbTablesAsync(int val, Runnable after) {
        Task<Void> t = new Task<>() {
            @Override protected Void call() {
                DatabaseHandler.setNbTables(val);
                return null;
            }
        };
        t.setOnSucceeded(ev -> {
            nbTables = val;
            if (after != null) after.run();
        });
        dbExec.execute(t);
    }

   

    private void showStats() {
        VBox view = createPageBase("Historique des Commandes", this::showHome);

        Button btnReload = createBtn("🔄 ACTUALISER", GRAD_BLUE);
        Button btnDeleteOne = createBtn("🗑 SUPPRIMER", GRAD_RED);
        Button btnDeleteAll = createBtn("🗑 VIDER", GRAD_RED);

        TableView<DatabaseHandler.CommandeData> table = new TableView<>();
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.setPrefHeight(420);
        table.setStyle("-fx-background-color: rgba(0,0,0,0.55); -fx-control-inner-background: rgba(0,0,0,0.55);");

        TableColumn<DatabaseHandler.CommandeData, Integer> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("id"));

        TableColumn<DatabaseHandler.CommandeData, String> colDate = new TableColumn<>("DATE");
        colDate.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("dateHeure"));

        TableColumn<DatabaseHandler.CommandeData, String> colTable = new TableColumn<>("TABLE");
        colTable.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("numTable"));

        TableColumn<DatabaseHandler.CommandeData, Double> colTotal = new TableColumn<>("TOTAL (DA)");
        colTotal.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("montantTotal"));

        table.getColumns().setAll(colId, colDate, colTable, colTotal);

        TextArea ticketDisplay = new TextArea();
        ticketDisplay.setEditable(false);
        ticketDisplay.setPrefHeight(280);
        ticketDisplay.setPromptText("Sélectionnez une commande...");
        ticketDisplay.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 14; -fx-control-inner-background: rgba(0,0,0,0.55); -fx-text-fill: white;");

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;
            String fullTicket =
                    "======= TICKET #" + newVal.getId() + " =======\n" +
                    "Date: " + newVal.getDateHeure() + "\n" +
                    "Table: " + newVal.getNumTable() + "\n" +
                    "---------------------------\n" +
                    (newVal.getDetails() != null ? newVal.getDetails() : "") + "\n" +
                    "---------------------------\n" +
                    "TOTAL: " + newVal.getMontantTotal() + " DA\n" +
                    "===========================";
            ticketDisplay.setText(fullTicket);
        });

        btnReload.setOnAction(e -> loadCommandesAsync(table));
        btnDeleteOne.setOnAction(e -> {
            DatabaseHandler.CommandeData sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) {
                new Alert(Alert.AlertType.WARNING, "Sélectionnez une commande à supprimer !").show();
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Supprimer la commande ID #" + sel.getId() + " ?",
                    ButtonType.YES, ButtonType.NO);

            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

            Task<Void> t = new Task<>() {
                @Override protected Void call() {
                    DatabaseHandler.deleteCommandeById(sel.getId());
                    return null;
                }
            };

            t.setOnSucceeded(ev -> {
                loadCommandesAsync(table);
                ticketDisplay.clear();
                new Alert(Alert.AlertType.INFORMATION, "Commande supprimée ✅").show();
            });

            t.setOnFailed(ev -> {
                if (t.getException() != null) t.getException().printStackTrace();
                new Alert(Alert.AlertType.ERROR, "Erreur suppression").show();
            });

            dbExec.execute(t);
        });


        
        btnDeleteAll.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "Imprimer la clôture (imprimante CLIENT) + vider tout l'historique ?",
                    ButtonType.YES, ButtonType.NO);
            if (alert.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

            Task<Void> t = new Task<>() {
                @Override protected Void call() {
                    List<DatabaseHandler.CommandeData> all = DatabaseHandler.getAllCommandes();
                    if (all == null || all.isEmpty()) return null;

                    double total = 0;
                    StringBuilder sb = new StringBuilder();

                    sb.append("===== CLOTURE / HISTORIQUE =====\n");
                    sb.append(DatabaseHandler.getSetting("nom_resto", "TOMMY BURGER")).append("\n");
                    sb.append("Date: ").append(java.time.LocalDateTime.now()).append("\n");
                    sb.append("-------------------------------\n");

                    for (DatabaseHandler.CommandeData c : all) {
                        total += c.getMontantTotal();
                        sb.append("#").append(c.getId())
                                .append(" | ").append(c.getDateHeure())
                                .append(" | ").append(c.getNumTable())
                                .append(" | ").append(String.format("%.2f", c.getMontantTotal()))
                                .append(" DA\n");
                    }

                    sb.append("-------------------------------\n");
                    sb.append("NB COMMANDES: ").append(all.size()).append("\n");
                    sb.append("TOTAL GLOBAL: ").append(String.format("%.2f", total)).append(" DA\n");
                    sb.append("===============================\n\n\n");

                    DatabaseHandler.printRecapTicket(sb.toString());
                    DatabaseHandler.deleteAllCommandes();
                    return null;
                }
            };

            t.setOnSucceeded(ev -> {
                loadCommandesAsync(table);
                ticketDisplay.clear();
                new Alert(Alert.AlertType.INFORMATION, "Clôture imprimée + historique vidé ✅").show();
            });

            t.setOnFailed(ev -> {
                if (t.getException() != null) t.getException().printStackTrace();
                new Alert(Alert.AlertType.ERROR, "Erreur impression / vidage").show();
            });

            dbExec.execute(t);
        });
        HBox top = new HBox(12, btnReload, btnDeleteOne, btnDeleteAll);
        top.setAlignment(Pos.CENTER);


        VBox layout = new VBox(12, top, table, labelW("APERÇU DU TICKET :"), ticketDisplay);
        view.getChildren().add(createGlass("Journal des Ventes", layout));
        setCenter(view);

        loadCommandesAsync(table);
    }

    private void loadCommandesAsync(TableView<DatabaseHandler.CommandeData> table) {
        Task<List<DatabaseHandler.CommandeData>> t = new Task<>() {
            @Override protected List<DatabaseHandler.CommandeData> call() {
                return DatabaseHandler.getAllCommandes();
            }
        };
        t.setOnSucceeded(ev -> table.setItems(FXCollections.observableArrayList(t.getValue())));
        dbExec.execute(t);
    }
}
