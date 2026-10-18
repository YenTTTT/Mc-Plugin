# 原版客戶端怪物外觀、招式與地形首領

2026-10-18，Paper 1.21.11／Java 21。使用原版生物、尺寸屬性、裝備、音效與粒子；玩家不需要模組、資源包或 LibsDisguises。外觀會接近主題生物，沒有新增自訂骨架或貼圖。

## 本次內容

- 為資源中的 123 個既有怪物 ID 加上外觀／機制設定，再新增 20 種普通怪與 10 種荒野 Boss，共 153 個 profiles。舊 `example.yml` 的四個範例也有 profile，但該範例檔不會自動部署；實服載入數取決於部署模板及是否停用 profile。
- 暴怒熊人 `berserker_bear` 改成棕色熊貓，尺寸 1.3 倍；衝鋒與震地交替，生命降至 35% 以下進入狂暴。衝鋒依實際身體移動路徑判定，站在遠處不會被瞬間命中，側移及障礙物可用於躲避。
- 狼類使用狼、樹木／岩石守衛使用鐵魔像、龍類使用幻翼、剪刀手使用衛道士；搭配各自尺寸、護甲及主題特效。色碼用於粒子與新增皮革裝備，不會將所有原版生物貼圖重新染色。
- 保留南瓜騎士、剪刀手、豬屍獸及熔核巨獸三階段共六個 ID 的既有專屬機制，其餘 147 個 profiles 使用共用的招式控制器。舊機制不會同時疊加新通用招式。

## 十種地形內容

每列前兩種為普通怪，最後一種為稀有 Boss。普通怪依距離等級、環境及時段進池；不是按玩家等級縮放。主世界新增普通模板合計涵蓋 Lv.1–60，Nether Lv.30 起，End Lv.50 起；每種實際範圍見後方清單。新增模板皆接受全天，仍需有生怪來源且通過全域時段／亮度設定。

| 環境 | 新增普通怪 | 稀有 Boss | 戰鬥主題 |
| --- | --- | --- | --- |
| 平原 | 草原角獸、烈日守望者 | 荒原角王 | 衝鋒、揮擊、戰吼、雷擊 |
| 森林 | 苔背野豬、荊棘樹衛 | 古樹守望者 | 突進、防禦、毒霧、震地 |
| 沙漠 | 沙丘毒蠍、沙晶術士 | 沙暴巨像 | 飛撲、毒霧、風壓、落點法術 |
| 沼澤 | 毒沼巨蛙、腐孢巡行者 | 瘴沼巨獸 | 毒霧、吸血、震地 |
| 洞穴 | 晶簇螨、迴響傀儡 | 晶心守護者 | 奧術落點、暗影射線、雷擊 |
| 冰雪 | 霜牙山貓、封霜亡魂 | 冰川熊王 | 飛撲、冰霜射線、箭雨、衝鋒 |
| 山地 | 風暴山羊、峭壁掠翼 | 雷角霸主 | 衝鋒、風壓、飛撲、雷擊 |
| 地獄 | 燼牙獵犬、玄武岩衛士 | 熾爐領主 | 吐息、飛撲、震地、防禦 |
| 海岸 | 礁岸甲蟹、潮汐掠奪者 | 裂潮巨像 | 揮擊、箭雨、推擊、吸血 |
| 終界 | 虛空爬行者、裂隙哨衛 | 裂隙君王 | 暗影穿刺、奧術落點、吸血、雷擊 |

海岸屬於 `ocean` 環境標籤，包含海灘／岸線，仍需陸地穩定地面；這批內容沒有水下生成器。洞穴高度判定忽略樹葉，避免樹冠造成洞穴誤判。較大模型需有足夠空間，不會硬塞進狹窄洞穴或強載相鄰區塊。

## 招式與躲避

新控制器的每隻怪依 profile 輪流施放 1–4 招。預設普通怪蓄力 1 秒、Boss 1.4 秒，地面標記、射線及 ActionBar 顯示招式。落點法術鎖定蓄力開始時的位置，移出範圍可躲避；射線檢查方塊遮擋。衝鋒／飛撲只在接下來一秒沿真實身體路徑判定，每名玩家每次最多命中一次。

`FORTIFY` 提供四秒 40% 減傷；`HOWL` 為八格內同樣持有戰吼招式的怪提供八秒 20% 招式傷害加成，沒有憑空召喚新怪。毒霧是一輪範圍命中後附加三秒中毒，不是持續存在的地面毒區。三連箭是真實投射物，不能拾取；本控制器追蹤的投射物全服最多 256 枚，八秒或來源卸載時清除。

新機制 Boss 在 50%／25% 生命進入二／三階段，招式間隔變為原本 75%／55%，招式傷害變為 115%／130%；普通狂暴依各 profile 門檻進入第二階段。這些倍率作用於招式，沒有額外永久改寫基本屬性。六個保留專屬機制的 ID 沿用原機制，不能套用本段的通用階段說明。

