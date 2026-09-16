package github.nighter.smartspawner.hooks.economy.shops.providers.economyshopgui;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.economy.shops.providers.ShopProvider;
import lombok.RequiredArgsConstructor;
import me.gypopo.economyshopgui.api.EconomyShopGUIHook;
import me.gypopo.economyshopgui.objects.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

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
            Plugin economyShopGUI = null;
            for (String pluginName : PLUGIN_NAMES) {
                economyShopGUI = Bukkit.getPluginManager().getPlugin(pluginName);
                if (economyShopGUI != null) {
                    break;
                }
            }

            if (economyShopGUI != null) {
                return true;
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
            ShopItem shopItem = EconomyShopGUIHook.getShopItem(item);

            if (shopItem == null) {
                // Not configured in the shop at all -> not sellable.
                return 0.0;
            }

            if (!isSellable(shopItem)) {
                // Buy-only items (e.g. Phantom Membrane in the default shop) must not be sellable.
                return 0.0;
            }

            Double sellPrice = EconomyShopGUIHook.getItemSellPrice(shopItem, item);
            if (sellPrice == null || sellPrice <= 0.0) {
                return 0.0;
            }
            return sellPrice;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * EconomyShopGUI marks buy-only items either via a sell-disabled flag or by a missing/negative
     * sell price. The API surface differs between versions (free vs Premium), so the flag is read
     * reflectively and we fall back to the raw sell price when it is absent.
     */
    private boolean isSellable(ShopItem shopItem) {
        for (String method : new String[]{"isSellable", "canSell", "isSellEnabled"}) {
            try {
                Object result = shopItem.getClass().getMethod(method).invoke(shopItem);
                if (result instanceof Boolean bool) {
                    return bool;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Method not present in this EconomyShopGUI version, try the next one.
            }
        }

        try {
            Object raw = shopItem.getClass().getMethod("getSellPrice").invoke(shopItem);
            if (raw instanceof Number number) {
                return number.doubleValue() > 0.0;
            }
            if (raw == null) {
                return false;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // No sell price accessor; fall through and let the hook price decide.
        }

        return true;
    }
}
