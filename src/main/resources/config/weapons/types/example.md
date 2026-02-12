# 【武器基本資料】
  name: string = ""                 # 名稱（內部用/可選）
  id: string = ""                   # 內部ID：唯一識別碼（UUID 36碼）
  material: string = ""             # 物品類型：Minecraft 材質（Spigot Material）
  rarity: string = ""               # 稀有度：COMMON / RARE / LEGENDARY / MYTHIC
  bindType: string = ""             # 綁定類型：NONE / BIND_PLAYER / DROP_VANISH

# 【外觀設定】
  display:
    name: string = ""               # 顯示名稱（可用 & 色碼）
    lore: string = -""              # 物品描述（每行一條）
                   -""                  
    customModelData: int = null     # 模型資料值：CustomModelData（可選，沒用就 null）
    enchantGlint: boolean = false   # 附魔外觀：true/false（只做光效，不一定真的附魔）

# 【基礎戰鬥屬性】
  baseStats:
    baseDamage: int = 0.0               # 基礎傷害
    attackSpeed: int = 1.6              
    # 攻擊速度(MinMax(20,無上限)，預設4.0，影響方式: tick=20/attackSpeed)

    critChancePercent: int = 0.0        # 暴擊率（%）
    critDamageMultiplier: int = 1.5     # 暴擊傷害倍率（例如 1.5 = 150%）
    knockbackStrength: int = 0.0        # 擊退強度
    durabilityCostMultiplier: int = 1.0 # 耐久度消耗倍率

# 【特殊機制】
  special:
    backstabEnabled: false              # 背刺加成：true/false
    backstabMultiplier: 1.5             # 背刺倍率
    armorPierce: false                  # 穿透傷害：以百分比計算，MinMax(0,100)，0代表0%穿透 100代表100%穿透
    aoeRadius: 0.0                      # 範圍攻擊半徑（0=無）
    lifeStealPercent: 0.0               # 吸血比例（%）

# 【主動技能】（武器技能：跟天賦技能分開）
  activeSkill:
    skillKey: ""                        # 技能名稱/代碼（建議用 key，例如 fire_slash）
    trigger: RIGHT_CLICK                # 觸發方式：LEFT_CLICK / RIGHT_CLICK / SHIFT_ATTACK
    cooldownSeconds: 0.0                # 冷卻時間（秒）
    description: ""                     # 技能效果描述
    params: {}                          # 技能參數（可擴充，沒有就 {}）

# 【被動效果】（可做成多個，最通用）
  passives:
    - passiveKey: ""                    # 被動效果代碼（例如 bleed_on_hit）
      condition: ON_HIT                 # 觸發條件：ON_HIT / ON_KILL / ON_CRIT / ON_TAKE_DAMAGE
      chancePercent: 0.0                # 觸發機率（%）
      description: ""                   # 被動效果描述
      params: {}                        # 被動參數（可擴充）

# 【元素/狀態效果】
  element:
    type: NONE                          # 元素屬性：FIRE / ICE / LIGHTNING / POISON / NONE
    state: ""                           # 附加狀態（例如 BURN / SLOW / POISON，沒就空字串）
    durationSeconds: 0.0                # 持續時間（秒）

# 【音效與視覺】
  fx:
    attackSound: ""                     # 攻擊音效（minecraft:xxx）
    skillSound: ""                      # 技能音效
    particle: ""                        # 粒子效果（例如 FLAME / CRIT / CLOUD）

# 【限制條件】
  requirements:
    levelRequired: 0                    # 等級需求
    jobRequired: ""                     # 職業需求（沒限制就空字串）
    tradeable: true                     # 是否可交易
    droppable: true                     # 是否可掉落

# 【平衡與備註】
  balance:
    role: DPS                           # 設計定位：DPS / CONTROL / SURVIVAL / BURST
    notes: ""                           # 設計備註
