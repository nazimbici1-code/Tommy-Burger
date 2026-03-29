package FastFood;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

public class ApiServer {
    private static final Logger logger = LoggerFactory.getLogger(ApiServer.class);
    
    // On lit le port de Railway via la variable d'environnement, sinon 8080 en local
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

    public static void start() {
        // Configuration de Javalin avec support CORS pour Flutter
        Javalin app = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(rule -> {
                    rule.anyHost(); // Autorise toutes les origines pour le dashboard Flutter
                });
            });
        });

        // CORRECTION MAJEURE : Railway nécessite uniquement le port
        app.start(PORT); 

        // ================= ROUTE DE TEST =================
        app.get("/", ctx -> {
            ctx.json(Map.of(
                    "success", true,
                    "message", "API Tommy Burger en ligne",
                    "status", "Serveur opérationnel"
            ));
        });

        // ================= LOGIN ADMIN =================
        app.post("/login", ctx -> {
            try {
                Map<String, Object> body = ctx.bodyAsClass(Map.class);
                String user = body.get("username") != null ? body.get("username").toString().trim() : "";
                String pass = body.get("password") != null ? body.get("password").toString() : "";

                if (user.isEmpty() || pass.isEmpty()) {
                    ctx.status(400).json(Map.of("success", false, "message", "Identifiants manquants"));
                    return;
                }

                boolean isValid = DatabaseHandler.checkLogin(user, pass, "Admin");

                if (isValid) {
                    logger.info("✅ Connexion réussie : {}", user);
                    ctx.status(200).json(Map.of("success", true, "message", "Bienvenue", "role", "Admin"));
                } else {
                    ctx.status(401).json(Map.of("success", false, "message", "Identifiants incorrects"));
                }
            } catch (Exception e) {
                logger.error("Erreur login", e);
                ctx.status(500).json(Map.of("success", false, "message", "Erreur serveur"));
            }
        });

        // ================= ETAT DU SERVEUR & BDD =================
        app.get("/client/status", ctx -> {
            try (var conn = DatabaseHandler.connect()) {
                boolean dbActive = conn != null && !conn.isClosed();
                ctx.json(Map.of("online", true, "database", dbActive));
            } catch (Exception e) {
                ctx.json(Map.of("online", true, "database", false));
            }
        });

        // ================= MENU (GET) =================
        app.get("/menu", ctx -> {
            try {
                ctx.json(DatabaseHandler.getMenu());
            } catch (Exception e) {
                ctx.status(500).json(Map.of("success", false, "message", "Erreur menu"));
            }
        });

        // ================= AJOUTER PRODUIT =================
        app.post("/menu", ctx -> {
            try {
                Map<String, Object> body = ctx.bodyAsClass(Map.class);
                String nom = body.get("nom").toString();
                double prix = Double.parseDouble(body.get("prix").toString());
                String categorie = body.get("categorie").toString();

                if (DatabaseHandler.addProduit(nom, prix, categorie)) {
                    ctx.status(201).json(Map.of("success", true));
                } else {
                    ctx.status(500).json(Map.of("success", false));
                }
            } catch (Exception e) {
                ctx.status(400).json(Map.of("success", false));
            }
        });

        // ================= SUPPRIMER PRODUIT =================
        app.delete("/menu/{id}", ctx -> {
            try {
                int id = Integer.parseInt(ctx.pathParam("id"));
                if (DatabaseHandler.deleteProduit(id)) {
                    ctx.status(200).json(Map.of("success", true));
                } else {
                    ctx.status(404).json(Map.of("success", false));
                }
            } catch (Exception e) {
                ctx.status(400).json(Map.of("success", false));
            }
        });

        // ================= STATS & ANALYSE =================
        app.get("/stats/jour", ctx -> {
            try {
                ctx.json(Map.of(
                    "success", true,
                    "chiffre_affaires", DatabaseHandler.getStatsPeriode("Jour"),
                    "nombre_commandes", DatabaseHandler.getNombreCommandesPeriode("Jour")
                ));
            } catch (Exception e) { ctx.status(500); }
        });

        app.get("/stats/analyse-semaine", ctx -> {
            try {
                double actuelle = DatabaseHandler.getStatsPeriode("Semaine");
                double precedente = DatabaseHandler.getStatsPeriode("SemainePrecedente");
                double variation = (precedente > 0) ? ((actuelle - precedente) / precedente) * 100 : 0;
                
                ctx.json(Map.of(
                    "success", true,
                    "variation", Math.round(variation * 100.0) / 100.0,
                    "semaine_actuelle", actuelle,
                    "analyse", variation >= 0 ? "📈 Tendance positive" : "📉 Tendance en baisse"
                ));
            } catch (Exception e) { ctx.status(500); }
        });

        // ================= LOGS =================
        app.get("/logs", ctx -> {
            try {
                ctx.json(DatabaseHandler.getLogs());
            } catch (Exception e) { ctx.status(500); }
        });

        logger.info("🚀 Serveur prêt sur le port {}", PORT);
    }

    public static void main(String[] args) {
        try {
            // Initialisation de la BDD avant le lancement
            DatabaseHandler.setupDatabase();
            start();
        } catch (Exception e) {
            logger.error("Échec du démarrage", e);
        }
    }
}
