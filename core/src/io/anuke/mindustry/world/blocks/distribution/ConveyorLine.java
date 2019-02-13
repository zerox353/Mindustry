package io.anuke.mindustry.world.blocks.distribution;

import io.anuke.annotations.Annotations.Struct;
import io.anuke.arc.collection.Array;
import io.anuke.arc.collection.IntArray;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.distribution.Conveyor.ConveyorEntity;

/**Stores one continuous line of conveyors with one input and one output.*/
public class ConveyorLine{
    private static int lastID;
    /**all tiles in this line, unordered (fix?)*/
    private final Array<Tile> tiles = new Array<>();
    /**items, sorted from back of conveyor to front of conveyor.*/
    private final IntArray items = new IntArray();
    /**seed that updates this line*/
    private final Tile seed;
    /**movement speed of items*/
    private final float speed;
    /**ID for debugging purposes*/
    public final int id;

    public ConveyorLine(Tile seed){
        //add seed entity so it updates
        this.seed = seed;
        this.seed.entity.add();
        this.id = lastID++;
        this.speed = ((Conveyor)seed.block()).speed;
    }

    /**adds a tile to this line.
     * there are 2 possibilities:
     * 1) this tile is facing a tile in the line
     * 2) a tile in this line is facing this tile
     */
    public void add(Tile tile){

        //backflow, find what's facing this tile
        //it doesn't really matter what *this* tile is facing, because it can't be a different line; facing takes priority
        for(Tile near : tile.entity.proximity()){
            if(near.block() != seed.block() || near.facing() != tile) continue;

            ConveyorEntity entity = near.entity();

            //found a line, merge and stop.
            if(entity.line != this){
                entity.line.merge(this);
                return;
            }
        }
    }

    public void remove(Tile tile){
        if(tile == seed){
            //remove tile entity if it's in the seed so it stops updating
            tile.entity.remove();
        }

        //no tiles left, stop, this line is dead
        if(tiles.size == 1 && tiles.first() == tile){
            return;
        }

        //find index of tile, everything below it will be split off
        int index = tiles.indexOf(tile);

        //make sure another conveyor line is needed
        if(index == tiles.size - 1){
            //last element is removed, end there
            //TODO remove items
            tiles.pop();
        }else if(index != 0){
            ConveyorLine line = new ConveyorLine(tiles.get(0));
            //reparent all tiles to new line below this one
            for(int i = 0; i < index; i++){
                //TODO add items
                tiles.get(index).<ConveyorEntity>entity().line = line;
                line.tiles.add(tiles.get(index));
            }
            //TODO remove items in range
            //remove all tiles in the range.
            tiles.removeRange(0, index);
        }else{
            //TODO remove items
            //if not, just remove that tile (at back) and be done
            tiles.remove(0);
        }
    }

    /**merges a line with another line which is directly in front.*/
    public void merge(ConveyorLine other){
        //remove other's entity to stop double updates
        other.seed.entity.remove();
        tiles.addAll(other.tiles);
        //reparent lines
        for(Tile tile : other.tiles){
            tile.<ConveyorEntity>entity().line = this;
        }
        //TODO merge items
    }

    public void update(){
        //TODO move items
    }

    //size: 1 int
    @Struct
    class ItemPosStruct{
        /**item ID*/
        byte item;
        /**item x tilt; -127 would be left side of conveyor while 127 would be right*/
        byte tilt;
        /**item position in conveyor line*/
        short position;
    }
}
