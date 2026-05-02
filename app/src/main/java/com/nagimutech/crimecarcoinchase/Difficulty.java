package com.nagimutech.crimecarcoinchase;

enum Difficulty {
    DEBUT("\u0414\u0435\u0431\u044e\u0442", 1, 1.1f, 0.8f),
    BEGINNER("\u041d\u0430\u0447\u0438\u043d\u0430\u044e\u0449\u0438\u0439", 1, 1.3f, 1.0f),
    AMATEUR("\u041b\u044e\u0431\u0438\u0442\u0435\u043b\u044c", 2, 1.44f, 1.35f),
    PRO("\u041f\u0440\u043e\u0444\u0435\u0441\u0441\u0438\u043e\u043d\u0430\u043b", 3, 1.56f, 1.7f);

    final String label;
    final int policeCount;
    final float policeSpeed;
    final float ratingMultiplier;

    Difficulty(String label, int policeCount, float policeSpeed, float ratingMultiplier) {
        this.label = label;
        this.policeCount = policeCount;
        this.policeSpeed = policeSpeed;
        this.ratingMultiplier = ratingMultiplier;
    }
}
