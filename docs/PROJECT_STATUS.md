# CustomRPG 功能盤點與原版世界改造進度

最後更新：2026-10-17

設計依據：`PLUGIN_DESIGN_VANILLA_WORLD_ZERO_BUILDING.md`（使用者提供的 143 節設計稿）。

本文件區分「原有程式已有實作」、「本次完成」與「尚待開發」。已有實作代表存在程式與接線，不代表所有玩法都已通過實服驗收；本次也沒有一次實作整份設計稿。

## 1. 目前專案已有功能

| 系統 | 原有實作範圍 | 主要程式入口 |
| --- | --- | --- |
| 角色成長 | 等級、經驗、屬性點、天賦點；力量、魔力、敏捷、體質、防禦、精神；玩家 YAML 儲存 | [PlayerStats](../src/main/java/com/customrpg/players/PlayerStats.java)、[PlayerStatsManager](../src/main/java/com/customrpg/managers/PlayerStatsManager.java) |
| 種族 | 人類、精靈、獸人、矮人、不死族、惡魔、天使；基礎屬性、成長、武器加成與被動設定 | [RaceManager](../src/main/java/com/customrpg/races/RaceManager.java)、[種族設定](../src/main/resources/config/races.yml) |
| 自訂武器 | YAML 武器模板、稀有度、需求、特殊攻擊效果與武器技能 | [WeaponManager](../src/main/java/com/customrpg/managers/WeaponManager.java)、[WeaponListener](../src/main/java/com/customrpg/listeners/WeaponListener.java) |
| 裝備 | 護甲、飾品、欄位與需求檢查、屬性同步、套裝與符文資料載入；不等於完整鍛造／鑲嵌玩法已完成 | [EquipmentManager](../src/main/java/com/customrpg/equipment/EquipmentManager.java)、[ArmorManager](../src/main/java/com/customrpg/equipment/ArmorManager.java) |
| 天賦 | 烈焰、暗黑、武器、科技、自然、宿儺、赤血、刺客、野獸、弓系分支；天賦樹、主動／被動效果與切換 | [TalentBranch](../src/main/java/com/customrpg/talents/TalentBranch.java)、[TalentManager](../src/main/java/com/customrpg/managers/TalentManager.java)、[TalentSkillManager](../src/main/java/com/customrpg/managers/TalentSkillManager.java) |
| 戰鬥資源 | 魔力與回復、血液資源、弓系專注、野獸相關技能、技能冷卻與增益管理 | [managers](../src/main/java/com/customrpg/managers/)、[武器技能框架](../src/main/java/com/customrpg/weaponSkills/) |
| 技能操作與 GUI | 選單、屬性、種族、裝備、天賦介面；技能欄、快捷施法與切換提示 | [gui](../src/main/java/com/customrpg/gui/)、[SkillBarManager](../src/main/java/com/customrpg/managers/SkillBarManager.java) |
| 自訂怪物 | 模板等級範圍、生命／傷害成長、裝備、技能／特殊行為、時段與環境標籤、掉落物、武器與裝備掉落、擊殺經驗 | [MobManager](../src/main/java/com/customrpg/managers/MobManager.java)、[MobListener](../src/main/java/com/customrpg/listeners/MobListener.java)、[怪物模板](../src/main/resources/config/mobs/types/) |
| 偽裝與顯示 | LibsDisguises 整合、部分模型／顯示實體實作、怪物等級名稱、血量顯示、傷害數字、BossBar | [MobManager](../src/main/java/com/customrpg/managers/MobManager.java)、[HealthDisplayManager](../src/main/java/com/customrpg/managers/HealthDisplayManager.java)、[BossBarManager](../src/main/java/com/customrpg/managers/BossBarManager.java) |
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

Nether 與 End 使用各自的世界中心，預設沒有出生點安全圈，最低等級分別為 30、50；維度最低等級可高於距離分區的範圍。End 目前仍在生怪設定的預設排除清單內，且現有內容尚未提供完整 End 怪物池。

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
| 2. 村莊與 Bell | 未開始 | 真正村莊／鐘發現、持久 Village ID、動態村莊安全區、玩家發現紀錄、Waypoint 解鎖與傳送 |
| 3. 原版結構 | 未開始 | Structure 偵測與區塊快取、結構保護／優先怪物池、Mineshaft／Trial Chamber 等開放式副本、進度與個人寶箱 |
| 4. 事件與 Boss | 未開始 | 合法動態場地選取、村外防禦事件、傳送門事件、原版地形世界 Boss；取代固定地圖場地依賴 |
| 5. 採集與製作 | 未開始 | 原版礦物／樹木／植物／釣魚職業、原版工作站互動、鍛造／強化／分解／鍊金與材料倉庫 |
| 6. 長期進度 | 未開始 | 聲望、經濟、Codex、原生動態任務模板、主線、World Tier、Nether／End 完整內容與資料庫服務 |

後續優先補充：各生態域／維度／等級池覆蓋、可設定權重與結構優先選擇、稀有／Champion 階級及詞綴。每次功能完成後更新本表與驗收紀錄，勿將設計稿條目直接列為已完成。

## 5. 驗證紀錄

### 本次已執行

- 使用 JDK 21 編譯與 Maven 驗證；專案目前沒有正式測試套件，`mvn verify` 通過不等於 Paper 遊戲內測試通過。
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
