# 舊版天賦恢復紀錄

> **永夜禁術更新（2026-10-23）**：暗黑依新草案由 12 主動合併成 8，總數 204；保留原始存檔與投點，以最高 rank 對應新招式。未恢復八大主題，其他流派與獵人分頁保留。見 [DARK_RUNTIME.md](DARK_RUNTIME.md)。

> **現行大地更新（2026-10-23）**：大地依新草案新增為 7 普通＋1 大絕，總數現為 208，仍維持 15 舊流派及八大主題撤除。舊四 ID／已學等級保留；泰坦與冰冠共用一個 Shift＋F 槽，見 [EARTH_RUNTIME.md](EARTH_RUNTIME.md)。

> **後續冰霜更新（2026-10-23）**：保留此頁作為撤除八大主題的紀錄。當前冰霜改為 7 普通＋1 大絕，總數 204；配置零度王權後 Shift＋F 可施放大絕。八大主題仍撤除、舊 rank 保留，651 項測試通過。操作與驗收見 [FROST_RUNTIME.md](FROST_RUNTIME.md)。

2026-10-23，依使用者要求撤除八大主題與新增核心，恢復舊版流派天賦。

**COMPLETED**：`/talent` 直接顯示 15 個流派、200 個天賦（130 主動、66 被動、4 武器被動）。保留烈焰、暗黑、武器、科技、自然、宿儺、赤血、刺客、野獸、弓箭、冰霜、雷霆、大地、聖光、獵人；獵人六個共通／角色分頁保留。

八大主題選單、`/talent core`、80 個 `v3_*` 核心定義、核心技能執行器、召喚、被動戰鬥與血量加成都已移除。`talents_core.yml` 不再打包、生成或讀取；伺服器若有舊檔，只是未使用的殘留設定。既有 200 個天賦的 ID、數值、傷害與前置不因本次撤除重設。

舊主動技能在天賦頁右鍵選擇技能槽，使用 **1～4／F** 施放，**5** 仍為種族能力。專用核心大絕槽與 HUD 提示撤下；**Shift＋F** 恢復原版交換副手。`ultimate-skill` 欄位僅為存檔相容保留，不能從快捷鍵或物品施放。

玩家等級、舊天賦、所有已存投點與快捷槽原位保留。已移除核心的 rank／投點紀錄不刪除、不自動退款，殘留綁定視為不存在技能，可自行綁上舊技能。若要退回所有投點後重配，由管理員使用現有 `/talent reset <玩家>`；這會一併重置該玩家全部天賦，請確認目的後使用。沒有執行玩家資料批次修改。

VFX 引擎與 `/rpgvfx` 的三種無傷害預覽保留。弧月斬仍接受 `w01`／`v3_weapon_crescent_slash` 作為預覽別名，**該 ID 已不是可學習或施放的天賦**；W01 實戰接線隨核心技能撤除，其他舊技能特效不變。自製武器達等級後隱藏等級需求的功能保留。

實際修改：`TalentMainMenuGUI` 恢復流派導覽與舊技能綁定；`TalentCommand` 移除核心入口／補全；`TalentManager` 只載入舊設定；`TalentSkillManager`、`CustomRPG`、`CombatBalance`、`PlayerStatsManager` 拆除核心派送、排程與加成；`SkillBarListener`、`SkillBarCommand`、`PlayerTalents` 移除大絕的施放入口，保留序列化相容。刪除 CoreTalent／CoreTalentCatalog／TalentTheme／CoreTalentSkills／CoreSummons／CoreTalentVisuals 與核心 YAML。VFX 清單工具只盤點現行 200 天賦，另加 20 筆武器／物品／種族定義；設計文件與對照 CSV 留作歷史資料。

**驗證**：Java 21、`mvn clean verify`，607 tests、0 failures／errors／skipped；紀錄 `target/talent-legacy-verify.log`。乾淨建置避免已刪除的核心 class／YAML 殘留於 JAR。涵蓋 15 流派點擊、獵人分頁、200 定義、舊四槽空格、退休技能拒絕學習／綁定／施放、投點與 rank 儲存、未知未來版本保護；即使伺服器殘留無效核心 YAML 亦不影響載入。

**NEEDS_VERIFICATION**：重啟 Paper 後的實際 GUI、舊技能施放、第三方插件互動與遊戲畫面。測試通過不代表已實際登入伺服器驗收。

本次建置產物為 `target/CustomRPG-1.0.jar`。偵測到伺服器運行時，更新只暫存於 `D:\下載\McServer\plugins\update\CustomRPG-1.0.jar`，待下一次正常完整重啟；運行中的舊版仍維持原狀。原 JAR、殘留核心設定及部署紀錄備份至 `D:\下載\McServer\backups\CustomRPG\legacy-talents-20261023-095600Z\`。請勿使用 Bukkit `/reload` 載入這次程式變更。

部署已完成暫存（STAGED_FOR_FULL_RESTART）：2026-10-23 17:56 台北時間，偵測到 Paper PID 34812，未更動運行中的 JAR 或玩家／設定檔。來源與暫存 SHA256 均為 `df8509685362310505bf22e22e00701ca794a5eb462c8df6e1d7fc24c582a5ef`；備份含前版 JAR、退休核心設定副本與 deployment.json。
