package io.anuke.mindustry.world.blocks.distribution;

import io.anuke.annotations.Annotations.Struct;
import io.anuke.arc.collection.IntArray;
import io.anuke.arc.collection.ObjectSet;
import io.anuke.mindustry.world.Tile;

/**Stores one continuous line of conveyors with one input and one output.*/
public class ConveyorLine{
    /**All tiles in this line. Unordered*/
    private ObjectSet<Tile> tiles = new ObjectSet<>();
    /**Items, sorted from back of conveyor to front of conveyor.*/
    private IntArray items = new IntArray();

    public void add(Tile tile){

    }

    //size: 1 int
    @Struct
    class ItemPos{
        /**item ID*/
        byte item;
        /**item x tilt; -127 would be left side of conveyor while 127 would be right*/
        byte tilt;
        /**item position in conveyor line*/
        short position;
    }
}
