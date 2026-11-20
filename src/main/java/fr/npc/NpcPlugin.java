package fr.npc;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class NpcPlugin extends JavaPlugin implements Listener {

    private static final String PERM = "npc.admin";
    private NamespacedKey npcKey;
    private final Map<UUID, UUID> renaming = new HashMap<>();

    private static final Map<String, Material> ICONS = new LinkedHashMap<>();
    static {
        ICONS.put("farmer", Material.WHEAT);
        ICONS.put("librarian", Material.LECTERN);
        ICONS.put("cleric", Material.BREWING_STAND);
        ICONS.put("armorer", Material.BLAST_FURNACE);
        ICONS.put("butcher", Material.SMOKER);
        ICONS.put("cartographer", Material.CARTOGRAPHY_TABLE);
        ICONS.put("fisherman", Material.BARREL);
        ICONS.put("fletcher", Material.FLETCHING_TABLE);
        ICONS.put("leatherworker", Material.CAULDRON);
        ICONS.put("mason", Material.STONECUTTER);
        ICONS.put("shepherd", Material.LOOM);
        ICONS.put("toolsmith", Material.SMITHING_TABLE);
        ICONS.put("weaponsmith", Material.GRINDSTONE);
        ICONS.put("nitwit", Material.GREEN_DYE);
        ICONS.put("none", Material.BARRIER);
    }

    private static final int SLOT_RENAME = 18, SLOT_TP = 20, SLOT_BABY = 22, SLOT_NAME = 24, SLOT_REMOVE = 26;

    @Override
    public void onEnable() {
        npcKey = new NamespacedKey(this, "npc");
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("NpcPlugin active.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Commande reservee aux joueurs.");
            return true;
        }
        if (!p.hasPermission(PERM)) {
            p.sendMessage(Component.text("Tu dois etre admin pour faire ca.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            p.sendMessage(Component.text("Usage : /npc <metier> (ex: farmer)", NamedTextColor.YELLOW));
            return true;
        }
        Villager.Profession prof = Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(args[0].toLowerCase()));
        if (prof == null) {
            p.sendMessage(Component.text("Metier inconnu. Utilise la touche Tab pour voir la liste.", NamedTextColor.RED));
            return true;
        }
        Villager v = p.getWorld().spawn(p.getLocation(), Villager.class, npc -> {
            npc.setProfession(prof);
            npc.setVillagerLevel(5);
            npc.setAI(false);
            npc.setInvulnerable(true);
            npc.setSilent(true);
            npc.setPersistent(true);
            npc.setRemoveWhenFarAway(false);
            npc.setCustomNameVisible(true);
            npc.customName(Component.text(prof.key().value(), NamedTextColor.GOLD));
            npc.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1);
        });
        p.sendMessage(Component.text("PNJ cree (" + prof.key().value() + "). Clic droit dessus pour l'editer.", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission(PERM)) return List.of();
        List<String> out = new ArrayList<>();
        for (Villager.Profession p : Registry.VILLAGER_PROFESSION) {
            String n = p.key().value();
            if (n.startsWith(args[0].toLowerCase())) out.add(n);
        }
        return out;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!isNpc(e.getRightClicked())) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!p.hasPermission(PERM)) return;
        openEditor(p, (Villager) e.getRightClicked());
    }

    private boolean isNpc(Entity en) {
        return en instanceof Villager
                && en.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE);
    }

    private static class Editor implements InventoryHolder {
        final UUID npc;
        Inventory inv;
        Editor(UUID npc) { this.npc = npc; }
        @Override public Inventory getInventory() { return inv; }
    }

    private void openEditor(Player p, Villager v) {
        Editor holder = new Editor(v.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("
