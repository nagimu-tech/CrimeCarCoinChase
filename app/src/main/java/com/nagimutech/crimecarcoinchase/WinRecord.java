package com.nagimutech.crimecarcoinchase;

final class WinRecord {
    final int rating;
    final String difficulty;
    final String difficultyKey;
    final int seconds;
    final int damage;
    final int wealth;

    WinRecord(int rating, String difficulty, String difficultyKey, int seconds, int damage, int wealth) {
        this.rating = rating;
        this.difficulty = difficulty;
        this.difficultyKey = difficultyKey;
        this.seconds = seconds;
        this.damage = damage;
        this.wealth = wealth;
    }

    String encode() {
        return rating + "|" + difficulty + "|" + difficultyKey + "|" + seconds + "|" + damage + "|" + wealth;
    }

    boolean betterThan(WinRecord other) {
        if (wealth != other.wealth) {
            return wealth > other.wealth;
        }
        if (seconds != other.seconds) {
            return seconds < other.seconds;
        }
        return damage < other.damage;
    }

    static WinRecord decode(String raw) {
        String[] parts = raw.split("\\|");
        try {
            if (parts.length == 6) {
                return new WinRecord(
                        Integer.parseInt(parts[0]),
                        parts[1],
                        parts[2],
                        Integer.parseInt(parts[3]),
                        Integer.parseInt(parts[4]),
                        Integer.parseInt(parts[5])
                );
            }
            if (parts.length == 4) {
                int rating = Integer.parseInt(parts[0]);
                return new WinRecord(rating, parts[1], parts[1], Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), rating);
            }
        } catch (NumberFormatException error) {
            return null;
        }
        return null;
    }
}
