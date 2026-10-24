# 冰霜系實裝、操作與驗收

> **現行大地更新（2026-10-23）**：後續大地已實裝，當前總數 208；泰坦與零度王權共用同一個大絕攜帶槽。冰霜 8 招機制物品仍是木棒，見 [EARTH_RUNTIME.md](EARTH_RUNTIME.md)。

2026-10-23，Java 21／Paper API 1.21.11-R0.1-SNAPSHOT，Maven。

**COMPLETED**：7 招普通技能、零度王權、寒霜印記、5 個普通攜帶槽與獨立大絕槽、8 套獨立粒子動畫及無傷害預覽。**NEEDS_VERIFICATION**：Paper 實際施法、視覺品質、音量、第三方保護插件及多人實服效能。沒有從工具開服，也沒有觀看遊戲畫面。

## 學習與配置

`/talent` → 冰霜系，左鍵學習、右鍵已學主動技能進入綁定頁。從 7 招普通技能選擇 5 招，配置到 **1～4／F**；零度王權放入綁定頁下方的獨立大絕槽（星星，GUI slot 52），以 **Shift＋F** 施放。配置零度王權或泰坦鎮界時才攔截 Shift＋F；其他情況仍交換副手。數字 5 保留種族能力，`/skillbar` 切換技能列模式。

機制物品統一為木棒（STICK），已學習且已攜帶的技能亦可右鍵木棒施放；蹲下右鍵沿用機制物品技能循環。兩種入口共用魔力及冷卻檢查。大絕不能放入普通槽，普通技能不能放入大絕槽。

舊四招 `frost_shard`、`frost_nova`、`frost_lance`、`frost_blizzard` 保留 ID 與已學等級，對應新穿心、追獵、十字、霧域；原投點沒有刪除或退款。7 招普通技能各 3 級，大絕 1 級。第 2／3 級直接傷害為第 1 級的 110%／120%，霜影瞬步與冰縛十字改為耗魔降低 10%／20%；不增加控制時間、範圍或位移距離。既有冰霜被動 `frost_attunement`、`frost_resolve` 保留。寒霜印記是內建機制，沒有額外學習節點或技能槽需求。

仍是 **15 流派**，沒有恢復已撤除的八大主題與 80 核心。總數由 200 變為 **204 天賦：134 ACTIVE、66 PASSIVE、4 WEAPON_PASSIVE**；冰霜系為 8 主動＋2 被動。其他流派及獵人角色分頁保留。

## 技能數值與戰鬥

以下為第 1 級、Minecraft 原生生命值基礎單位；2 點為 1 顆心。實際扣血仍經既有 `SkillDamageGuard`、等級換算、共用技能預算、減傷及虛擬生命流程，並攜帶施法玩家來源。未額外加入攻擊力／法強公式，不能把表中的基礎傷害當作最終扣血保證。

| 技能／ID | 耗魔／冷卻 | 實際機制 |
| --- | --- | --- |
| 極冰穿心 `frost_shard` | 10／6 秒 | 射程 14 格，6 段射線依時序檢查牆與首名敵人，傷害 7、減速 20% 兩秒、1 印記。 |
| 寒棘追獵 `frost_nova` | 18／12 秒 | 前方最多 8 格地面、寬 1.5 格，逐段傷害 8、1 印記；每名敵人每次施放只結算一次。斷崖、牆及不安全地形會中止延伸。 |
| 霜影瞬步 `frost_step` | 16／14 秒 | 優先沿最近移動方向，否則朝面向，安全位移最多 5 格；不能穿牆。霜痕留 2 秒，敵人首次進入減速 25% 1.5 秒，沒有直接傷害或印記。 |
| 凝晶護鏡 `frost_mirror` | 20／24 秒 | 5 秒減傷 25%；第一次有效敵方直接攻擊觸發一次碎晶反擊，6 tick 飛行後造成 3 傷害及 1 印記。燃燒與本系霧域持續傷害不觸發反擊；虛空／強制擊殺不減免。 |
| 冰縛十字 `frost_lance` | 18／18 秒 | 可見敵人 10 格，減速 50% 兩秒、1 印記；沒有直接傷害。 |
| 寒潮霧域 `frost_blizzard` | 26／26 秒 | 瞄準 12 格內地面，半徑 3.5 格、6 秒；減速 25%，第 40／80／120 tick 各造成 2 傷害。每名敵人整次最多 1 印記。 |
| 冰棱折光 `frost_prism` | 22／17 秒 | 可見首敵 12 格受 6 傷害，最多續跳兩名距前一命中位置 5 格內可見敵人、各 4 傷害；不重複，命中各 1 印記。首敵死亡仍可續跳，取消傷害不產生假命中光路。 |
| 零度王權 `frost_crown` | 65／100 秒 | 瞄準 12 格內地面、半徑 6 格；前搖 30 tick，傷害 16，一般敵人定身 30 tick。Boss 改為減速 20% 40 tick。一般敵人寒霜殘域減速 30% 4 秒，沒有追加傷害。 |

