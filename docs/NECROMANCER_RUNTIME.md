# 死靈法師系 — 亡者君臨：實裝與驗收

> **2026-10-24 尖牙更新**：依使用者要求，骨脊裂矛的主攻擊動畫改用原版 **EvokerFangs**，瞄準路線下方地面由近到遠依序張合；`/vfx preview necro_lance` 同步更新。只改視覺，原本14格準星射線、第6～12tick命中、最多兩敵8／5傷害、12MP／7秒CD與玩家進度均保留。原版尖牙傷害以標記攔截，避免另打一段。尖牙屬暫時視覺實體，不佔亡靈隊伍欄位；以下2026-10-23的建置／部署紀錄為歷史。

2026-10-23；Java 21、Paper API 1.21.11-R0.1-SNAPSHOT、Maven。新增第 16 個獨立流派，保留原 15 流派與玩家紀錄；共 212 天賦（142 主動、66 被動、4 武器被動）。八大主題與 80 核心仍維持撤除。

第七招依使用者確認，採詳細段落的 **骸骨縫合・葬骨巨衛**。無印記、無額外能量條、無屍體需求。實作與自動化驗證狀態見文末；**Paper 實戰、實際畫面、音量及多人實服效能仍為 NEEDS_VERIFICATION**。

## 操作與成長

`/talent` → 死靈法師系（新主選單格 29）。七個普通技能各三階，從中攜帶五招至 **1～4／F**；冥府開門一階，攜帶於共用大絕槽 **Shift＋F**，數字 5 仍為種族能力。機制物品採 **骨頭 BONE**：已學且已攜帶後右鍵，蹲下右鍵循環同物品技能；與快捷鍵共用 MP／CD。重綁、重置或重載時會清理召喚物。

下表是第一階原生生命值基礎（2 點＝1 心）。Lance／Command／Prison／Sacrifice 的直接基礎傷害在第二／三階為 110%／120%；Summon／Lantern／Colossus 改為耗魔 90%／80%，召喚生命、次數、控制與承傷上限不額外成長。大絕一階，整樹總投點 22。所有直接傷害仍走既有玩家歸屬、等級換算、技能預算、減傷與虛擬生命結算；沒有新增魔抗公式，基礎數字不等於最終扣血。

| 技能／預覽 ID | MP／冷卻 | 實際機制 |
| --- | --- | --- |
| 骨脊裂矛 `necro_lance` | 12／7秒 | 14格、最多兩名敵人，8／5魔法基礎傷害；第6～12 tick逐段檢查當前敵人與方塊，不能穿牆，每敵一次。 |
| 墓土喚骸 `necro_summon` | 26／28秒 | 兩名普通戰士，各10生命、12秒、每40 tick最多一次2點物理基礎傷害、各最多4次；重召替換現有隊伍，巨衛存在時禁止。 |
| 喪鐘號令 `necro_command` | 13／13秒 | 視線12格指定敵人，立即3傷害；集火6秒，追擊速度+20%，每名現有僕從接下來最多兩次攻擊各+1；無僕從仍可用。 |
| 骨棺禁錮 `necro_prison` | 20／20秒 | 視線10格，4傷害；一般敵人定身24 tick，接著25%減速40 tick；Boss僅25%減速40 tick。 |
| 冥燈庇魂 `necro_lantern` | 21／24秒 | 自己5點吸收盾100 tick，自己的戰士／精銳／巨衛減傷40%100 tick；不復活，重施只替換自己的同源盾額。 |
| 亡骸祭爆 `necro_sacrifice` | 18／22秒 | 選10格內自己可見、最接近準星的普通戰士；先標記消耗並移除，第11 tick於原位置半徑3造成9傷害及25%減速40 tick；無合法僕從不成立施法。 |
| 骸骨縫合・葬骨巨衛 `necro_colossus` | 22／26秒 | 需要兩名活普通戰士，合成一名20生命巨衛8秒；每40 tick 3物理基礎傷害，最多4次；召喚時對4格內普通敵對怪嘲諷40 tick，Boss／玩家免疫嘲諷。主人距4格內遭敵方直接攻擊，可由巨衛實際承受最多6傷害一次；遭取消不白拿減傷、不耗此機會。佔兩召喚欄，不能獻祭。 |
| 冥府開門・百骸行軍 `necro_gate` | 75／120秒 | 前搖30 tick可慢移（35%減速），第30 tick鎖定當下位置與面向；前方14×9格、垂直高度差最多3格，43／53／63 tick各4／4／6傷害，第二波減速20%40 tick。每波重新檢查位置與牆壁，第71 tick召兩名精銳取代整隊，各14生命、8秒、每40 tick 2物理傷害各最多2次。 |

