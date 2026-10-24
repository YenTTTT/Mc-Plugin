package com.customrpg.managers;

import java.util.*;
import org.bukkit.entity.Player;

/** Own absorption grants only; never restores spent absorption or replaces another plugin's grants. */
final class EarthShields {
    interface Access {double amount(Player p);void amount(Player p,double value);void capacity(Player p,double own);}
    private record Key(UUID owner,String skill){}
    private static final class Grant {final Key key;double left;final long until;Grant(Key k,double n,long until){key=k;left=n;this.until=until;}}
    private static final class Account {final Player player;final List<Grant> grants=new ArrayList<>();double observed;Account(Player p,double n){player=p;observed=n;}}
    private final Map<UUID,Account> accounts=new HashMap<>();private final Access access;
    EarthShields(Access access){this.access=access;}
    private void sync(Account a){double current=access.amount(a.player),lost=Math.max(0,a.observed-current);for(var g:a.grants){double used=Math.min(g.left,lost);g.left-=used;lost-=used;}a.observed=current;}
    private double total(Account a){return a.grants.stream().mapToDouble(g->g.left).sum();}
    private void subtract(Account a,double n){double value=Math.max(0,access.amount(a.player)-n);access.capacity(a.player,total(a));access.amount(a.player,value);a.observed=access.amount(a.player);}
    void grant(Player p,UUID owner,String skill,double amount,long until){
        if(amount<=0||accounts.size()>=1024&&!accounts.containsKey(p.getUniqueId()))return;
        var a=accounts.computeIfAbsent(p.getUniqueId(),id->new Account(p,access.amount(p)));sync(a);var key=new Key(owner,skill);
        double replaced=a.grants.stream().filter(g->g.key.equals(key)).mapToDouble(g->g.left).sum();a.grants.removeIf(g->g.key.equals(key));
        // Update capacity after replacing the previous contribution, preserving external absorption.
        a.grants.add(new Grant(key,amount,until));double value=Math.max(0,access.amount(p)-replaced)+amount;
        access.capacity(p,total(a));access.amount(p,value);a.observed=access.amount(p);
    }
    void tick(long tick){for(var a:new ArrayList<>(accounts.values())){sync(a);double removed=a.grants.stream().filter(g->g.until<=tick||g.left<=0).mapToDouble(g->g.left).sum();a.grants.removeIf(g->g.until<=tick||g.left<=0);if(removed>0||a.grants.isEmpty())subtract(a,removed);if(a.grants.isEmpty())accounts.remove(a.player.getUniqueId());}}
    void owner(UUID owner){for(var a:new ArrayList<>(accounts.values())){sync(a);double n=a.grants.stream().filter(g->g.key.owner.equals(owner)).mapToDouble(g->g.left).sum();a.grants.removeIf(g->g.key.owner.equals(owner));subtract(a,n);if(a.grants.isEmpty())accounts.remove(a.player.getUniqueId());}}
    void target(UUID id){var a=accounts.remove(id);if(a!=null){sync(a);double n=total(a);a.grants.clear();subtract(a,n);}}
    void clear(){for(var id:new ArrayList<>(accounts.keySet()))target(id);}
}
