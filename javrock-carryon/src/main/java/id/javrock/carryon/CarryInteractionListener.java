package id.javrock.carryon;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public class CarryInteractionListener implements Listener {

    private final JavrockCarryOnPlugin plugin;
    private final CarryManagerService carryService;

    public CarryInteractionListener(JavrockCarryOnPlugin plugin, CarryManagerService carryService) {
        this.plugin = plugin;
        this.carryService = carryService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }

        if (carryService.isCarrying(player.getUniqueId())) {
            event.setCancelled(true);
            if (action == Action.RIGHT_CLICK_BLOCK) {
                carryService.placeCarriedBlock(player, event.getClickedBlock(), event.getBlockFace());
            } else {
                carryService.placeCarriedBlock(player, null, null);
            }
            return;
        }

        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block clicked = event.getClickedBlock();
            if (carryService.canCarryBlock(clicked.getType())) {
                event.setCancelled(true);
                carryService.pickUpBlock(player, clicked);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        if (carryService.isCarrying(player.getUniqueId())) {
            event.setCancelled(true);
            carryService.releaseCarriedEntity(player);
            return;
        }

        Entity target = event.getRightClicked();
        if (carryService.canCarryEntity(target)) {
            event.setCancelled(true);
            carryService.pickUpEntity(player, target);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (carryService.isCarrying(event.getPlayer().getUniqueId())) {
            carryService.cleanupAll();
        }
    }
}
