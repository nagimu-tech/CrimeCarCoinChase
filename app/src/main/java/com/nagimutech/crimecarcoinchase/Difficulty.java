package com.nagimutech.crimecarcoinchase;

enum Difficulty {
    DEBUT("Дебют", 1, 0.45f, 0.8f),
    BEGINNER("Начинающий", 1, 0.55f, 1.0f),
    AMATEUR("Любитель", 2, 0.62f, 1.35f),
    PRO("Профессионал", 3, 0.68f, 1.7f);

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
