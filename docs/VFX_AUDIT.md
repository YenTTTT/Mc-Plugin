# 技能 VFX 審計與首批重製設計

> **2026-10-24 養雞第二版視覺**：按使用者修改，散米改中心生長綠點、地面補給蛋改真成年雞及愛心、草巢母雞改真堆肥桶，雞蛋命中生物不加自訂粒子。雞神30秒留場。這些是明確機制／視覺替換，舊DNA已更新；43份DNA與240筆盤點仍維持。見 [CHICKEN_RUNTIME.md](CHICKEN_RUNTIME.md)，遊戲內辨識度與多人負載待驗收。

> **養雞新增審計（2026-10-24）**：原專案沒有此獨立流派。8招主體依次為飛蛋／散米／雙黃補給／啼鳴公雞／護巢母雞／真鬥雞／多排雞群／真巨型雞神；不以同一圈形工具換色。鬥雞與雞神由受控原版Chicken實體呈現，VFX只畫輔助與預警。43 DNA／240盤點，既有尖牙更新保留；見 [CHICKEN_RUNTIME.md](CHICKEN_RUNTIME.md)。原生雞動作與實際辨識度仍待遊戲驗收。

> **2026-10-24 尖牙更新**：骨脊裂矛依使用者要求改為原版EvokerFangs視覺，原技能傷害判定與數值保留；預覽同樣顯示無傷害尖牙。這招使用有數量上限的暫時原版實體，舊版全粒子敘述對此招已不適用。見 [NECROMANCER_RUNTIME.md](NECROMANCER_RUNTIME.md) 的尖牙更新及人工驗收。

> **亡者君臨新增審計（2026-10-23）**：原專案沒有獨立死靈分支；獸系原版AI與暗黑純粒子影獸不能直接表達兩欄可受傷、融合及軍團。新增NecroTalentSkills、ControlledUndead與八套獨立骨骼幾何；主體分別是脊矛、墓手、鐘、胸腔、燈、骨刺、巨衛與骨門軍團。212天賦／35 DNA／232盤點，見 [NECROMANCER_RUNTIME.md](NECROMANCER_RUNTIME.md)。幾何與預算可自動驗證，實際辨識度仍待遊戲驗收。

> **永夜禁術更新（2026-10-23）**：暗黑舊 12 個執行分支改由 DarkTalentSkills 對應新 8 招；新視覺強調文字縫線、人形回聲、鉤爪獸、垂直幕、折板及破碎黯日。現為 204 天賦、27 DNA、224 盤點；舊 ID 相容與審計見 [DARK_RUNTIME.md](DARK_RUNTIME.md)。

> **現行大地更新（2026-10-23）**：原大地 4 招共用 ring／Field 的問題，已由 8 套獨立幾何與戰鬥事件接線處理；四碑與冰冠不同構圖。現共 208 天賦、228 筆盤點、19 份 DNA。實際視覺仍待驗收，審計與對照見 [EARTH_RUNTIME.md](EARTH_RUNTIME.md)。

> **冰霜重製進度（2026-10-23）**：原冰霜共用特效的審計發現已由 8 套獨立動畫及真實戰鬥接線處理，仍待遊戲畫面驗收。當前 204 天賦＋20 其他定義＝224 筆，DNA 11 份（8 冰霜＋3 原展示）；八大主題仍撤除。下方改造前分析保留為歷史證據。見 [FROST_RUNTIME.md](FROST_RUNTIME.md)。

> **現行版本更新（2026-10-23）**：八大主題／80核心及其執行器已撤除。本報告保留修改前的歷史證據；涉及CoreTalentSkills、CoreTalentVisuals、W01實戰等段落不再代表運行中的程式。重新匯出的CSV只含200舊天賦＋20其他定義，共220筆。三種 `/rpgvfx` 效果目前皆為獨立純預覽。見 [舊版恢復說明](TALENT_LEGACY_RUNTIME.md)。

2026-10-23，修改前依工作區程式盤點。這是程式結構審計，沒有觀看 Minecraft 畫面；換皮風險依實際共用繪圖分支判定，不能當作實服觀感評分。前輪未提交工作完整保留。

## 建置與技能入口

