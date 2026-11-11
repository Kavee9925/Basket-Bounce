package com.basketbounce.storage;

import com.mongodb.MongoClientSettings;
import com.mongodb.ConnectionString;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

/** MongoDB persistence and authentication for Basket Bounce. */
public final class MongoDBService implements AutoCloseable {
    public static final String CONNECTION_URL = "mongodb://localhost:27017";
    public static final String DATABASE_NAME = "basketbounce";

    private static final MongoDBService INSTANCE = new MongoDBService();

    private MongoClient client;
    private MongoCollection<Document> users;
    private MongoCollection<Document> profiles;
    private MongoCollection<Document> gameRuns;

    public record UserAccount(String id, String fullName, String username, String email) { }
    public record PlayerStats(int highestScore, int highestLevel, int totalGames, long totalScore) { }

    private MongoDBService() { }

    public static MongoDBService getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize() {
        if (client != null) return;

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(CONNECTION_URL))
                .applyToClusterSettings(builder -> builder.serverSelectionTimeout(5, TimeUnit.SECONDS))
                .build();
        MongoClient newClient = MongoClients.create(settings);
        try {
            MongoDatabase newDatabase = newClient.getDatabase(DATABASE_NAME);
            for (String collection : newDatabase.listCollectionNames()) {
                // Listing forces an initial connection before the service is marked ready.
            }
            createCollectionIfMissing(newDatabase, "users");
            createCollectionIfMissing(newDatabase, "player_profiles");
            createCollectionIfMissing(newDatabase, "game_runs");

            MongoCollection<Document> newUsers = newDatabase.getCollection("users");
            MongoCollection<Document> newProfiles = newDatabase.getCollection("player_profiles");
            MongoCollection<Document> newGameRuns = newDatabase.getCollection("game_runs");

            newUsers.createIndex(Indexes.ascending("usernameLower"), new IndexOptions().unique(true));
            newUsers.createIndex(Indexes.ascending("emailLower"), new IndexOptions().unique(true));
            newProfiles.createIndex(Indexes.ascending("userId"), new IndexOptions().unique(true));
            newGameRuns.createIndex(Indexes.ascending("userId", "createdAt"));

            client = newClient;
            users = newUsers;
            profiles = newProfiles;
            gameRuns = newGameRuns;
        } catch (RuntimeException exception) {
            newClient.close();
            throw exception;
        }
    }

    private void createCollectionIfMissing(MongoDatabase target, String name) {
        boolean exists = false;
        for (String existing : target.listCollectionNames()) {
            if (existing.equals(name)) {
                exists = true;
                break;
            }
        }
        if (!exists) target.createCollection(name);
    }

    public synchronized UserAccount createAccount(
            String fullName, String username, String email, char[] password
    ) {
        initialize();
        String cleanName = fullName.trim();
        String cleanUsername = username.trim();
        String cleanEmail = email.trim().toLowerCase(Locale.ROOT);
        String usernameLower = cleanUsername.toLowerCase(Locale.ROOT);

        if (users.find(or(eq("usernameLower", usernameLower), eq("emailLower", cleanEmail))).first() != null) {
            throw new IllegalArgumentException("That username or email address is already registered.");
        }

        String passwordHash = BCrypt.hashpw(new String(password), BCrypt.gensalt(12));
        Document user = new Document("fullName", cleanName)
                .append("username", cleanUsername)
                .append("usernameLower", usernameLower)
                .append("email", cleanEmail)
                .append("emailLower", cleanEmail)
                .append("passwordHash", passwordHash)
                .append("createdAt", java.time.Instant.now());

        try {
            users.insertOne(user);
        } catch (MongoException exception) {
            if (exception.getCode() == 11000) {
                throw new IllegalArgumentException("That username or email address is already registered.");
            }
            throw exception;
        }

        String userId = user.getObjectId("_id").toHexString();
        profiles.insertOne(new Document("userId", userId)
                .append("fullName", cleanName)
                .append("username", cleanUsername)
                .append("highestScore", 0)
                .append("highestLevel", 1)
                .append("totalGames", 0)
                .append("totalScore", 0L)
                .append("createdAt", java.time.Instant.now()));
        return new UserAccount(userId, cleanName, cleanUsername, cleanEmail);
    }

    public synchronized UserAccount login(String usernameOrEmail, char[] password) {
        initialize();
        String loginValue = usernameOrEmail.trim().toLowerCase(Locale.ROOT);
        Document user = users.find(or(eq("usernameLower", loginValue), eq("emailLower", loginValue))).first();
        if (user == null || !BCrypt.checkpw(new String(password), user.getString("passwordHash"))) {
            throw new IllegalArgumentException("Incorrect username/email or password.");
        }
        users.updateOne(eq("_id", user.getObjectId("_id")), set("lastLoginAt", java.time.Instant.now()));
        return new UserAccount(
                user.getObjectId("_id").toHexString(),
                user.getString("fullName"),
                user.getString("username"),
                user.getString("email")
        );
    }

    public synchronized void saveGame(UserAccount account, int score, int level) {
        initialize();
        java.time.Instant now = java.time.Instant.now();
        gameRuns.insertOne(new Document("userId", account.id())
                .append("username", account.username())
                .append("score", score)
                .append("level", level)
                .append("playedOn", LocalDate.now().toString())
                .append("createdAt", now));
        profiles.updateOne(eq("userId", account.id()), combine(
                inc("totalGames", 1),
                inc("totalScore", score),
                max("highestScore", score),
                max("highestLevel", level),
                set("updatedAt", now)
        ), new UpdateOptions().upsert(true));
    }

    public synchronized PlayerStats getPlayerStats(UserAccount account) {
        initialize();
        Document profile = profiles.find(eq("userId", account.id())).first();
        if (profile == null) return new PlayerStats(0, 1, 0, 0);
        Object storedTotalScore = profile.get("totalScore");
        long totalScore = storedTotalScore instanceof Number number ? number.longValue() : 0L;
        return new PlayerStats(
                profile.getInteger("highestScore", 0),
                profile.getInteger("highestLevel", 1),
                profile.getInteger("totalGames", 0),
                totalScore
        );
    }

    @Override
    public synchronized void close() {
        if (client != null) {
            client.close();
            client = null;
            users = null;
            profiles = null;
            gameRuns = null;
        }
    }
}
