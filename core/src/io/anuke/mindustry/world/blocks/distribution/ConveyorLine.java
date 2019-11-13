package io.anuke.mindustry.world.blocks.distribution;

import io.anuke.annotations.Annotations.*;
import io.anuke.arc.*;
import io.anuke.arc.collection.*;
import io.anuke.arc.func.*;
import io.anuke.arc.graphics.g2d.*;
import io.anuke.arc.math.*;
import io.anuke.arc.math.geom.*;
import io.anuke.arc.util.*;
import io.anuke.mindustry.entities.type.*;
import io.anuke.mindustry.gen.ItemPos;
import io.anuke.mindustry.type.*;
import io.anuke.mindustry.ui.*;
import io.anuke.mindustry.world.*;
import io.anuke.mindustry.world.blocks.distribution.Conveyor.*;

import static io.anuke.mindustry.Vars.*;

//things to fix:
//1) animations are broken since items don't save their position in the conveyor when they were inputted
//2) no saving
//3) no loading
//4) can't withdraw items
//5) items aren't per conveyor (is this even possible?)
//6) pretends to use overdriving when it actually doesn't
//7) "overflow" to other conveyors doesn't work, as the positions of items can't be negative
//8) rendering could be more efficient, and isn't sorted properly

/**Stores one continuous line of conveyors with one input and one output.*/
public class ConveyorLine{
    /**distance units per conveyor block*/
    private static final int unitMult = 3000;
    /**spacing between items*/
    private static final int itemSpacing = unitMult / 3;
    /**edges of the conveyor, rotated*/
    private static final Point2[] edges = {new Point2(1, 1), new Point2(-1, 1), new Point2(-1, -1), new Point2(1, -1)};
    /**last frame drawn*/
    private long lastFrameID = -1;
    /**start and end tiles of this line*/
    private Tile start, end;
    /**items, sorted from back of conveyor to front of conveyor.*/
    private final IntArray items = new IntArray();
    /**seed that updates this line*/
    private Tile seed;
    /**movement speed of items in units*/
    private final int speed;

    private int index = 0;
    private int maxOffset = 0;

    public ConveyorLine(Tile seed){
        //add seed entity so it updates
        this.seed = seed;
        this.seed.entity.add();
        this.speed = (int)(((Conveyor)seed.block()).speed * unitMult);
        this.seed.<ConveyorEntity>entity().line = this;
        this.start = this.end = seed;
    }

    public boolean acceptItem(Tile tile){
        int rawDst = (1 + Math.max(Math.abs(tile.x - end.x), Math.abs(tile.y - end.y))) * unitMult;
        return maxOffset <= rawDst - itemSpacing;
    }

    public void handleItem(Tile tile, Tile from, Item item, int offset){
        //distance in conveyor units from end
        int rawDst = (1 + Math.max(Math.abs(tile.x - end.x), Math.abs(tile.y - end.y))) * unitMult + offset;
        int total = 0;
        int lastTotal = 0;
        int src = //src direction based on input
            tile.behind() == from ? 0 :
            tile.relativeNear(-1) == from ? 1 :
            tile.relativeNear(1) == from ? 2 : 0;

        maxOffset = Math.max(rawDst, maxOffset);
        for(int i = items.size - 1; i >= 0; i--){
            int curr = items.get(i);
            total += ItemPos.space(curr);
            if(rawDst < total){ //found place to insert

                int inserti = i + 1;
                int result = ItemPos.get((byte)item.id, rawDst - lastTotal, src);
                if(inserti == items.size){
                    items.add(result);
                }else{
                    items.insert(inserti, result);
                }

                int finalDst = ItemPos.space(curr) - (rawDst - lastTotal);
                //update previous item's distance
                items.set(i, ItemPos.space(curr, finalDst));
                return;
            }

            lastTotal = total;
        }

        int toInsert = ItemPos.get((byte)item.id, rawDst - total, src);
        if(items.size == 0){
            items.add(toInsert);
        }else{
            items.insert(0, toInsert);
        }
    }

