package io.anuke.mindustry.world.blocks.distribution;

import io.anuke.arc.*;
import io.anuke.arc.graphics.g2d.*;
import io.anuke.arc.math.*;
import io.anuke.arc.math.geom.*;
import io.anuke.arc.util.*;
import io.anuke.mindustry.entities.traits.BuilderTrait.*;
import io.anuke.mindustry.entities.type.*;
import io.anuke.mindustry.graphics.*;
import io.anuke.mindustry.type.*;
import io.anuke.mindustry.world.*;
import io.anuke.mindustry.world.blocks.*;
import io.anuke.mindustry.world.meta.*;

import java.io.*;

import static io.anuke.mindustry.Vars.tilesize;

public class Conveyor extends Block implements Autotiler{
    private TextureRegion[][] regions = new TextureRegion[7][4];
    protected float speed = 0f;

    protected Conveyor(String name){
        super(name);
        //conveyors don't update by default: only one initial 'seed' conveyor entity updates the conveyor line
        //this entity is removed in the end
        rotate = true;
        destructible = true;
        layer = Layer.conveyor;
        group = BlockGroup.transportation;
        hasItems = true;
        itemCapacity = 4;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(BlockStat.itemsMoved, speed * 60, StatUnit.itemsSecond);
    }

    @Override
    public void load(){
        super.load();

        for(int i = 0; i < regions.length; i++){
            for(int j = 0; j < 4; j++){
                regions[i][j] = Core.atlas.find(name + "-" + i + "-" + j);
            }
        }
    }

    @Override
    public void draw(Tile tile){
        ConveyorEntity entity = tile.entity();
        byte rotation = tile.rotation();

        int frame = entity.clogHeat <= 0.5f ? (int)(((Time.time() * speed * 8f * entity.timeScale)) % 4) : 0;
        Draw.rect(regions[Mathf.clamp(entity.blendbits, 0, regions.length - 1)][Mathf.clamp(frame, 0, regions[0].length - 1)], tile.drawx(), tile.drawy(),
        tilesize * entity.blendsclx, tilesize * entity.blendscly, rotation * 90);
    }

    @Override
    public boolean shouldIdleSound(Tile tile){
        ConveyorEntity entity = tile.entity();
        return entity.clogHeat <= 0.5f ;
    }

    @Override
    public void onProximityUpdate(Tile tile){
        super.onProximityUpdate(tile);

        ConveyorEntity entity = tile.entity();
        int[] bits = buildBlending(tile, tile.rotation(), null, true);
        entity.blendbits = bits[0];
        entity.blendsclx = bits[1];
        entity.blendscly = bits[2];
    }

    @Override
    public void drawRequestRegion(BuildRequest req, Eachable<BuildRequest> list){
        int[] bits = getTiling(req, list);

        if(bits == null) return;

        TextureRegion region = regions[bits[0]][0];
        Draw.rect(region, req.drawx(), req.drawy(), region.getWidth() * bits[1] * Draw.scl * req.animScale, region.getHeight() * bits[2] * Draw.scl * req.animScale, req.rotation * 90);
    }

    @Override
    public boolean blends(Tile tile, int rotation, int otherx, int othery, int otherrot, Block otherblock){
        return otherblock.outputsItems() && lookingAt(tile, rotation, otherx, othery, otherrot, otherblock);
    }

    @Override
    public TextureRegion[] generateIcons(){
        return new TextureRegion[]{Core.atlas.find(name + "-0-0")};
    }

    @Override
    public void drawLayer(Tile tile){
        ConveyorEntity entity = tile.entity();
        entity.line.draw();
    }

    @Override
    public void onProximityAdded(Tile tile){
        Tile facing = tile.facing();
        //find block of same type that this is facing, add if necessary and stop
        if(facing != null && facing.block() == this && facing.rotation() == tile.rotation()){
            facing.<ConveyorEntity>entity().line.addLast(tile);
            return;
        }

        facing = tile.behind();
        //find block of same type that this is facing, add if necessary and stop
        if(facing != null && facing.block() == this && facing.rotation() == tile.rotation()){
            facing.<ConveyorEntity>entity().line.addFirst(tile);
        }
    }

    @Override
    public void onProximityRemoved(Tile tile){
        tile.<ConveyorEntity>entity().line.remove(tile);
    }

    @Override
    public void update(Tile tile){
        //note that only 1 conveyor in the line has their entity updating in the list, so this only gets called once
        tile.<ConveyorEntity>entity().line.update();
    }

    @Override
    public void unitOn(Tile tile, Unit unit){
        ConveyorEntity entity = tile.entity();

        entity.noSleep();

        float speed = this.speed * tilesize / 2.3f;
        float centerSpeed = 0.1f;
        float centerDstScl = 3f;
        float tx = Geometry.d4[tile.rotation()].x, ty = Geometry.d4[tile.rotation()].y;

        float centerx = 0f, centery = 0f;

        if(Math.abs(tx) > Math.abs(ty)){
            centery = Mathf.clamp((tile.worldy() - unit.y) / centerDstScl, -centerSpeed, centerSpeed);
            if(Math.abs(tile.worldy() - unit.y) < 1f) centery = 0f;
        }else{
            centerx = Mathf.clamp((tile.worldx() - unit.x) / centerDstScl, -centerSpeed, centerSpeed);
            if(Math.abs(tile.worldx() - unit.x) < 1f) centerx = 0f;
        }

        unit.velocity().add((tx * speed + centerx) * entity.delta(), (ty * speed + centery) * entity.delta());
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        ConveyorEntity entity = tile.entity();
        return entity.line.acceptItem(tile);
    }

    @Override
    public void handleItem(Item item, Tile tile, Tile source){
        ConveyorEntity entity = tile.entity();
        entity.line.handleItem(tile, source, item, 0);
    }

    @Override
    public TileEntity newEntity(){
        return new ConveyorEntity();
    }

    public class ConveyorEntity extends TileEntity{
        int blendbits;
        int blendsclx, blendscly;
        float clogHeat = 0f;

        ConveyorLine line;

        @Override
        public TileEntity init(Tile tile, boolean shouldAdd){
            super.init(tile, shouldAdd);
            //this is a bit of a hack; sets the entity before the line can init
            tile.entity = this;
            line = new ConveyorLine(tile);
            return this;
        }

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void read(DataInput stream, byte revision) throws IOException{
            super.read(stream, revision);
            if(revision == 0){
                int amount = stream.readInt();
                for(int i = 0; i < amount; i++){
                    //information is currently discarded for old versions
                    stream.readInt();
                }
            }else{

            }
        }

        @Override
        public void write(DataOutput stream) throws IOException{
            super.write(stream);
        }
    }

}