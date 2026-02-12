package com.customrpg.weaponSkills;

import com.customrpg.managers.WeaponManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * SkillContext
 *
 * Runtime context passed to skills. This is the only thing a Skill should need.
 */
public class SkillContext {

    private final Player caster;
    private final SkillTriggerType triggerType;
    private final ItemStack itemInHand;
    private final String weaponKey;
    private final WeaponManager.WeaponData weaponData;
    private final Entity explicitTarget;

    private final SkillServices services;

    public SkillContext(Player caster,
                        SkillTriggerType triggerType,
                        ItemStack itemInHand,
                        String weaponKey,
                        WeaponManager.WeaponData weaponData,
                        Entity explicitTarget,
                        SkillServices services) {
        this.caster = caster;
        this.triggerType = triggerType;
        this.itemInHand = itemInHand;
        this.weaponKey = weaponKey;
        this.weaponData = weaponData;
        this.explicitTarget = explicitTarget;
        this.services = services;
    }

    public Player caster() { return caster; }

    public SkillTriggerType triggerType() { return triggerType; }

    public ItemStack itemInHand() { return itemInHand; }

    public String weaponKey() { return weaponKey; }

    public WeaponManager.WeaponData weaponData() { return weaponData; }

    public Entity explicitTarget() { return explicitTarget; }

    public SkillServices services() { return services; }
}

