package com.tehbeard.beardstat.utils;

import java.util.UUID;

import net.dragonzone.promise.Promise;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import com.tehbeard.beardstat.BeardStat;
import com.tehbeard.beardstat.containers.EntityStatBlob;
import com.tehbeard.beardstat.dataproviders.identifier.IdentifierService;
import com.tehbeard.beardstat.listeners.defer.DelegateDecrement;
import com.tehbeard.beardstat.listeners.defer.DelegateIncrement;
import com.tehbeard.beardstat.listeners.defer.DelegateSet;
import com.tehbeard.beardstat.manager.EntityStatManager;

/**
 * Provides helper methods for recording stats
 * @author James
 *
 * Methods that take a {@link Player} object use the BeardStat.DEFAULT_DOMAIN domain, and world provided by the player.
 * modifyXXX methods adjust stats relatively. if you pass in +3, the stat is incremented by 3.
 * setXXX methods adjust stats absolutely. If you pass 50, the stat is now 50. 
 */
@SuppressWarnings("deprecation")
public class PluginStatUtils {

    private EntityStatManager manager = null;
    private final String domain;
    public PluginStatUtils(String domain){
        this.domain = domain;
        this.manager = JavaPlugin.getPlugin(BeardStat.class).getStatManager();
    }
    
    /**
     * Increment/decrement a stat based on a {@link PotionEffect}
     * @param player
     * @param category
     * @param effect
     * @param amount 
     */
    public void modifyStatPotion(Player player,String category,PotionEffect effect, int amount){
        modifyStatPlayer(player, category, IdentifierService.getIdForPotionEffect(effect), amount);
    }
    
    /**
     * Increment/decrement a stat based on a {@link Entity}
     * @param player
     * @param category
     * @param entity
     * @param amount
     */
    public void modifyStatEntity(Player player, String category, Entity entity, int amount){
        modifyStatPlayer(player, category, IdentifierService.getIdForEntity(entity), amount);
        //TODO -  Deprecate or add api handle?
        if (entity instanceof Skeleton) {
            modifyStatPlayer(player, category, ((Skeleton) entity).getSkeletonType().toString().toLowerCase()+ "_skeleton",amount);
        }

        if (entity instanceof Zombie) {
            if (((Zombie) entity).isVillager()) {
                modifyStatPlayer(player, category, "villager_zombie", amount);
            }
        }
    }
    
    /**
     * Sets a players stat to the provided value
     * @param player
     * @param category
     * @param statistic
     * @param amount
     */
    public void setPlayerStat(Player player,String category, String statistic, int amount){
        set( player.getUniqueId(), 
                domain, 
                player.getWorld().getName(), 
                category, 
                statistic,
                amount);
    }
    
    /**
     * Increment/decrement a stat
     * @param player
     * @param category
     * @param statistic
     * @param amount
     */
    public void modifyStatPlayer(Player player,String category, String statistic, int amount){
        modifyStat(
                player.getUniqueId(), 
                domain, 
                player.getWorld().getName(), 
                category, 
                statistic,
                null, 
                amount);
    }

    /**
     * Increment/decrement a stat based on a {@link ItemStack}
     * @param player
     * @param category
     * @param item
     * @param amount
     */
    public void modifyStatItem(Player player,  String category, ItemStack item, int amount){
        String baseId = IdentifierService.getIdForItemStack(item);
        String metaId = IdentifierService.getIdForItemStackWithMeta(item);
        modifyStat(player.getUniqueId(), domain, player.getWorld().getName(), category, baseId, metaId, amount);
        
    }

    /**
     * Increment/decrement a stat based on a {@link Block}
     * @param player
     * @param category
     * @param block
     * @param amount
     */
    public void modifyStatBlock(Player player, String category, Block block, int amount){
        modifyStatBlock(player.getUniqueId(),
                domain,
                player.getWorld().getName(),
                category,
                block,
                amount);
    }

    /**
     * Increment/decrement a stat based on a {@link Block}
     * @param player
     * @param domain
     * @param world
     * @param category
     * @param block
     * @param amount
     */
    public void modifyStatBlock(UUID uuid,String domain, String world, String category, Block block, int amount){
        String baseId = IdentifierService.getIdForMaterial(block.getType());
        String metaId = IdentifierService.getIdForMaterial(block.getType(),block.getData());
        modifyStat(uuid, domain, world, category, baseId, metaId, amount);
    }

    /**
     * Increment/decrement a stat
     * @param uuid
     * @param domain
     * @param world
     * @param category
     * @param baseId
     * @param metaId
     * @param amount
     */
    public void modifyStat(UUID uuid,String domain, String world, String category, String baseId, String metaId, int amount){
        boolean inc = (amount > 0);
        int am = Math.abs(amount);

        if(inc){
            increment(uuid, domain, world, category, baseId, am);
            if(metaId != null){
                increment(uuid, domain, world, category, metaId, am);
            }
        }
        else
        {
            decrement(uuid, domain, world, category, baseId, am);
            if(metaId != null){
                decrement(uuid, domain, world, category, metaId, am);
            }
        }
    }

    /**
     * Increments a stat
     * @param player
     * @param domain
     * @param world
     * @param category
     * @param statistic
     * @param amount
     */
    public void increment(UUID uuid,String domain, String world, String category, String statistic, int amount){
        Promise<EntityStatBlob> blob = manager.getBlobForUUID(uuid.toString().replaceAll("-",""));
        blob.onResolve(new DelegateIncrement(domain,world,category,statistic,amount));
    }

    /**
     * Decrements a stat
     * @param player
     * @param domain
     * @param world
     * @param category
     * @param statistic
     * @param amount
     */
    public void decrement(UUID uuid,String domain, String world, String category, String statistic, int amount){
        Promise<EntityStatBlob> blob = manager.getBlobForUUID(uuid.toString().replaceAll("-",""));
        blob.onResolve(new DelegateDecrement(domain,world,category,statistic,amount));
    }
    
    /**
     * Sets a stat
     * @param player
     * @param domain
     * @param world
     * @param category
     * @param statistic
     * @param amount
     */
    public void set(UUID uuid,String domain, String world, String category, String statistic, int amount){
        Promise<EntityStatBlob> blob = manager.getBlobForUUID(uuid.toString().replaceAll("-",""));
        blob.onResolve(new DelegateSet(domain,world,category,statistic,amount));
    }

}
