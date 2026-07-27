package me.perch.shopfinder.utils;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CustomItemMatchers {

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