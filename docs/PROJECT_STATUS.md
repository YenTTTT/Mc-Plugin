# CustomRPG 功能盤點與原版世界改造進度

最後更新：2026-10-18

固定耗魔與種族加成調整已完成（COMPLETED）：天賦施放依 YAML `manaCost` 固定扣魔，不再按最大 MP 比例；種族六屬性合計從 Lv.1 的18點成長至 Lv.60 的36點，GUI顯示目前等級額外加成與成長預覽。最新 **mvn verify：441 tests 通過**；Paper實服仍 NEEDS_VERIFICATION，規則見 [RACES.md](RACES.md) 與 [BALANCE_RUNTIME.md](BALANCE_RUNTIME.md)。

天賦機制物品施法已恢復（COMPLETED）：102 招主動天賦具備物品／按鍵設定，主手施放、蹲下右鍵循環、GUI 操作提示；快捷鍵與種族 5 保留，施法共用戰鬥 v2 的魔力／生命消耗／冷卻。舊 HOTKEY 設定只在記憶體轉換，不覆寫原檔。該階段 **mvn verify：433 tests 通過**，實服操作及保護插件相容 NEEDS_VERIFICATION；操作見 [SKILL_SWITCH_SYSTEM.md](../SKILL_SWITCH_SYSTEM.md)。

戰鬥數值 v2 已接入：玩家／裝備／種族／天賦／怪物、HP與MP成長、一次性原檔備份遷移、遠程快照及一比一虛擬HP；部署與驗收見 [BALANCE_RUNTIME.md](BALANCE_RUNTIME.md)。**mvn clean verify：415 tests通過**；Paper與遊玩平衡仍 NEEDS_VERIFICATION。

Lv.1～60 初始數值模型見 [INITIAL_BALANCE_MODEL.md](INITIAL_BALANCE_MODEL.md)：每級三點、HP／防禦／攻速正規化武器／怪物 TTK 公式，60 級與 480 武器列、12,162 個敏感度案例；參考模型已接入戰鬥與自動遷移，正式部署後載入舊檔時執行。

怪物頭頂愛心血條已移除：不再生成／同步血條 ArmorStand，啟動及實體載入時只清理本插件標記的舊血條，並去除舊名稱血量後綴；怪物名稱／等級、玩家 HUD、BossBar 保留。實際 Paper 顯示與舊區塊載入清理仍待伺服器驗證。

自然生怪排查與最新診斷見 [MOB_SPAWNING.md](MOB_SPAWNING.md)。每次重啟後仍需 `/finishrpg`；預設 vanilla 只替換原版自然敵怪。怪物外觀與地形 Boss 見 [MOB_ENCOUNTERS.md](MOB_ENCOUNTERS.md)。新天賦見 [TALENT_EXPANSION.md](TALENT_EXPANSION.md)。工坊／村莊商人見 [CRAFTING_AND_MERCHANTS.md](CRAFTING_AND_MERCHANTS.md)。完整測試最新為 441 項，下方各階段數字保留為歷史紀錄。

前輪紀錄的設計依據：`PLUGIN_DESIGN_VANILLA_WORLD_ZERO_BUILDING.md`（使用者提供的 143 節設計稿）。該原稿未存於目前 Repository；本輪以實際程式、Git 與使用者要求的原版世界原則核對，不宣稱已重新審閱原稿。

2026-10-18 接手結果與完整審查／後續工作見 [AI_AGENT_HANDOFF.md](AI_AGENT_HANDOFF.md)。下方第 2–3 節為前輪功能紀錄，本輪保留其生怪設計，補完裝備接線並修復編譯與生命週期問題。

本文件區分「原有程式已有實作」、「本次完成」與「尚待開發」。已有實作代表存在程式與接線，不代表所有玩法都已通過實服驗收；本次也沒有一次實作整份設計稿。

## 1. 目前專案已有功能

