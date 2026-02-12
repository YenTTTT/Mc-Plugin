package com.customrpg.weaponSkills;

import java.util.Collections;
import java.util.Set;

/**
 * BaseSkill
 *
 * Convenience base class for skills.
 */
public abstract class BaseSkill implements Skill {

    private final String id;
    private final String name;
    private final Set<SkillTriggerType> supportedTriggers;

    protected BaseSkill(String id, String name, Set<SkillTriggerType> supportedTriggers) {
        this.id = id;
        this.name = name;
        this.supportedTriggers = supportedTriggers == null ? Collections.emptySet() : Set.copyOf(supportedTriggers);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<SkillTriggerType> getSupportedTriggers() {
        return supportedTriggers;
    }
}