    public void draw(){
        if(lastFrameID == Core.graphics.getFrameId()){
            return;
        }

        lastFrameID = Core.graphics.getFrameId();
        int offset = 0;
        Point2 pt = Geometry.d4[end.rotation()];
        int dx = -pt.x, dy = -pt.y;
        int length = length();
        float startX = end.worldx() + pt.x*tilesize/2f, startY = end.worldy() + pt.y*tilesize/2f;
        float itemSize = 5f;

        for(int i = items.size - 1; i >= 0; i --){
            int item = items.get(i);
            int src = ItemPos.src(item);
            offset += ItemPos.space(item);

            TextureRegion region = content.item(ItemPos.item(item)).icon(Cicon.medium);
            float posOffset = offset / (float)unitMult * tilesize;
            float x = startX + dx*posOffset, y = startY + dy*posOffset;

            if(src != 0){
                Tile on = get(Mathf.clamp(length - 1 - offset / unitMult, 0, length - 1));

                int rot = src == 1 && on.rotation() % 2 == 1 ? (on.rotation() + 2) % 4 : on.rotation();
                Point2 p = edges[rot];
                int ex = p.x, ey = p.y * (src == 1 ? -1 : 0);
                float degrees = rot % 2 == 0 ? (1f-(((offset-1)/(float)unitMult)%1f))*90f : ((((offset-1)/(float)unitMult)%1f))*90f;
                x = on.worldx() + ex*tilesize/2f - ex*Angles.trnsx(degrees, tilesize/2f);
                y = on.worldy() + ey*tilesize/2f - ey*Angles.trnsy(degrees, tilesize/2f);
            }

            Draw.rect(region, x, y, itemSize, itemSize);
        }
    }

    /** adds a tile to the tail of this line.*/
    public void addLast(Tile tile){
        tile.<ConveyorEntity>entity().line.seed.entity.remove();
        tile.<ConveyorEntity>entity().line = this;
        start = tile;

        Tile next = tile.behind();

        if(next != null && next.rotation() == tile.rotation() && next.block() == tile.block()){
            next.<ConveyorEntity>entity().line.merge(this);
        }
    }

    /** adds a tile to the head of this line.*/
    public void addFirst(Tile tile){
        tile.<ConveyorEntity>entity().line.seed.entity.remove();
        tile.<ConveyorEntity>entity().line = this;
        end = tile;

        Tile next = tile.facing();

        //offset item at head by spacing
        if(items.size > 0){
            int src = items.peek();
            items.set(items.size - 1, ItemPos.space(src, ItemPos.space(src) + unitMult));
            maxOffset += unitMult;
        }

        if(next != null && next.rotation() == tile.rotation() && next.block() == tile.block()){
            merge(next.<ConveyorEntity>entity().line);
        }else if(next != null && next.block() == tile.block()){
            ConveyorLine line = next.<ConveyorEntity>entity().line;
            Array<TileEntity> entities = tileGroup.all();
            int idx1 = entities.indexOf(seed.entity);
            int idx2 = entities.indexOf(line.seed.entity);
            if(idx1 != -1 && idx2 != -1 && idx2 > idx1){
                entities.swap(idx1, idx2);
            }
        }
    }