| 系統 | 原有實作範圍 | 主要程式入口 |
| --- | --- | --- |
| 角色成長 | 等級、經驗、屬性點、天賦點；力量、魔力、敏捷、體質、防禦、精神；玩家 YAML 儲存 | [PlayerStats](../src/main/java/com/customrpg/players/PlayerStats.java)、[PlayerStatsManager](../src/main/java/com/customrpg/managers/PlayerStatsManager.java) |
| 種族 | 原七族＋龍裔、獸裔、暗精靈、半身人、魔像族，共十二族；屬性成長、武器／箭矢親和、暴擊、移速、脫戰回復；十二族各有主動技能，快捷鍵 5 施放 | [RaceManager](../src/main/java/com/customrpg/races/RaceManager.java)、[種族評估](RACES.md)、[新增設定](../src/main/resources/config/races_additional.yml) |
| 自訂武器 | YAML 武器模板、稀有度、需求、特殊攻擊效果與武器技能 | [WeaponManager](../src/main/java/com/customrpg/managers/WeaponManager.java)、[WeaponListener](../src/main/java/com/customrpg/listeners/WeaponListener.java) |
| 裝備 | 護甲、飾品、需求與屬性；新增 980 件工坊裝備、素材工作台配方與村莊綠寶石商人；不等於完整鍛造／鑲嵌玩法 | [EquipmentManager](../src/main/java/com/customrpg/equipment/EquipmentManager.java)、[ArmorManager](../src/main/java/com/customrpg/equipment/ArmorManager.java) |
| 天賦 | 烈焰、暗黑、武器、科技、自然、宿儺、赤血、刺客、野獸、弓系，以及新增冰霜／雷霆／大地／聖光，共十四分支、164 天賦；主動快捷鍵 1～4 | [TalentBranch](../src/main/java/com/customrpg/talents/TalentBranch.java)、[TalentManager](../src/main/java/com/customrpg/managers/TalentManager.java)、[TalentSkillManager](../src/main/java/com/customrpg/managers/TalentSkillManager.java) |
| 戰鬥資源 | 魔力與回復、血液資源、弓系專注、野獸相關技能、技能冷卻與增益管理 | [managers](../src/main/java/com/customrpg/managers/)、[武器技能框架](../src/main/java/com/customrpg/weaponSkills/) |
| 技能操作與 GUI | 選單、屬性、種族、裝備、天賦介面；技能欄、快捷施法與切換提示 | [gui](../src/main/java/com/customrpg/gui/)、[SkillBarManager](../src/main/java/com/customrpg/managers/SkillBarManager.java) |
| 自訂怪物 | 模板等級範圍、生命／傷害成長、裝備、技能／特殊行為、時段與環境標籤、掉落物、武器與裝備掉落、擊殺經驗 | [MobManager](../src/main/java/com/customrpg/managers/MobManager.java)、[MobListener](../src/main/java/com/customrpg/listeners/MobListener.java)、[怪物模板](../src/main/resources/config/mobs/types/) |
| 偽裝與顯示 | LibsDisguises 整合、部分模型／顯示實體實作、怪物等級名稱、玩家血量 HUD、傷害數字、BossBar（怪物頭頂愛心血條已移除） | [MobManager](../src/main/java/com/customrpg/managers/MobManager.java)、[HealthDisplayManager](../src/main/java/com/customrpg/managers/HealthDisplayManager.java)、[BossBarManager](../src/main/java/com/customrpg/managers/BossBarManager.java) |
| 任務整合 | BeautyQuests 自訂怪物工廠、擊殺通知、冷卻管理；不是完整的原版世界動態任務服務 | [BeautyQuestsHook](../src/main/java/com/customrpg/integration/BeautyQuestsHook.java)、[QuestCooldownManager](../src/main/java/com/customrpg/integration/QuestCooldownManager.java) |
| NPC | 自訂 NPC 建立／保存、區塊載入處理、互動、BeautyQuests 任務綁定介面 | [NPC 模組](../src/main/java/com/customrpg/integration/npc/) |
| 舊式區域 | 矩形等級區域選取、保護區、安全區、固定 Boss 區域；Boss 專屬小怪、冷卻、排他生成與邊界限制 | [ZoneManager](../src/main/java/com/customrpg/managers/ZoneManager.java)、[ProtectionAreaManager](../src/main/java/com/customrpg/managers/ProtectionAreaManager.java)、[BossZoneManager](../src/main/java/com/customrpg/managers/BossZoneManager.java) |
| 管理指令 | `/weapon`、`/custommob`、`/rpg`、`/stats`、`/stat`、`/equipment`、`/talent`、`/race`、`/menu`、`/mana`、`/skillbar`、`/rpgnpc`、`/setmob`、`/protectarea`、`/bosszone`、`/mobspawn`、`/finishrpg` | [指令註冊](../src/main/java/com/customrpg/CustomRPG.java)、[plugin.yml](../src/main/resources/plugin.yml) |

目前玩家資料主要使用 YAML，不能視為設計稿中的資料庫、非同步持久化、聲望或經濟服務已完成。舊天賦技能與新武器技能框架仍並存。

## 2. 本次完成：第一階段原版世界生怪核心

對應設計稿第 12、17–22、29、53–58、65、129 節的部分內容。

### 2.1 世界水平距離分區

