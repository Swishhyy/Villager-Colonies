package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.WWMC;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Player supply orders: payment up front, exclusive acceptance, and credit only for goods the destination accepts. */
public final class PlayerContracts {
    private PlayerContracts() {}
    public static Item item(MultiplayerData.Contract order) {
        Identifier id=Identifier.tryParse(order.item); Item item=id==null ? null : BuiltInRegistries.ITEM.getValue(id);
        return item==null ? Items.AIR : item;
    }
    public static boolean withdraw(ServerPlayer player,int emeralds) {
        if(emeralds<0 || emeralds>64 || InventoryOps.count(List.of(player.getInventory()),s -> s.is(Items.EMERALD))<emeralds) return false;
        for(int n=0;n<emeralds;n++) InventoryOps.takeOne(List.of(player.getInventory()),s -> s.is(Items.EMERALD));
        return true;
    }
    public static String post(ServerLevel level,Settlement issuer,ServerPlayer publisher,ItemStack example,int amount,int payment) {
        if(issuer==null || issuer.trading.npc || issuer.campaign.parent!=null || !TownAccess.manages(issuer,publisher.getUUID())) return "Post contracts from a main settlement you manage.";
        if(publisher.level()!=level || publisher.distanceToSqr(Vec3.atCenterOf(issuer.center))>64 || !MultiplayerViews.valid(publisher,issuer.center)) return "Post the offer at your settlement banner.";
        if(example.isEmpty() || !ItemStack.isSameItemSameComponents(example,new ItemStack(example.getItem()))) return "Hold one plain, undamaged example of the requested item.";
        if(amount<1 || amount>256 || payment<1 || payment>64) return "Request 1–256 items and reserve 1–64 emeralds.";
        var data=MultiplayerData.get(level);
        if(data.contracts.size()>=64 || data.contracts.stream().filter(c -> c.issuer.equals(issuer.id)).count()>=8) return "Finish or cancel an existing offer first (eight per settlement, 64 per world).";
        if(SettlementService.townStorage(level,issuer).isEmpty()) return "Your warehouse needs loaded storage for deliveries.";
        if(!withdraw(publisher,payment)) return "Carry the full emerald payment; it is reserved when you post.";
        var order=new MultiplayerData.Contract(UUID.randomUUID(),issuer.id,publisher.getUUID(),BuiltInRegistries.ITEM.getKey(example.getItem()).toString(),amount,0,payment,Optional.empty(),Optional.empty());
        data.contracts.add(order); data.setDirty();
        CampaignService.record(level,issuer,"Posted a player contract: "+amount+" "+example.getHoverName().getString()+" for "+payment+" emeralds (reserved).");
        WWMC.LOGGER.info("[WWMC] [contracts] {} posted {}: {} x{}, payment {}",publisher.getUUID(),order.id,order.item,amount,payment);
        return "Contract posted; payment reserved. Other settlements can accept it on a banner's Multiplayer tab.";
    }
    public static String accept(ServerLevel level,Settlement supplier,ServerPlayer player,UUID id) {
        var data=MultiplayerData.get(level); var order=data.contract(id);
        Settlement issuer=order==null ? null : SettlementData.get(level).byId(order.issuer);
        if(order==null || issuer==null || order.supplier!=null) return "This contract is no longer available.";
        if(supplier==null || supplier.trading.npc || supplier.campaign.parent!=null || !TownAccess.manages(supplier,player.getUUID())
                || supplier.id.equals(issuer.id) || supplier.owner.equals(issuer.owner)) return "Accept for a different main settlement you manage.";
        order.supplier=supplier.id; order.recipient=player.getUUID(); data.setDirty();
        CampaignService.record(level,supplier,"Accepted "+issuer.name+"'s supply contract. Deliver at its banner; payment goes to "+player.getName().getString()+".");
        WWMC.LOGGER.info("[WWMC] [contracts] {} accepted {} for settlement {}",player.getUUID(),id,supplier.id);
        return "Accepted. Carry plain requested items to "+issuer.name+"'s banner at "+issuer.center.toShortString()+". Reserved payment goes to you after delivery.";
    }
    /** Moving from an arbitrary real container also lets the same delivery path be verified with warehouse fixtures. */
    public static int handoff(List<Container> sources,List<Container> destinations,Item wanted,int maximum) {
        int delivered=0;
        if(wanted==Items.AIR || sources.stream().anyMatch(destinations::contains)) return 0;
        for(Container source:sources) for(int slot=0;slot<source.getContainerSize() && delivered<maximum;slot++) {
            ItemStack held=source.getItem(slot);
            if(!held.is(wanted) || !ItemStack.isSameItemSameComponents(held,new ItemStack(wanted))) continue;
            ItemStack offered=held.copyWithCount(Math.min(held.getCount(),maximum-delivered));
            int before=offered.getCount();
            for(Container destination:destinations) { offered=InventoryOps.insert(destination,offered); if(offered.isEmpty()) break; }
            int accepted=before-offered.getCount();
            if(accepted>0) { source.removeItem(slot,accepted); source.setChanged(); delivered+=accepted; }
        }
        return delivered;
    }
    public static String deliver(ServerLevel level,ServerPlayer player,UUID id) {
        var data=MultiplayerData.get(level); var order=data.contract(id);
        Settlement issuer=order==null ? null : SettlementData.get(level).byId(order.issuer);
        Settlement supplier=order==null || order.supplier==null ? null : SettlementData.get(level).byId(order.supplier);
        if(order==null || issuer==null || !TownAccess.manages(supplier,player.getUUID())) return "Only the accepted supplier's owner or stewards may deliver.";
        if(player.level()!=level || player.distanceToSqr(Vec3.atCenterOf(issuer.center))>64 || !MultiplayerViews.valid(player,issuer.center)) return "Bring the goods to the issuing settlement's banner.";
        int moved=handoff(List.of(player.getInventory()),SettlementService.townStorage(level,issuer),item(order),order.remaining());
        if(moved==0) return "No goods moved: carry plain requested items and leave room in the issuer's loaded warehouse.";
        order.delivered+=moved; data.setDirty();
        if(order.remaining()>0) return "Delivered "+moved+". "+order.remaining()+" still needed; the payment remains reserved.";
        data.pay(order.recipient,order.payment); data.contracts.remove(order); data.setDirty();
        CampaignService.record(level,issuer,"Player contract completed by "+supplier.name+"; "+order.payment+" reserved emeralds paid.");
        CampaignService.record(level,supplier,"Completed "+issuer.name+"'s player supply contract.");
        WWMC.LOGGER.info("[WWMC] [contracts] {} completed {}; released {} emeralds to {}",supplier.id,id,order.payment,order.recipient);
        return "Contract completed. Reserved emeralds are ready to collect at a banner.";
    }
    public static String cancel(ServerLevel level,ServerPlayer player,UUID id) {
        var data=MultiplayerData.get(level); var order=data.contract(id);
        Settlement issuer=order==null ? null : SettlementData.get(level).byId(order.issuer);
        if(order==null || order.supplier!=null || !TownAccess.manages(issuer,player.getUUID())) return "Only an unaccepted offer can be cancelled by its issuing settlement.";
        data.pay(order.publisher,order.payment); data.contracts.remove(order); data.setDirty();
        WWMC.LOGGER.info("[WWMC] [contracts] Cancelled {}; refunded {} emeralds to {}",id,order.payment,order.publisher);
        return "Offer cancelled. Reserved emeralds returned to the publisher's payment balance.";
    }
    public static String abandon(ServerLevel level,ServerPlayer player,UUID id) {
        var data=MultiplayerData.get(level); var order=data.contract(id);
        Settlement supplier=order==null || order.supplier==null ? null : SettlementData.get(level).byId(order.supplier);
        if(order==null || !TownAccess.manages(supplier,player.getUUID())) return "Only the accepted supplier can release this contract.";
        // Already delivered goods stay at the issuer; a new supplier finishes only the remaining amount.
        order.supplier=null; order.recipient=null; data.setDirty();
        return "Contract released. Delivered goods remain credited; the remaining request is available again.";
    }
    public static int collect(ServerLevel level,ServerPlayer player) {
        var data=MultiplayerData.get(level); long balance=data.payments.getOrDefault(player.getUUID(),0L); int paid=0;
        while(balance>0 && paid<1024) {
            ItemStack offered=new ItemStack(Items.EMERALD,(int)Math.min(64,balance));
            ItemStack left=InventoryOps.insert(player.getInventory(),offered);
            int moved=offered.getCount()-left.getCount(); if(moved==0) break;
            balance-=moved; paid+=moved;
        }
        if(balance==0) data.payments.remove(player.getUUID()); else data.payments.put(player.getUUID(),balance);
        if(paid>0) data.setDirty(); return paid;
    }
    /** Missing towns refund reserved offers instead of silently deleting the payment. Accepted offers do not expire offline. */
    public static void cleanup(ServerLevel level) {
        var data=MultiplayerData.get(level); var towns=SettlementData.get(level);
        for(var order:new ArrayList<>(data.contracts)) {
            if(towns.byId(order.issuer)==null) { data.pay(order.publisher,order.payment); data.contracts.remove(order); data.setDirty(); }
            else if(order.supplier!=null && towns.byId(order.supplier)==null) { order.supplier=null; order.recipient=null; data.setDirty(); }
        }
    }
}
