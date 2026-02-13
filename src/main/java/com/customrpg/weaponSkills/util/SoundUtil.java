package com.customrpg.weaponSkills.util;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;

/**
 * SoundUtil
 *
 * Reusable sound helper that ensures sound names are properly formatted
 * for Minecraft 1.21+ which requires lowercase identifiers.
 */
public class SoundUtil {

    /**
     * Play a sound at a location with proper formatting
     *
     * @param location The location to play the sound
     * @param soundName The sound name (will be converted to lowercase)
     * @param volume The volume of the sound
     * @param pitch The pitch of the sound
     */
    public void playSound(Location location, String soundName, float volume, float pitch) {
        if (location == null || soundName == null || soundName.isBlank()) {
            return;
        }

        World world = location.getWorld();
        if (world == null) {
            return;
        }

        // Convert to lowercase to match Minecraft 1.21+ identifier requirements
        // Minecraft requires identifiers to be [a-z0-9/._-] only
        String safeSoundName = soundName.trim().toLowerCase(Locale.ROOT);

        try {
            world.playSound(location, safeSoundName, volume, pitch);
        } catch (Exception e) {
            // If the sound fails to play, log but don't crash
            // Optionally fallback to a default sound
            try {
                world.playSound(location, "entity.experience_orb.pickup", volume, pitch);
            } catch (Exception ignored) {
                // Even fallback failed, just ignore
            }
        }
    }
}

