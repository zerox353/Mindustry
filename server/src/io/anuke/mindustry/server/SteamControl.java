package io.anuke.mindustry.server;

import com.codedisaster.steamworks.*;
import io.anuke.arc.*;
import io.anuke.arc.backends.headless.*;
import io.anuke.arc.util.*;
import io.anuke.mindustry.*;
import io.anuke.mindustry.core.Version;
import io.anuke.mindustry.game.EventType.*;
import io.anuke.mindustry.steam.*;

public class SteamControl{

    public boolean init(){

        if(Version.modifier != null && Version.modifier.contains("steam")){
            try{
                HeadlessFiles files = new HeadlessFiles();
                //create appid file, since that is required
                if(!files.local("steam_appid.txt").exists()){
                    files.local("steam_appid.txt").writeString("1127400");
                }

                SteamAPI.loadLibraries();

                if(!SteamAPI.init()){
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
        SVars.net = new SNet(null);
        Events.on(ServerLoadEvent.class, e -> {
            Core.app.addListener(new ApplicationListener(){
                @Override
                public void update(){
                    if(SteamAPI.isSteamRunning()){
                        SteamAPI.runCallbacks();
                    }
                }

                @Override
                public void dispose(){
                    if(Vars.steam && SteamAPI.isSteamRunning()){
                        SteamAPI.shutdown();
                    }
                }
            });
        });
    }
}