只有同世界、可見且在範圍內的生存／冒險玩家可成為招式目標，並檢查本插件安全區、保護區與追擊限制。射出的箭在命中時重新檢查來源與目標資格。傷害經正常事件處理，被取消或未造成有效傷害時不追加擊退、吸血或點燃。中立草原狼需被玩家攻擊才反擊，膽小野雞會逃跑，已馴服生物不施放招式。

新控制器不放火、不放岩漿、不破壞方塊；自訂苦力怕使用蓄力爆破，取消原生爆炸；取消末影人／劫掠獸改方塊及雪傀儡積雪。熔核巨獸原先的火焰／岩漿落點也改為粒子能量點；吸收期間玩家靠近且可見能量點便能消耗它，未消耗的每點回復 5% 最大生命。這不等於整個插件所有歷史技能都已完成地形／插件相容性審計。

## 荒野 Boss 出現規則

荒野 Boss 共用一次正常自然替換或 supplemental 補怪嘗試，沒有另外開一條刷怪來源。只有 `boss: true` 且含 `wilderness-boss` 標籤的十個新增 Boss 可出現；`event-only` 與熔核召喚物不會混入普通池。

部署檔 `plugins/CustomRPG/config/wilderness_bosses.yml` 預設：

```yaml
enabled: true
chance-per-spawn: 0.01
cell-size: 1024
max-per-world: 2
cooldown-seconds: 1800
lifetime-seconds: 1200
```

在環境、等級與保護條件通過的生成嘗試中有 1% 機會嘗試 Boss；每世界最多兩隻，每個 1024×1024 格區域從遭遇建立起冷卻 30 分鐘，存活上限 20 分鐘。附近 80 格內的生存／冒險玩家收到通知。冷卻及實體 UUID 原子存入 `data/wilderness_bosses.yml`，正常重啟保留冷卻，卸載的 Boss 在期限內仍占名額，過期載回時移除。到期與停用清除不發擊殺掉落。

建立實體前先保存冷卻與身分；資料損壞或寫入失敗時只暫停荒野 Boss，檔案保留，普通怪仍能繼續。Boss 生成失敗會嘗試普通怪；只有有效替代怪建立成功才移除原怪。相同區域的失敗嘗試也保留已保存的冷卻。

## 安裝、重載與測試

1. 使用 `target/CustomRPG-1.0.jar` 更新並重啟相容的 Paper 1.21.11 伺服器，會補建三份新檔：`config/mobs/encounters.yml`、`config/mobs/types/biome_expansion.yml`、`config/wilderness_bosses.yml`。現有怪物數值、掉落與玩家資料不會被覆寫。
2. 每次重啟仍需 `/finishrpg` 才開始自然替換；預設 vanilla 模式仍依賴原版敵怪的 NATURAL 來源。白天亮處沒有來源時不保證刷怪；可沿用 [生怪診斷](MOB_SPAWNING.md) 或選擇 supplemental 模式。
3. 新安裝的生怪設定不再預設排除終界；舊部署檔不會自動更改。要啟用終界，請從 `config/mob_spawner.yml` 的 `disabled-worlds` 移除實際 End 世界名稱，例如 `world_the_end`。
4. 編輯已部署 `encounters.yml` 後用 `/mobspawn reload` 更新外觀、招式與荒野 Boss 規則。錯誤的 profile 檔不會取代上一份有效設定。這個指令不重讀所有 MobData 模板檔，新增／修改模板數值或掉落需重啟。
5. 模型種類變更作用於新生成實體，舊實體不會被強制替換 UUID。已載入的怪重載時更新尺寸／招式，清除舊蓄力；新增外觀裝備只補空欄位，保留既有裝備，因此不會靠重載強制換掉現有裝備或皮革顏色。停用 profile 會回退到原有模板／舊行為。

直接試怪可用管理員指令指定等級；省略等級仍依原模板範圍隨機：

```text
/custommob info berserker_bear
/custommob spawn berserker_bear 30
/custommob spawn boss_glacier_ursine 25
/custommob spawn boss_crystal_heart 30
/mobspawn status
```

請到本插件安全圈外的寬敞穩定地面，使用生存／冒險模式觀察追擊與招式。手動 spawn 不必先等自然來源，仍檢查安全區與模型空間；手動生成不使用荒野 Boss 的機率／持久區域冷卻／存活期限。其他未標記的舊 Boss 仍依原本手動或固定 Boss 區域機制出現。

## 本機驗證與實服待驗收