- Maven、Java 21、Paper API `1.21.11-R0.1-SNAPSHOT`；`plugin.yml` 宣告 `api-version: 1.21`。本輪使用本機相同 API 的 sources.jar 核對 `DUST`、`DUST_COLOR_TRANSITION`／`DustTransition`、`CRIT`、`ELECTRIC_SPARK`，不依賴較新版 API。
- 共280天賦定義：200舊節點（130 ACTIVE、66 PASSIVE、4 WEAPON_PASSIVE）與80核心（32普通主動、8大絕、40自動節點）。核心、舊15分支與獵人六角色頁維持原ID及學習進度。
- `TalentSkillManager.castSkillDirectly` 是天賦物品／快捷鍵入口，分流到 `CoreTalentSkills`、`HunterTalentSkills`、`ExpandedTalentSkills` 及舊執行器。核心用單調時間的固定MP／HP／CD閘門；命中走 `SkillDamageGuard.damageNormalized` 與戰鬥v2共用池。
- 核心普通技能依各招使用扇區、線段、射線、地面落點、隊友或召喚；持續效果共用5tick任務，前搖／多段的傷害時間在效果執行器，不在特效工具。`CoreTalentSkills.hit` 只有實際HP損失大於零才播放成功命中線。
- 另有武器技能 `thorn_spike`、`fire_nova`、`dash`、`vampire_drain`，由 `weaponSkills.managers.SkillManager`／`ConfigDrivenWeaponSkill` 配置派送，使用獨立冷卻及既有DamageManager；舊物品技能 `fireball`、`heal`、`dash`、`lightning_strike` 另走舊SkillManager；十二種族各有RacialSkill。不同子系統的同名ID不能視為同一技能。
- 完整ID、類型、執行器及視覺分群見 [VFX_SKILL_INVENTORY.csv](data/VFX_SKILL_INVENTORY.csv)，由 [audit_inventory.py](../tools/vfx/audit_inventory.py) 核對 bundled YAML 與現有設計對照資料產出。包含被動節點；不將被動誤稱為手動動畫。

## 具體問題與優先級

| 優先 | 技能／群組 | 程式證據 | 問題與重製方向 |
| --- | --- | --- | --- |
| P0 | W01弧月斬、W02震地破擊、W03貫穿突刺、W04迴旋斬舞 | `CoreTalentVisuals.draw` 的 WEAPON 分支 | 四招同一條靜態水平弧；未表現地裂、突進軌跡或三層旋斬。先重製W01為有厚度、逐段掃出的曲面及錯位殘影，後三招保留現行並各立設計。 |
| P0 | E01～E04、D01/D03/D04、L01～L04、B01～B04、N01～N04、A01～A04、S01～S04，共27普通主動 | `CoreTalentVisuals.draw` 最後的else | 全部使用相同波浪圓環；色彩及中心位置有差異，但主要輪廓與運動相同。E03要用分叉折線，A04要用向心軌道，S01要用逐段結構。不能只改色。 |
| P0 | 核心成功命中共用直線 | `CoreTalentSkills.hit` → `CoreTalentVisuals.line` | 斬擊、藤蔓、雷電、光矛皆是32點直線。首招改用實際命中才出現的雙切口，不額外判傷。 |
| P1 | E10四象天災 | `CoreTalentVisuals.draw` 的E10僅改tint，輪廓仍落到預警／else圓環 | 分幕色彩存在，但冰晶、風壓、分叉雷及火幕缺少不同空間結構。 |
| P1 | 冰霜／雷霆／大地／聖光的持續領域 | `ExpandedTalentSkills.cast/tick` → `ring` | 四領域同一平面圓，寒風、雷暴、防壁及聖域缺少動態差異；beam也共用直線。 |
| P1 | 奇犽路線、酷拉皮卡路線、尼特羅掌法 | `HunterVisuals.style/pulse` | 已有THUNDER／CHAIN／BUDDHA角色輪廓，是可保留基礎；但同角色多招仍共用相同pulse，雷掌與落雷、壹掌與九十九掌需要獨立節奏及運動。 |
| P1 | `nen_rock`與`nen_paper` | `HunterVisuals.pulse` 中FIST/ORB合併case | 拳與遠程念彈起手同一球狀環繞；應區分壓縮拳面與前進彈體。 |
| P2 | 舊`void_eruption`／`magic_vortex`／連斬群 | `TalentSkillManager` 各execute方法 | 需逐招畫面驗收，不能只憑名稱宣稱完全相同；先提供獨立虛空塌縮預覽比較，尚不搬動戰鬥執行器。 |
| P2 | 配置武器技能 | `ParticleUtil.AnimationStyle` 五種樣式 | NONE/FALLING/SPIRAL/CONE/LINE_STEPS工具可重用，但目前配置只改粒子參數即可套同樣式；`burst`強制count至少1，不支援方向性count=0。新引擎明確區分方向粒子。 |

