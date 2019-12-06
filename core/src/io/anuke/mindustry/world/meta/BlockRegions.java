package io.anuke.mindustry.world.meta;

import io.anuke.arc.*;
import io.anuke.arc.collection.*;
import io.anuke.arc.graphics.g2d.*;
import io.anuke.mindustry.world.*;

public class BlockRegions{
    private final Block block;
    private final Array<String> names = new Array<>(8);
    private TextureRegion[] cache;

    public BlockRegions(Block block){
        this.block = block;
        addRaw(block.name);
    }

    public void load(){
        cache = names.map(name -> Core.atlas.find(name)).toArray(TextureRegion.class);
    }

    public TextureRegion get(int id){
        return cache == null ? Core.atlas.find(names.get(id)) : cache[id];
    }

    public int add(String name){
        return addRaw(block.name + "-" + name);
    }

    public int addRaw(String name){
        names.add(name);
        return names.size - 1;
    }
}
