package github.nighter.smartspawner.hooks.economy.shops.providers.economyshopgui;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.economy.shops.providers.ShopProvider;
import lombok.RequiredArgsConstructor;
import me.gypopo.economyshopgui.api.EconomyShopGUIHook;
import me.gypopo.economyshopgui.api.prices.AdvancedSellPrice;
import me.gypopo.economyshopgui.objects.ShopItem;
import me.gypopo.economyshopgui.util.EcoType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;

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

    /**
     * Mirrors what /sell and /sellgui do in EconomyShopGUI(-Premium):
     * anything the shop buys there is sellable here, at the same price.
     */
    @Override
    public double getSellPrice(Material material) {
        try {
            ItemStack item = new ItemStack(material);
            ShopItem shopItem = EconomyShopGUIHook.getShopItem(item);

            if (shopItem == null || !EconomyShopGUIHook.isSellAble(shopItem)) {
                return 0.0;
            }

            // Items with several sell prices (multi-currency setups) return null from the plain
            // getItemSellPrice(...), which is why they looked unsellable before.
            if (EconomyShopGUIHook.hasMultipleSellPrices(shopItem)) {
                double advanced = getAdvancedSellPrice(shopItem, item);
                if (advanced > 0.0) {
                    return advanced;
                }
            }

            Double sellPrice = EconomyShopGUIHook.getItemSellPrice(shopItem, item);
            if (sellPrice != null && sellPrice > 0.0) {
                return sellPrice;
            }

            // Last resort: the item-only lookup, which resolves some dynamic-pricing setups.
            Double fallback = EconomyShopGUIHook.getItemSellPrice(item);
            return fallback != null && fallback > 0.0 ? fallback : 0.0;
        } catch (Exception | NoSuchMethodError e) {
            return 0.0;
        }
    }

    private double getAdvancedSellPrice(ShopItem shopItem, ItemStack item) {
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
        } catch (Exception | NoSuchMethodError e) {
            return 0.0;
        }
    }
}