大招採原文分鏡的第43／53／63 tick結算：1.5秒是鎖方向的施法前搖，第一波實際於2.15秒抵達。第71 tick精銳各存活完整160 tick，至第231 tick；門本身第211～225 tick崩解，不以視覺閉門提前刪除精銳。三波完整基礎14，精銳未受號令時再最多8。

## 召喚物控制與安全邊界

普通戰士與精銳各佔一欄，巨衛佔兩欄。每玩家最多兩個死靈戰鬥實體；全域控制器最多128實體。召喚時以安全地面、已載入區塊、世界邊界及視線檢查兩個位置，兩具新核心都生成獲准才替換原隊伍；若生成遭取消，清除準備中的實體並保留舊隊伍，普通召喚回傳失敗，外層不扣 MP／CD。大招若第71 tick已無安全召喚位置，保留原隊伍，不憑空生成騎士；大招本身已施放，仍保留原消耗。

每具核心是 **AI關閉、無掉落、不可撿物、非持久化的原版 Skeleton**，普通持石劍、精銳穿鎖鏈甲、巨衛持盾與斧；傷害由插件控制並以主人作為原生攻擊者，不讓原版AI或箭矢追加攻擊。粒子巨衛的輪廓比核心大，**碰撞箱仍是普通骷髏大小**。大型軍團全部是粒子，沒有生成數十隻怪物。

移動由同一時鐘以每兩tick的短步、地面與碰撞／視線檢查控制，不啟用原版尋路；遇牆、缺少安全地面時停止，不能保證繞過複雜迷宮。距主人超過12格、主人離線／死亡／換世界／進保護區、僕從失效或持續結束，都移除召喚物。普通傳送也清理；既有冰霜瞬步／大地突進／暗影置換沿用其內部移動識別。實體成立時同步播放組裝動畫，首攻至少40 tick後，生命與戰鬥不等待粒子畫完。

OWNER PDC在spawn consumer中先寫入，早於CreatureSpawnEvent。死靈核心不走野怪血量遷移／材料掉落／擊殺被動，其他召喚物的自動集火也排除它們。禁止核心原版攻擊、發射箭矢、轉化、燃燒與原版傳送門跨界；敵方普通怪可以攻擊，玩家只有符合PvP、隊伍及區域規則才可傷害僕從。新流派的自動選敵略過所有已標記死靈核心，避免召喚物互打；PvP對手可用普通攻擊傷害它們。

僕從生命保持草案的10／14／20原生生命值，不自動套用野怪或玩家等級血量；高等級敵人的既有RPG傷害可能很快殺死它們，需要實服平衡驗收。護盾只移除本來源未消耗的剩餘額，不補回已花的吸收值；巨衛轉移按真正生命／吸收值減少量扣原傷害，使用既有Transfer防重入。外部保護可取消原生傷害與 `NecroControlEvent`，其相容性仍須在實服驗證。

所有登出、死亡、換世界、重綁、重置、reload、disable路徑清理實體、排程佇列、減速屬性、護盾及動畫。啟動及載入區塊時清除帶本插件OWNER標記、卻不在當前控制表中的孤兒實體；不刪其他怪物。