- 新增 [WorldZoneManager](../src/main/java/com/customrpg/managers/WorldZoneManager.java) 與 [DistanceZone](../src/main/java/com/customrpg/world/DistanceZone.java)。
- 各世界預設以該世界的 **World Spawn** 為中心，不再寫死舊地圖座標；可設定個別世界的中心。
- 只計算 `hypot(x - originX, z - originZ)`，爬山、下洞或換世界不再使用舊的三維距離／其他世界距離歸零公式。
- 等級以所在區域內的距離插值，加上預設 `-2 ~ +2` 隨機變動，再限制於區域等級範圍；精英不額外突破範圍。
- 不讀取玩家等級決定怪物等級。超過最後距離範圍時沿用最後區域，主世界預設最高 Lv.60。

| 主世界水平距離 | 建議等級 |
| --- | --- |
| `0 <= distance < 150` | 安全區，不生成敵對原版怪或受本插件管理的自訂怪 |
| `150 <= distance < 700` | Lv.1–10 |
| `700 <= distance < 1600` | Lv.8–20 |
| `1600 <= distance < 2800` | Lv.18–30 |
| `2800 <= distance < 4500` | Lv.28–40 |
| `4500 <= distance < 6500` | Lv.38–50 |
| `6500 <= distance` | Lv.48–60，8500 後基礎等級封頂 |

Nether 與 End 使用各自的世界中心，預設沒有出生點安全圈，最低等級分別為 30、50；維度最低等級可高於距離分區的範圍。新增終界普通怪與荒野首領後，新安裝不再預設排除 End；舊部署若仍排除，需自行從 disabled-worlds 移除該世界。這不是完整終界玩法或主線。

### 2.2 以原版自然生成事件驅動

預設流程：

```text
原版 CreatureSpawnEvent（預設只處理 NATURAL 敵對生物）
  → 原版與其他插件先完成事件處理；已取消事件不接管
  → 下一 tick 確認原怪仍有效、未命名、不是載具／乘客，也未標記為自訂怪
  → 檢查啟動狀態、世界、世界邊界、安全區、保護區與 Boss 排他區
  → 檢查附近生存／冒險玩家、自訂怪上限、同區塊上限與怪物間距
  → 原位置可否容納陸地怪（不傳送到地表或別的欄位）
  → 維度／地表生態域／洞穴標籤 + 時段 + 等級範圍選模板
  → 擲普通／精英階級，套用既有屬性、行為與掉落系統
  → 自訂怪有效生成後才移除原怪；沒有匹配模板或生成失敗則保留原版怪
```

- 預設 `mode: vanilla`，不會再同時執行玩家周圍的定時補怪，避免兩套來源疊加。
- 保留 `mode: supplemental`，供管理員選擇定時補怪；兩個模式互斥。
- 保留 `/finishrpg` 啟動門檻；每次重啟仍須執行。安全區的原版敵怪生成阻擋不依賴此指令。
- 預設不替換刷怪籠、指令、其他插件的 `CUSTOM`、試煉、突襲、繁殖等來源；`CUSTOM`／`COMMAND` 即使誤加至設定也不接管，避免遞迴。
- 不繞過原版自然生成的亮度、玩家距離與全域數量規則。因此不是執行指令後幾秒內必定出怪，`/summon` 也不能用來驗收自然替換。
- 額外預設限制：每位玩家附近 10 隻自訂怪、每個區塊 8 隻、怪物間距 4 格；達上限時保留原版怪，而非刪除原怪。
- 一般／精英野外怪允許原版遠距離卸除，不再全部強制常駐。

### 2.3 環境池、時段與精英

- 使用現有怪物模板的 `tags`、`spawn-time`、`level-range`，模板在符合條件的池內等機率選取。
- 支援 `plains`、`forest`、`jungle`、`desert`、`swamp`、`ice`／`snow`、`mountain`、`ocean`、`cave`、`nether`、`end` 標籤。
- Nether／End 只使用對應維度標籤；主世界地下優先使用 `cave`，避免洞穴生成變成地表補怪。
- 不再以「時間篩選後是空的就用全部怪物」回退；也不再以最近等級模板跨區填補。
- 新安裝精英基礎機率 1%，每 10 級加 1%，最高 7%；沿用現有精英生命倍率、名稱前綴、發光與經驗加成。
- 本次沒有新增 Rare／Champion、隨機精英詞綴、專屬稀有掉落或圖鑑。既有模板自帶特殊行為與掉落仍照常使用。
- 目前仍是陸地安全落點驗證；水生、飛行或大型生物位置不適用時保留原版怪。海洋／End／部分等級池內容不足也會保留原版怪，不代表已有完整生態內容。

### 2.4 安全區與追擊範圍

