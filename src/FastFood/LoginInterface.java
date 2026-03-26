package FastFood;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import java.io.InputStream;

public class LoginInterface extends Application {

    private double xOffset = 0;
    private double yOffset = 0;

    // Styles CSS centralisés
    private static final String LOGIN_CARD_STYLE =
            "-fx-background-color: rgba(255, 255, 255, 0.12); " +
            "-fx-background-radius: 30; " +
            "-fx-border-color: rgba(255, 255, 255, 0.25); " +
            "-fx-border-radius: 30; " +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 15, 0, 0, 0);";

    private static final String FIELD_STYLE =
            "-fx-background-color: rgba(255,255,255,0.08); " +
            "-fx-text-fill: white; " +
            "-fx-prompt-text-fill: rgba(255,255,255,0.5); " +
            "-fx-background-radius: 12; " +
            "-fx-padding: 12; " +
            "-fx-border-color: rgba(255,255,255,0.15); " +
            "-fx-border-radius: 12;";

    private static final String LOGIN_BTN_STYLE =
            "-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-weight: bold; " +
            "-fx-background-radius: 25; -fx-padding: 12; -fx-cursor: hand;";

    private static final String LOGIN_BTN_HOVER =
            "-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold; " +
            "-fx-background-radius: 25; -fx-padding: 12; -fx-cursor: hand;";

    @Override
    public void start(Stage stage) {
        DatabaseHandler.setupDatabase();
        stage.initStyle(StageStyle.UNDECORATED);

        StackPane root = new StackPane();
        setupBackground(root);

        Region overlay = new Region();
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.65);");

        BorderPane uiLayer = new BorderPane();
        HBox titleBar = createTitleBar(stage);
        uiLayer.setTop(titleBar);

        VBox loginCard = createLoginCard(stage);
        uiLayer.setCenter(loginCard);

        root.getChildren().addAll(overlay, uiLayer);

        // Déplacement de la fenêtre
        titleBar.setOnMousePressed(e -> { xOffset = e.getSceneX(); yOffset = e.getSceneY(); });
        titleBar.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - xOffset);
            stage.setY(e.getScreenY() - yOffset);
        });

        Scene scene = new Scene(root, 900, 650);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();

        applyEntranceAnimation(loginCard);
    }

    private void setupBackground(StackPane pane) {
        // Chemin absolu pour ressources Maven (Standard)
        try (InputStream is = getClass().getResourceAsStream("/FastFood/background.jpg")) {
            if (is != null) {
                Image bg = new Image(is);
                pane.setBackground(new Background(new BackgroundImage(bg,
                        BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
                        BackgroundPosition.CENTER, 
                        new BackgroundSize(100, 100, true, true, false, true))));
            } else {
                pane.setStyle("-fx-background-color: #2c3e50;");
                System.err.println("[ERREUR] background.jpg introuvable");
            }
        } catch (Exception e) {
            pane.setStyle("-fx-background-color: #2c3e50;");
        }
    }

    private HBox createTitleBar(Stage stage) {
        HBox titleBar = new HBox(10);
        titleBar.setPrefHeight(80);
        titleBar.setPadding(new Insets(15, 25, 0, 25));
        titleBar.setAlignment(Pos.CENTER_LEFT);

        StackPane brand = new StackPane();
        try (InputStream is = getClass().getResourceAsStream("/FastFood/tommy_logo.jpg")) {
            if (is != null) {
                ImageView logo = new ImageView(new Image(is, 50, 50, true, true));
                logo.setClip(new Circle(25, 25, 25));
                brand.getChildren().add(logo);
            } else {
                Text brandText = new Text("TOMMY BURGER");
                brandText.setFill(Color.WHITE);
                brandText.setFont(Font.font("System", FontWeight.BOLD, 18));
                brand.getChildren().add(brandText);
            }
        } catch (Exception e) { }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // BOUTON RÉDUIRE (_)
        Button btnMinimize = new Button("_");
        btnMinimize.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 20; -fx-cursor: hand;");
        btnMinimize.setOnAction(e -> stage.setIconified(true));

        // BOUTON FERMER (✕)
        Button btnClose = new Button("✕");
        btnClose.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 20; -fx-cursor: hand;");
        btnClose.setOnMouseEntered(e -> btnClose.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 20;"));
        btnClose.setOnMouseExited(e -> btnClose.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 20;"));
        btnClose.setOnAction(e -> Platform.exit());

        titleBar.getChildren().addAll(brand, spacer, btnMinimize, btnClose);
        return titleBar;
    }

    private VBox createLoginCard(Stage stage) {
        VBox card = new VBox(20);
        card.setAlignment(Pos.CENTER);
        card.setMaxSize(380, 520);
        card.setPadding(new Insets(40));
        card.setStyle(LOGIN_CARD_STYLE);
        card.setOpacity(0);

        // BURGER EN BLANC
        Text burgerIcon = new Text("🍔");
        burgerIcon.setFont(Font.font(70));
        burgerIcon.setFill(Color.WHITE); 

        Text loginTitle = new Text("CONNEXION");
        loginTitle.setFill(Color.WHITE);
        loginTitle.setFont(Font.font("System", FontWeight.BOLD, 28));

        TextField userField = new TextField();
        userField.setPromptText("Identifiant");
        userField.setStyle(FIELD_STYLE);

        PasswordField passField = new PasswordField();
        passField.setPromptText("Mot de passe");
        passField.setStyle(FIELD_STYLE);

        ToggleGroup group = new ToggleGroup();
        RadioButton rbAdmin = new RadioButton("Admin");
        rbAdmin.setToggleGroup(group);
        rbAdmin.setSelected(true);
        rbAdmin.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        RadioButton rbCaissier = new RadioButton("Caissier");
        rbCaissier.setToggleGroup(group);
        rbCaissier.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        HBox roleBox = new HBox(40, rbAdmin, rbCaissier);
        roleBox.setAlignment(Pos.CENTER);

        Button loginBtn = new Button("SE CONNECTER");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setStyle(LOGIN_BTN_STYLE);

        ProgressIndicator loader = new ProgressIndicator();
        loader.setMaxSize(25, 25);
        loader.setVisible(false);

        HBox btnRow = new HBox(15, loginBtn, loader);
        btnRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(loginBtn, Priority.ALWAYS);

        loginBtn.setOnMouseEntered(e -> loginBtn.setStyle(LOGIN_BTN_HOVER));
        loginBtn.setOnMouseExited(e -> loginBtn.setStyle(LOGIN_BTN_STYLE));

        loginBtn.setOnAction(e -> {
            RadioButton selected = (RadioButton) group.getSelectedToggle();
            handleLoginAsync(userField.getText(), passField.getText(), selected.getText(), stage, loginBtn, loader);
        });

        card.getChildren().addAll(burgerIcon, loginTitle, userField, passField, roleBox, btnRow);
        return card;
    }

    private void handleLoginAsync(String username, String password, String role,
                                  Stage currentStage, Button loginBtn, ProgressIndicator loader) {

        if (username.trim().isEmpty() || password.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Attention", "Veuillez remplir tous les champs.");
            return;
        }

        loginBtn.setDisable(true);
        loader.setVisible(true);

        Task<Boolean> task = new Task<>() {
            @Override protected Boolean call() throws Exception {
                return DatabaseHandler.checkLogin(username.trim(), password.trim(), role);
            }
        };

        task.setOnSucceeded(ev -> {
            if (task.getValue()) {
                currentStage.close();
                Platform.runLater(() -> {
                    try {
                        if (role.equalsIgnoreCase("Admin")) {
                            new AdminInterface().start(new Stage());
                        } else {
                            new BorneCommande().start(new Stage());
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                });
            } else {
                loginBtn.setDisable(false);
                loader.setVisible(false);
                showAlert(Alert.AlertType.ERROR, "Échec", "Identifiants incorrects.");
            }
        });

        task.setOnFailed(ev -> {
            loginBtn.setDisable(false);
            loader.setVisible(false);
            showAlert(Alert.AlertType.ERROR, "Erreur Serveur", "Connexion à la base de données impossible.");
        });

        new Thread(task).start();
    }

    private void applyEntranceAnimation(VBox node) {
        FadeTransition fade = new FadeTransition(Duration.millis(1000), node);
        fade.setFromValue(0);
        fade.setToValue(1);

        TranslateTransition move = new TranslateTransition(Duration.millis(1000), node);
        move.setFromY(50);
        move.setToY(0);

        new ParallelTransition(fade, move).play();
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        DialogPane dp = alert.getDialogPane();
        dp.setStyle("-fx-background-color: #2c3e50; -fx-border-color: #f39c12;");
        Node label = dp.lookup(".content.label");
        if (label != null) label.setStyle("-fx-text-fill: white;");
        alert.showAndWait();
    }

    public static void main(String[] args) { launch(args); }
}
