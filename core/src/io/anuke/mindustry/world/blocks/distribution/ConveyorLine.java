package io.anuke.mindustry.world.blocks.distribution;

import io.anuke.annotations.Annotations.Struct;
import io.anuke.annotations.Annotations.StructField;
import io.anuke.arc.Core;
import io.anuke.arc.collection.IntQueue;
import io.anuke.arc.function.Consumer;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.math.geom.Geometry;
import io.anuke.mindustry.gen.ItemPos;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.distribution.Conveyor.ConveyorEntity;

import static io.anuke.mindustry.Vars.world;

/**Stores one continuous line of conveyors with one input and one output.*/
public class ConveyorLine{
    /**distance units per conveyor block*/
    private static final int unitMult = 3000;
    /**spacing between items*/
    private static final int itemSpacing = unitMult / 3;
    /**last frame drawn*/
    private long lastFrameID = -1;
    /**start and end tiles of this line*/
    private Tile start, end;
    /**items, sorted from back of conveyor to front of conveyor.*/
    private final IntQueue items = new IntQueue();
    /**seed that updates this line*/
    private Tile seed;
    /**movement speed of items in units*/
    private final int speed;

    private int index = 0;

    public ConveyorLine(Tile seed){
        //add seed entity so it updates
        this.seed = seed;
        this.seed.entity.add();
        this.speed = (int)(((Conveyor)seed.block()).speed * unitMult);
        this.seed.<ConveyorEntity>entity().line = this;
        this.start = this.end = seed;
    }

    public void handleItem(Tile tile, Item item){
        items.addFirst(ItemPos.get((byte)item.id, (short)0));
    }

    public void draw(){
        if(lastFrameID == Core.graphics.getFrameId()){
            return;
        }

        lastFrameID = Core.graphics.getFrameId();
        int offset = 0;
        int dx = Geometry.d4[end.rotation()].x, dy = Geometry.d4[end.rotation()].y;

        for(int i = 0; i < items.size; i++){
            int item = items.get(i);
        }
    }

    /** adds a tile to the tail of this line.*/
    public void addLast(Tile tile){
        start = tile;

        Tile next = tile.getNearby((tile.rotation() + 2) % 4);

        if(next != null && next.rotation() == tile.rotation() && next.block() == tile.block()){
            next.<ConveyorEntity>entity().line.merge(this);
        }
    }

    /** adds a tile to the head of this line.*/
    public void addFirst(Tile tile){
        end = tile;
        Tile next = tile.getNearby(tile.rotation());

        if(next != null && next.rotation() == tile.rotation() && next.block() == tile.block()){
            merge(next.<ConveyorEntity>entity().line);
        }
    }

    //TODO remove items
    public void remove(Tile tile){
        if(tile == end){ //tile is at end, move it back
            end = end.behind();
        }else if(tile == start){ //tile is at start, move it forward
            start = start.facing();
        }else if(start != end){ //only run this if there's still tiles left here
            if(seed != start){
                seed.entity.remove();
                seed = start;
                seed.entity.add();
            }

            Tile oldEnd = end;
            Tile newStart = tile.facing();
            end = tile.behind();

            ConveyorLine line = new ConveyorLine(newStart);
            line.end = oldEnd;
            line.start = newStart;
            //reparent tiles greater in index
            each(other -> {
                if(index(other) > index(tile)){
                    other.<ConveyorEntity>entity().line = line;
                }
            });
        }
    }

    /** merges a line with another line, which must be directly in front.*/
    public void merge(ConveyorLine other){
        //remove other's entity to stop double updates
        other.seed.entity.remove();
        end = other.end;
        //reparent lines
        other.each(tile -> tile.<ConveyorEntity>entity().line = this);

        //add all items
        for(int i = 0; i < other.items.size; i++){
            items.addLast(other.items.get(i));
        }
    }

    public void update(){
        int head = items.get(index);
        int offset = ItemPos.space(head);
        byte item = ItemPos.item(head);

    }

    public int index(Tile tile){
        if(end.x == start.x){ //vertical
            return Math.max(tile.y - start.y, tile.y - end.y);
        }else{ //horizontal
            return Math.max(tile.x - start.x, tile.x - end.x);
        }
    }

    public void each(Consumer<Tile> cons){
        if(end.x == start.x){ //vertical
            int len = Math.abs(start.y - end.y);
            int sign = Mathf.sign(end.y - start.y);
            for(int i = 0; i <= len; i++){
                cons.accept(world.tile(start.x, start.y + sign*i));
            }
        }else{ //horizontal
            int len = Math.abs(start.x - end.x);
            int sign = Mathf.sign(end.x - start.x);
            for(int i = 0; i <= len; i++){
                cons.accept(world.tile(start.x + sign*i, start.y));
            }
        }
    }

    //size: 1 int
    @Struct
    class ItemPosStruct{
        /**item ID*/
        byte item;
        /**item position in conveyor line relative to the last item*/
        @StructField(24)
        short space;
    }
}
