package com.nagimutech.crimecarcoinchase;

final class GameConfig {
    static final String APP_VERSION = "2.1.2";
    static final int STORAGE_SCHEMA = 1;
    static final String PREFS = "pogonya_za_monetami";
    static final String PREF_WINS = "wins_v1";
    static final String PREF_TOTAL_WEALTH = "total_wealth_v2";
    static final String PREF_AWARDS = "awards_v1";
    static final String PREF_EARLY_GAMES = "early_games_v1";
    static final String PREF_LATE_GAMES = "late_games_v1";
    static final String PREF_SCHEMA = "schema";

    static final int MAP_WIDTH = 17;
    static final int MAP_HEIGHT = 29;
    static final int MAX_DAMAGE = 5;
    static final int HISTORY_LIMIT = 10;

    private GameConfig() {
    }
}
