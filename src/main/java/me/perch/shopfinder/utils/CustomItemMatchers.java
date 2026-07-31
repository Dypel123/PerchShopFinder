package me.perch.shopfinder.utils;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.Objects;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CustomItemMatchers {

    private static final NamespacedKey CRAZY_VOUCHER_ITEM =
            Objects.requireNonNull(NamespacedKey.fromString(
                    "crazyvouchers:crazyvouchers_voucher_item"
            ));

    private static final NamespacedKey PERCH_TRACKER_ID =
            Objects.requireNonNull(NamespacedKey.fromString(
                    "perchtrackers:tracker_id"
            ));

    private static final Set<Material> POTION_MATERIALS = EnumSet.of(
            Material.POTION,
            Material.SPLASH_POTION,
            Material.LINGERING_POTION,
            Material.TIPPED_ARROW
    );

    private static final Set<Material> UNNAMED_ONLY_MATERIALS =
            EnumSet.of(
                    Material.NAME_TAG,
                    Material.TRIPWIRE_HOOK,
                    Material.PAPER,
                    Material.MAP,
                    Material.BOOK,
                    Material.CHEST
            );

    private CustomItemMatchers() {
    }

    public static boolean isUnbreakable(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().isUnbreakable();
    }

    public static boolean isVoucher(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        PersistentDataContainer data =
                item.getItemMeta().getPersistentDataContainer();

        String voucherItem = data.get(
                CRAZY_VOUCHER_ITEM,
                PersistentDataType.STRING
        );

        return voucherItem != null
                && !voucherItem.toLowerCase(Locale.ROOT)
                .startsWith("randomtracker");
    }

    public static boolean isTracker(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        PersistentDataContainer data =
                item.getItemMeta().getPersistentDataContainer();

        // A tracker item itself.
        String trackerId = data.get(
                PERCH_TRACKER_ID,
                PersistentDataType.STRING
        );

        if (trackerId != null) {
            return true;
        }

        // A CrazyVouchers item which gives a random tracker.
        String voucherItem = data.get(
                CRAZY_VOUCHER_ITEM,
                PersistentDataType.STRING
        );

        return voucherItem != null
                && voucherItem.toLowerCase(Locale.ROOT)
                .startsWith("randomtracker");
    }

    public static boolean isTagItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        var meta = item.getItemMeta();

        // A renamed Minecraft name tag.
        if (item.getType() == Material.NAME_TAG
                && meta.hasDisplayName()) {
            return true;
        }

        // A CrazyVouchers voucher that gives a tag.
        String voucherItem = meta.getPersistentDataContainer().get(
                CRAZY_VOUCHER_ITEM,
                PersistentDataType.STRING
        );

        return voucherItem != null
                && voucherItem.toLowerCase(Locale.ROOT)
                .startsWith("tag");
    }

    public static boolean isShulkerBox(ItemStack item) {
        if (item == null) {
            return false;
        }

        Material material = item.getType();

        return material == Material.SHULKER_BOX
                || material.name().endsWith("_SHULKER_BOX");
    }

    public static boolean isFullShulkerOfMaterial(
            ItemStack item,
            Material expectedMaterial
    ) {
        if (!isShulkerBox(item)
                || expectedMaterial == null
                || !item.hasItemMeta()) {
            return false;
        }

        if (!(item.getItemMeta() instanceof BlockStateMeta blockStateMeta)
                || !blockStateMeta.hasBlockState()) {
            return false;
        }

        if (!(blockStateMeta.getBlockState()
                instanceof ShulkerBox shulkerBox)) {
            return false;
        }

        ItemStack[] contents = shulkerBox
                .getSnapshotInventory()
                .getStorageContents();

        // A standard shulker must contain all 27 storage slots.
        if (contents.length != 27) {
            return false;
        }

        for (ItemStack content : contents) {
            if (content == null
                    || content.getType() == Material.AIR
                    || content.getType() != expectedMaterial) {
                return false;
            }

            // Respects 64-stack, 16-stack, unstackable and
            // custom maximum-stack-size items.
            if (content.getAmount() != content.getMaxStackSize()) {
                return false;
            }
        }

        return true;
    }

    public static boolean loreContains(
            ItemStack item,
            String searchText
    ) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        var meta = item.getItemMeta();

        if (!meta.hasLore()) {
            return false;
        }

        List<String> lore = meta.getLore();

        if (lore == null) {
            return false;
        }

        String normalizedSearch =
                searchText.toLowerCase(Locale.ROOT);

        return lore.stream().anyMatch(line ->
                line != null
                        && line.toLowerCase(Locale.ROOT)
                        .contains(normalizedSearch)
        );
    }

    public static boolean hasStoredEnchantment(
            ItemStack item,
            Enchantment enchantment
    ) {
        if (item == null
                || item.getType() != Material.ENCHANTED_BOOK
                || !item.hasItemMeta()) {
            return false;
        }

        return item.getItemMeta()
                instanceof EnchantmentStorageMeta meta
                && meta.hasStoredEnchant(enchantment);
    }

    public static boolean hasPotionEffect(
            ItemStack item,
            PotionEffectType effect,
            boolean turtleMaster
    ) {
        if (item == null
                || !POTION_MATERIALS.contains(item.getType())
                || !item.hasItemMeta()) {
            return false;
        }

        if (!(item.getItemMeta() instanceof PotionMeta meta)) {
            return false;
        }

        try {
            var data = meta.getBasePotionData();

            if (data != null && data.getType() != null) {
                String potionType = data.getType().name();

                if (turtleMaster) {
                    return potionType.equals("TURTLE_MASTER");
                }

                // Avoid treating Turtle Master as a normal Slowness potion.
                if (!potionType.equals("TURTLE_MASTER")
                        && effect != null
                        && data.getType().getEffectType() == effect) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }

        return !turtleMaster
                && effect != null
                && meta.hasCustomEffects()
                && meta.getCustomEffects().stream()
                .anyMatch(potionEffect ->
                        potionEffect.getType().equals(effect));
    }

    public static boolean requiresUnnamedVariantFilter(
            Material material
    ) {
        return material != null
                && UNNAMED_ONLY_MATERIALS.contains(material);
    }

    public static boolean isUnnamedMaterial(
            ItemStack item,
            Material expectedMaterial
    ) {
        if (item == null
                || item.getType() != expectedMaterial) {
            return false;
        }

        return !item.hasItemMeta()
                || !item.getItemMeta().hasDisplayName();
    }
}