- 新世界預設使用出生點 150 格的水平圓柱安全區，涵蓋地下與高處。
- 新增 [MobLeashManager](../src/main/java/com/customrpg/managers/MobLeashManager.java)：保存出生世界、X/Y/Z、出生區域及區域最低等級至實體 PDC。
- 軟限制 48 格：禁止取得安全區、較低區域或超出出生點追擊距離的目標；超出軟距離後清除目標並尋路回家。
- 硬限制 80 格、進入安全區或較低區域：返回原出生點；出生點區塊未載入、落點已無效或變成安全區時移除，不強載區塊、不發放擊殺獎勵。
- 定期僅處理追蹤的已載入怪物；區塊載入後依 PDC 恢復追蹤。既有 Boss 區域仍使用自己的邊界管理。
- 統一自訂怪安全落點的世界邊界、安全區、保護區及已載入區塊檢查；Boss 與小怪亦使用檢查後的精確位置，不再二次搜尋跳到其他高度。
- 此處「安全區」不代表已實作完整 PvP、環境傷害免疫或所有原版怪物的追擊防護。

### 2.5 區域通知與管理資訊

- 新增 [WorldSpawnListener](../src/main/java/com/customrpg/listeners/WorldSpawnListener.java)，處理自然替換、目標限制、載入追蹤與跨區通知。
- 登入、移動或傳送時依區域鍵顯示區名／建議等級；停留同區不連續刷標題。
- `/mobspawn status` 改顯示目前模式、水平距離、實際世界中心、區域資訊與生怪統計。
- `/finishrpg` 不再保證 3–10 秒內出怪，並能辨識出生點安全圈。

## 3. 設定與舊資料遷移

| 設定檔 | 用途 |
| --- | --- |
| [world_zones.yml](../src/main/resources/config/world_zones.yml) | 新增：距離分區、各維度安全半徑／最低等級、世界中心覆寫、跨區通知、舊區域開關 |
| [mob_spawner.yml](../src/main/resources/config/mob_spawner.yml) | 生怪模式、來源、補怪參數、數量限制、精英生命倍率、追擊距離、亮度與排除世界 |
| [怪物模板](../src/main/resources/config/mobs/types/) | 各怪物的 `tags`、`spawn-time`、`level-range`、屬性、技能、裝備與掉落 |
| [safe_zones.yml](../src/main/resources/config/safe_zones.yml)／[boss_zones.yml](../src/main/resources/config/boss_zones.yml) | 舊地圖固定座標區域，資料保留但預設不載入 |

升級步驟：

1. 備份伺服器世界、玩家資料與 `plugins/CustomRPG/`。
2. 更換 JAR、重啟伺服器；插件會建立缺少的 `config/world_zones.yml`，**不覆寫已存在的設定檔**。
3. 新設定預設 `legacy-regions-enabled: false`，停用原地圖城堡／寺廟／村莊安全區與固定 Boss 場地，避免任意 Seed 出現不存在的地標。這會改變舊地圖安全區與 Boss 行為；要保留它們，請設為 `true`。
4. 舊 `mob_spawner.yml` 沒有 `mode` 時採用 `vanilla`。請自行補上本次新增參數，或依新版資源設定比對合併；舊精英機率 20% 將被上限限制到 7%，若要使用新的起始值請改成 `0.01`。
5. 舊 `difficulty-scaling.origin`／`blocks-per-level`、`safe-zone`、各階級 `level-offset-*` 不再決定新流程。舊 `MobSpawnManager` 類別仍保留，但主插件啟動的是 `DistanceSpawnManager`，不能將舊類別中的玩家縮放／Boss 保底當成目前啟用功能。
6. 若要以原版村莊為新手中心，先自行找到村莊，使用 `/setworldspawn`，或設定 `worlds.<世界名稱>.origin: { x: ..., z: ... }`。本次**沒有自動尋村、建村、放鐘或修改方塊**。
7. 執行 `/mobspawn reload` 重載世界、生怪、安全區與 Boss 區域設定；已啟動的狀態會保留。重載會清除已追蹤的區域 Boss 與小怪，避免停用或更換區域後殘留，再由正常流程生成。再以 `/finishrpg` 啟動尚未啟動的系統，以 `/mobspawn status` 檢查。

新模式不依賴 `/setmob` 人工矩形區域決定怪物等級；該工具與舊資料仍保留，其舊 tier 診斷不代表新的距離分區。世界邊界／預生成不會自動修改，需由伺服器管理員另行設定。

## 4. 開發進度與後續排程