需要目標／地面的技能在找不到合法落點時不扣魔、不進冷卻。各招沿用既有安全區、PvP、同隊／寵物與視線篩選。純控制另派送可取消的 `FrostControlEvent`；第三方插件若只處理傷害事件，仍需實服確認其對純控制的相容性。

### 寒霜印記

每個施法者對各目標分別累積，最多 3 層，命中刷新 6 秒。第三層消耗印記，觸發凝霜：一般敵人定身 16 tick，Boss 改為減速 30% 16 tick。同一目標的 160 tick 凝霜冷卻跨施法者共用。冷卻中維持最多 3 層，冷卻後須再有一次合格命中才觸發。

霧域只在有效傷害時嘗試附印記，整次每敵最多一次；霜痕沒有印記。王權爆發只附 1 層，不觸發被動凝霜，不把被動定身疊在大絕定身上。Boss 不受定身，也不再被王權殘域覆蓋成 30% 減速，保留草案指定的 20%／2 秒。

## 八套獨立演出

| 動畫類別 | 核心輪廓 | 運動／節奏 | 招牌畫面與聲音 |
| --- | --- | --- | --- |
| `FrostVfx.Piercer` | 長錐冰槍、交錯細霜痕 | 0～4 聚合、5～10 穿刺、定向碎裂 | 清楚槍尖切入；結晶提示與玻璃碎音。 |
| `FrostVfx.Fangs` | 不等高、傾斜立體冰牙 | 先裂地，6～13 近至遠生長，逐段崩碎 | 一列尖牙；分段冰塊破裂聲。 |
| `FrostVfx.PhantomStep` | 兩道人形殘影、雙鋸齒軌跡 | 身形拉伸、平移、由上而下散去 | 人形霜影；短促滑行音。 |
| `FrostVfx.Mirror` | 三面傾斜菱形鏡 | 隨玩家移動與緩慢轉角，首擊局部破碎 | 一面鏡破、獨立 `MirrorShard` 飛向攻擊者；玻璃音。 |
| `FrostVfx.Cross` | 四向鎖片、交錯 X | 向內夾擊，凝霜時維持更久 | 下半身交叉束縛；短結晶音。 |
| `FrostVfx.Mist` | 多條高低不同的 Bezier 霧帶 | 侵入、低空交織、拉長消散 | 冷流穿插且保留視線；低音環境音。 |
| `FrostVfx.Prism` | 雙軸八面體、折線光路 | 逐邊形成、按真實命中逐節折射 | 稜鏡連到不同敵人；逐跳短晶音。 |
| `FrostVfx.Crown` | 六角高空晶冠、六向地面雪紋 | 警示、凝結、停頓、收束、十二路崩裂 | 天空晶冠閉合；低沉預兆、短促結晶高潮。 |

每招獨立動畫類別，只共用座標、取樣及渲染工具。沒有生成方塊、Display 或模型；使用實際 API 的 DUST、DUST_COLOR_TRANSITION、SNOWFLAKE、END_ROD、BLOCK(PACKED_ICE) 與 CLOUD。定點粒子 count=1、offset=0；既有方向性 CRIT 仍使用 count=0。DNA 見 [VFX_DNA.csv](data/VFX_DNA.csv)，含原三展示共 11 份。

草案同時寫「1.5 秒前搖」與較晚的爆發分鏡，實裝以 **第 30 tick 結算**，接續收束與碎裂動畫；殘域至第 110 tick（前搖後 4 秒），最後純裝飾消散可延續至第 132 tick。動畫尾段沒有額外傷害或殘域控制。瞬步實際採安全位置瞬移，3～8 tick 是滑行殘影演出，不依粒子推動角色。

## 管理員逐招預覽

OP 或 `customrpg.vfx.admin`：