## 八招視覺與無傷害預覽

管理員／OP在更新JAR並完整重啟後，可逐一執行：

```text
/vfx preview necro_lance
/vfx preview necro_summon
/vfx preview necro_command
/vfx preview necro_prison
/vfx preview necro_lantern
/vfx preview necro_sacrifice
/vfx preview necro_colossus
/vfx preview necro_gate
/vfx quality low
/vfx quality medium
/vfx quality high
/vfx stats
/vfx stop
```

每招獨立動畫類別，僅共用骨段／頭骨／肋曲線與既有渲染數學。預覽不生成真僕從、不傳送、不造成傷害，不消耗MP／CD／投點。共35份DNA與232筆跨系統盤點，見 [VFX_DNA.csv](data/VFX_DNA.csv) 與 [VFX_SKILL_INVENTORY.csv](data/VFX_SKILL_INVENTORY.csv)。

| 技能 | 主要輪廓／動畫高潮 | 每tick取樣上限 |
| --- | --- | --- |
| 裂矛 | 原版喚魔者尖牙成列甦醒，6～12tick依序張口；命中仍按原射線與碎骨回饋 | 純數學示意上限224；實戰改用最多7具無傷害尖牙 |
| 喚骸 | 左右不對稱墓裂、手掌、前臂、肩與頭骨分段出土 | 288 |
| 號令 | 破損鐘體傾斜、鐘槌擺動、三道半月聲波抵達骷髏標記 | 256 |
| 骨棺 | 三維Bezier彎肋由地表生長，向內合成胸腔與脊鎖 | 288 |
| 冥燈 | 左肩六角燈框、抖動魂火、連至當前僕從的曲線魂線 | 288 |
| 祭爆 | 種子固定的不等長骨刺增生，11tick直線外射與碎骨拋落 | 288 |
| 巨衛 | 左右骨架拆開、綠色縫線向心、巨盾與骨槌形成；實際承傷才裂盾 | 384 |
| 冥門 | 7格骨柱與肋拱、三排各5名簡化骨兵、三波前衝及由上崩解 | 384 |

實戰巨衛另有跟隨核心的輪廓層，普通／精銳保留原版實體外觀與魂眼。門的WARNING矩形先於裝飾採樣，最高優先100；巨衛／冥燈60，其餘40，預覽10。沿用每人LOW96／MEDIUM160／HIGH240發送、32格距離、遠距LOD、全域8192取樣／4000發送／16000可見性檢查；支援動態方向與平移，最長動畫上限由200調整為260tick，以容納12秒普通僕從及226tick冥門，其餘預算不增加。

## 驗證與人工驗收

自動化結果與本批JAR部署紀錄於下方補列。測試涵蓋新配置原子重載、既有流派及玩家紀錄、快捷選單、召喚攻擊間隔與次數、集火加傷、獻祭失敗及延遲、融合條件與承傷實際損失、吸收盾、防友傷與PvP、孤兒清除、Boss免定身、牆壁阻擋、取消及關服。全方向幾何檢查、LOW警示與拱門保留、動態方向、64動畫並發、取樣／發送上限及結束清空另有純Java測試。

實服驗收清單全部仍為 **NEEDS_VERIFICATION**：

- 不看顏色，逐招辨認長矛、出土手掌、鐘、胸腔、燈、刺束、持盾巨骨與冥門；若輪廓或節奏不清楚，調整幾何與分鏡。
- 實際攻擊、擊殺經驗／任務歸屬、隊友／PvP關閉／安全區、外部傷害取消；普通10HP、精銳14HP、巨衛20HP是否適合現行RPG數值。
- 僕從能受傷、失去目標／遇牆／斜坡／遠離主人、嘲諷與Boss免疫、取消融合與部分生成遭拒；確認不掉落裝備／材料／經驗。
- 玩家登出／死亡／換世界／重啟後無殘留；每玩家只有兩個召喚欄，反覆施放不堆疊。
- LOW／MEDIUM／HIGH時危險矩形與門輪廓仍可見，敵人與環境不被遮住；三波真實命中與動畫時間相符。
- 多人同場量測伺服器tick、實體移動、封包與客戶端FPS，記錄實測硬體／人數；純Java數字不能作為TPS／FPS保證。

