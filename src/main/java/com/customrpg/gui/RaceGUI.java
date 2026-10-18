package com.customrpg.gui;

import com.customrpg.CustomRPG;
import com.customrpg.races.RaceData;
import com.customrpg.races.RaceManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * RaceGUI - 種族選擇介面 (分頁式)
 *
 * 第一頁: 種族選擇列表 — 點擊種族進入詳情頁
 * 第二頁: 種族詳情 — 基礎屬性 / 成長倍率 / 種族技能 / 武器親和 (tooltip 顯示)
 *         + 選擇按鈕 + 返回按鈕
 */
public class RaceGUI implements Listener {

    private final CustomRPG plugin;
    private final RaceManager raceManager;

    // ===== 第一頁：種族列表 =====
    private static final String LIST_TITLE = "§6§l選擇你的種族";
    private static final int LIST_SIZE = 54;
    private static final int[] RACE_SLOTS = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};

    // ===== 第二頁：種族詳情 =====
    private static final String DETAIL_TITLE_PREFIX = "§6§l種族詳情 §8» ";
    private static final int DETAIL_SIZE = 45; // 5 rows

    // 詳情頁 slot 配置
    private static final int SLOT_RACE_ICON = 4;        // 種族圖標 (上方正中)
    private static final int SLOT_BASE_STATS = 19;      // 基礎屬性
    private static final int SLOT_GROWTH = 21;           // 成長倍率
    private static final int SLOT_SKILLS = 23;           // 種族技能
    private static final int SLOT_WEAPON = 25;           // 武器親和
    private static final int SLOT_PASSIVE = 22;          // 被動效果 (中間)
    private static final int SLOT_CONFIRM = 40;          // 選擇按鈕
    private static final int SLOT_BACK = 36;             // 返回按鈕

    // 種族圖標材質
    private static final Map<String, Material> RACE_ICONS = new LinkedHashMap<>();
    static {
        RACE_ICONS.put("human", Material.IRON_CHESTPLATE);
        RACE_ICONS.put("elf", Material.BOW);
        RACE_ICONS.put("orc", Material.IRON_AXE);
        RACE_ICONS.put("dwarf", Material.IRON_PICKAXE);
        RACE_ICONS.put("undead", Material.WITHER_SKELETON_SKULL);
        RACE_ICONS.put("demon", Material.BLAZE_POWDER);
        RACE_ICONS.put("angel", Material.FEATHER);
        RACE_ICONS.put("dragon", Material.DRAGON_HEAD);
        RACE_ICONS.put("dragonkin",Material.DRAGON_HEAD);
        RACE_ICONS.put("beastkin",Material.RABBIT_FOOT);
        RACE_ICONS.put("dark_elf",Material.AMETHYST_SHARD);
        RACE_ICONS.put("halfling",Material.WHEAT);
        RACE_ICONS.put("golem",Material.IRON_BLOCK);
    }

    // 武器類型中文對照
    private static final Map<String, String> WEAPON_NAME_ZH = new LinkedHashMap<>();
    static {
        WEAPON_NAME_ZH.put("SWORD", "劍");
        WEAPON_NAME_ZH.put("TWO_HAND_SWORD", "雙手劍");
        WEAPON_NAME_ZH.put("TWO_HAND_STAFF", "雙手長棍");
        WEAPON_NAME_ZH.put("AXE", "斧");
        WEAPON_NAME_ZH.put("TWO_HAND_AXE", "雙手斧");
        WEAPON_NAME_ZH.put("BOW", "弓");
        WEAPON_NAME_ZH.put("STAFF", "法杖");
        WEAPON_NAME_ZH.put("SCYTHE", "鐮刀");
        WEAPON_NAME_ZH.put("TWO_HAND_SCYTHE", "雙手鐮刀");
        WEAPON_NAME_ZH.put("SPEAR", "長槍");
        WEAPON_NAME_ZH.put("UNKNOWN", "未知");
    }

    // 種族技能中文對照


    // 執行時狀態
    private final Map<UUID, Map<Integer, String>> playerListSlotMap = new HashMap<>(); // 列表頁 slot→raceId
    private final Map<UUID, String> playerDetailRace = new HashMap<>();               // 詳情頁正在查看的 raceId
    private final Map<UUID,Inventory> openViews=new HashMap<>();
    private final Map<UUID,Integer> listPages=new HashMap<>();

    public RaceGUI(CustomRPG plugin, RaceManager raceManager) {
        this.plugin = plugin;
        this.raceManager = raceManager;
    }

    // ═══════════════════════════════════════
    //  第一頁：種族列表
    // ═══════════════════════════════════════

    public void open(Player player) {
        open(player,0);
    }
    public void open(Player player,int requestedPage) {
        Inventory gui = Bukkit.createInventory(null, LIST_SIZE, LIST_TITLE);

        // 填充背景
        ItemStack filler = createFiller(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < LIST_SIZE; i++) {
            gui.setItem(i, filler);
        }

        // 放置種族圖標
        Map<Integer, String> slotMap = new HashMap<>();
        List<RaceData> races = new ArrayList<>(raceManager.getAllRaces());
        int pages=Math.max(1,(races.size()+RACE_SLOTS.length-1)/RACE_SLOTS.length);
        int page=Math.max(0,Math.min(requestedPage,pages-1));
        String currentRaceId = raceManager.getPlayerRaceId(player);

        for (int i = 0; page*RACE_SLOTS.length+i < races.size() && i < RACE_SLOTS.length; i++) {
            RaceData race = races.get(page*RACE_SLOTS.length+i);
            int slot = RACE_SLOTS[i];
            Material icon = RACE_ICONS.getOrDefault(race.getId().toLowerCase(), Material.PLAYER_HEAD);

            boolean isCurrent = race.getId().equalsIgnoreCase(currentRaceId);
            gui.setItem(slot, createListItem(race, icon, isCurrent));
            slotMap.put(slot, race.getId());
        }

        playerListSlotMap.put(player.getUniqueId(), slotMap);
        listPages.put(player.getUniqueId(),page);
        if(page>0) gui.setItem(45,createItem(Material.ARROW,"§e上一頁",List.of()));
        if(page+1<pages) gui.setItem(53,createItem(Material.ARROW,"§e下一頁",List.of()));
        gui.setItem(49,createItem(Material.BOOK,"§e第 "+(page+1)+" / "+pages+" 頁",List.of("§7共 "+races.size()+" 個種族")));

        // 頂部：當前種族資訊
        if(!raceManager.isPlayerDataAvailable(player)) {
            gui.setItem(4,createItem(Material.BARRIER,"§c種族資料暫時無法使用",List.of("§7選擇已鎖定，請聯繫管理員修復資料。")));
        } else if (currentRaceId != null) {
            RaceData currentRace = raceManager.getRaceData(currentRaceId);
            if (currentRace != null) {
                ItemStack info = createItem(Material.NETHER_STAR,
                        "§6§l你的種族: " + currentRace.getDisplayName(),
                        List.of("§7這是你目前選擇的種族。", "",
                                "§e注意: §c種族只能選擇一次！",
                                "§e如需更換，請聯繫管理員。"));
                gui.setItem(4, info);
            }
        } else {
            gui.setItem(4, createItem(Material.BARRIER,
                    "§c§l尚未選擇種族",
                    List.of("§7點擊下方的種族圖標來查看詳情！")));
        }

        openViews.put(player.getUniqueId(),gui);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    /**
     * 列表頁的種族物品 — 簡短 tooltip + "點擊查看詳情"
     */
    private ItemStack createListItem(RaceData race, Material icon, boolean isCurrent) {
        String title = race.getDisplayName();
        if (isCurrent) {
            title += " §a§l[目前種族]";
        }

        List<String> lore = new ArrayList<>();
        if (race.getDescription() != null && !race.getDescription().isEmpty()) {
            lore.add("§7" + race.getDescription());
        }
        lore.add("");
        lore.add("§c⚔ 力量 §f" + race.getBaseStrength()
                + "  §b✦ 魔法 §f" + race.getBaseMagic()
                + "  §a➤ 敏捷 §f" + race.getBaseAgility());
        lore.add("§d♥ 體力 §f" + race.getBaseVitality()
                + "  §9⛨ 防禦 §f" + race.getBaseDefense()
                + "  §5✧ 精神 §f" + race.getBaseSpirit());
        lore.add("");
        if (isCurrent) {
            lore.add("§a▶ 點擊查看詳情");
        } else {
            lore.add("§e▶ 點擊查看詳情");
        }

        ItemStack item = createItem(icon, title, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ═══════════════════════════════════════
    //  第二頁：種族詳情
    // ═══════════════════════════════════════

    /**
     * 開啟種族詳情頁
     */
    private void openDetailPage(Player player, String raceId,int page) {
        RaceData race = raceManager.getRaceData(raceId);
        if (race == null) return;

        String detailTitle = DETAIL_TITLE_PREFIX + race.getDisplayName();
        // 截斷標題避免超長
        if (detailTitle.length() > 32) {
            detailTitle = detailTitle.substring(0, 32);
        }

        Inventory gui = Bukkit.createInventory(null, DETAIL_SIZE, detailTitle);

        // 填充背景
        ItemStack filler = createFiller(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < DETAIL_SIZE; i++) {
            gui.setItem(i, filler);
        }

        // 裝飾邊框 (第一排 & 最後一排用深灰)
        ItemStack border = createFiller(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, border);
            gui.setItem(36 + i, border);
        }

        boolean isCurrent = raceId.equalsIgnoreCase(raceManager.getPlayerRaceId(player));

        // ── Slot 4: 種族圖標 (名稱 + 簡述) ──
        Material raceIcon = RACE_ICONS.getOrDefault(raceId.toLowerCase(), Material.PLAYER_HEAD);
        {
            String title = race.getDisplayName();
            if (isCurrent) title += " §a§l[目前種族]";
            List<String> lore = new ArrayList<>();
            if (race.getDescription() != null && !race.getDescription().isEmpty()) {
                lore.add("§7" + race.getDescription());
            }
            lore.add("");
            lore.add("§8將滑鼠移到下方物品查看詳細資訊");
            gui.setItem(SLOT_RACE_ICON, createItem(raceIcon, title, lore));
        }

        // ── Slot 19: 基礎屬性 ──
        {
            List<String> lore = new ArrayList<>();
            lore.add("§7選擇此種族後立即獲得的屬性加成");
            lore.add("");
            lore.add("§c  ⚔ 力量: §f+" + race.getBaseStrength());
            lore.add("§b  ✦ 魔法: §f+" + race.getBaseMagic());
            lore.add("§a  ➤ 敏捷: §f+" + race.getBaseAgility());
            lore.add("§d  ♥ 體力: §f+" + race.getBaseVitality());
            lore.add("§9  ⛨ 防禦: §f+" + race.getBaseDefense());
            lore.add("§5  ✧ 精神: §f+" + race.getBaseSpirit());
            if (race.getBaseMana() > 0) {
                lore.add("§3  ✦ 魔力: §f+" + race.getBaseMana());
            }
            if (race.getBaseManaRegen() > 0) {
                lore.add("§3  ↻ 魔力回復: §f+" + String.format("%.1f/s", race.getBaseManaRegen()));
            }
            gui.setItem(SLOT_BASE_STATS, createItem(Material.DIAMOND, "§e§l✦ 基礎屬性", lore));
        }

        // ── Slot 21: 成長倍率 ──
        {
            List<String> lore = new ArrayList<>();
            lore.add("§7每升一級時，屬性成長的倍率");
            lore.add("§7倍率越高，升級時獲得的屬性越多");
            lore.add("");
            Map<String, Double> growthMap = race.getGrowthMap();
            for (Map.Entry<String, Double> entry : growthMap.entrySet()) {
                double val = entry.getValue();
                String arrow = val > 1.0 ? "§a▲" : (val < 1.0 ? "§c▼" : "§7●");
                lore.add(arrow + " " + entry.getKey() + ": §f" + String.format("%.1fx", val));
            }
            gui.setItem(SLOT_GROWTH, createItem(Material.EXPERIENCE_BOTTLE, "§6§l✦ 成長倍率", lore));
        }

        // ── Slot 22: 被動效果 ──
        {
            boolean hasPassive = race.getBonusCritChance() != 0 || race.getBonusMoveSpeed() != 0
                    || race.getBonusCritDamage() != 0 || race.getBonusHealthRegen() != 0;
            List<String> lore = new ArrayList<>();
            if (hasPassive) {
                lore.add("§7種族自帶的被動加成效果");
                lore.add("");
                if (race.getBonusCritChance() > 0) lore.add("§e  暴擊率: §a+" + String.format("%.1f%%", race.getBonusCritChance()));
                if (race.getBonusCritDamage() > 0) lore.add("§e  暴擊傷害: §a+" + String.format("%.0f%%", race.getBonusCritDamage() * 100));
                if (race.getBonusMoveSpeed() != 0) lore.add("§e  移動速度: "+(race.getBonusMoveSpeed()<0?"§c":"§a")+String.format("%+.0f%%", race.getBonusMoveSpeed()*100));
                if (race.getBonusHealthRegen() != 0) lore.add("§e  生命回復修正: "+(race.getBonusHealthRegen()<0?"§c":"§a")+String.format("%+.1f HP/s",race.getBonusHealthRegen()));
            } else {
                lore.add("§7此種族沒有額外被動效果");
            }
            lore.add("§7脫戰後回復："+String.format("%.1f HP/s",raceManager.getRecoveryPerSecond(race)));
            gui.setItem(SLOT_PASSIVE, createItem(Material.BEACON, "§2§l✦ 被動效果", lore));
        }

        // ── Slot 23: 種族技能 ──
        {
            List<String> lore = new ArrayList<>();
            var skill = com.customrpg.races.RacialSkill.forRace(race.getId());
            if (skill != null) {
                lore.add("§e數字鍵 5：§f" + skill.displayName());
                lore.add("§7" + skill.description());
                lore.add("§7冷卻：§f" + skill.cooldown() + " 秒  §7消耗：§b" + skill.manaCost() + " MANA");
                lore.add("§7快捷技能列預設開啟，/skillbar 切換");
            } else {
                lore.add("§7此自訂種族尚未定義主動技能");
            }
            gui.setItem(SLOT_SKILLS, createItem(Material.ENCHANTED_BOOK, "§d§l✦ 種族技能 [5]", lore));
        }

        // ── Slot 25: 武器親和 ──
        {
            List<String> lore = new ArrayList<>();
            lore.add("§7使用不同武器時的傷害倍率");
            lore.add("§7§o(100% = 正常傷害)");
            lore.add("");
            Map<String, Double> wb = race.getWeaponBonuses();
            if (wb.isEmpty()) {
                lore.add("§7  所有武器: §f" + String.format("%.0f%%", race.getDefaultWeaponBonus() * 100));
            } else {
                for (Map.Entry<String, Double> entry : wb.entrySet()) {
                    double bonus = entry.getValue();
                    String color = bonus > 1.0 ? "§a" : (bonus < 1.0 ? "§c" : "§f");
                    String weaponZh = WEAPON_NAME_ZH.getOrDefault(entry.getKey(), entry.getKey());
                    lore.add("§e  " + weaponZh + ": " + color + String.format("%.0f%%", bonus * 100));
                }
                lore.add("§7  其他武器: §f" + String.format("%.0f%%", race.getDefaultWeaponBonus() * 100));
            }
            gui.setItem(SLOT_WEAPON, createItem(Material.IRON_SWORD, "§3§l✦ 武器親和", lore));
        }

        // ── Slot 36: 返回按鈕 ──
        gui.setItem(SLOT_BACK, createItem(Material.ARROW, "§c§l← 返回種族列表", List.of("§7點擊返回種族選擇畫面")));

        // ── Slot 40: 選擇 / 已選擇 按鈕 ──
        if (isCurrent) {
            gui.setItem(SLOT_CONFIRM, createItem(Material.LIME_STAINED_GLASS_PANE,
                    "§a§l✓ 已選擇此種族",
                    List.of("§7你目前就是這個種族。")));
        } else if (raceManager.hasRace(player)) {
            gui.setItem(SLOT_CONFIRM, createItem(Material.RED_STAINED_GLASS_PANE,
                    "§c§l✗ 無法切換種族",
                    List.of("§7你已經選擇了種族。", "§7如需更換，請聯繫管理員。")));
        } else {
            gui.setItem(SLOT_CONFIRM, createItem(Material.LIME_DYE,
                    "§a§l✦ 選擇此種族",
                    List.of("", "§e點擊確認選擇 " + race.getDisplayName(),
                            "", "§c⚠ 種族只能選擇一次！")));
        }

        playerDetailRace.put(player.getUniqueId(), raceId);
        openViews.put(player.getUniqueId(),gui); listPages.put(player.getUniqueId(),page);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
    }

    // ═══════════════════════════════════════
    //  事件處理
    // ═══════════════════════════════════════

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if(openViews.get(player.getUniqueId())!=event.getView().getTopInventory()) return;
        String title = event.getView().getTitle();

        if (title.equals(LIST_TITLE)) {
            handleListClick(event, player);
        } else if (title.startsWith(DETAIL_TITLE_PREFIX)) {
            handleDetailClick(event, player);
        }
    }

    /**
     * 處理列表頁點擊 → 開啟詳情頁
     */
    private void handleListClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= LIST_SIZE) return;
        int page=listPages.getOrDefault(player.getUniqueId(),0);
        if(slot==45||slot==53) {
            int next=page+(slot==45?-1:1); player.closeInventory();
            plugin.getServer().getScheduler().runTaskLater(plugin,()->{ if(player.isOnline()) open(player,next); },1L); return;
        }

        Map<Integer, String> slotMap = playerListSlotMap.get(player.getUniqueId());
        if (slotMap == null) return;

        String raceId = slotMap.get(slot);
        if (raceId == null) return;

        // 關閉列表頁，延遲 1 tick 開啟詳情頁
        player.closeInventory();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if(player.isOnline()) openDetailPage(player,raceId,page); }, 1L);
    }

    /**
     * 處理詳情頁點擊
     */
    private void handleDetailClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= DETAIL_SIZE) return;

        if (slot == SLOT_BACK) {
            int page=listPages.getOrDefault(player.getUniqueId(),0);
            // 返回列表頁
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if(player.isOnline()) open(player,page); }, 1L);
            return;
        }

        if (slot == SLOT_CONFIRM) {
            String raceId = playerDetailRace.get(player.getUniqueId());
            if (raceId == null) return;

            // 已有種族 → 不可選
            if (raceManager.hasRace(player)) {
                player.sendMessage("§c§l[種族] §c你已經選擇了種族！如需更換，請聯繫管理員。");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // 執行選擇
            boolean success = raceManager.choosePlayerRace(player, raceId);
            if (success) {
                RaceData raceData = raceManager.getRaceData(raceId);
                String raceName = raceData != null ? raceData.getDisplayName() : raceId;

                player.closeInventory();
                player.sendMessage("§6§l════════════════════════════════");
                player.sendMessage("§a§l  ✓ 種族選擇成功！");
                player.sendMessage("§e  你選擇了: " + raceName);
                player.sendMessage("");
                if (raceData != null) {
                    player.sendMessage("§7  基礎屬性加成已套用！");
                    player.sendMessage("§7  力量+" + raceData.getBaseStrength()
                            + " 魔法+" + raceData.getBaseMagic()
                            + " 敏捷+" + raceData.getBaseAgility());
                    player.sendMessage("§7  體力+" + raceData.getBaseVitality()
                            + " 防禦+" + raceData.getBaseDefense()
                            + " 精神+" + raceData.getBaseSpirit());
                }
                player.sendMessage("§6§l════════════════════════════════");

                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING,
                        player.getLocation().add(0, 1, 0), 50, 0.5, 1.0, 0.5, 0.1);
            } else {
                player.sendMessage("§c§l[種族] §c種族選擇失敗，未變更選擇。"+raceManager.getLastError());
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }

            // 清除狀态
            playerDetailRace.remove(player.getUniqueId());
            playerListSlotMap.remove(player.getUniqueId());
        }
    }

    // ═══════════════════════════════════════
    //  工具方法
    // ═══════════════════════════════════════

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if(openViews.get(event.getWhoClicked().getUniqueId())==event.getView().getTopInventory()) event.setCancelled(true);
    }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        UUID id=event.getPlayer().getUniqueId();
        if(openViews.get(id)==event.getInventory()) clear(id);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { clear(event.getPlayer().getUniqueId()); }
    private void clear(UUID id) { openViews.remove(id); listPages.remove(id); playerListSlotMap.remove(id); playerDetailRace.remove(id); }
    public void cleanup() {
        for(Player player:plugin.getServer().getOnlinePlayers()) if(openViews.containsKey(player.getUniqueId())) player.closeInventory();
        openViews.clear(); listPages.clear(); playerListSlotMap.clear(); playerDetailRace.clear();
    }
    private ItemStack createItem(Material material, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFiller(Material material) {
        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }
        return filler;
    }
}
