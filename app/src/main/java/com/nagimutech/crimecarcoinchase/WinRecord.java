package com.nagimutech.crimecarcoinchase;

final class WinRecord {
    final int rating;
    final String difficulty;
    final int seconds;
    final int damage;

    WinRecord(int rating, String difficulty, int seconds, int damage) {
        this.rating = rating;
        this.difficulty = difficulty;
        this.seconds = seconds;
        this.damage = damage;
    }

    String encode() {
        return rating + "|" + difficulty + "|" + seconds + "|" + damage;
    }

    static WinRecord decode(String raw) {
        String[] parts = raw.split("\\|");
        if (parts.length != 4) {
            return null;
        }
        try {
            return new WinRecord(
                    Integer.parseInt(parts[0]),
                    parts[1],
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])
            );
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
