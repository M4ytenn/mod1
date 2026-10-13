package io.github.m4ytenn.hermodoors;

import io.github.m4ytenn.hermodoors.door.Stage;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class HermoConfig {
    public static final Server SERVER;
    public static final ModConfigSpec SERVER_SPEC;
    public static final Client CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        Pair<Server, ModConfigSpec> server = new ModConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();
        Pair<Client, ModConfigSpec> client = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    private HermoConfig() {}

    /**
     * Also read on the client (to decide whether to offer the write-off line), where the synced server config
     * could in theory be missing; fall back to the strict default then.
     */
    public static boolean ownerOnlyPassport() {
        try {
            return SERVER.ownerOnlyPassport.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static final class Server {
        public final ModConfigSpec.BooleanValue defaultPower;
        public final ModConfigSpec.BooleanValue defaultPump;
        public final ModConfigSpec.BooleanValue defaultClamps;
        public final ModConfigSpec.BooleanValue defaultWheel;
        public final ModConfigSpec.IntValue defaultDifficulty;
        public final ModConfigSpec.BooleanValue ownerOnlyPassport;
        public final ModConfigSpec.BooleanValue redstoneOpensUnlocked;
        public final ModConfigSpec.BooleanValue enforceMinimumTimes;

        Server(ModConfigSpec.Builder b) {
            b.comment("Opening procedure a freshly placed door starts with, before its passport is filled in.")
                    .push("defaults");
            defaultPower = b.comment("Restore power on the breaker panel.").define("power", true);
            defaultPump = b.comment("Pump up the hydraulics and hold the pressure.").define("pump", true);
            defaultClamps = b.comment("Knock the seized wedge clamps loose.").define("clamps", true);
            defaultWheel = b.comment("Crank the handwheel to pull the bolts.").define("wheel", true);
            defaultDifficulty = b.comment("Difficulty category: 1, 2 or 3.").defineInRange("difficulty", 2, 1, 3);
            b.pop();

            b.push("rules");
            ownerOnlyPassport = b.comment(
                    "If true, only the player who placed a door (plus creative players and operators) can open its",
                    "passport to change the procedure or dismantle it. If false, anyone can.")
                    .define("ownerOnlyPassport", true);
            redstoneOpensUnlocked = b.comment("Once a door is unlocked, a redstone signal opens and closes it.")
                    .define("redstoneOpensUnlocked", true);
            enforceMinimumTimes = b.comment(
                    "Reject stage completions that arrive faster than a human could manage (basic anti-cheat).")
                    .define("enforceMinimumTimes", true);
            b.pop();
        }

        public int defaultStageMask() {
            int mask = 0;
            if (defaultPower.get()) mask |= Stage.POWER.bit;
            if (defaultPump.get()) mask |= Stage.PUMP.bit;
            if (defaultClamps.get()) mask |= Stage.CLAMPS.bit;
            if (defaultWheel.get()) mask |= Stage.WHEEL.bit;
            return mask;
        }
    }

    public static final class Client {
        public final ModConfigSpec.BooleanValue screenShake;
        public final ModConfigSpec.BooleanValue typewriterJitter;

        Client(ModConfigSpec.Builder b) {
            screenShake = b.comment("Shake the control panel on hammer blows, breaker trips and wheel slips.")
                    .define("screenShake", true);
            typewriterJitter = b.comment("Uneven typewriter baseline on the door passport.")
                    .define("typewriterJitter", true);
        }
    }
}