| 階段 | 狀態 | 工作 |
| --- | --- | --- |
| 0. 基礎盤點 | 完成 | 依程式與註冊點整理既有系統，區分舊實作與設計目標 |
| 1. 原版世界生怪核心 | 程式完成，待實服驗收 | 水平距離分區、自然生成替換、環境／時段／等級篩選、上限、普通／精英、追擊限制、通知與設定遷移 |
| 2. 村莊與 Bell | 部分程式完成，待實服驗收 | 已有生成村莊結構 ID 與裝備商人；Bell、動態安全區、玩家發現紀錄、Waypoint 尚未開始 |
| 3. 原版結構 | 未開始 | Structure 偵測與區塊快取、結構保護／優先怪物池、Mineshaft／Trial Chamber 等開放式副本、進度與個人寶箱 |
| 4. 事件與 Boss | 部分程式完成，待實服驗收 | 動態事件 Framework＋Meteor Impact 已實作與本機測試；村外防禦、Demon Rift、十種地形荒野 Boss 已加入，既有固定 Boss 區域保留 |
| 5. 採集與製作 | 部分程式完成，待實服驗收 | 已有打怪素材、980 件工作台裝備、核心拆解與配方圖鑑；採集職業、其他工作站／鍊金、完整鍛造及材料倉庫未開始 |
| 6. 長期進度 | 未開始 | 聲望、經濟、Codex、原生動態任務模板、主線、World Tier、Nether／End 完整內容與資料庫服務 |

後續優先補充：各生態域／維度／等級池覆蓋、可設定權重與結構優先選擇、稀有／Champion 階級及詞綴。每次功能完成後更新本表與驗收紀錄，勿將設計稿條目直接列為已完成。

## 5. 驗證紀錄

### 前輪已記錄（歷史紀錄）

- 前輪曾記錄使用 JDK 21 編譯與 Maven 驗證，當時沒有正式測試套件。2026-10-18 接手重新編譯實際發現跨 package helper 存取錯誤，故不能依前輪成功紀錄推定 HEAD 可建置；本輪已修復，結果見第 6 節。
- YAML 解析檢查：新增距離設定、修改的生怪設定與現有怪物模板可解析；六段距離／等級範圍逐一對照設計稿。
- 直接以 Java 執行 `DistanceZone`：六區合計 60,000 次隨機距離檢查，另驗證起終點插值、超界距離、等級限制與非法區域拒絕。
- `git diff --check` 檢查；原先已有的未追蹤檔案保留不變。

