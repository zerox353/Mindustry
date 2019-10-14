package io.anuke.mindustry.net;

import io.anuke.arc.*;
import io.anuke.arc.collection.*;
import io.anuke.arc.util.*;

import java.io.*;

import static io.anuke.mindustry.Vars.headless;

public class Administration{
    /** All player info. Maps UUIDs to info. This persists throughout restarts. */
    private ObjectMap<String, PlayerInfo> playerInfo = new ObjectMap<>();
    private Array<String> bannedIPs = new Array<>();
    private Array<String> whitelist = new Array<>();

    public Administration(){
        Core.settings.defaults(
            "strict", true,
            "servername", "Server"
        );

        load();
    }

    public int getPlayerLimit(){
        return Core.settings.getInt("playerlimit", 0);
    }

    public void setPlayerLimit(int limit){
        Core.settings.putSave("playerlimit", limit);
    }

    public void setStrict(boolean on){
        Core.settings.putSave("strict", on);
    }

    public boolean getStrict(){
        return Core.settings.getBool("strict");
    }

    public boolean allowsCustomClients(){
        return Core.settings.getBool("allow-custom", !headless);
    }

    public void setCustomClients(boolean allowed){
        Core.settings.put("allow-custom", allowed);
        Core.settings.save();
    }

    /** Call when a player joins to update their information here. */
    public void updatePlayerJoined(String id, String ip, String name){
        PlayerInfo info = getCreateInfo(id);
        info.lastName = name;
        info.lastIP = ip;
        info.timesJoined++;
        if(!info.names.contains(name, false)) info.names.add(name);
        if(!info.ips.contains(ip, false)) info.ips.add(ip);
    }

    public boolean banPlayer(String uuid){
        return banPlayerID(uuid) || banPlayerIP(getInfo(uuid).lastIP);
    }

    /**
     * Bans a player by IP; returns whether this player was already banned.
     * If there are players who at any point had this IP, they will be UUID banned as well.
     */
    public boolean banPlayerIP(String ip){
        if(bannedIPs.contains(ip, false))
            return false;

        for(PlayerInfo info : playerInfo.values()){
            if(info.ips.contains(ip, false)){
                info.banned = true;
            }
        }

        bannedIPs.add(ip);
        save();

        return true;
    }

    /** Bans a player by UUID; returns whether this player was already banned. */
    public boolean banPlayerID(String id){
        if(playerInfo.containsKey(id) && playerInfo.get(id).banned)
            return false;

        getCreateInfo(id).banned = true;

        save();

        return true;
    }

    /**
     * Unbans a player by IP; returns whether this player was banned in the first place.
     * This method also unbans any player that was banned and had this IP.
     */
    public boolean unbanPlayerIP(String ip){
        boolean found = bannedIPs.contains(ip, false);

        for(PlayerInfo info : playerInfo.values()){
            if(info.ips.contains(ip, false)){
                info.banned = false;
                found = true;
            }
        }

        bannedIPs.removeValue(ip, false);

        if(found) save();

        return found;
    }

    /**
     * Unbans a player by ID; returns whether this player was banned in the first place.
     * This also unbans all IPs the player used.
     */
    public boolean unbanPlayerID(String id){
        PlayerInfo info = getCreateInfo(id);

        if(!info.banned)
            return false;

        info.banned = false;
        bannedIPs.removeAll(info.ips, false);
        save();

        return true;
    }

    /**
     * Returns list of all players with admin status
     */
    public Array<PlayerInfo> getAdmins(){
        Array<PlayerInfo> result = new Array<>();
        for(PlayerInfo info : playerInfo.values()){
            if(info.admin){
                result.add(info);
            }
        }
        return result;
    }

    /**
     * Returns list of all players with admin status
     */
    public Array<PlayerInfo> getBanned(){
        Array<PlayerInfo> result = new Array<>();
        for(PlayerInfo info : playerInfo.values()){
            if(info.banned){
                result.add(info);
            }
        }
        return result;
    }

    /**
     * Returns all banned IPs. This does not include the IPs of ID-banned players.
     */
    public Array<String> getBannedIPs(){
        return bannedIPs;
    }

    /**
     * Makes a player an admin. Returns whether this player was already an admin.
     */
    public boolean adminPlayer(String id, String usid){
        PlayerInfo info = getCreateInfo(id);

        if(info.admin && info.adminUsid != null && info.adminUsid.equals(usid))
            return false;

        info.adminUsid = usid;
        info.admin = true;
        save();

        return true;
    }

    /**
     * Makes a player no longer an admin. Returns whether this player was an admin in the first place.
     */
    public boolean unAdminPlayer(String id){
        PlayerInfo info = getCreateInfo(id);

        if(!info.admin)
            return false;

        info.admin = false;
        save();

        return true;
    }

    public boolean isWhitelistEnabled(){
        return Core.settings.getBool("whitelist", false);
    }

    public void setWhitelist(boolean enabled){
        Core.settings.putSave("whitelist", enabled);
    }

    public boolean isWhitelisted(String id, String usid){
        return !isWhitelistEnabled() || whitelist.contains(usid + id);
    }

    public boolean whitelist(String id){
        PlayerInfo info = getCreateInfo(id);
        if(whitelist.contains(info.adminUsid + id)) return false;
        whitelist.add(info.adminUsid + id);
        save();
        return true;
    }

    public boolean unwhitelist(String id){
        PlayerInfo info = getCreateInfo(id);
        if(whitelist.contains(info.adminUsid + id)){
            whitelist.remove(info.adminUsid + id);
            save();
            return true;
        }
        return false;
    }