完整 `mvn clean verify`：231 tests，0 failures／errors／skipped；149 個 production Java、39 個 test 類別、35 份資源 YAML。新增五個測試類別共 38 項案例，涵蓋所有內建 profile、各地形等級覆蓋、壞設定保留、蓄力／躲避／衝鋒真實路徑、傷害取消、保護區、箭矢命中資格、生命週期、持久冷卻、卸載／到期、寫入失敗、身分先存後生成、Boss 註冊失敗回滾、模型碰撞及指定等級指令。

沒有啟動 Paper 測試服。仍需驗收原版客戶端外觀、熊貓／山羊／幻翼原生 AI、多人傷害事件順序、反作弊與第三方插件、真實卸載／重啟及效能；數值是初始設定，未宣稱完成戰鬥平衡。共用計時器每五 ticks 最多更新 128 隻已載入怪，繁忙時會延長更新間隔；嚴重延遲超過三秒的蓄力不補發瞬間傷害。

## 全部外觀／招式對照

本表由本輪資源檔整理。實服自訂數值／掉落以部署檔為準。既有範例若未部署，不會因 profile 存在便自行建立模板。尺寸是 SCALE 倍率；史萊姆／岩漿怪及幻翼另有原生 size 設定。

| ID／名稱 | 來源 | 等級 | 原版實體 × 尺寸 | 招式或專屬機制 |
| --- | --- | --- | --- | --- |
| `boss_pumpkin_knight`／(野外Boss) 南瓜騎士 | `boss.yml` | 20–30 | `ZOMBIE` × 1.22 | 沿用專屬：`boss_pumpkin_knight` |
| `boss_crazy_scissor`／(野外Boss) 瘋狂剪刀手 | `boss.yml` | 20–30 | `VINDICATOR` × 1.14 | 沿用專屬：`boss_scissor_frenzy` |
| `boss_zombie_beast`／(野外Boss) 豬屍獸 | `boss.yml` | 20–30 | `ZOGLIN` × 1.22 | 沿用專屬：`boss_zombie_beast` |
| `boss_skull_general`／(野外Boss) 骸骨將軍 | `boss.yml` | 40–55 | `SKELETON` × 1.22 | 防禦姿態、三連箭、震地重擊 |
| `boss_infernal_queen`／(野外Boss) 煉獄魔女 | `boss.yml` | 45–60 | `WITCH` × 1.22 | 火焰吐息、奧術落點、生命汲取 |
| `boss_abyss_dragon_rider`／(野外Boss) 深淵龍騎 | `boss.yml` | 60–78 | `PHANTOM` × 1.26 | 火焰吐息、疾風推擊、飛撲 |
| `boss_shadow_lord`／(野外Boss) 暗影君王 | `boss.yml` | 65–80 | `WITHER_SKELETON` × 1.14 | 暗影穿刺、生命汲取、飛撲 |
| `boss_doomsday_judge`／(野外Boss) 末世審判者 | `boss.yml` | 80–95 | `RAVAGER` × 1.14 | 雷擊落點、防禦姿態、直線衝鋒 |
| `boss_void_archmage`／(野外Boss) 虛空大法師 | `boss.yml` | 82–96 | `SKELETON` × 1.26 | 奧術落點、暗影穿刺、雷擊落點 |
| `boss_world_ender`／(野外Boss) 世界終結者 | `boss.yml` | 92–100 | `IRON_GOLEM` × 1.26 | 震地重擊、奧術落點、雷擊落點 |
| `boss_magma_core`／✦ 熔核巨獸 ✦ | `boss.yml` | 50–70 | `MAGMA_CUBE` × 1.18 | 沿用專屬：`boss_magma_core` |
| `magma_core_medium`／熔核分裂體 | `boss.yml` | 45–65 | `MAGMA_CUBE` × 1.06 | 沿用專屬：`magma_core_medium` |
| `magma_core_small`／熔核碎體 | `boss.yml` | 40–60 | `MAGMA_CUBE` × 1.06 | 沿用專屬：`magma_core_small` |
| `leveled_zombie`／菁英殭屍 | `enhanced_example.yml` | 1–10 | `ZOMBIE` × 1.02 | 扇形揮擊、直線衝鋒 |
| `armored_skeleton`／鐵甲弓箭手 | `enhanced_example.yml` | 5–15 | `SKELETON` × 1.02 | 三連箭、防禦姿態 |
| `aggressive_pig`／狂暴豬 | `enhanced_example.yml` | 3–12 | `PIG` × 1.06 | 扇形揮擊、直線衝鋒 |
| `snow_zombie`／雪地殭屍 | `example.yml` | ?–? | `ZOMBIE` × 0.94 | 冰霜射線、扇形揮擊 |
| `fire_skeleton`／烈焰骷髏 | `example.yml` | ?–? | `SKELETON` × 1.02 | 三連箭、火焰吐息 |
| `giant_slime`／巨型史萊姆 | `example.yml` | ?–? | `SLIME` × 0.94 | 震地重擊、飛撲 |
| `shadow_creeper`／暗影苦力怕 | `example.yml` | ?–? | `CREEPER` × 1.06 | 蓄力爆破、暗影穿刺 |
| `meteor_parasite`／隕石寄生體 | `meteor.yml` | 1–60 | `ENDERMITE` × 0.94 | 扇形揮擊、直線衝鋒 |
| `astral_crawler`／星界爬行者 | `meteor.yml` | 1–60 | `SPIDER` × 1.02 | 扇形揮擊、直線衝鋒 |
| `fallen_guardian`／殞落守衛 | `meteor.yml` | 1–60 | `HUSK` × 0.94 | 防禦姿態、震地重擊 |
| `meteor_elite`／隕星精英 | `meteor.yml` | 1–60 | `WITHER_SKELETON` × 1.02 | 扇形揮擊、直線衝鋒 |
| `meteor_core_guardian`／隕石核心守護者 | `meteor.yml` | 1–60 | `HUSK` × 1.22 | 防禦姿態、震地重擊 |
| `grass_zombie`／草原殭屍 | `tier1_beginner.yml` | 1–5 | `ZOMBIE` × 0.98 | 扇形揮擊、直線衝鋒 |
| `forest_spider`／林間蜘蛛 | `tier1_beginner.yml` | 1–5 | `SPIDER` × 0.98 | 毒霧爆發、飛撲 |
| `lost_skeleton`／迷路骷髏 | `tier1_beginner.yml` | 1–4 | `SKELETON` × 1.02 | 三連箭、暗影穿刺 |
| `meadow_slime`／草原史萊姆 | `tier1_beginner.yml` | 1–3 | `SLIME` × 1.02 | 震地重擊、飛撲 |
| `feral_wolf`／野生狼人 | `tier1_beginner.yml` | 2–5 | `WOLF` × 0.98 | 族群戰吼、飛撲 |
| `mushroom_zombie`／毒蘑菇殭屍 | `tier1_beginner.yml` | 3–8 | `ZOMBIE` × 1.06 | 扇形揮擊、直線衝鋒 |
| `cave_bat`／洞穴蝙蝠精 | `tier1_beginner.yml` | 3–7 | `VEX` × 0.98 | 扇形揮擊、直線衝鋒 |
| `graverobber`／盜墓者 | `tier1_beginner.yml` | 4–8 | `ZOMBIE` × 0.98 | 扇形揮擊、暗影穿刺 |
| `spore_spider`／孢子蜘蛛 | `tier1_beginner.yml` | 3–7 | `CAVE_SPIDER` × 1.06 | 毒霧爆發、飛撲 |
| `wandering_archer`／流浪弓手 | `tier1_beginner.yml` | 4–8 | `SKELETON` × 1.06 | 三連箭、暗影穿刺 |
| `creeper_hatchling`／苦力怕幼體 | `tier1_beginner.yml` | 5–10 | `CREEPER` × 1.06 | 蓄力爆破、毒霧爆發 |
| `rotten_treant`／腐木守衛 | `tier1_beginner.yml` | 6–12 | `IRON_GOLEM` × 0.8 | 防禦姿態、震地重擊 |
| `phantom_sentry`／幽魂哨兵 | `tier1_beginner.yml` | 7–13 | `STRAY` × 1.06 | 扇形揮擊、直線衝鋒 |
| `shadow_lurker`／暗影潛行者 | `tier1_beginner.yml` | 8–15 | `SPIDER` × 1.02 | 暗影穿刺、飛撲 |
| `stone_golem`／岩石傀儡 | `tier1_beginner.yml` | 8–15 | `IRON_GOLEM` × 0.8 | 防禦姿態、震地重擊 |
| `slime_bomber`／黏液爆破者 | `tier1_beginner.yml` | 3–8 | `SLIME` × 0.94 | 蓄力爆破、毒霧爆發 |
| `wild_chicken`／野生雞 | `tier1_beginner.yml` | 1–3 | `CHICKEN` × 0.94 | 逃離威脅 |
| `lesser_elemental`／小型元素靈 | `tier1_beginner.yml` | 4–10 | `VEX` × 0.94 | 冰霜射線、奧術落點 |
| `rage_boar`／狂暴野豬 | `tier1_beginner.yml` | 3–8 | `PIG` × 1.02 | 扇形揮擊、直線衝鋒 |
| `shadow_spider`／影襲蜘蛛 | `tier1_beginner.yml` | 5–12 | `SPIDER` × 0.94 | 毒霧爆發、飛撲 |
| `skeleton_mage`／骷髏法師 | `tier1_beginner.yml` | 4–10 | `SKELETON` × 1.02 | 奧術落點、生命汲取 |
| `prairie_wolf`／草原狼 | `tier1_beginner.yml` | 2–6 | `WOLF` × 1.06 | 族群戰吼、飛撲 |
| `small_bug`／小蟲子 | `tier1_beginner.yml` | 1–5 | `SILVERFISH` × 1.02 | 毒霧爆發、飛撲 |
| `wasteland_raider`／荒野掠奪者 | `tier2_intermediate.yml` | 15–25 | `ZOMBIE` × 0.98 | 扇形揮擊、直線衝鋒 |
| `desert_mummy`／沙漠木乃伊 | `tier2_intermediate.yml` | 15–24 | `HUSK` × 1.06 | 扇形揮擊、直線衝鋒 |
| `swamp_spider`／毒沼蜘蛛 | `tier2_intermediate.yml` | 15–22 | `CAVE_SPIDER` × 1.02 | 毒霧爆發、飛撲 |
| `shadow_assassin`／暗影刺客 | `tier2_intermediate.yml` | 16–25 | `ZOMBIE` × 1.02 | 暗影穿刺、飛撲 |
| `elite_crossbowman`／精英弩手 | `tier2_intermediate.yml` | 17–25 | `PILLAGER` × 0.94 | 三連箭、暗影穿刺 |
| `berserker_zombie`／狂戰殭屍 | `tier2_intermediate.yml` | 15–25 | `ZOMBIE` × 1.02 | 扇形揮擊、直線衝鋒 |
| `explosive_skeleton`／爆裂骷髏 | `tier2_intermediate.yml` | 16–25 | `SKELETON` × 1.06 | 三連箭、暗影穿刺 |
| `stalker_spider`／潛伏蜘蛛 | `tier2_intermediate.yml` | 17–25 | `SPIDER` × 0.94 | 毒霧爆發、飛撲 |
| `frost_zombie`／冰霜殭屍 | `tier2_intermediate.yml` | 15–24 | `ZOMBIE` × 1.06 | 扇形揮擊、直線衝鋒 |
| `charge_beast`／衝鋒野獸 | `tier2_intermediate.yml` | 18–25 | `RAVAGER` × 0.94 | 扇形揮擊、直線衝鋒 |
| `plague_carrier`／瘟疫散播者 | `tier2_intermediate.yml` | 18–25 | `ZOMBIE` × 0.98 | 毒霧爆發、生命汲取 |
| `iron_guard`／鋼鐵守衛 | `tier2_intermediate.yml` | 20–25 | `ZOMBIE` × 1.02 | 防禦姿態、震地重擊 |
| `lava_slime`／熔岩史萊姆 | `tier2_intermediate.yml` | 20–30 | `MAGMA_CUBE` × 0.98 | 震地重擊、火焰吐息 |
| `undead_knight`／亡靈騎士 | `tier2_intermediate.yml` | 22–35 | `ZOMBIE` × 0.98 | 防禦姿態、震地重擊 |
| `frost_archer`／冰霜射手 | `tier2_intermediate.yml` | 20–32 | `STRAY` × 1.02 | 三連箭、冰霜射線 |
| `forest_witch`／森林女巫 | `tier2_intermediate.yml` | 25–38 | `WITCH` × 0.98 | 毒霧爆發、奧術落點 |
| `berserker_bear`／暴怒熊人 | `tier2_intermediate.yml` | 28–40 | `PANDA` × 1.3 | 直線衝鋒、震地重擊 |
| `dark_ranger`／黑暗遊俠 | `tier2_intermediate.yml` | 28–40 | `SKELETON` × 0.94 | 三連箭、暗影穿刺 |
| `corrupted_guard`／腐化衛士 | `tier2_intermediate.yml` | 30–40 | `ZOMBIE` × 1.02 | 防禦姿態、震地重擊 |
| `fire_lizard`／噴火蜥蜴 | `tier2_intermediate.yml` | 25–35 | `BLAZE` × 1.06 | 火焰吐息、疾風推擊 |
| `creeper_mage`／苦力怕巫師 | `tier2_intermediate.yml` | 22–32 | `CREEPER` × 1.06 | 奧術落點、蓄力爆破 |
| `cave_troll`／洞穴巨魔 | `tier2_intermediate.yml` | 25–35 | `RAVAGER` × 1.06 | 防禦姿態、震地重擊 |
| `frost_wolf`／霜狼 | `tier2_intermediate.yml` | 15–22 | `WOLF` × 0.98 | 族群戰吼、飛撲 |
| `ice_crystal_slime`／冰晶史萊姆 | `tier2_intermediate.yml` | 15–24 | `SLIME` × 1.06 | 震地重擊、飛撲 |
| `snow_patrol`／雪原巡邏兵 | `tier2_intermediate.yml` | 16–26 | `SKELETON` × 0.94 | 三連箭、冰霜射線 |
| `frozen_undead`／凍骨亡靈 | `tier2_intermediate.yml` | 17–27 | `STRAY` × 0.94 | 扇形揮擊、直線衝鋒 |
| `blizzard_bear`／暴雪巨熊 | `tier2_intermediate.yml` | 18–28 | `POLAR_BEAR` × 1.06 | 直線衝鋒、震地重擊 |
| `frost_witch`／冰霜女巫 | `tier2_intermediate.yml` | 18–28 | `WITCH` × 0.94 | 冰霜射線、毒霧爆發 |
| `ice_spike_spider`／冰刺蜘蛛 | `tier2_intermediate.yml` | 16–25 | `SPIDER` × 1.02 | 毒霧爆發、飛撲 |
| `permafrost_golem`／永凍傀儡 | `tier2_intermediate.yml` | 20–30 | `SNOW_GOLEM` × 1.3 | 防禦姿態、冰霜射線 |
| `frost_specter`／寒霜幽靈 | `tier2_intermediate.yml` | 19–28 | `VEX` × 1.06 | 冰霜射線、生命汲取 |
| `arctic_creeper`／冰原苦力怕 | `tier2_intermediate.yml` | 18–26 | `CREEPER` × 1.06 | 冰霜射線、蓄力爆破 |
| `snowridge_hunter`／雪嶺獵手 | `tier2_intermediate.yml` | 20–30 | `SKELETON` × 1.06 | 三連箭、冰霜射線 |
| `ice_fang_beast`／冰牙狂獸 | `tier2_intermediate.yml` | 20–30 | `WOLF` × 1.02 | 族群戰吼、飛撲 |
| `arctic_raider`／極地掠奪者 | `tier2_intermediate.yml` | 22–30 | `ZOMBIE` × 0.94 | 扇形揮擊、直線衝鋒 |
| `ice_mage`／冰魄法師 | `tier2_intermediate.yml` | 20–30 | `SKELETON` × 1.02 | 冰霜射線、奧術落點 |
| `ice_scorpion`／冰封蠍王 | `tier2_intermediate.yml` | 22–30 | `CAVE_SPIDER` × 0.98 | 毒霧爆發、飛撲 |
| `crimson_berserker`／深紅狂戰士 | `tier3_advanced.yml` | 40–55 | `ZOMBIE` × 1.06 | 扇形揮擊、直線衝鋒 |
| `void_wraith`／虛空幽靈 | `tier3_advanced.yml` | 40–55 | `VEX` × 0.94 | 奧術落點、生命汲取 |
| `infernal_witch`／煉獄女巫 | `tier3_advanced.yml` | 42–58 | `WITCH` × 0.98 | 火焰吐息、生命汲取 |
| `skull_knight`／骸骨騎士 | `tier3_advanced.yml` | 40–55 | `SKELETON` × 1.06 | 防禦姿態、震地重擊 |
| `thunder_creeper`／雷霆苦力怕 | `tier3_advanced.yml` | 42–55 | `CREEPER` × 1.06 | 蓄力爆破、雷擊落點 |
| `demon_hound`／魔犬 | `tier3_advanced.yml` | 45–60 | `WOLF` × 0.94 | 族群戰吼、飛撲 |
| `nether_archer`／地獄弓手 | `tier3_advanced.yml` | 45–60 | `WITHER_SKELETON` × 1.02 | 三連箭、暗影穿刺 |
| `crystal_golem`／水晶傀儡 | `tier3_advanced.yml` | 48–62 | `IRON_GOLEM` × 1.05 | 防禦姿態、震地重擊 |
| `plague_spider`／瘟疫蜘蛛 | `tier3_advanced.yml` | 45–60 | `SPIDER` × 0.94 | 毒霧爆發、飛撲 |
| `shadow_mage`／暗影法師 | `tier3_advanced.yml` | 50–65 | `SKELETON` × 1.06 | 奧術落點、生命汲取 |
| `warden_zombie`／守衛者殭屍 | `tier3_advanced.yml` | 55–70 | `IRON_GOLEM` × 1.05 | 防禦姿態、震地重擊 |
| `storm_blaze`／風暴烈焰 | `tier3_advanced.yml` | 50–65 | `BLAZE` × 1.02 | 雷擊落點、疾風推擊 |
| `abyssal_slime`／深淵史萊姆 | `tier3_advanced.yml` | 50–65 | `SLIME` × 0.94 | 震地重擊、飛撲 |
| `cursed_pillager`／詛咒掠奪者 | `tier3_advanced.yml` | 48–62 | `PILLAGER` × 0.98 | 扇形揮擊、直線衝鋒 |
| `blood_wolf`／血狼 | `tier3_advanced.yml` | 50–65 | `WOLF` × 1.06 | 族群戰吼、飛撲 |
| `doom_knight`／末日騎士 | `tier4_endgame.yml` | 70–85 | `ZOMBIE` × 1.06 | 防禦姿態、震地重擊 |
| `celestial_archer`／天界弓手 | `tier4_endgame.yml` | 70–85 | `SKELETON` × 1.02 | 三連箭、暗影穿刺 |
| `elder_witch`／上古女巫 | `tier4_endgame.yml` | 72–88 | `WITCH` × 1.02 | 奧術落點、生命汲取 |
| `dragon_zombie`／龍屍 | `tier4_endgame.yml` | 75–90 | `PHANTOM` × 0.94 | 火焰吐息、飛撲 |
| `void_spider`／虛空蜘蛛 | `tier4_endgame.yml` | 70–85 | `SPIDER` × 0.94 | 毒霧爆發、飛撲 |
| `hell_blaze`／煉獄烈焰 | `tier4_endgame.yml` | 72–88 | `BLAZE` × 1.02 | 火焰吐息、疾風推擊 |
| `titan_golem`／泰坦傀儡 | `tier4_endgame.yml` | 78–92 | `IRON_GOLEM` × 1.05 | 防禦姿態、震地重擊 |
| `death_mage`／死亡法師 | `tier4_endgame.yml` | 75–90 | `WITHER_SKELETON` × 1.06 | 奧術落點、生命汲取 |
| `ancient_creeper`／遠古苦力怕 | `tier4_endgame.yml` | 75–90 | `CREEPER` × 1.06 | 蓄力爆破、雷擊落點 |
| `chaos_vex`／混沌妖精 | `tier4_endgame.yml` | 80–95 | `VEX` × 0.94 | 奧術落點、生命汲取 |
| `world_serpent`／世界之蛇 | `tier4_endgame.yml` | 85–100 | `RAVAGER` × 0.94 | 毒霧爆發、直線衝鋒、生命汲取 |
| `ender_guardian`／終界守護者 | `tier4_endgame.yml` | 85–100 | `SKELETON` × 0.94 | 防禦姿態、震地重擊 |
| `apocalypse_witch`／天啟女巫 | `tier4_endgame.yml` | 88–100 | `WITCH` × 1.06 | 火焰吐息、生命汲取 |
| `oblivion_slime`／忘卻史萊姆王 | `tier4_endgame.yml` | 90–100 | `SLIME` × 1.06 | 震地重擊、飛撲 |
| `final_warden`／深淵看守者 | `tier4_endgame.yml` | 95–100 | `IRON_GOLEM` × 1.05 | 防禦姿態、震地重擊、暗影穿刺 |
| `rotten_zombie`／腐蝕殭屍 | `wild_mobs.yml` | 1–15 | `ZOMBIE` × 0.98 | 扇形揮擊、直線衝鋒 |
| `skeleton_warrior`／骷髏戰士 | `wild_mobs.yml` | 3–20 | `SKELETON` × 1.02 | 扇形揮擊、防禦姿態 |
| `shadow_archer`／暗影弓手 | `wild_mobs.yml` | 5–25 | `SKELETON` × 1.02 | 三連箭、暗影穿刺 |
| `venomous_spider`／毒牙蜘蛛 | `wild_mobs.yml` | 2–18 | `SPIDER` × 1.02 | 毒霧爆發、飛撲 |
| `unstable_creeper`／不穩定苦力怕 | `wild_mobs.yml` | 5–20 | `CREEPER` × 1.06 | 蓄力爆破、毒霧爆發 |
| `dark_witch`／暗夜女巫 | `wild_mobs.yml` | 8–30 | `WITCH` × 0.94 | 奧術落點、生命汲取 |
| `blaze_guard`／烈焰衛兵 | `wild_mobs.yml` | 10–30 | `BLAZE` × 0.94 | 防禦姿態、震地重擊 |
| `ironclad_undead`／鐵甲亡靈 | `wild_mobs.yml` | 10–25 | `ZOMBIE` × 0.94 | 扇形揮擊、直線衝鋒 |
| `prairie_hornbeast`／草原角獸 | `biome_expansion.yml` | 1–35 | `RAVAGER` × 0.65 | 直線衝鋒、扇形揮擊 |
| `sunfield_sentinel`／烈日守望者 | `biome_expansion.yml` | 20–60 | `HUSK` × 1.05 | 防禦姿態、雷擊落點 |
| `boss_prairie_alpha`／荒原角王 | `biome_expansion.yml` | 8–60 | `RAVAGER` × 1.15 | 族群戰吼、直線衝鋒、震地重擊 |
| `mossback_boar`／苔背野豬 | `biome_expansion.yml` | 1–35 | `HOGLIN` × 0.75 | 直線衝鋒、扇形揮擊 |
| `thornbark_guardian`／荊棘樹衛 | `biome_expansion.yml` | 20–60 | `IRON_GOLEM` × 0.85 | 防禦姿態、震地重擊 |
| `boss_ancient_oak`／古樹守望者 | `biome_expansion.yml` | 8–60 | `IRON_GOLEM` × 1.2 | 防禦姿態、毒霧爆發、震地重擊 |
| `dune_scorpion`／沙丘毒蠍 | `biome_expansion.yml` | 1–35 | `CAVE_SPIDER` × 1.1 | 毒霧爆發、飛撲 |
| `glassstorm_caster`／沙晶術士 | `biome_expansion.yml` | 20–60 | `HUSK` × 1.05 | 疾風推擊、奧術落點 |
| `boss_sandstorm_colossus`／沙暴巨像 | `biome_expansion.yml` | 8–60 | `RAVAGER` × 1.2 | 疾風推擊、直線衝鋒、雷擊落點 |
| `bog_frog`／毒沼巨蛙 | `biome_expansion.yml` | 1–35 | `FROG` × 1.5 | 毒霧爆發、飛撲 |
| `rotspore_shambler`／腐孢巡行者 | `biome_expansion.yml` | 20–60 | `BOGGED` × 1.05 | 生命汲取、毒霧爆發 |
| `boss_marsh_matriarch`／瘴沼巨獸 | `biome_expansion.yml` | 8–60 | `PANDA` × 1.3 | 毒霧爆發、震地重擊、生命汲取 |
| `crystal_mite`／晶簇螨 | `biome_expansion.yml` | 1–35 | `SILVERFISH` × 1.4 | 奧術落點、飛撲 |
| `echo_golem`／迴響傀儡 | `biome_expansion.yml` | 20–60 | `IRON_GOLEM` × 0.9 | 震地重擊、暗影穿刺 |
| `boss_crystal_heart`／晶心守護者 | `biome_expansion.yml` | 8–60 | `IRON_GOLEM` × 1.2 | 防禦姿態、奧術落點、雷擊落點 |
| `rime_lynx`／霜牙山貓 | `biome_expansion.yml` | 1–35 | `OCELOT` × 1.25 | 飛撲、冰霜射線 |
| `icebound_revenant`／封霜亡魂 | `biome_expansion.yml` | 20–60 | `STRAY` × 1.1 | 三連箭、冰霜射線 |
| `boss_glacier_ursine`／冰川熊王 | `biome_expansion.yml` | 8–60 | `POLAR_BEAR` × 1.3 | 直線衝鋒、震地重擊、冰霜射線 |
| `storm_goat`／風暴山羊 | `biome_expansion.yml` | 1–35 | `GOAT` × 1.15 | 直線衝鋒、疾風推擊 |
| `cliff_harrier`／峭壁掠翼 | `biome_expansion.yml` | 20–60 | `PHANTOM` × 1.2 | 疾風推擊、飛撲 |
| `boss_thunderhorn`／雷角霸主 | `biome_expansion.yml` | 8–60 | `RAVAGER` × 1.3 | 直線衝鋒、雷擊落點、震地重擊 |
| `ash_hound`／燼牙獵犬 | `biome_expansion.yml` | 30–35 | `WOLF` × 1.05 | 火焰吐息、飛撲 |
| `basalt_warden`／玄武岩衛士 | `biome_expansion.yml` | 30–60 | `WITHER_SKELETON` × 1.1 | 防禦姿態、火焰吐息 |
| `boss_ember_forge`／熾爐領主 | `biome_expansion.yml` | 30–60 | `MAGMA_CUBE` × 1.4 | 火焰吐息、震地重擊、防禦姿態 |
| `reef_crab`／礁岸甲蟹 | `biome_expansion.yml` | 1–35 | `SPIDER` × 0.7 | 扇形揮擊、防禦姿態 |
| `tide_reaver`／潮汐掠奪者 | `biome_expansion.yml` | 20–60 | `DROWNED` × 1.05 | 疾風推擊、三連箭 |
| `boss_tidebreaker`／裂潮巨像 | `biome_expansion.yml` | 8–60 | `IRON_GOLEM` × 1.2 | 疾風推擊、震地重擊、生命汲取 |
| `void_skitter`／虛空爬行者 | `biome_expansion.yml` | 45–60 | `ENDERMITE` × 1.4 | 暗影穿刺、飛撲 |
| `rift_sentinel`／裂隙哨衛 | `biome_expansion.yml` | 50–60 | `ENDERMAN` × 1.0 | 暗影穿刺、奧術落點 |
| `boss_rift_sovereign`／裂隙君王 | `biome_expansion.yml` | 50–60 | `ENDERMAN` × 1.5 | 奧術落點、生命汲取、雷擊落點 |