```text
/vfx preview frost_shard
/vfx preview frost_nova
/vfx preview frost_step
/vfx preview frost_mirror
/vfx preview frost_lance
/vfx preview frost_blizzard
/vfx preview frost_prism
/vfx preview frost_crown
/vfx quality low
/vfx quality medium
/vfx quality high
/vfx stats
/vfx stop
```

`/vfx` 為 `/rpgvfx` 別名；若其他插件占用短名，改用 `/rpgvfx`。預覽只發送給操作者，沒有傷害、控制、位移、魔力或真正冷卻，也不需要先學技能。護鏡預覽第 40 tick 自動展示碎鏡，不須真的挨打；折光預覽使用合成三節端點，實戰才依真實命中生成。`showcase` 仍是原本斬擊／雷霆／虛空三展示，冰霜請逐招 preview。stop 停止後續發送，已發送粒子按客戶端自然壽命消散。

## 排程、預算及清理

冰霜戰鬥共用一個 1 tick 任務，VFX 共用另一個 1 tick 任務；沒有為每顆粒子建立任務。降低畫質只影響發送，沒有改變傷害、命中範圍、狀態、MP 或 CD。一般技能動畫優先 40、護鏡 60、王權 100、預覽 10。

新 VFX Runtime 上限：64 實例／每人 6；每 tick 8192 取樣、4000 粒子發送、16000 投遞檢查、24 音效。每觀看者 LOW／MEDIUM／HIGH 為 96／160／240 粒子；16 格後降低密度，32 格外不送。王權先保留六向地面預警與冠體輪廓，LOW 丟棄裝飾層，不能只靠提高粒子數掩蓋輪廓問題。這些上限只涵蓋新 Runtime，其他流派尚未遷移的舊繪圖不在其中。

各技能單幀取樣額度：穿心 192、追獵 256、瞬步 224、護鏡 192、十字 192、霧域 256、折光 256、王權 384。戰鬥每次範圍掃描最多 24 個有效敵人；控制狀態最多 1024、印記及免疫表各 4096。技能進場檢查待辦工作量約 192、護鏡／霜痕各 64；連鎖與王權仍可生成後續工作，因此 192 是入場門檻，不是待辦總數硬上限。

死亡、登出、世界切換、一般傳送、技能重置／換綁定會取消所屬後續工作與動畫。目標失效不再結算；控制到期移除本插件專用移速 modifier，不改永久基礎屬性。停用／天賦重載清除任務、狀態與視覺。瞬步自己的安全傳送使用獨立標記，保留它剛生成的霜痕及殘影。

## 自動化驗證與資源量測

`mvn clean verify`：**651 tests，0 failures／errors／skipped，BUILD SUCCESS**。紀錄 [frost-verify.log](../target/frost-verify.log)。涵蓋舊 rank 保存與設定原檔不覆寫、8 招載入與錯誤回滾、Shift＋F／退休核心拒絕、六欄說明、印記／免疫、Boss、有效傷害、逐段穿刺／地牙、一次反擊、霧域三跳與印記限制、折光首敵死亡續跳、30 tick 王權、位移碰撞、未載入區塊、取消／世界切換／關閉清理、局部旋轉、原版粒子 data 型別、LOW 預警及輪廓、64 動畫預算。

另外執行 `tools/vfx/FrostLoadProbe.java`，純 Java 模擬觀看者及各招動畫，輸出 [frost-vfx-load-report.csv](../target/frost-vfx-load-report.csv)：

| 情境 | 接受／請求 | 尖峰動畫 | 尖峰取樣／發送／投遞檢查 | Runtime 累計 CPU ms | 結束殘留 |
| --- | --- | --- | --- | --- | --- |
| 單人依序 8 招，1200 ticks | 8／8 | 1 | 360／240／362 | 41.294 | 0 |
| 32 人同時各一招，150 ticks | 32／32 | 32 | 4056／4000／4024 | 49.415 | 0 |
| 64 人同時各一招，150 ticks | 64／64 | 64 | 7000／4000／4132 | 71.811 | 0 |

這是 headless 計數與本次 JVM CPU 時間，沒有 Paper 實體掃描、真正發封包、網路或客戶端繪製，不能換算成實服 FPS／TPS 或多人容量。未做遊戲畫面驗收，不能宣告視覺品質合格。

### 實際修改範圍