    public boolean isIPBanned(String ip){
        return bannedIPs.contains(ip, false) || (findByIP(ip) != null && findByIP(ip).banned);
    }

    public boolean isIDBanned(String uuid){
        return getCreateInfo(uuid).banned;
    }

    public boolean isAdmin(String id, String usid){
        PlayerInfo info = getCreateInfo(id);
        return info.admin && usid.equals(info.adminUsid);
    }

    /** Finds player info by IP, UUID and name. */
    public ObjectSet<PlayerInfo> findByName(String name){
        ObjectSet<PlayerInfo> result = new ObjectSet<>();

        for(PlayerInfo info : playerInfo.values()){
            if(info.lastName.toLowerCase().equals(name.toLowerCase()) || (info.names.contains(name, false))
            || info.ips.contains(name, false) || info.id.equals(name)){
                result.add(info);
            }
        }

        return result;
    }

    public Array<PlayerInfo> findByIPs(String ip){
        Array<PlayerInfo> result = new Array<>();

        for(PlayerInfo info : playerInfo.values()){
            if(info.ips.contains(ip, false)){
                result.add(info);
            }
        }

        return result;
    }

    public PlayerInfo getInfo(String id){
        return getCreateInfo(id);
    }

    public PlayerInfo getInfoOptional(String id){
        return playerInfo.get(id);
    }

    public PlayerInfo findByIP(String ip){
        for(PlayerInfo info : playerInfo.values()){
            if(info.ips.contains(ip, false)){
                return info;
            }
        }
        return null;
    }

    public Array<PlayerInfo> getWhitelisted(){
        return playerInfo.values().toArray().select(p -> isWhitelisted(p.id, p.adminUsid));
    }

    private PlayerInfo getCreateInfo(String id){
        if(playerInfo.containsKey(id)){
            return playerInfo.get(id);
        }else{
            PlayerInfo info = new PlayerInfo(id);
            playerInfo.put(id, info);
            save();
            return info;
        }
    }

    public void save(){
        Core.settings.putObject("player-data", playerInfo);
        Core.settings.putObject("ipbans", bannedIPs);
        Core.settings.putObject("whitelist", whitelist);
        Core.settings.save();
    }

    @SuppressWarnings("unchecked")
    private void load(){
        if(!loadLegacy()){
            playerInfo = Core.settings.getObject("player-data", ObjectMap.class, ObjectMap::new);
            bannedIPs = Core.settings.getObject("ipbans", Array.class, Array::new);
            whitelist = Core.settings.getObject("whitelist", Array.class, Array::new);
        }else{
            save();
            Log.info("Loaded legacy server data.");
        }
    }

    private boolean loadLegacy(){
        try{
            byte[] info = Core.settings.getBytes("player-info");
            byte[] ips = Core.settings.getBytes("banned-ips");
            byte[] whitelist = Core.settings.getBytes("whitelisted");

            if(info != null){
                DataInputStream d = new DataInputStream(new ByteArrayInputStream(info));
                int size = d.readInt();
                if(size != 0){
                    d.readUTF();
                    d.readUTF();

                    for(int i = 0; i < size; i++){
                        String mapKey = d.readUTF();

                        PlayerInfo data = new PlayerInfo();

                        data.id = d.readUTF();
                        data.lastName = d.readUTF();
                        data.lastIP = d.readUTF();
                        int ipsize = d.readInt();
                        if(ipsize != 0){
                            d.readUTF();
                            for(int j = 0; j < ipsize; j++){
                                data.ips.add(d.readUTF());
                            }
                        }

                        int namesize = d.readInt();
                        if(namesize != 0){
                            d.readUTF();
                            for(int j = 0; j < ipsize; j++){
                                data.names.add(d.readUTF());
                            }
                        }
                        //ips, names...
                        data.adminUsid = d.readUTF();
                        data.timesKicked = d.readInt();
                        data.timesJoined = d.readInt();
                        data.banned = d.readBoolean();
                        data.admin = d.readBoolean();
                        data.lastKicked = d.readLong();

                        playerInfo.put(mapKey, data);
                    }
                }
                Core.settings.remove("player-info");
            }

            if(ips != null){
                DataInputStream d = new DataInputStream(new ByteArrayInputStream(ips));
                int size = d.readInt();
                if(size != 0){
                    d.readUTF();
                    for(int i = 0; i < size; i++){
                        bannedIPs.add(d.readUTF());
                    }
                }
                Core.settings.remove("banned-ips");
            }

            if(whitelist != null){
                DataInputStream d = new DataInputStream(new ByteArrayInputStream(whitelist));
                int size = d.readInt();
                if(size != 0){
                    d.readUTF();
                    for(int i = 0; i < size; i++){
                        this.whitelist.add(d.readUTF());
                    }
                }
                Core.settings.remove("whitelisted");
            }

            return info != null || ips != null || whitelist != null;
        }catch(Throwable e){
            e.printStackTrace();
        }
        return false;
    }

    public static class PlayerInfo{
        public String id;
        public String lastName = "<unknown>", lastIP = "<unknown>";
        public Array<String> ips = new Array<>();
        public Array<String> names = new Array<>();
        public String adminUsid;
        public int timesKicked;
        public int timesJoined;
        public boolean banned, admin;
        public long lastKicked; //last kicked timestamp

        PlayerInfo(String id){
            this.id = id;
        }

        public PlayerInfo(){
        }
    }

    public static class TraceInfo{
        public String ip, uuid;
        public boolean modded, mobile;

        public TraceInfo(String ip, String uuid, boolean modded, boolean mobile){
            this.ip = ip;
            this.uuid = uuid;
            this.modded = modded;
            this.mobile = mobile;
        }
    }

}
