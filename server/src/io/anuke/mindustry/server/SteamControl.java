package io.anuke.mindustry.server;

import com.codedisaster.steamworks.*;
import io.anuke.arc.util.*;
import io.anuke.mindustry.*;
import io.anuke.mindustry.core.Version;

public class SteamControl{

    public boolean init(){

        if(Version.modifier != null && Version.modifier.contains("steam")){
            try{
                SteamAPI.loadLibraries();

                if(!SteamAPI.init()){
                    Log.err("Steam client failed to initialize! Exiting.");
                    return false;
                }else{
                    Log.info("Connected to Steam API.");
                    Vars.steam = true;
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
}
