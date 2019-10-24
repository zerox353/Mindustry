package io.anuke.mindustry.server;

import io.anuke.arc.*;
import io.anuke.arc.backends.headless.*;
import io.anuke.mindustry.*;
import io.anuke.mindustry.core.*;
import io.anuke.mindustry.game.EventType.*;
import io.anuke.mindustry.mod.*;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.net.*;

import static io.anuke.mindustry.Vars.*;

public class ServerLauncher implements ApplicationListener{
    public static final SteamControl scontrol = new SteamControl();
    public static String[] args;

    public static void main(String[] arg){
        args = arg;
        try{
            Vars.platform = new Platform(){};
            Version.init();
            //skip initializing when steam control says there's an error
            if(!scontrol.init()){
                System.exit(1);
            }

            Vars.net = new Net(Vars.platform.getNet());
            new HeadlessApplication(new ServerLauncher(), null, throwable -> CrashSender.send(throwable, f -> {}));
        }catch(Throwable t){
            CrashSender.send(t, f -> {});
        }
    }

    @Override
    public void init(){
        Core.settings.setDataDirectory(Core.files.local("config"));
        loadLocales = false;
        headless = true;

        Vars.loadSettings();
        Vars.init();
        content.createContent();
        content.init();

        Core.app.addListener(logic = new Logic());
        Core.app.addListener(netServer = new NetServer());
        Core.app.addListener(new ServerControl(args));

        mods.each(Mod::init);

        Events.fire(new ServerLoadEvent());
    }


}
