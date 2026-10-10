package me.arcade.games.arena;

public enum GameType {
    PROP_HUNT("Prop Hunt", 2, 16),
    BLOCK_PARTY("BlockParty", 2, 16),
    TNT_RUN("TNTRun", 2, 24),
    PARTY_GAMES("Party Games", 2, 16),
    MINI_WALLS("Mini Walls", 2, 16);

    public final String display;
    public final int min, max;

    GameType(String display, int min, int max) {
        this.display = display;
        this.min = min;
        this.max = max;
    }

    public static GameType parse(String s) {
        if (s == null) return null;
        String k = s.replace("_", "").replace("-", "").replace(" ", "").toLowerCase();
        for (GameType t : values()) {
            if (t.name().replace("_", "").toLowerCase().equals(k)) return t;
        }
        return null;
    }
}
