package com.nagimutech.crimecarcoinchase;

final class GameConfig {
    static final int APP_VERSION_CODE = 30;
    static final String APP_VERSION = "2.5.0";
    static final String UPDATE_CHECK_URL = "http://217.114.11.79:8080/android-version";
    static final String MULTIPLAYER_WS_URL = "ws://217.114.11.79:8080/game";
    static final String PROF_REGISTER_URL = "http://217.114.11.79:8080/prof/register";
    static final String PROF_STATUS_URL = "http://217.114.11.79:8080/prof/status";
    static final String PROF_SET_TEST_URL = "http://217.114.11.79:8080/prof/set-test";
    static final int STORAGE_SCHEMA = 1;
    static final String PREFS = "pogonya_za_monetami";
    static final String PREF_WINS = "wins_v1";
    static final String PREF_TOTAL_WEALTH = "total_wealth_v2";
    static final String PREF_AWARDS = "awards_v1";
    static final String PREF_EARLY_GAMES = "early_games_v1";
    static final String PREF_LATE_GAMES = "late_games_v1";
    static final String PREF_BANKNOTES = "banknotes_v1";
    static final String PREF_OWNED_SHOP_ITEMS = "owned_shop_items_v1";
    static final String PREF_SELECTED_BACKGROUND = "selected_background_v1";
    static final String PREF_SELECTED_WALL = "selected_wall_v1";
    static final String PREF_SELECTED_PLAYER = "selected_player_v1";
    static final String PREF_SELECTED_POLICE = "selected_police_v1";
    static final String PREF_PROF_INSTALL_ID = "prof_install_id_v1";
    static final String PREF_PROF_ENABLED = "prof_enabled_v1";
    static final String PREF_AVATAR = "avatar_v1";
    static final String PREF_SCHEMA = "schema";

    static final int MAP_WIDTH = 17;
    static final int MAP_HEIGHT = 31;
    static final int MAX_DAMAGE = 5;
    static final int HISTORY_LIMIT = 10;

    private GameConfig() {
    }
}
