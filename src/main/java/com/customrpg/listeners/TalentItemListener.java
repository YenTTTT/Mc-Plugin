package com.customrpg.listeners;

import com.customrpg.managers.TalentManager;
import com.customrpg.managers.TalentSkillManager;
import com.customrpg.talents.Talent;
import com.customrpg.talents.TalentItemBinding;
import com.customrpg.talents.TalentType;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Item casting is an alternative to hotkeys; both enter the same validated casting service. */
public final class TalentItemListener implements Listener {
    private final TalentManager talents;
    private final TalentSkillManager skills;
    private final Map<UUID, Map<Material, String>> selected = new HashMap<>();

    public TalentItemListener(TalentManager talents, TalentSkillManager skills) {
        this.talents = talents;
        this.skills = skills;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getItem() == null) return;
        Action action = event.getAction();
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        if (!right && !left) return;
        // Right-clicking empty air may deny only block use by vanilla prediction.
        if (event.useItemInHand() == Event.Result.DENY
                || ((action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_BLOCK)
                    && event.useInteractedBlock() == Event.Result.DENY)) return;
        Player player = event.getPlayer();
        if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) return;
        Material material = event.getItem().getType();
        List<Talent> available = available(player, material);
        if (available.isEmpty()) return;

        Map<Material, String> choices = selected.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>());
        Talent current = available.stream().filter(t -> t.getId().equals(choices.get(material)))
                .findFirst().orElse(available.get(0));
        if (right && player.isSneaking()) {
            // Switching never casts or spends resources, even when only one skill is equipped.
            current = available.get((available.indexOf(current) + 1) % available.size());
            choices.put(material, current.getId());
            player.sendMessage("§e[技能切換] §a" + current.getName() + " §7（"
                    + TalentItemBinding.triggerName(current.getTriggerType()) + "施放）");
            event.setCancelled(true);
            return;
        }
        String trigger = (right ? "RIGHT_CLICK" : "LEFT_CLICK") + (player.isSneaking() ? "_SNEAK" : "");
        if (!trigger.equalsIgnoreCase(current.getTriggerType())) return;
        // Also suppress vanilla use / weapon skills on cooldown or insufficient mana.
        event.setCancelled(true);
        skills.castSkillDirectly(player, current.getId(), event.getItem());
    }

    private List<Talent> available(Player player, Material material) {
        var investment = talents.getPlayerTalents(player);
        Map<String, Talent> result = new LinkedHashMap<>();
        for (String id : investment.getSelectedSkills()) {
            if (id == null || investment.getTalentLevel(id) <= 0) continue;
            Talent talent = talents.findTalent(id);
            if (talent != null && talent.getType() == TalentType.ACTIVE
                    && TalentItemBinding.matches(talent.getMechanism(), material)) result.put(id, talent);
        }
        return new ArrayList<>(result.values());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { selected.remove(event.getPlayer().getUniqueId()); }
}
