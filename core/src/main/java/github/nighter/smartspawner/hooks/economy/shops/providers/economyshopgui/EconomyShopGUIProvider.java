package github.nighter.smartspawner.hooks.economy.shops.providers.economyshopgui;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.economy.shops.providers.ShopProvider;
import lombok.RequiredArgsConstructor;
import me.gypopo.economyshopgui.api.EconomyShopGUIHook;
import me.gypopo.economyshopgui.api.prices.AdvancedSellPrice;
import me.gypopo.economyshopgui.objects.ShopItem;
import me.gypopo.economyshopgui.objects.shops.ShopSection;
import me.gypopo.economyshopgui.util.EcoType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;

/**
 * Mirrors what /sell and /sellgui do in EconomyShopGUI(-Premium): whatever the shop buys there
 * is sellable from the spawner GUI, at the same price, without any markup.
 */
@RequiredArgsConstructor
public class EconomyShopGUIProvider implements ShopProvider {
    private final SmartSpawner plugin;
    private static final String[] PLUGIN_NAMES = {"EconomyShopGUI", "EconomyShopGUI-Premium"};

    @Override
    public String getPluginName() {
        if (plugin.getServer().getPluginManager().getPlugin("EconomyShopGUI-Premium") != null) {
            return "EconomyShopGUI-Premium";
        }
        return "EconomyShopGUI";
    }

    @Override
    public boolean isAvailable() {
        try {
            for (String pluginName : PLUGIN_NAMES) {
                Plugin economyShopGUI = Bukkit.getPluginManager().getPlugin(pluginName);
                if (economyShopGUI != null) {
                    return true;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error initializing EconomyShopGUI integration: " + e.getMessage());
        }
        return false;
    }

    @Override
    public double getSellPrice(Material material) {
        try {
            ItemStack item = new ItemStack(material);

            // Fast path: the entry EconomyShopGUI resolves for this plain item.
            double price = priceOf(EconomyShopGUIHook.getShopItem(item), item);
            if (price > 0.0) {
                return price;
            }

            // The fast path picks exactly one entry. A material can appear in several sections,
            // e.g. PHANTOM_MEMBRANE as a buy-only perk item and again in the sell section; if the
            // buy-only entry wins, the item looks unsellable. So scan every section for the best
            // sell price the shop actually offers.
            return scanSectionsForSellPrice(material, item);
        } catch (Exception | LinkageError e) {
            return 0.0;
        }
    }

    private double scanSectionsForSellPrice(Material material, ItemStack item) {
        Map<String, ShopSection> sections;
        try {
            sections = EconomyShopGUIHook.getSections();
        } catch (Exception | LinkageError e) {
            return 0.0;
        }
        if (sections == null || sections.isEmpty()) {
            return 0.0;
        }

        double best = 0.0;
        for (ShopSection section : sections.values()) {
            if (section == null) {
                continue;
            }
            for (ShopItem shopItem : section.getShopItems()) {
                if (!matchesMaterial(shopItem, material)) {
                    continue;
                }
                double price = priceOf(shopItem, item);
                if (price > best) {
                    best = price;
                }
            }
        }
        return best;
    }

    private boolean matchesMaterial(ShopItem shopItem, Material material) {
        if (shopItem == null) {
            return false;
        }
        try {
            if (shopItem.hasItemError() || shopItem.isDisplayItem()) {
                return false;
            }
            ItemStack stack = shopItem.getShopItem();
            return stack != null && stack.getType() == material;
        } catch (Exception | LinkageError e) {
            return false;
        }
    }

    /** Sell price of one shop entry, 0 when that entry is not sellable. */
    private double priceOf(ShopItem shopItem, ItemStack item) {
        if (shopItem == null) {
            return 0.0;
        }
        try {
            if (!EconomyShopGUIHook.isSellAble(shopItem)) {
                return 0.0;
            }

            // Items with several sell prices (multi-currency setups) return null from the plain
            // getItemSellPrice(...), so they have to go through AdvancedSellPrice.
            if (EconomyShopGUIHook.hasMultipleSellPrices(shopItem)) {
                double advanced = advancedSellPrice(shopItem, item);
                if (advanced > 0.0) {
                    return advanced;
                }
            }

            Double sellPrice = EconomyShopGUIHook.getItemSellPrice(shopItem, item);
            return sellPrice != null && sellPrice > 0.0 ? sellPrice : 0.0;
        } catch (Exception | LinkageError e) {
            return 0.0;
        }
    }

    private double advancedSellPrice(ShopItem shopItem, ItemStack item) {
        try {
            AdvancedSellPrice advanced = EconomyShopGUIHook.getMultipleSellPrices(shopItem);
            if (advanced == null || !advanced.isSellAble()) {
                return 0.0;
            }

            EcoType preferred = advanced.getSellTypes().isEmpty() ? null : advanced.getSellTypes().getFirst();
            Map<EcoType, Double> prices = advanced.getSellPrices(preferred, item);
            if (prices == null || prices.isEmpty()) {
                return 0.0;
            }

            double best = 0.0;
            for (Double price : prices.values()) {
                if (price != null && price > best) {
                    best = price;
                }
            }
            return best;
        } catch (Exception | LinkageError e) {
            return 0.0;
        }
    }
}
