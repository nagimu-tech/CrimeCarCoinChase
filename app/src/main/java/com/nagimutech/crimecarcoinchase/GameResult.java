package com.nagimutech.crimecarcoinchase;

final class GameResult {
    final Difficulty difficulty;
    final int wealth;
    final int totalWealth;
    final int seconds;
    final int damage;
    final long startedAt;
    final boolean fastField;
    final boolean won;

    GameResult(Difficulty difficulty, int wealth, int totalWealth, int seconds, int damage, long startedAt, boolean fastField, boolean won) {
        this.difficulty = difficulty;
        this.wealth = wealth;
        this.totalWealth = totalWealth;
        this.seconds = seconds;
        this.damage = damage;
        this.startedAt = startedAt;
        this.fastField = fastField;
        this.won = won;
    }
}
