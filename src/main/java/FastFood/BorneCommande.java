package FastFood;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BorneCommande extends Application {

    private final String GOLD_COLOR = "#FFD700";
    private final String DARK_GLASS = "rgba(0, 0, 0, 0.85)";

    private StackPane root;
    private BorderPane mainCaisseLayout;
    private Stage primaryStage;

    private String tableSelectionnee = "";
    private VBox itemsList;
    private Label lblTotal;
    private double totalCommande = 0.0;

    private List<AdminInterface.Categorie> cachedCategories;
    private final Map<String, Image> imageCache = new HashMap<>();

    private TextField searchField;
    private FlowPane categoriesPane;
    private FlowPane productsPane;
    private Node draggedNode;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        DatabaseHandler.setupDatabase();

        root = new StackPane();

        ImageView bgView = new ImageView();
        try {
            Image bgImg = new Image(
                    getClass().getResource("/FastFood/background.jpg").toExternalForm(),
                    true
            );

            bgView.setImage(bgImg);
            bgView.fitWidthProperty().bind(root.widthProperty());
            bgView.fitHeightProperty().bind(root.heightProperty());
            bgView.setPreserveRatio(false);

            root.getChildren().add(bgView);

        } catch (Exception e) {
            root.setStyle("-fx-background-color: #0d0d0d;");
        }

        Region overlay = new Region();
        overlay.setStyle("-fx-background-color: radial-gradient(center 50% 50%, radius 100%, rgba(0,0,0,0.12), rgba(0,0,0,0.72));");
        root.getChildren().add(overlay);

        Scene scene = new Scene(root, 1280, 800);

        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.F11) {
                ouvrirTiroirCaisseAsync();
            }
        });

        primaryStage.initStyle(StageStyle.UNDECORATED);
        primaryStage.setFullScreen(true);
        primaryStage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
        primaryStage.setScene(scene);
        primaryStage.show();

        loadCategoriesAsync(this::showAccueil);
    }

    private void loadCategoriesAsync(Runnable after) {
        Task<List<AdminInterface.Categorie>> t = new Task<>() {
            @Override
            protected List<AdminInterface.Categorie> call() {
                return DatabaseHandler.getAllCategories();
            }
        };

        t.setOnSucceeded(ev -> {
            cachedCategories = t.getValue();
            if (after != null) after.run();
        });

        t.setOnFailed(ev -> {
            if (t.getException() != null) t.getException().printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Erreur chargement catégories").show();
        });

        new Thread(t, "borne-load-cats").start();
    }

    private void playClickAnimation(Node node, Runnable action) {
        ScaleTransition st = new ScaleTransition(Duration.millis(80), node);
        st.setFromX(1.0);
        st.setFromY(1.0);
        st.setToX(0.92);
        st.setToY(0.92);
        st.setCycleCount(2);
        st.setAutoReverse(true);
        st.setOnFinished(e -> action.run());
        st.play();
    }

    private void showAccueil() {
        totalCommande = 0;

        VBox centerLayout = new VBox(60);
        centerLayout.setAlignment(Pos.CENTER);

        Text mainTitle = createStyledText(DatabaseHandler.getSetting("nom_resto", "TOMMY BURGER"), 110);
        mainTitle.setEffect(new DropShadow(20, Color.BLACK));

        HBox options = new HBox(60);
        options.setAlignment(Pos.CENTER);

        Button btnSurPlace = createTommyButton("🍴", "SUR PLACE", "EXPÉRIENCE VIP");
        Button btnEmporter = createTommyButton("🛍️", "À EMPORTER", "FAST & FRESH");

        btnSurPlace.setOnAction(e -> playClickAnimation(btnSurPlace, this::showPlanDeSalle));
        btnEmporter.setOnAction(e -> playClickAnimation(btnEmporter, () -> {
            tableSelectionnee = "À EMPORTER";
            showInterfaceCaissier();
        }));

        options.getChildren().addAll(btnSurPlace, btnEmporter);
        centerLayout.getChildren().addAll(mainTitle, options);

        updateMainContent(centerLayout);
    }

    private void showPlanDeSalle() {
        VBox planLayout = new VBox(40);
        planLayout.setAlignment(Pos.CENTER);
        planLayout.setPadding(new Insets(20));

        HBox topRow = new HBox(30);
        topRow.setAlignment(Pos.CENTER);

        Button btnBack = createModernBackButton("RETOUR");
        btnBack.setOnAction(e -> playClickAnimation(btnBack, this::showAccueil));

        Text title = createStyledText("PLAN DE SALLE", 70);
        topRow.getChildren().addAll(btnBack, title);

        FlowPane flowTables = new FlowPane(35, 35);
        flowTables.setAlignment(Pos.CENTER);

        int maxTables = DatabaseHandler.getNbTables();
        for (int i = 1; i <= maxTables; i++) {
            final int n = i;
            Button b = createPremiumRoundTable(n);
            b.setOnAction(e -> playClickAnimation(b, () -> {
                tableSelectionnee = "TABLE " + n;
                showInterfaceCaissier();
            }));
            flowTables.getChildren().add(b);
        }

        planLayout.getChildren().addAll(topRow, flowTables);
        updateMainContent(planLayout);
    }

    private void showInterfaceCaissier() {
        mainCaisseLayout = new BorderPane();
        mainCaisseLayout.setPadding(new Insets(120, 40, 40, 40));

        VBox panier = new VBox(25);
        panier.setPrefWidth(430);
        panier.setPadding(new Insets(28));
        panier.setStyle(
                "-fx-background-color: rgba(15,15,15,0.92);" +
                "-fx-background-radius: 30;"
        );

        Text lblTicket = createStyledText("MON PANIER", 28);

        itemsList = new VBox(15);
        itemsList.setFillWidth(true);

        ScrollPane scrollItems = new ScrollPane(itemsList);
        scrollItems.setPrefHeight(470);
        scrollItems.setFitToWidth(true);
        scrollItems.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollItems.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollItems.setPannable(true);
        makeScrollPaneTransparent(scrollItems);

        VBox footerPanier = new VBox(18);
        footerPanier.setAlignment(Pos.CENTER);

        lblTotal = new Label("0.00 DA");
        lblTotal.setFont(Font.font("Impact", 42));
        lblTotal.setTextFill(Color.web(GOLD_COLOR));

        Button btnPay = new Button("VALIDER LA COMMANDE");
        btnPay.setPrefSize(360, 64);
        btnPay.setStyle(
                "-fx-background-color: #2ecc71;" +
                "-fx-text-fill: white;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 19;" +
                "-fx-background-radius: 18;" +
                "-fx-cursor: hand;"
        );
        btnPay.setOnAction(e -> playClickAnimation(btnPay, () -> {
            if (totalCommande > 0) validerCommandeAsync();
        }));

        Button btnDrawer = new Button("💰 OUVRIR LE TIROIR");
        btnDrawer.setPrefSize(360, 52);
        btnDrawer.setStyle(
                "-fx-background-color: #f1c40f;" +
                "-fx-text-fill: #111;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 17;" +
                "-fx-background-radius: 16;" +
                "-fx-cursor: hand;"
        );
        btnDrawer.setOnAction(e -> playClickAnimation(btnDrawer, this::ouvrirTiroirCaisseAsync));

        Button btnCancel = new Button("ANNULER TOUT");
        btnCancel.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-text-fill: #ff5c5c;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 15;" +
                "-fx-cursor: hand;"
        );
        btnCancel.setOnAction(e -> playClickAnimation(btnCancel, () -> {
            totalCommande = 0;
            if (itemsList != null) itemsList.getChildren().clear();
            if (lblTotal != null) lblTotal.setText("0.00 DA");
            showAccueil();
        }));

        footerPanier.getChildren().addAll(
                new Separator(),
                lblTotal,
                btnPay,
                btnDrawer,
                btnCancel
        );

        panier.getChildren().addAll(lblTicket, scrollItems, footerPanier);
        mainCaisseLayout.setRight(panier);

        showCategoriesMenu();
        updateMainContent(mainCaisseLayout);
    }

    private void showCategoriesMenu() {
        VBox leftArea = new VBox(22);
        leftArea.setAlignment(Pos.TOP_LEFT);

        HBox header = new HBox(18);
        header.setAlignment(Pos.CENTER_LEFT);

        Button btnBack = createModernBackButton("⬅");
        btnBack.setOnAction(e -> playClickAnimation(btnBack, this::showAccueil));

        Text t = createStyledText(tableSelectionnee, 44);
        header.getChildren().addAll(btnBack, t);

        searchField = new TextField();
        searchField.setPromptText("Rechercher une catégorie ou un plat...");
        searchField.setPrefWidth(520);
        searchField.setMaxWidth(520);
        searchField.setPrefHeight(42);
        searchField.setStyle(
                "-fx-background-color: rgba(255,255,255,0.95);" +
                "-fx-text-fill: #111;" +
                "-fx-prompt-text-fill: #9a9a9a;" +
                "-fx-background-radius: 22;" +
                "-fx-border-radius: 22;" +
                "-fx-border-color: transparent;" +
                "-fx-font-size: 15;" +
                "-fx-padding: 0 16 0 16;"
        );

        categoriesPane = new FlowPane(20, 20);
        categoriesPane.setAlignment(Pos.TOP_LEFT);
        categoriesPane.setPadding(new Insets(10, 5, 10, 5));

        refreshCategoriesView("");

        searchField.textProperty().addListener((obs, oldVal, newVal) -> refreshCategoriesView(newVal));

        ScrollPane scrollMenu = new ScrollPane(categoriesPane);
        scrollMenu.setFitToWidth(true);
        scrollMenu.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollMenu.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollMenu.setPannable(true);
        makeScrollPaneTransparent(scrollMenu);

        leftArea.getChildren().addAll(header, searchField, scrollMenu);
        mainCaisseLayout.setCenter(leftArea);
    }

    private void refreshCategoriesView(String keyword) {
        if (categoriesPane == null) return;
        categoriesPane.getChildren().clear();
        if (cachedCategories == null) return;

        String search = keyword == null ? "" : keyword.trim().toLowerCase();

        for (AdminInterface.Categorie cat : cachedCategories) {
            boolean matchCategorie = cat.nom != null && cat.nom.toLowerCase().contains(search);

            boolean matchProduit = false;
            if (cat.produits != null) {
                for (AdminInterface.Produit p : cat.produits) {
                    if (p.nom != null && p.nom.toLowerCase().contains(search)) {
                        matchProduit = true;
                        break;
                    }
                }
            }

            if (search.isBlank() || matchCategorie || matchProduit) {
                Button catBtn = createPremiumMiniButton(cat.nom, cat.img);
                catBtn.setUserData(cat);
                catBtn.setOnAction(e -> playClickAnimation(catBtn, () -> showProductsForCategory(cat)));
                enableCategoryDrag(catBtn, cat);
                categoriesPane.getChildren().add(catBtn);
            }
        }
    }

    private void showProductsForCategory(AdminInterface.Categorie cat) {
        VBox productView = new VBox(22);
        productView.setAlignment(Pos.TOP_LEFT);

        HBox header = new HBox(18);
        header.setAlignment(Pos.CENTER_LEFT);

        Button btnBack = createModernBackButton("⬅");
        btnBack.setOnAction(e -> playClickAnimation(btnBack, this::showCategoriesMenu));

        Text title = createStyledText(cat.nom, 44);
        header.getChildren().addAll(btnBack, title);

        searchField = new TextField();
        searchField.setPromptText("Rechercher un plat...");
        searchField.setPrefWidth(420);
        searchField.setMaxWidth(420);
        searchField.setPrefHeight(42);
        searchField.setStyle(
                "-fx-background-color: rgba(255,255,255,0.95);" +
                "-fx-text-fill: #111;" +
                "-fx-prompt-text-fill: #9a9a9a;" +
                "-fx-background-radius: 22;" +
                "-fx-border-radius: 22;" +
                "-fx-border-color: transparent;" +
                "-fx-font-size: 15;" +
                "-fx-padding: 0 16 0 16;"
        );

        productsPane = new FlowPane(20, 20);
        productsPane.setAlignment(Pos.TOP_LEFT);
        productsPane.setPadding(new Insets(10, 5, 10, 5));

        refreshProductsView(cat, "");

        searchField.textProperty().addListener((obs, oldVal, newVal) -> refreshProductsView(cat, newVal));

        ScrollPane scrollProd = new ScrollPane(productsPane);
        scrollProd.setFitToWidth(true);
        scrollProd.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollProd.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollProd.setPannable(true);
        makeScrollPaneTransparent(scrollProd);

        productView.getChildren().addAll(header, searchField, scrollProd);
        mainCaisseLayout.setCenter(productView);
    }

    private void refreshProductsView(AdminInterface.Categorie cat, String keyword) {
        if (productsPane == null || cat == null) return;

        productsPane.getChildren().clear();
        String search = keyword == null ? "" : keyword.trim().toLowerCase();

        if (cat.produits != null) {
            for (AdminInterface.Produit prod : cat.produits) {
                boolean match = prod.nom != null && prod.nom.toLowerCase().contains(search);

                if (search.isBlank() || match) {
                    Button pBtn = createProductCard(prod);
                    pBtn.setUserData(prod);
                    pBtn.setOnAction(e -> playClickAnimation(pBtn, () -> ajouterAuPanier(prod)));
                    enableProductDrag(pBtn, prod, cat);
                    productsPane.getChildren().add(pBtn);
                }
            }
        }
    }

    private void enableCategoryDrag(Button btn, AdminInterface.Categorie cat) {
        btn.setOnDragDetected(e -> {
            draggedNode = btn;
            Dragboard db = btn.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(cat.nom);
            db.setContent(content);
            e.consume();
        });

        btn.setOnDragOver(e -> {
            if (e.getGestureSource() != btn && e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });

        btn.setOnDragDropped(e -> {
            if (draggedNode != null && draggedNode != btn && categoriesPane != null) {
                int draggedIndex = categoriesPane.getChildren().indexOf(draggedNode);
                int targetIndex = categoriesPane.getChildren().indexOf(btn);

                if (draggedIndex >= 0 && targetIndex >= 0) {
                    categoriesPane.getChildren().remove(draggedNode);
                    categoriesPane.getChildren().add(targetIndex, draggedNode);
                    saveCategoryOrder();
                }
            }
            e.setDropCompleted(true);
            e.consume();
        });
    }

    private void enableProductDrag(Button btn, AdminInterface.Produit prod, AdminInterface.Categorie cat) {
        btn.setOnDragDetected(e -> {
            draggedNode = btn;
            Dragboard db = btn.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(prod.nom);
            db.setContent(content);
            e.consume();
        });

        btn.setOnDragOver(e -> {
            if (e.getGestureSource() != btn && e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });

        btn.setOnDragDropped(e -> {
            if (draggedNode != null && draggedNode != btn && productsPane != null) {
                int draggedIndex = productsPane.getChildren().indexOf(draggedNode);
                int targetIndex = productsPane.getChildren().indexOf(btn);

                if (draggedIndex >= 0 && targetIndex >= 0) {
                    productsPane.getChildren().remove(draggedNode);
                    productsPane.getChildren().add(targetIndex, draggedNode);
                    saveProductOrder(cat);
                }
            }
            e.setDropCompleted(true);
            e.consume();
        });
    }

    private void saveCategoryOrder() {
        if (categoriesPane == null) return;

        for (int i = 0; i < categoriesPane.getChildren().size(); i++) {
            Node node = categoriesPane.getChildren().get(i);
            if (node instanceof Button btn && btn.getUserData() instanceof AdminInterface.Categorie cat) {
                DatabaseHandler.updateCategoryOrder(cat.id, i);
                cat.ordre = i;
            }
        }

        cachedCategories.sort((a, b) -> Integer.compare(a.ordre, b.ordre));
    }

    private void saveProductOrder(AdminInterface.Categorie cat) {
        if (productsPane == null || cat == null) return;

        for (int i = 0; i < productsPane.getChildren().size(); i++) {
            Node node = productsPane.getChildren().get(i);
            if (node instanceof Button btn && btn.getUserData() instanceof AdminInterface.Produit prod) {
                DatabaseHandler.updateProductOrder(prod.id, i);
                prod.ordre = i;
            }
        }

        if (cat.produits != null) {
            cat.produits.sort((a, b) -> Integer.compare(a.ordre, b.ordre));
        }
    }

    private void ajouterAuPanier(AdminInterface.Produit prod) {
        if (itemsList == null) return;

        double prixUnitaire = prod.prix;

        VBox blocProduit = new VBox(10);
        blocProduit.setPadding(new Insets(15));
        blocProduit.setStyle(
                "-fx-background-color: rgba(255,255,255,0.05);" +
                "-fx-background-radius: 18;" +
                "-fx-border-color: rgba(255,255,255,0.08);" +
                "-fx-border-radius: 18;"
        );
        blocProduit.setUserData(prixUnitaire);

        Label lblNom = new Label(prod.nom);
        lblNom.setFont(Font.font("System", FontWeight.BOLD, 16));
        lblNom.setTextFill(Color.WHITE);

        HBox bottomActions = new HBox(12);
        bottomActions.setAlignment(Pos.CENTER_LEFT);

        Label lblQt = new Label("1");
        lblQt.setFont(Font.font("System", FontWeight.BOLD, 18));
        lblQt.setTextFill(Color.web(GOLD_COLOR));

        Button btnMoins = createCartControlBtn("-", "rgba(255,255,255,0.12)");
        btnMoins.setOnAction(e -> playClickAnimation(btnMoins, () -> {
            int qt = Integer.parseInt(lblQt.getText());
            if (qt > 1) {
                lblQt.setText(String.valueOf(qt - 1));
                totalCommande -= prixUnitaire;
                lblTotal.setText(String.format("%.2f DA", totalCommande));
            }
        }));

        Button btnPlus = createCartControlBtn("+", "rgba(255,255,255,0.12)");
        btnPlus.setOnAction(e -> playClickAnimation(btnPlus, () -> {
            int qt = Integer.parseInt(lblQt.getText());
            lblQt.setText(String.valueOf(qt + 1));
            totalCommande += prixUnitaire;
            lblTotal.setText(String.format("%.2f DA", totalCommande));
        }));

        Button btnNote = createCartControlBtn("📝", "#2980b9");
        btnNote.setOnAction(e -> playClickAnimation(btnNote, () ->
                showModernInputOverlay("NOTE SPÉCIALE", "Instruction pour : " + prod.nom, note -> {
                    if (note != null && !note.isBlank()) {
                        lblNom.setText(prod.nom + "\n* " + note);
                        lblNom.setTextFill(Color.web(GOLD_COLOR));
                    }
                })
        ));

        Button btnSuppr = createCartControlBtn("🗑", "#c0392b");
        btnSuppr.setOnAction(e -> playClickAnimation(btnSuppr, () -> {
            int qt = Integer.parseInt(lblQt.getText());
            totalCommande -= (prixUnitaire * qt);
            itemsList.getChildren().remove(blocProduit);
            lblTotal.setText(String.format("%.2f DA", totalCommande));
        }));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bottomActions.getChildren().addAll(btnMoins, lblQt, btnPlus, spacer, btnNote, btnSuppr);
        blocProduit.getChildren().addAll(lblNom, bottomActions);
        itemsList.getChildren().add(blocProduit);

        totalCommande += prixUnitaire;
        lblTotal.setText(String.format("%.2f DA", totalCommande));
    }

    private void validerCommandeAsync() {
        String numBon = String.valueOf((int) (System.currentTimeMillis() % 1000));
        String contentClient = buildTicketClient(numBon);
        String contentCuisine = buildTicketCuisine(numBon);

        StackPane loading = new StackPane(new Label("IMPRESSION..."));
        ((Label) loading.getChildren().get(0)).setTextFill(Color.WHITE);
        loading.setStyle("-fx-background-color: rgba(0,0,0,0.75);");
        root.getChildren().add(loading);

        Task<Void> t = new Task<>() {
            @Override
            protected Void call() {
                PrinterService.printText("printer_name_client", contentClient);
                PrinterService.openCashDrawer();
                PrinterService.printText("printer_name_cuisine", contentCuisine);
                DatabaseHandler.enregistrerCommande(tableSelectionnee, totalCommande, contentClient);
                return null;
            }
        };

        t.setOnSucceeded(ev -> {
            root.getChildren().remove(loading);
            showModernTicketOverlay(numBon, contentClient, contentCuisine);
            loadCategoriesAsync(null);
        });

        t.setOnFailed(ev -> {
            root.getChildren().remove(loading);
            if (t.getException() != null) t.getException().printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Erreur impression / DB").show();
        });

        new Thread(t, "print-order").start();
    }

    private String buildTicketClient(String numBon) {
        StringBuilder tc = new StringBuilder();
        tc.append("      --- TICKET CLIENT ---\n");
        tc.append("        ").append(DatabaseHandler.getSetting("nom_resto", "TOMMY BURGER")).append("\n");
        tc.append("BON N° : ").append(numBon).append("\n");
        tc.append("MODE   : ").append(tableSelectionnee).append("\n");
        tc.append("-------------------------------\n");

        if (itemsList != null) {
            for (Node node : itemsList.getChildren()) {
                VBox vb = (VBox) node;
                Label n = (Label) vb.getChildren().get(0);
                Label q = (Label) ((HBox) vb.getChildren().get(1)).getChildren().get(1);
                double prixU = (double) vb.getUserData();

                String[] split = n.getText().split("\n\\* ");
                String nomProd = split[0];

                int qt = Integer.parseInt(q.getText());
                tc.append(String.format("%-15s x%-2d %8.2f\n", nomProd, qt, prixU * qt));
            }
        }

        tc.append("-------------------------------\n");
        tc.append(String.format("TOTAL : %.2f DA\n", totalCommande));
        tc.append("\n\n");
        return tc.toString();
    }

    private String buildTicketCuisine(String numBon) {
        StringBuilder tk = new StringBuilder();
        tk.append("      *** BON CUISINE ***\n");
        tk.append("COMMANDE N° : ").append(numBon).append("\n");
        tk.append("MODE : ").append(tableSelectionnee).append("\n");
        tk.append("-------------------------------\n");

        if (itemsList != null) {
            for (Node node : itemsList.getChildren()) {
                VBox vb = (VBox) node;
                Label n = (Label) vb.getChildren().get(0);
                Label q = (Label) ((HBox) vb.getChildren().get(1)).getChildren().get(1);

                String[] split = n.getText().split("\n\\* ");
                String nomProd = split[0];
                String noteProd = (split.length > 1) ? split[1] : "";

                tk.append(String.format("%-18s x%s\n", nomProd.toUpperCase(), q.getText()));
                if (!noteProd.isBlank()) {
                    tk.append(" (!) NOTE: ").append(noteProd.toUpperCase()).append("\n");
                }
            }
        }

        tk.append("-------------------------------\n\n\n");
        return tk.toString();
    }

    private void ouvrirTiroirCaisseAsync() {
        Task<Void> t = new Task<>() {
            @Override
            protected Void call() {
                PrinterService.openCashDrawer();
                return null;
            }
        };
        new Thread(t, "drawer").start();
    }

    private void showModernTicketOverlay(String num, String contentClient, String contentCuisine) {
        HBox dualBox = new HBox(30);
        dualBox.setAlignment(Pos.CENTER);
        dualBox.getChildren().addAll(
                createTicketView("TICKET CLIENT", contentClient),
                createTicketView("BON CUISINE", contentCuisine)
        );

        Button done = new Button("RETOUR À L'ACCUEIL");
        done.setStyle(
                "-fx-background-color: #27ae60;" +
                "-fx-text-fill: white;" +
                "-fx-font-weight: bold;" +
                "-fx-pref-width: 400;" +
                "-fx-pref-height: 60;" +
                "-fx-background-radius: 20;" +
                "-fx-cursor: hand;"
        );

        VBox mainV = new VBox(30, dualBox, done);
        mainV.setAlignment(Pos.CENTER);

        StackPane glass = new StackPane(mainV);
        glass.setStyle("-fx-background-color: rgba(0,0,0,0.9);");
        root.getChildren().add(glass);

        done.setOnAction(e -> playClickAnimation(done, () -> {
            root.getChildren().remove(glass);
            totalCommande = 0;
            if (itemsList != null) itemsList.getChildren().clear();
            if (lblTotal != null) lblTotal.setText("0.00 DA");
            showAccueil();
        }));
    }

    private VBox createTicketView(String title, String content) {
        VBox card = new VBox(14);
        card.setAlignment(Pos.TOP_LEFT);
        card.setPrefWidth(420);
        card.setMaxWidth(420);

        card.setStyle(
                "-fx-background-color: rgba(255,255,255,0.96);" +
                "-fx-background-radius: 26;" +
                "-fx-padding: 18 18 16 18;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 30, 0.2, 0, 10);"
        );

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Label badge = new Label(title.equalsIgnoreCase("TICKET CLIENT") ? "CLIENT" : "CUISINE");
        badge.setStyle(
                "-fx-background-color: #111;" +
                "-fx-text-fill: " + GOLD_COLOR + ";" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 6 12;" +
                "-fx-background-radius: 999;"
        );

        Text t = new Text(title);
        t.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 22));
        t.setFill(Color.web("#111"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label tiny = new Label("THERMAL PREVIEW");
        tiny.setStyle("-fx-text-fill: #777; -fx-font-size: 11; -fx-font-weight: bold;");

        header.getChildren().addAll(badge, t, spacer, tiny);

        TextArea area = new TextArea(content);
        area.setEditable(false);
        area.setWrapText(false);
        area.setPrefHeight(520);
        area.setStyle(
                "-fx-control-inner-background: rgba(255,255,255,0.72);" +
                "-fx-background-color: transparent;" +
                "-fx-text-fill: #111;" +
                "-fx-font-family: 'Consolas';" +
                "-fx-font-size: 13;" +
                "-fx-background-radius: 16;" +
                "-fx-border-radius: 16;" +
                "-fx-border-color: rgba(0,0,0,0.08);" +
                "-fx-border-width: 1;"
        );

        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_LEFT);

        Label tip = new Label("✅ Vérifie puis retourne à l’accueil");
        tip.setStyle("-fx-text-fill: #444; -fx-font-size: 12; -fx-font-weight: bold;");

        footer.getChildren().add(tip);

        card.getChildren().addAll(header, new Separator(), area, footer);
        return card;
    }

    private void showModernInputOverlay(String title, String sub, java.util.function.Consumer<String> callback) {
        VBox overlayBox = new VBox(20);
        overlayBox.setAlignment(Pos.CENTER);
        overlayBox.setStyle(
                "-fx-background-color: " + DARK_GLASS + ";" +
                "-fx-background-radius: 30;" +
                "-fx-padding: 40;" +
                "-fx-border-color: " + GOLD_COLOR + ";" +
                "-fx-border-width: 2;"
        );
        overlayBox.setMaxSize(520, 320);

        Text t = createStyledText(title, 40);

        Label s = new Label(sub);
        s.setTextFill(Color.WHITE);

        TextField input = new TextField();
        input.setPromptText("Ex: Sans oignons, bien cuit...");
        input.setStyle("-fx-pref-height: 50; -fx-background-radius: 10;");

        HBox btns = new HBox(20);
        btns.setAlignment(Pos.CENTER);

        Button ok = new Button("VALIDER");
        ok.setStyle(
                "-fx-background-color: " + GOLD_COLOR + ";" +
                "-fx-font-weight: bold;" +
                "-fx-pref-width: 150;" +
                "-fx-pref-height: 45;" +
                "-fx-background-radius: 10;"
        );

        Button cancel = new Button("ANNULER");
        cancel.setStyle(
                "-fx-background-color: #e74c3c;" +
                "-fx-text-fill: white;" +
                "-fx-pref-width: 150;" +
                "-fx-pref-height: 45;" +
                "-fx-background-radius: 10;"
        );

        btns.getChildren().addAll(cancel, ok);
        overlayBox.getChildren().addAll(t, s, input, btns);

        StackPane glass = new StackPane(overlayBox);
        glass.setStyle("-fx-background-color: rgba(0,0,0,0.8);");
        root.getChildren().add(glass);

        ok.setOnAction(e -> {
            try {
                callback.accept(input.getText());
            } finally {
                root.getChildren().remove(glass);
            }
        });

        cancel.setOnAction(e -> root.getChildren().remove(glass));
    }

    private Button createCartControlBtn(String text, String color) {
        Button b = new Button(text);
        b.setStyle(
                "-fx-background-color: " + color + ";" +
                "-fx-text-fill: white;" +
                "-fx-font-weight: bold;" +
                "-fx-background-radius: 12;" +
                "-fx-min-width: 45;" +
                "-fx-min-height: 45;" +
                "-fx-cursor: hand;"
        );
        return b;
    }

    private Button createModernBackButton(String txt) {
        Button b = new Button(txt);
        b.setStyle(
                "-fx-background-color: rgba(0,0,0,0.55);" +
                "-fx-text-fill: " + GOLD_COLOR + ";" +
                "-fx-border-color: rgba(255,215,0,0.55);" +
                "-fx-border-radius: 30;" +
                "-fx-background-radius: 30;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 10 22;" +
                "-fx-cursor: hand;"
        );
        return b;
    }

    private Button createPremiumRoundTable(int num) {
        StackPane container = new StackPane();

        Circle table = new Circle(50);
        table.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#2c3e50")),
                new Stop(1, Color.web("#000000"))));
        table.setStroke(Color.web("#ffffff", 0.2));
        table.setStrokeWidth(2.5);

        Text txt = new Text(String.valueOf(num));
        txt.setFont(Font.font("Impact", FontWeight.BOLD, 32));
        txt.setFill(Color.WHITE);

        container.getChildren().addAll(table, txt);

        Button btn = new Button();
        btn.setGraphic(container);
        btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
        return btn;
    }

    private Button createPremiumMiniButton(String name, String imgPath) {
        VBox b = new VBox(10);
        b.setAlignment(Pos.CENTER);

        ImageView iv = new ImageView(loadImage(imgPath, 100, 78));
        iv.setFitWidth(100);
        iv.setFitHeight(78);
        iv.setPreserveRatio(true);

        Text t = new Text(name);
        t.setFont(Font.font("Impact", 18));
        t.setFill(Color.WHITE);

        b.getChildren().addAll(iv, t);

        Button btn = new Button();
        btn.setGraphic(b);
        btn.setPrefSize(170, 145);
        btn.setStyle(
                "-fx-background-color: rgba(10,10,10,0.78);" +
                "-fx-background-radius: 22;" +
                "-fx-cursor: hand;"
        );
        return btn;
    }

    private Button createProductCard(AdminInterface.Produit p) {
        VBox v = new VBox(10);
        v.setAlignment(Pos.CENTER);

        ImageView iv = new ImageView(loadImage(p.img, 95, 72));
        iv.setFitWidth(95);
        iv.setFitHeight(72);
        iv.setPreserveRatio(true);

        Text n = new Text(p.nom);
        n.setFill(Color.WHITE);
        n.setFont(Font.font("System", FontWeight.BOLD, 17));

        Text pr = new Text(String.format("%.2f DA", p.prix));
        pr.setFill(Color.web(GOLD_COLOR));
        pr.setFont(Font.font(18));

        v.getChildren().addAll(iv, n, pr);

        Button b = new Button();
        b.setGraphic(v);
        b.setPrefSize(200, 165);
        b.setStyle(
                "-fx-background-color: rgba(255,255,255,0.08);" +
                "-fx-background-radius: 18;" +
                "-fx-cursor: hand;"
        );
        return b;
    }

    private void updateMainContent(Node content) {
        if (root.getChildren().size() > 2) {
            root.getChildren().remove(2, root.getChildren().size());
        }

        root.getChildren().addAll(content, createTopBar());

        FadeTransition ft = new FadeTransition(Duration.millis(450), content);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();
    }

    private AnchorPane createTopBar() {
        AnchorPane topBar = new AnchorPane();
        topBar.setPickOnBounds(false);

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(0));

        Button minBtn = new Button("—");
        minBtn.setPrefSize(60, 42);
        minBtn.setStyle(
                "-fx-background-color: #f4b400;" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 18;" +
                "-fx-font-weight: bold;" +
                "-fx-background-radius: 0 0 0 12;" +
                "-fx-cursor: hand;"
        );
        minBtn.setOnAction(e -> {
            if (primaryStage != null) {
                primaryStage.setIconified(true);
            }
        });

        Button closeBtn = new Button("✕");
        closeBtn.setPrefSize(60, 42);
        closeBtn.setStyle(
                "-fx-background-color: #ff5f56;" +
                "-fx-text-fill: white;" +
                "-fx-font-weight: bold;" +
                "-fx-background-radius: 0 0 0 12;" +
                "-fx-cursor: hand;"
        );
        closeBtn.setOnAction(e -> Platform.exit());

        actions.getChildren().addAll(minBtn, closeBtn);

        AnchorPane.setTopAnchor(actions, 0.0);
        AnchorPane.setRightAnchor(actions, 0.0);

        topBar.getChildren().add(actions);
        return topBar;
    }

    private Text createStyledText(String string, double size) {
        Text text = new Text(string);
        text.setFont(Font.font("Impact", size));
        text.setFill(Color.WHITE);
        text.setStroke(Color.web(GOLD_COLOR));
        text.setStrokeWidth(size / 55);
        return text;
    }

    private Button createTommyButton(String icon, String titleStr, String subStr) {
        VBox box = new VBox(15);
        box.setAlignment(Pos.CENTER);

        Label lblIcon = new Label(icon);
        lblIcon.setFont(Font.font(80));
        lblIcon.setTextFill(Color.WHITE);

        Text txtTitle = createStyledText(titleStr, 45);

        Label lblSub = new Label(subStr);
        lblSub.setFont(Font.font("System", FontWeight.BOLD, 14));
        lblSub.setTextFill(Color.web("#b0b0b0"));

        box.getChildren().addAll(lblIcon, txtTitle, lblSub);

        Button btn = new Button();
        btn.setGraphic(box);
        btn.setPrefSize(400, 300);
        btn.setStyle(
                "-fx-background-color: rgba(0,0,0,0.72);" +
                "-fx-background-radius: 36;" +
                "-fx-cursor: hand;"
        );
        return btn;
    }

    private void makeScrollPaneTransparent(ScrollPane scrollPane) {
        scrollPane.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-background: transparent;" +
                "-fx-border-color: transparent;" +
                "-fx-padding: 0;"
        );

        scrollPane.skinProperty().addListener((obs, oldSkin, newSkin) -> Platform.runLater(() -> {
            Node viewport = scrollPane.lookup(".viewport");
            if (viewport != null) {
                viewport.setStyle("-fx-background-color: transparent;");
            }

            Node vBar = scrollPane.lookup(".scroll-bar:vertical");
            if (vBar != null) {
                vBar.setStyle("-fx-opacity: 0;");
                vBar.setManaged(false);
                vBar.setVisible(false);
            }

            Node hBar = scrollPane.lookup(".scroll-bar:horizontal");
            if (hBar != null) {
                hBar.setStyle("-fx-opacity: 0;");
                hBar.setManaged(false);
                hBar.setVisible(false);
            }
        }));

        Platform.runLater(() -> {
            Node viewport = scrollPane.lookup(".viewport");
            if (viewport != null) {
                viewport.setStyle("-fx-background-color: transparent;");
            }

            Node vBar = scrollPane.lookup(".scroll-bar:vertical");
            if (vBar != null) {
                vBar.setStyle("-fx-opacity: 0;");
                vBar.setManaged(false);
                vBar.setVisible(false);
            }

            Node hBar = scrollPane.lookup(".scroll-bar:horizontal");
            if (hBar != null) {
                hBar.setStyle("-fx-opacity: 0;");
                hBar.setManaged(false);
                hBar.setVisible(false);
            }
        });
    }

    private Image loadImage(String path, double w, double h) {
        String key = (path == null ? "" : path) + "|" + w + "x" + h;
        if (imageCache.containsKey(key)) return imageCache.get(key);

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

    public static void main(String[] args) {
        launch(args);
    }
}