## 修改檔案

- 新增 `talents/NecroSkill.java`、`NecroControlEvent.java`、`managers/NecroRoster.java`、`NecroTalentSkills.java`、`utils/ControlledUndead.java`、`vfx/NecroVfx.java`、`config/talents_necro.yml`。
- 接線 `TalentBranch`、`TalentLoadout`、`TalentManager`、`TalentSkillManager`、`CustomRPG`、`TalentCommand`、`TalentMainMenuGUI`、`TalentTreeGUI`、`TalentLore`。
- 既有 `VfxRuntime`、`VfxEffects`、`BukkitVfxService`加入時長、方向與預覽；`BalanceCombatListener`加入死靈最終減傷、排除核心野怪血量。
- `ExpandedTalentSkills`、`BeastManager`、`MobListener`、`WeaponListener`、`BowTalentListener`、`TalentPassiveEffectManager`、`CraftingLootListener`排除核心誤選／獎勵。
- 新增 `NecroConfigTest`、`NecroTalentSkillsTest`、`NecroVfxTest`；現有選單／總量測試更新至16流派／212天賦。
- `tools/vfx/NecroLoadProbe.java`及DNA／盤點匯出工具、兩份CSV與本文件、狀態／交接／操作文件同步更新。既有未提交修改保留，沒有重置工作目錄或改寫玩家檔案。

## 本批實際驗證與部署結果

**COMPLETED**：最終 `mvn verify` 通過 **745 tests，0 failures／errors／skipped**；此前 `mvn clean verify` 完整通過743項，最終再加入持續嘲諷與大招阻牆測試後全套重驗。日誌 `target/necro-verify.log`。JAR內所有class／resource逐一與target/classes比對一致、ZIP CRC正常、八主題核心不存在；`target/necro-artifact.json` 保存核對結果。

純Java `NecroLoadProbe`（本機、HIGH、無Paper世界／封包／原生實體移動與AI）結果如下。單人依序跑8招共1920tick，並發情境各240tick；CPU為整段runtime累積時間、含暖機，不能當作每tick延遲或TPS／FPS。

| 情境 | 接受動畫 | 峰值取樣 | 峰值發送 | 峰值可見性檢查 | 累積runtime CPU ms | 結束殘留 |
| --- | --- | --- | --- | --- | --- | --- |
| single_each_skill | 8 | 377 | 240 | 377 | 99.823 | 0 |
| simultaneous_32 | 32 | 3696 | 4000 | 4024 | 151.123 | 0 |
| simultaneous_64 | 64 | 6544 | 4000 | 4031 | 194.593 | 0 |

量測原始值：`target/necro-vfx-load-report.csv`；可用 `tools/vfx/NecroLoadProbe.java` 重現。實體控制成本、真實網路、第三方插件及畫面仍未量測。

部署時間 **2026-10-23 21:57:21 Asia/Taipei**；偵測伺服器未執行，已備份舊主JAR後更新 `D:/下載/McServer/plugins/CustomRPG-1.0.jar`。當時沒有pending update JAR，不建立額外副本；未開／停服，未寫既有設定或玩家資料。下一次完整啟動會建立缺少的 `config/talents_necro.yml` 並載入新分頁。

- 產物：`target/CustomRPG-1.0.jar`，**1,677,178 bytes**。
- SHA-256：`67b404dbd98c14c6426f3e799468bc1c479f7e0f72e29189b4533c7935edc067`，建置與部署檔一致。
- 舊版備份：`D:\下載\McServer\backups\CustomRPG\necro-20261023-135721Z/previous-live.jar`。
- 部署收據：`target/necro-deployment.json` 及備份資料夾內 `deployment.json`。
- 啟用後用 `/talent` 的死靈法師分頁及 `/vfx preview necro_gate` 驗收；不能以 `/talent reload` 載入尚未重啟的新Java類別。

