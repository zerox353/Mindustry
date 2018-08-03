package io.anuke.mindustry.maps.missions;

import com.badlogic.gdx.math.Vector2;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.content.blocks.StorageBlocks;
import io.anuke.mindustry.game.GameMode;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.maps.generation.Generation;
import io.anuke.mindustry.world.blocks.Floor;
import io.anuke.ucore.noise.Noise;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.util.Mathf;

public interface Mission{
    boolean isComplete();
    String displayString();
    GameMode getMode();
    void display(Table table);

    default void generate(Generation gen){}

    default void generateCoreAt(Generation gen, int coreX, int coreY, Team team){
        Noise.setSeed(0);
        float targetElevation = Math.max(gen.tiles[coreX][coreY].getElevation(), 1);

        int lerpDst = 20;
        for(int x = -lerpDst; x <= lerpDst; x++){
            for(int y = -lerpDst; y <= lerpDst; y++){
                int wx = gen.width/2 + x, wy = gen.height/2 + y;

                float dst = Vector2.dst(wx, wy, coreX, coreY);
                float elevation = gen.tiles[wx][wy].getElevation();

                if(dst < lerpDst){
                    elevation = Mathf.lerp(elevation, targetElevation, Mathf.clamp(2*(1f-(dst / lerpDst))) + Noise.nnoise(wx, wy, 8f, 1f));
                }

                if(gen.tiles[wx][wy].floor().liquidDrop == null){
                    gen.tiles[wx][wy].setElevation((int) elevation);
                }else{
                    gen.tiles[wx][wy].setFloor((Floor) Blocks.sand);
                }
            }
        }

        gen.tiles[coreX][coreY].setBlock(StorageBlocks.core);
        gen.tiles[coreX][coreY].setTeam(team);
    }
}
