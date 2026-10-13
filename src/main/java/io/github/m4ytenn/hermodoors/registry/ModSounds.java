package io.github.m4ytenn.hermodoors.registry;

import io.github.m4ytenn.hermodoors.HermoDoors;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Sounds recorded for this mod. Everything else reuses vanilla metal, piston and lever sounds. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, HermoDoors.MODID);

    /** Long pressure release when the seal breaks. */
    public static final DeferredHolder<SoundEvent, SoundEvent> DOOR_UNSEAL = register("door.unseal");
    /** Low rumble of a heavy leaf on the move. */
    public static final DeferredHolder<SoundEvent, SoundEvent> DOOR_RUMBLE = register("door.rumble");
    /** Short burst from the relief valve. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PANEL_HISS = register("panel.hiss");
    /** Breaker trip: crackle and buzz. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PANEL_ARC = register("panel.arc");
    /** Mains hum when the panel comes alive. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PANEL_HUM = register("panel.hum");
    /** Typewriter key on the passport form. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PASSPORT_TYPE = register("passport.type");

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HermoDoors.id(name)));
    }
}