## 喚魔者尖牙的視覺邊界（2026-10-24）

實戰及遊戲內預覽使用真正的原版EvokerFangs外觀，純Java幾何測試中的Lance只保留顎形示意，不會在遊戲重複疊畫舊飛行骨矛。Paper 1.21.11 API已確認支援 [EvokerFangs與attack delay](https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/EvokerFangs.html)。

每次MEDIUM／HIGH最多7對，LOW最多4對；每玩家同時最多14具，全域128具，另受既有VFX Runtime每玩家6／全域64動畫實例的準入限制。尖牙採非持久化、無主人、預設不可見，預覽只對施放者showEntity；實戰初次生成時只開放32格內、能看見施法者的觀眾（最多檢查64人）。原版自帶音效靜音，再由現有音效預算播一次，沒有替每對尖牙新增BukkitTask。

位置沿原瞄準射線取樣，下投至3格內的安全地表；缺地、液體、未載入區塊或阻牆時略過／停止視覺生成。朝天空等情境可能只有少量尖牙或沒有地面尖牙，**不會因此改寫原本空中射線的傷害判定**；地面顎部模型也不是新增的範圍攻擊判定。品質或視覺名額不足不會影響原傷害／MP／CD。

出生consumer先寫入專用PDC標記；LOWEST與HIGHEST兩次攔截帶標記尖牙的原版傷害，包含DamageSource直接來源備援，保留自然喚魔者尖牙的正常傷害。單一既有VFX時鐘在token取消、到期48tick、離線、死亡、換世界及disable清理實體；停止預覽不會清除實戰尖牙。出生遭取消不遺留實體；啟動／區塊載入只清本插件標記的孤兒。

新增 `EvokerFangVisuals.java`、`EvokerFangVisualsTest.java`，接線 `BukkitVfxService`、`VfxCommand`、`NecroVfx`，更新DNA與盤點。不修改NecroTalentSkills的戰鬥射線與技能設定。新增測試驗證7對位置／延遲、原生傷害隔離、預覽隔離、停止與關閉清理、LOW降級、每人與全域上限、無地面／區塊／牆壁／生成遭拒。實際張合動畫、斜坡／樓梯與多人實服效能仍 **NEEDS_VERIFICATION**；沒有啟動伺服器或觀看遊戲畫面。

本批 `mvn verify`：**754 tests，0 failures／errors／skipped**，`target/fangs-verify.log`。已靜態檢視本機實服Paper 1.21.11的EvokerFangs bytecode，無主人分支用 `eventEntityDamager(this)` 送出原版6點魔法傷害事件，與本次標記攔截一致；檔案 `target/fangs-native-bytecode.txt`。這是程式與API驗證，並非遊戲內實測。

部署前再次檢查時伺服器已停止，因此於 **2026-10-24 09:53:36 Asia/Taipei** 備份後直接更新 `D:/下載/McServer/plugins/CustomRPG-1.0.jar`；不是pending update，下一次完整啟動即載入。沒有從工具開／停服，沒有寫玩家資料或既有技能設定。

- JAR：1,686,312 bytes；SHA-256 `43207f35cf5aaf2a2431fd24dea2447c95964613a0c210a2af6c7310e2b3eab7`。
- 備份：`D:\下載\McServer\backups\CustomRPG\fangs-20261024-015336Z/previous-live.jar`。
- 部署核對：`target/fangs-artifact.json`／`target/fangs-deployment.json`；JAR的class／resource與target/classes一致、CRC正常。
- 驗收：開服後 `/vfx preview necro_lance`；實際戰鬥與畫面仍待驗收。
