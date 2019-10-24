package io.anuke.mindustry.server.steam;

import com.codedisaster.steamworks.*;
import com.codedisaster.steamworks.SteamGameServerAPI.*;
import io.anuke.arc.*;
import io.anuke.arc.backends.headless.*;
import io.anuke.arc.util.*;
import io.anuke.mindustry.*;
import io.anuke.mindustry.core.Version;
import io.anuke.mindustry.game.EventType.*;

public class SteamControl{

    public boolean init(){

        if(Version.modifier != null && Version.modifier.contains("steam")){
            try{
                HeadlessFiles files = new HeadlessFiles();
                //create appid file, since that is required
                if(!files.local("steam_appid.txt").exists()){
                    files.local("steam_appid.txt").writeString("1127400");
                }

                SteamGameServerAPI.loadLibraries();

                if(!SteamGameServerAPI.init(0x7f000001, (short)6568, (short)6567, (short)6569, ServerMode.AuthenticationAndSecure, Version.build + "")){
                    Log.err("Steam client failed to initialize! Exiting.");
                    return false;
                }else{
                    Log.info("&lc[Connected to Steam API.]");
                    Vars.steam = true;
                    setProviders();
                    return true;
                }
            }catch(Throwable e){
                Log.err("Failed to load Steam native libraries. Exiting.");
                Log.err(e);
            }
            return false;
        }
        return true;
    }

    private void setProviders(){
        Events.on(ServerLoadEvent.class, e -> {
            Core.app.addListener(new ApplicationListener(){
                @Override
                public void update(){
                    if(Vars.steam){
                        SteamGameServerAPI.runCallbacks();
                    }
                }

                @Override
                public void dispose(){
                    if(Vars.steam){
                        SteamGameServerAPI.shutdown();
                    }
                }
            });
        });
    }
}