修改前的靜態規模：`TalentSkillManager` 5458行、281處spawnParticle、50處runTaskTimer、17處runTaskLater。這是呼叫位置數，不是同時任務數，也不能推算TPS。Core/Hunter/Expanded各有一個5tick任務；CoreVisuals/HunterVisuals本身不開排程。幾何重複存在於ParticleUtil、CoreVisuals、HunterVisuals、Expanded的ring/beam及舊大型執行器。

核心已有每次64點、施法者384位置/秒、觀察者768位置/秒限制；Hunter只有每次64點，Expanded直接world.spawnParticle。它們沒有跨系統共用每tick發送預算與品質分層。新引擎只保證已接入的新效果，不能宣稱舊特效都受新全域預算約束。

## 重構邊界與首批視覺DNA

新增純計算幾何／局部座標、獨立時間軸、單一每tick Runtime、帶層級的粒子送出與品質預算。效果只輸出取樣及聲音，不持有TalentManager、ManaManager或傷害服務；遊戲內預覽不走施法入口。幾何、渲染與排程可以共用，三招的造型及時間分支各自實作。

| 維度 | W01弧月斬（首招、戰鬥與預覽） | 雷霆審判（純預覽樣本） | 虛空崩解（純預覽樣本） |
| --- | --- | --- | --- |
| 戰鬥定位 | 原本即時100度、4.5格扇區；不增加傷害前搖 | 示範定向落雷與連鎖；未接入E03／獵人傷害 | 示範延遲區域演出；未接入A04／舊虛空傷害 |
| 主輪廓 | 具厚度的非均勻刃帶，100度橫向展開 | 高處落下的不規則主幹及側枝 | 不對稱三維多邊形碎片、空心核心 |
| 運動 | 快速單向掃切、反向錯位殘影 | 垂直折線突現、停頓、跳接 | 不同半徑與角速度向心旋轉，停頓後反向碎裂 |
| 招牌畫面 | 刃尖超前、刃腹拉寬、局部斷裂 | 一幀強主幹及離散側枝，隨後連鎖 | 所有碎片在核心消失前懸停，再碎裂外翻 |
| 節奏 | 無額外蓄力；短掃切與較淡殘留 | 細預示、突發落雷、間斷電流 | 較長塌縮、短暫靜默、非球形釋放 |
| 命中 | 真正扣血後才畫雙切口；取消事件不假報命中 | 預覽的合成端點只作展示，不代表實際命中 | 預覽破碎波不送出傷害或控制 |
| 裝飾 | 少量定向火星；不靠爆炸遮住刀形 | 微小電暈，低品質先移除側枝裝飾 | 較少逆向塵屑；低品質保留碎片骨架 |
| 聲音 | 單次短破風，命中短促切割聲 | 低音預示、雷擊短爆、弱連鎖點聲 | 向心低音、高潮前靜默、破碎短聲 |

LOW/MEDIUM/HIGH都保留主要輪廓；新Runtime限制每玩家每tick、全域粒子發送、可見範圍、取樣數及發送工作量。預警先；命中／輪廓為關鍵組，組內按主形／切口優先級，再處理主體／裝飾。品質只影響演出。預覽只給管理員本人，實戰才按觀看距離與可見性篩選其他玩家。

## 驗收出口

每招需逐項人工回答：去色能否辨認、輪廓是否獨特、運動是否不同、招牌時刻是否集中、節奏是否符合戰鬥、是否只放大換色、是否遮蔽判定／環境、多人時是否仍清楚。任何一項不合格，回頭改幾何／節奏，不以加粒子量作為通過條件。

本輪先實作可預覽W01及兩個對照樣本；完成數學、座標、時間軸、品質、生命週期、權限、無傷害預覽與建置測試。**NEEDS_VERIFICATION：原版客戶端實際觀感、音量、多人TPS與封包負載；未人工驗收前不批次遷移其他戰鬥技能。** 操作與實作結果記於 [VFX_RUNTIME.md](VFX_RUNTIME.md)。
