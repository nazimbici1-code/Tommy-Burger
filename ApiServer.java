package FastFood;

import io.javalin.Javalin;
import io.javalin.plugin.bundled.CorsPluginConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ApiServer {
    private static final Logger logger = LoggerFactory.getLogger(ApiServer.class);
    
    // CORRECTION : On lit le port de Railway, sinon on utilise 8080 (Local)
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

    public static void start() {
        // Configuration de Javalin avec support CORS complet pour Flutter
        Javalin app = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(rule -> {
                    rule.anyHost(); // Autorise toutes les origines
                });
            });
        }).start("0.0.0.0", PORT); // ÉCOUTE SUR TOUT LE RÉSEAU

        // ================= ROUTE DE TEST =================
        app.get("/", ctx -> {
            ctx.json(Map.of(
                    "success", true,
                    "message", "API Tommy Burger en ligne",
                    "status", "Serveur accessible sur Railway/Réseau"
            ));
        });

        // ================= LOGIN ADMIN =================
        app.post("/login", ctx -> {
            try {
                Map<String, Object> body = ctx.bodyAsClass(Map.class);

                String user = body.get("username") != null ? body.get("username").toString().trim() : "";
                String pass = body.get("password") != null ? body.get("password").toString() : "";

                if (user.isEmpty() || pass.isEmpty()) {
                    ctx.status(400).json(Map.of(
                            "success", false,
                            "message", "Nom d'utilisateur ou mot de passe manquant"
                    ));
                    return;
                }

                boolean isValid = DatabaseHandler.checkLogin(user, pass, "Admin");

                if (isValid) {
                    logger.info("✅ Connexion réussie pour : {}", user);
                    ctx.status(200).json(Map.of(
                            "success", true,
                            "message", "Bienvenue " + user,
                            "role", "Admin"
                    ));
                } else {
                    logger.warn("⚠️ Tentative de connexion échouée pour : {}", user);
                    ctx.status(401).json(Map.of(
                            "success", false,
                            "message", "Identifiants incorrects"
                    ));
                }

            } catch (Exception e) {
                logger.error("❌ Erreur lors du login", e);
                ctx.status(500).json(Map.of(
                        "success", false,
                        "message", "Erreur serveur ou base de données"
                ));
            }
        });

        // ================= ETAT DU SERVEUR =================
        app.get("/client/status", ctx -> {
            try (var conn = DatabaseHandler.connect()) {
                boolean dbActive = conn != null && !conn.isClosed();

                ctx.status(200).json(Map.of(
                        "online", true,
                        "database", dbActive
                ));
            } catch (Exception e) {
                logger.error("Erreur status", e);
                ctx.status(200).json(Map.of(
                        "online", true,
                        "database", false
                ));
            }
        });

        // ================= MENU (GET) =================
        app.get("/menu", ctx -> {
            try {
                ctx.status(200).json(DatabaseHandler.getMenu());
            } catch (Exception e) {
                logger.error("Erreur récupération menu", e);
                ctx.status(500).json(Map.of(
                        "success", false,
                        "message", "Erreur lors de la récupération du menu"
                ));
            }
        });

        // ================= AJOUTER PRODUIT (POST) =================
        app.post("/menu", ctx -> {
            try {
                Map<String, Object> body = ctx.bodyAsClass(Map.class);
                String nom = body.get("nom").toString();
                double prix = Double.parseDouble(body.get("prix").toString());
                String categorie = body.get("categorie").toString();

                boolean success = DatabaseHandler.addProduit(nom, prix, categorie);
                if (success) {
                    ctx.status(201).json(Map.of("success", true, "message", "Produit ajouté"));
                } else {
                    ctx.status(500).json(Map.of("success", false, "message", "Erreur insertion BDD"));
                }
            } catch (Exception e) {
                ctx.status(400).json(Map.of("success", false, "message", "Données invalides"));
            }
        });

        // ================= SUPPRIMER PRODUIT (DELETE) =================
        app.delete("/menu/{id}", ctx -> {
            try {
                int id = Integer.parseInt(ctx.pathParam("id"));
                boolean success = DatabaseHandler.deleteProduit(id);
                if (success) {
                    ctx.status(200).json(Map.of("success", true, "message", "Produit supprimé"));
                } else {
                    ctx.status(404).json(Map.of("success", false, "message", "Produit non trouvé"));
                }
            } catch (Exception e) {
                ctx.status(400).json(Map.of("success", false, "message", "ID invalide"));
            }
        });

        // ================= STATS JOUR =================
        app.get("/stats/jour", ctx -> {
            try {
                double total = DatabaseHandler.getStatsPeriode("Jour");
                int nbCommandes = DatabaseHandler.getNombreCommandesPeriode("Jour");

                ctx.status(200).json(Map.of(
                        "success", true,
                        "periode", "Jour",
                        "chiffre_affaires", total,
                        "nombre_commandes", nbCommandes
                ));
            } catch (Exception e) {
                logger.error("Erreur stats jour", e);
                ctx.status(500).json(Map.of("success", false, "message", "Erreur stats jour"));
            }
        });

        // ================= STATS SEMAINE =================
        app.get("/stats/semaine", ctx -> {
            try {
                double total = DatabaseHandler.getStatsPeriode("Semaine");
                int nbCommandes = DatabaseHandler.getNombreCommandesPeriode("Semaine");
                ctx.status(200).json(Map.of(
                        "success", true,
                        "periode", "Semaine",
                        "chiffre_affaires", total,
                        "nombre_commandes", nbCommandes
                ));
            } catch (Exception e) {
                ctx.status(500).json(Map.of("success", false, "message", "Erreur stats semaine"));
            }
        });

        // ================= ANALYSE HEBDOMADAIRE =================
        app.get("/stats/analyse-semaine", ctx -> {
            try {
                double semaineActuelle = DatabaseHandler.getStatsPeriode("Semaine");
                double semainePrecedente = DatabaseHandler.getStatsPeriode("SemainePrecedente");
                int commandesActuelles = DatabaseHandler.getNombreCommandesPeriode("Semaine");
                int commandesPrecedentes = DatabaseHandler.getNombreCommandesPeriode("SemainePrecedente");

                double variation = 0.0;
                if (semainePrecedente > 0) {
                    variation = ((semaineActuelle - semainePrecedente) / semainePrecedente) * 100.0;
                }
                variation = Math.round(variation * 100.0) / 100.0;

                String analyse = variation >= 0 ? "📈 Tendance positive" : "📉 Tendance en baisse";

                ctx.status(200).json(Map.of(
                        "success", true,
                        "variation", variation,
                        "analyse", analyse,
                        "semaine_actuelle", semaineActuelle,
                        "commandes_semaine_actuelle", commandesActuelles
                ));

            } catch (Exception e) {
                ctx.status(500).json(Map.of("success", false, "message", "Erreur analyse"));
            }
        });

        // ================= LOGS =================
        app.get("/logs", ctx -> {
            try {
                ctx.status(200).json(DatabaseHandler.getLogs());
            } catch (Exception e) {
                ctx.status(500).json(Map.of("success", false, "message", "Erreur logs"));
            }
        });

        logger.info("🚀 Serveur Tommy Burger prêt sur le port {}", PORT);
    }

    public static void main(String[] args) {
        try {
            DatabaseHandler.setupDatabase();
            start();
        } catch (Exception e) {
            logger.error("Impossible de démarrer le serveur", e);
        }
    }
}