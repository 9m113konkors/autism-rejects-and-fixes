package com.example.minimal;

// Risk tiers shown on each addon module: how obvious the current configuration looks to
// other players (and to anyone watching your screen). The label is appended to the module's
// info() line and the color drives the module's indicator HUD.
public enum Tier {
    CLOSET("Closet", 0xFF8EE27C),
    LEGIT("Legit", 0xFFE6D15A),
    RISKY("Risky", 0xFFE8A25E),
    BLATANT("Blatant", 0xFFE86060),
    IMPOSSIBLE("Impossible", 0xFFFF5FD0);

    private final String label;
    private final int color;

    Tier(String label, int color) {
        this.label = label;
        this.color = color;
    }

    public String label() {
        return label;
    }

    public int color() {
        return color;
    }

    public static Tier forChance(int chancePct) {
        if (chancePct < 50) return CLOSET;
        if (chancePct < 75) return LEGIT;
        if (chancePct < 95) return RISKY;
        return BLATANT;
    }

    public static Tier forReach(double blocks) {
        if (blocks < 5.0) return CLOSET;
        if (blocks < 7.0) return LEGIT;
        if (blocks < 9.0) return RISKY;
        if (blocks < 20.0) return BLATANT;
        return IMPOSSIBLE;
    }

    // Velocity intensity = average knockback removed; more removal is more blatant.
    public static Tier forVelocity(int horizontalPct, int verticalPct) {
        double removed = 100.0 - (horizontalPct + verticalPct) / 2.0;
        if (removed >= 85.0) return BLATANT;
        if (removed >= 60.0) return RISKY;
        if (removed >= 30.0) return LEGIT;
        return CLOSET;
    }
}