    public void remove(Tile tile){
        if(start == end){
            if(tile != start){
                throw new IllegalArgumentException("Attempt to remove tile not in the conveyor: " + start + " != " + tile);
            }
            items.clear();
        }else if(tile == end){ //tile is at end, move it back
            int sum = 0;
            //items [endidx...size] are removed from the end, as they are on this tile
            int endidx = -1;
            for(int i = items.size - 1; i >= 0; i--){
                if(sum + ItemPos.space(items.get(i)) > unitMult){
                    endidx = i;
                    break;
                }else{
                    sum += ItemPos.space(items.get(i));
                }
            }

            if(endidx != -1){
                int prev = items.get(endidx);
                //offset item at the end by the remainder
                items.set(endidx, ItemPos.space(prev, ItemPos.space(prev) - (unitMult - sum)));

                index = 0;
                //only remove if there's actually something to remove; if the first item is out of bounds, ignore it
                if(endidx + 1 < items.size){
                    items.removeRange(endidx + 1, items.size - 1);
                }
            }else if(!items.isEmpty()){ //remove everything, as it is all in the end
                items.clear();
                maxOffset = 0;
                index = 0;
            }

            maxOffset -= unitMult;
            if(maxOffset < 0) maxOffset = 0;
            if(seed == end){
                seed.entity.remove();
                seed = end.behind();
                seed.entity.add();
            }
            end = end.behind();
        }else if(tile == start){ //tile is at start, move it forward
            int sum = 0;
            //items [0..startidx] are removed from the end, as they are on this tile
            int startidx = -1;
            int beginOffset = (length() - 1)*unitMult;
            for(int i = items.size - 1; i >= 0; i--){
                if(sum + ItemPos.space(items.get(i)) > beginOffset){
                    startidx = i;
                    break;
                }else{
                    sum += ItemPos.space(items.get(i));
                }
            }

            if(startidx != -1){
                items.removeRange(0, Math.min(startidx + 1, items.size-1));
                maxOffset = sum;
            }

            if(seed == start){
                seed.entity.remove();
                seed = start.facing();
                seed.entity.add();
            }
            start = start.facing();
        }else{ //conveyor removed somewhere that's on on the ends... augh
            if(seed != start){
                seed.entity.remove();
                seed = start;
                seed.entity.add();
            }

            Tile oldEnd = end;
            Tile newStart = tile.facing();

            ConveyorLine line = new ConveyorLine(newStart);
            line.end = oldEnd;
            line.start = newStart;
            //reparent tiles greater in index
            each(other -> {
                if(index(other) > index(tile)){
                    other.<ConveyorEntity>entity().line = line;
                }
            });

            int sum = 0;
            int removeLen = (line.length() + 1) * unitMult, reparentLen = line.length() * unitMult;
            int removeTo = -1, reparentTo = -1;

            for(int i = items.size - 1; i >= 0; i--){
                sum += ItemPos.space(items.items[i]);

                if(sum < reparentLen){
                    removeTo = i;
                    reparentTo = i;
                }else if(sum < removeLen){
                    removeTo = i;
                }else{
                    break;
                }
            }

            if(reparentTo != -1){
                //move out all items to the new line, set up max offset based on that
                line.items.addAll(items, reparentTo, items.size - reparentTo);
                int max = 0;
                for(int i = 0; i < line.items.size; i++){
                    max += ItemPos.space(line.items.get(i));
                }
                line.maxOffset = max;
            }

            //remove items from this line
            if(removeTo != -1){
                int tweaki = removeTo - 1;
                if(tweaki >= 0){
                    int it = items.get(tweaki);
                    int removeLength = (line.length() + 1) * unitMult;
                    int nextOffset = 0;
                    for(int i = removeTo; i < items.size - 1; i++){
                        nextOffset += ItemPos.space(items.get(i));
                    }
                    //properly offset the head item on this belt
                    items.set(tweaki, ItemPos.space(it, ItemPos.space(it) - (removeLength - nextOffset)));
                }
                items.removeRange(removeTo, items.size - 1);
            }else if(!items.isEmpty()){ //nothing to remove from this line, but there may be items on this line to be tweaked
                int head = items.peek();
                items.set(items.size - 1, ItemPos.space(head, ItemPos.space(head) - removeLen));
                //max offset moves back too
                maxOffset -= removeLen;
            }

            end = tile.behind();
        }
    }

    /** merges a line with another line, which must be directly in front.*/
    public void merge(ConveyorLine other){
        //remove other's entity to stop double updates
        other.seed.entity.remove();
        end = other.end;
        //reparent lines
        other.each(tile -> tile.<ConveyorEntity>entity().line = this);
        int toChange = items.size - 1;

        int total = 0;
        for(int i = 0; i < other.items.size; i++){
            total += ItemPos.space(other.items.items[i]);
        }

        if(!items.isEmpty()){
            maxOffset += other.length()*unitMult;
        }

        items.addAll(other.items);

        //merge last item properly
        if(toChange >= 0){
            int src = items.get(toChange);
            items.set(toChange, ItemPos.space(src, ItemPos.space(src) + (unitMult*other.length() - total)));
        }
    }