- 新增 `talents/FrostSkill.java`、`FrostControlEvent.java`、`managers/FrostMarks.java`、`FrostTalentSkills.java`、`vfx/FrostVfx.java`、`config/talents_frost.yml`。
- 接線 `CustomRPG`、`TalentManager`／`TalentSkillManager`／`TalentCommand`、`PlayerTalents`、兩個天賦 GUI、`TalentLore`、`TalentItemBinding`、`SkillBarListener`／`SkillBarCommand`。
- 擴充 `VfxRuntime` 動態錨點／範圍、`VfxMath` 局部座標、`VfxEffects`／`VfxCommand` 解析、`BukkitVfxService` 粒子適配、`plugin.yml` 別名。
- 新增 4 組 Frost 測試，補強既有 VFX／快捷鍵／設定／說明回歸；更新 DNA／盤點工具及 CSV。既有烈焰傷害歸屬與武器等級文字保留。

新增 frost YAML 首次啟動才另存，不覆寫現有 talents.yml／talents_additional.yml 或玩家檔；舊四招只在記憶體以新設計取代。若自訂 frost YAML 無效，重載保留之前成功的整套樹；有效格式須包含這 8 個 ID 與既定槽位／耗魔／冷卻。沒有自動重配或批次改寫玩家資料。

## 人工驗收待辦

- [ ] 在平地／坡面／牆前、不同俯仰方向逐招施放；穿心不穿牆，地牙不跨斷崖，瞬步不進牆，目標／地面失效不扣魔。
- [ ] 8 招各在 LOW／MEDIUM／HIGH 驗收：不看顏色仍可辨識輪廓、運動與高潮；王權 LOW 仍同時看得到地面危險預警和高空晶冠；不遮住敵人與環境。
- [ ] 檢查三印記、6 秒到期、跨玩家 8 秒免疫、Boss 不定身、霧域每施放單印記及王權不額外疊被動定身。
- [ ] 真怪／PvP 驗證傷害、擊殺歸屬、保護區及取消事件；護鏡只反擊一次、折光最多三敵且首敵死亡仍可續跳。
- [ ] 王權第 30 tick 爆發，正常殘域 4 秒、Boss 僅 20%／2 秒；確認舊被動及裝備減傷與技能並用的實際結果。
- [ ] 施法中登出／死亡／換世界／換綁定／重載／關閉插件，沒有殘留後續命中、任務或移速 modifier。
- [ ] 多人同施法量測實服 MSPT／TPS、封包與客戶端 FPS；超預算仍保留預警，技能數值及命中不隨品質變動。

## 本次 JAR 部署

2026-10-23 19:28（台灣時間）確認伺服器未執行後，已直接更新 `D:\下載\McServer\plugins\CustomRPG-1.0.jar`，完整啟動伺服器後生效。前版 JAR 與 deployment.json 備份在 `D:\下載\McServer\backups\CustomRPG\frost-redesign-20261023-112814Z`。沒有開服／停服，沒有覆寫伺服器既有設定或玩家檔；首次啟動會另存新的 `config/talents_frost.yml`。

SHA256：`e021bf9e3b1d9dbaec87dd76a4a21220783c0bbbee7c626cffd266c3e4f2b526`。已核對來源與安裝 JAR 雜湊、ZIP CRC、8 招動畫及 Frost 執行器／印記／設定確實打包，沒有退休的核心 class／YAML。部署紀錄亦在 [frost-deployment.json](../target/frost-deployment.json)。

## 機制物品更新

冰霜 8 招的預設機制物品改為木棒（STICK）。已部署設定中的舊預設「雪球」／SNOWBALL 於載入時在記憶體轉成木棒，技能說明同步顯示；不覆寫設定原檔，不改技能配置、rank、MP、CD 或傷害。1～4／F 與 Shift＋F 保留。管理員另行自訂的機制物品不強制替換。

本次 `mvn verify` 651 項測試通過（0 failures／errors／skipped），包含既有雪球設定轉換成木棒、原檔不覆寫、木棒可匹配且雪球不匹配、技能說明同步。紀錄 `target/frost-stick-verify.log`。伺服器執行中，新 JAR 已暫存 `D:\下載\McServer\plugins\update\CustomRPG-1.0.jar`，下次完整重啟生效；備份 `D:\下載\McServer\backups\CustomRPG\frost-stick-20261023-115046Z`。SHA256 `dd74bc7371b1c57cb3e2ba7f4fbb0c3ecaf65b129d544c67d660a95ee8591a70`。未開／停服，右鍵實際操作尚待遊戲驗收。
