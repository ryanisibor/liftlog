package app.liftlog.data

/** Stored in the database by name, so never rename an entry without a migration. */
enum class Muscle(val label: String) {
    CHEST("Chest"),
    LATS("Lats"),
    UPPER_BACK("Upper back"),
    LOWER_BACK("Lower back"),
    TRAPS("Traps"),
    SHOULDERS("Shoulders"),
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    FOREARMS("Forearms"),
    ABS("Abs"),
    OBLIQUES("Obliques"),
    GLUTES("Glutes"),
    QUADS("Quads"),
    HAMSTRINGS("Hamstrings"),
    CALVES("Calves"),
    ADDUCTORS("Adductors"),
    ABDUCTORS("Abductors"),
    NECK("Neck"),
    FULL_BODY("Full body"),
}

/** Stored in the database by name, so never rename an entry without a migration. */
enum class Equipment(val label: String) {
    BARBELL("Barbell"),
    DUMBBELL("Dumbbell"),
    MACHINE("Machine"),
    CABLE("Cable"),
    BODYWEIGHT("Bodyweight"),
    SMITH_MACHINE("Smith machine"),
    EZ_BAR("EZ bar"),
    KETTLEBELL("Kettlebell"),
    BAND("Band"),
    TRAP_BAR("Trap bar"),
    PLATE("Plate"),
    OTHER("Other"),
}