    public void update(){
        //nothing to update
        if(items.isEmpty()){
            maxOffset = 0;
            return;
        }

        //check item at front every frame to make sure it can move
        int hitem = items.peek();
        int hoffset = ItemPos.space(hitem);
        if(hoffset <= 0){
            Tile next = end.facing();
            if(next != null) next = next.link();
            Item citem = content.item(hitem);
            //if the item can be handled, remove it
            if(tryMoveItem(hitem, next)){
                items.pop();
                index = 0; //reset to index 0, as items there can move now
            }else if(index == 0){
                //if passing doesn't work, reset the head index to 0 to prevent massive negative overflows
                items.set(items.size - 1, ItemPos.space(hitem, 0));
                //otherwise, go to the next item to move it forward since this one is stuck
                index++;
            }
        }else{
            index = 0;
        }

        //nothing to update, re-check as an item may have been popped
        if(items.isEmpty()) return;

        //update other head item
        index = Mathf.clamp(index, 0, items.size - 1);
        int arridx = items.size - 1 - index;
        int head = items.get(arridx);
        int offset = ItemPos.space(head);
        byte item = ItemPos.item(head);

        //reached the end of the line for the head item
       if(offset <= itemSpacing && index != 0){ //hit edge of item up ahead, move up an index
            index ++;
        }else{
            //else, move item forward
            int moved = Math.max(1, (int)(Time.delta() * speed));
            int newOffset = Math.max(offset - moved, index == 0 ? 0 : itemSpacing);
            items.set(arridx, ItemPos.get(item, newOffset, (byte)0));

            //move max offset back every frame
            maxOffset -= (offset - newOffset);
        }
    }

    private boolean tryMoveItem(int itemp, Tile next){
        Item citem = content.item(itemp);
        if(next == null){
            return false;
        }

        if(next.block() instanceof Conveyor){
            if(next.block().acceptItem(citem, next, end)){
                ConveyorLine line = next.<ConveyorEntity>entity().line;
                line.handleItem(next, end, citem, ItemPos.space(itemp));
                return true;
            }
            return false;
        }else{
            return next.block().handleAcceptItem(citem, next, end);
        }
    }

    public int length(){
        return Math.max(Math.abs(start.x - end.x), Math.abs(start.y - end.y))+1;
    }

    public int index(Tile tile){
        if(end.x == start.x){ //vertical
            return Math.abs(tile.y - start.y);
        }else{ //horizontal
            return Math.abs(tile.x - start.x);
        }
    }

    public Tile get(int idx){
        if(end.x == start.x){ //vertical
            return world.tile(start.x, start.y + Mathf.sign(end.y - start.y)*idx);
        }else{ //horizontal
            return world.tile(start.x + Mathf.sign(end.x - start.x)*idx, start.y);
        }
    }

    public void each(Cons<Tile> cons){
        if(end.x == start.x){ //vertical
            int len = Math.abs(start.y - end.y);
            int sign = Mathf.sign(end.y - start.y);
            for(int i = 0; i <= len; i++){
                cons.get(world.tile(start.x, start.y + sign*i));
            }
        }else{ //horizontal
            int len = Math.abs(start.x - end.x);
            int sign = Mathf.sign(end.x - start.x);
            for(int i = 0; i <= len; i++){
                cons.get(world.tile(start.x + sign*i, start.y));
            }
        }
    }

    //size: 1 int
    @Struct
    class ItemPosStruct{
        /**item ID*/
        byte item;
        /**item position in conveyor line relative to the last item*/
        @StructField(22)
        int space;
        /**source direction: 0 (straight, nothing interesting), 1 (came from left), or 2 (from right).
         * 3 is also technically possible but let's not think about that*/
        @StructField(2)
        int src;
    }
}