本機 Maven 的預設 `JAVA_HOME` 與中文路徑編碼曾造成舊快取／編譯問題；本次以 JDK 21 及 UTF-8 執行成功，未改動專案依賴或使用者的永久環境變數。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
$env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8'
mvn verify
```

輸出為 `target/CustomRPG-1.0.jar`。目前編譯依賴為 Paper API `1.21.11-R0.1-SNAPSHOT`，請先在相容版本的測試服驗收，不要直接推定相容所有 1.21.x 版本。

### 尚待 Paper 測試服驗收

- [ ] 新 Seed／不同 World Spawn 下，出生點安全圈位置正確；高空與地下不繞過水平安全範圍。
- [ ] 在距離 150、700、1600、2800、4500、6500、8500 附近檢查區域、邊界與等級；不同玩家等級不改變結果。
- [ ] 白天、夜晚、森林、雪地、沙漠、洞穴、Nether 與無匹配模板處符合篩選；洞穴怪留在原高度。
- [ ] 原版自然怪成功替換時不重複；被其他插件取消、命名、載具／乘客、刷怪籠、`CUSTOM`、`COMMAND` 不誤替換。
- [ ] 多玩家重疊活動時，附近與區塊自訂怪上限、間距皆生效；原版生怪上限沒有因兩套補怪重疊而失控。
- [ ] 軟限制回家、硬限制返回、禁止高區怪追到低區／安全區、出生點區塊未載入時不強載；卸載再載入恢復追蹤。
- [ ] `/mobspawn reload` 保留啟動狀態，`vanilla`／`supplemental` 互斥，世界切換與傳送不重複刷通知。
- [ ] 舊區域預設停用；明確重新啟用後，Boss／小怪不進入安全區、不越過保護區或世界邊界。
- [ ] 裝備掉落、經驗、天賦互動、偽裝與 BeautyQuests 擊殺進度對新自然怪仍正常。

## 6. 2026-10-18 中斷恢復與本輪驗證

- 接手工作樹乾淨，未完成內容已包含在 `1520e53` 中；未丟棄或重寫前輪成果。
- **COMPLETED（本機驗證）**：修復編譯、補上原物品裝卸與保管存檔接線、PDC／強化同步、GUI 轉移／拖曳防護、損壞存檔與離線寫入失敗保護、技能副手重複觸發、被動回血工作生命週期、依賴描述及 exact 高度驗證。
- `mvn clean verify` 成功：39 項測試通過，0 failures／errors／skipped；29 份資源 YAML 以 Bukkit loader 解析通過，JAR 為 `target/CustomRPG-1.0.jar`。
- **僅完成程式修正／仍需實服**：BeautyQuests 可選啟動、真實 ItemStack NBT／序列化、物品總量、死亡政策、自然替換、追擊、Boss、傷害事件順序與第三方插件相容性。未執行 Paper Server 整合測試。
- **IN_PROGRESS**：跨檔存檔一致性、其他持久化檔案失敗保護、虛擬 HP 事件競爭驗證、裝備需求整合與效能基準；詳見交接文件。
- **NOT_STARTED（本段為上一輪恢復紀錄）**：村莊／Bell、結構、製作、資料庫等後續服務；動態事件的新需求進度見第 7 節。

## 7. Dynamic World Events / Meteor Impact（2026-10-18）

本次新需求允許插件程式生成暫時地形，結束必須恢復；Zero Manual Building 不等於禁止 procedural 地形。本輪在 `3b7aeb0` 上新增通用事件 Framework 與第一個 Meteor 事件，未重寫 Race／Skill／Equipment。

- **程式完成＋本機測試**：正式狀態機、持久隨機排程、保守自然選址、不可變快照、批次坑洞／核心、BlockDisplay 動畫、19 隻一次性怪物、貢獻、個人素材錢包、管理指令、清理／重啟還原。
- 玩家指令：`/events [info|list|wallet|claim <eventId>]`；管理員：`/mmorpg event ...`。設定為 `config/world_events.yml`，資料在 `data/world-events/`。
- **一般選址限制**：只接受插件啟用後觀測的新自然區塊，舊未知區塊不會自動信任；已知領地／編輯插件未接 adapter 時拒絕選址。無安全候選就取消、延後，start 的 here 不繞過驗證。
- **選址回報修正**：start 的初始回覆只代表排入選址；失敗現在直接通知發令者，列出嘗試數及各原因次數，info／伺服器紀錄保留原始診斷。新增 4 項回歸測試，連同原有共 88 項（本機 `mvn verify`）。
- **WorldGuard／WorldEdit 整合修正**：自動接上 WorldGuard 7 的區域與全域旗標檢查；標準 WorldEdit 7 在編輯前持久撤銷自然來源資格，拒絕事件／恢復區編輯，停用時解註冊。使用可選 provided 依賴；缺少插件可啟動，查詢失敗則拒絕。FAWE 仍未支援。新增 13 項測試，目前共 101 項（本機 `mvn verify`）；真實插件組合仍需實服驗收。
- **管理員測試模式**：`/mmorpg event test meteor here` 允許舊區塊、本插件安全區與更多簡單材質，放寬高低差並略過結構區塊／生態域限制；領地、液體、容器、快照及還原照常保留。測試標記持久化；一般事件不套用放寬規則。新增 12 個測試執行案例，完整套件共 113 項（本機 `mvn verify`）。
- **WorldEdit 診斷／測試補修**：編輯工具與領地提供者分開處理，固定位置管理員測試不再要求 WorldEdit／FAWE 追蹤；WorldGuard 等真正領地查詢仍必須通過，一般事件仍要求可靠編輯追蹤。info 顯示插件版本與初始化失敗原因，註冊失敗清理部分 hook，名稱正規化避免大小寫繞過。新增 3 項回歸案例，目前完整套件共 116 項；測試期間不可用未追蹤的編輯工具修改場地。
- **使用者已選定獎勵方式**：持久素材錢包；領獎標記與素材餘額同檔原子更新，沒有實體提領／Gold／額外 EXP 領獎。
- **恢復政策**：中斷事件啟動後進入清理，不補怪；已完成事件仍可用 ID 補領。未知地形衝突保留快照並停止覆寫。
- **待 Paper 實服驗收**：視覺、AI／碰撞、真實傷害與第三方插件、完整世界存檔耐久性、chunk 卸載與效能。不能把本機測試當成實服成功。

操作、保護邊界、擴充點與完整驗收清單見 [WORLD_EVENTS.md](WORLD_EVENTS.md)；開發階段／驗證結果見 [AI_AGENT_HANDOFF.md](AI_AGENT_HANDOFF.md)。

## 8. 種族擴充與修正（2026-10-18）

新增龍裔／獸裔／暗精靈／半身人／魔像族；新增檔獨立載入，原設定逐欄位優先，不覆寫玩家已選 ID 或七族自訂數值。選單擴至 54 格並分頁，保護圖示與清理狀態。

修正第一級成長偏移、種族覆寫等級魔力、裝備／種族／天賦魔力互相干擾、重置殘留加成、普通選族繞過、管理員換族先刪後存、壞檔與重載失敗；實作移速與單一共用脫戰回復 timer，接上原版箭矢種族親和與暴擊。該階段的舊技能名稱已由第 9 節的實際主動技能取代。

22 項新增測試，完整 `mvn verify` 為 138 tests／0 failures／0 errors／0 skipped；32 份 YAML 可解析，輸出 `target/CustomRPG-1.0.jar`。既有七族的數值差異、優先平衡對象與未完成範圍見 [RACES.md](RACES.md)。沒有實服傷害／多人平衡驗收，沒有自動調整原七族數值。

## 9. 技能快捷鍵與十二族主動技能（2026-10-18）

依使用者要求移除天賦機制材料與點擊／蹲下切換入口，改為預設開啟的快捷技能列：1～4 施放已學且攜帶的天賦，5 固定施放種族技能，6～9 選取手持裝備；`/skillbar` 切回原版物品欄，偏好以 PDC 保存。天賦仍檢查資源與冷卻，空手也能施放；武器自帶技能保留原操作。

十二族各有不同主動能力，GUI／資訊指令顯示真正的能力、消耗與冷卻。龍裔展開粒子雙翼並飛行 12 秒，普攻追加傷害與點燃；效果到期、換族、死亡、登出、傳送及停用均處理清理，僅回收本技能授予的飛行。加入空中收翼的下降保護及 PDC 殘留授權回收；種族共用冷卻不因換族／重登重置。

新增 41 項回歸案例，完整 `mvn clean verify` 共 **179 tests／0 failures／0 errors／0 skipped**，輸出 `target/CustomRPG-1.0.jar`。實際 Paper 客戶端按鍵、粒子外觀、飛行手感及多人插件相容仍待實服驗收。操作、十二族效果及原版按鍵限制見 [SKILL_SWITCH_SYSTEM.md](../SKILL_SWITCH_SYSTEM.md)。

## 10. 自然生怪診斷補修（2026-10-18）

使用者回報看不到自訂怪自然生成。確認原有啟動門檻與 vanilla 事件替換設計，未取得實服 status，尚不能判定該伺服器的實際阻擋原因。修正來源設定大小寫／空白導致完全不匹配；補上原本靜默跳過的結果、原版事件與取消統計、世界生怪開關／上限及當前模板池診斷。Debug 即使沒有事件也每約 30 秒 tick 時間輸出摘要，`/finishrpg` 也會提示世界生怪阻擋。

未改動預設模式、重啟啟動門檻、安全區、世界規則或模板內容；取消事件只觀測、不排程，替換失敗保留原怪。新增 14 項回歸測試，完整 `mvn verify` 為 **193 tests／0 failures／0 errors／0 skipped**；輸出 `target/CustomRPG-1.0.jar`。實服驗收及真正原因仍待使用者回傳狀態；操作見 [MOB_SPAWNING.md](MOB_SPAWNING.md)。

## 11. 怪物外觀、戰鬥招式與十種地形 Boss（2026-10-18）

使用者選定原版客戶端即可。新增外觀／機制 overlay，為資源中的 123 個既有 ID 加上原版生物、尺寸、裝備與特效；再新增 20 普通怪及 10 荒野 Boss，共 153 profiles。暴怒熊人改為 1.3 倍棕色熊貓，衝鋒／震地、35% 生命狂暴。保留六個專屬 Boss／熔核分裂 ID 的原機制，其餘 147 profiles 使用可蓄力躲避的招式序列。

新增內容涵蓋平原、森林、沙漠、沼澤、洞穴、冰雪、山地、Nether、海岸與 End。十個帶 wilderness-boss 標籤的首領共用正常生成嘗試，預設 1% 機率、每世界上限二、1024 格區域冷卻 30 分鐘、存活 20 分鐘；身分與冷卻先原子保存，資料故障只停荒野首領。既有 event-only 與召喚物不混入普通池。

傷害檢查視線、安全區與追擊限制，命中取消不追加效果；衝鋒依實際移動路徑，投射物有上限／期限並在命中再驗證。尺寸只改自身 transient modifier。原版爆炸／改方塊／雪跡被攔截，舊熔核的火／岩漿技能改粒子能量點，不殘留地形。共用 timer 限每五 ticks 128 隻，卸載、重載與停用清除自身狀態／投射物。

新檔自動補建，舊模板數值／掉落不覆寫；新安裝移除 End 預設排除，舊部署仍需自行合併。`/custommob spawn <id> [level]` 可指定測試等級，`/custommob info` 顯示實際外觀／招式，`/mobspawn reload` 重讀 overlay 與荒野 Boss 規則，改 MobData 模板需重啟。自然替換仍需每次 `/finishrpg`，沒有默改預設模式。

新增五類／38 項測試，完整 `mvn clean verify` **231 tests／0 failures／0 errors／0 skipped**；149 production Java、39 test 類別、35 YAML；JAR `target/CustomRPG-1.0.jar`。未啟動 Paper，外觀、真實 AI／碰撞、多人平衡及效能仍需實服驗收。完整名冊、設定及測試指令見 [MOB_ENCOUNTERS.md](MOB_ENCOUNTERS.md)。

## 12. 四種新天賦流派與 24 個天賦（2026-10-18）

保留原十流派／140 個定義，新增冰霜控場、雷霆連鎖、大地守衛、聖光支援四流派，各四主動＋兩被動、每招三級，每級一點，合計十四流派／164 天賦。新增檔 config/talents_additional.yml 自動建立，舊 talents.yml 同 ID 整個定義優先，玩家投點與快捷鍵綁定保留。1～4 仍施放主動天賦，5 仍為種族技能。

ExpandedTalentSkills 使用共用有界領域 timer，接上射線／碎冰、連鎖／擊退／安全位移、岩甲／同隊減傷陣、治療／淨化／同隊聖域。傷害尊重保護、遮擋及取消；無有效命中不追加效果；Boss 緩速時間減半。治療走 CUSTOM 事件，減傷陣取最高值，領域到期／卸載／死亡／登出／傳送／重置／重載／停用清除，沒有放置或破壞地形。

主選單擴到 54 格，顯示十四流派並加上實際 Inventory 身分、拖曳與關閉清理。分支指令／Tab 接上全部 enum，修正 warrior／mage 別名。/talent reload 從假成功訊息改成實際讀取兩檔、驗證後交換；錯誤保留原樹與持續狀態，成功重算線上玩家並清新領域。

新增四類測試與既有施法測試兩案，共 54 項；完整 `mvn clean verify` **285 tests／0 failures／0 errors／0 skipped**。150 production Java、43 test 類別、36 YAML；JAR target/CustomRPG-1.0.jar。未啟動 Paper，實服外觀、多人支援與戰鬥平衡仍待驗收。名冊、等級效果與操作見 [TALENT_EXPANSION.md](TALENT_EXPANSION.md)。

## 13. 工坊裝備、實體素材與村莊裝備商（2026-10-18）

使用者要求大量裝備、打怪素材可在工作台合成，以及每個村莊按所在地等級販售裝備；已選綠寶石貨幣。新增十種地形主題×七階×十四類共 980 件（560 武器／280 護甲／140 飾品）、84 素材、980 裝備配方＋七個核心拆解配方。八種武器／四護甲／戒指／項鍊接上原裝備、屬性及需求；新弓／長槍補發射等級與傷害快照。

自然敵怪與自製怪有玩家擊殺歸屬時掉素材，按怪物等級或地區等級選階級；事件專用／召喚物排除，隕石錢包獨立保留。工作台用原版 Shaped／Shapeless 配方與精確物品資料，PDC 防同名冒充，原版處理 Shift 批次扣料交付。/rpgcraft 有可翻頁配方／素材預覽、地形階級篩選與診斷；預覽不可取走。

原版生成村莊區塊與實體資料載入後，有安全空位就補一位固定盔甲商，不建房、不覆蓋原村民。每店 28 商品、每項八次庫存、30 分鐘補貨；級別按商人站位的 WorldZoneManager 基礎等級，與開店玩家等級無關；綠寶石走原版交易。村莊 ID／商人 UUID／卸載區塊原子保存，先存身分再生成，未知實體載入狀態不補第二隻。商人壞檔／IO 失敗只停商人，其餘工坊仍可用。

新設定 config/crafting_progression.yml 自動建立；資料 data/village_merchants.yml，玩家物品及庫存走原版存檔。保留舊裝備 YAML，重新載入舊裝備／武器會接回工坊模板。村莊／工坊不要求 /finishrpg；自然替換依舊需要。人工床鐘村、村莊安全圈、發現紀錄與 Waypoint 尚未加入。

新增七類／73 案，完整 `mvn clean verify` **358 tests／0 failures／0 errors／0 skipped**；159 production Java、50 test 類別、37 YAML，JAR target/CustomRPG-1.0.jar。未啟動 Paper，真實合成／滿包／多人交易／結構載入、外觀／平衡及效能待驗收。操作與完整 CSV 見 [CRAFTING_AND_MERCHANTS.md](CRAFTING_AND_MERCHANTS.md)。
