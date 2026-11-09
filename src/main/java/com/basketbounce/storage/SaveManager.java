package com.basketbounce.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class SaveManager {
    private final Path file = Path.of(System.getProperty("user.home"), ".basketbounce", "guest-data.json");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private GuestProfile profile;
    private List<Run> runs;

    public record Run(String player, int score, int level, String date) {
    }

    public SaveManager() {
        load();
    }

    private void load() {
        try {
            if (Files.exists(file)) {
                Data d = gson.fromJson(Files.readString(file), Data.class);
                profile = d.profile == null ? new GuestProfile() : d.profile;
                runs = d.runs == null ? new ArrayList<>() : d.runs;
            } else {
                profile = new GuestProfile();
                runs = new ArrayList<>();
            }
        } catch (Exception e) {
            profile = new GuestProfile();
            runs = new ArrayList<>();
        }
    }

    public synchronized void save(int score, int level) {
        profile.totalGames++;
        profile.totalScore += score;
        profile.highestScore = Math.max(profile.highestScore, score);
        profile.highestLevel = Math.max(profile.highestLevel, level);
        runs.add(new Run(profile.playerName, score, level, java.time.LocalDate.now().toString()));
        runs.sort(Comparator.comparingInt(Run::score).reversed());
        if (runs.size() > 50) runs = runs.subList(0, 50);
        persist();
    }

    private void persist() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(new Data(profile, runs)));
        } catch (IOException e) {
            System.err.println("Could not save guest profile: " + e.getMessage());
        }
    }

    public GuestProfile profile() {
        return profile;
    }

    public List<Run> leaderboard() {
        return List.copyOf(runs);
    }

    public void rename(String name) {
        if (name != null && !name.isBlank()) {
            profile.playerName = name.trim();
            persist();
        }
    }
    private static class Data {
        GuestProfile profile;
        List<Run> runs;
        Data(GuestProfile p, List<Run> r) {
            profile = p;
            runs = r;
        }
    }
}
