package za.co.entelect.challenge;

import za.co.entelect.challenge.command.*;
import za.co.entelect.challenge.entities.*;
import za.co.entelect.challenge.enums.PowerUps;
import za.co.entelect.challenge.enums.Terrain;

import java.util.*;

import static java.lang.Math.max;

public class Bot {

    private static final int maxSpeed = 9;
    private List<Integer> directionList = new ArrayList<>();

    private Random random;
    private GameState gameState;
    private Car opponent;
    private Car myCar;
    private final static Command FIX = new FixCommand();

    public Bot(Random random, GameState gameState) {
        this.random = random;
        this.gameState = gameState;
        this.myCar = gameState.player;
        this.opponent = gameState.opponent;

        directionList.add(-1);
        directionList.add(1);
    }

    public Command run() {
        List<Object> middle = getBlocksInFront(myCar.position.lane, myCar.position.block, myCar.speed);
        List<Object> middleBoost = getBlocksInFront(myCar.position.lane, myCar.position.block, 15);
        List<Object> middleAcc = getBlocksInFront(myCar.position.lane, myCar.position.block, NextSpeed());
        List<Object> middleDec = getBlocksInFront(myCar.position.lane, myCar.position.block, PrevSpeed());
        List<Object> right  = getBlocksInFront(myCar.position.lane+1, myCar.position.block-1, myCar.speed);
        List<Object> left  = getBlocksInFront(myCar.position.lane-1, myCar.position.block-1, myCar.speed);
        PowerUps P = TryPower();

        // Greedy
        if (myCar.boostCounter > 0){ // boosting logic
            if (!LaneClean(middleBoost)) {
                if(LaneClean(left)&&LaneClean(right))return PowerGreed(left,right);
                else if (LaneClean(right)) {
                    return new ChangeLaneCommand(1);
                } else if (LaneClean(left)) {
                    return new ChangeLaneCommand(0);
                } else if (hasPowerUp(PowerUps.LIZARD)) {
                    return new LizardCommand();
                }
            }
        } else if(IsCrashing() && hasPowerUp(PowerUps.LIZARD)){ //lizard logic if tailing opponent
            return new LizardCommand();
        } else if(myCar.damage >= 3){ // fix logic 1
            return new FixCommand();
        } else if(myCar.damage>=1 && hasPowerUp(PowerUps.BOOST)) { // fix logic 2 (to boost)
            return new FixCommand();
        } else if(myCar.damage==0 && LaneClean(middleBoost) && hasPowerUp(PowerUps.BOOST) && myCar.boostCounter==0){ // boost logic
            return new BoostCommand();
        } else if(myCar.speed==0){ // gas if speed 0
            return new AccelerateCommand();
        } else if(LaneClean(middle) && !LaneClean(middleAcc)){ //if car will crash when accel, but won't if not, try power ups
            if(P!=null){
                return UsePower(P);
            }
        } else if(!LaneClean(middleAcc)){ // obstacle logic (wall handled above)
            //1. won't crash when accel, continue
            //2. if exists clean lane (left/right), steer
            //3. use lizard if no clean lane, if none just accel
            //4. avoid wall, tank if necessary
            if(LaneClean(left) && LaneClean(right)){ // 2
                //checks for power up
                return PowerGreed(left,right);
            } else if(LaneClean(middle)){ // if "do nothing" works
                return new DoNothingCommand();
            } else if(LaneClean(left)){ // steer left
                return new ChangeLaneCommand(0);
            } else if(LaneClean(right)){ // steer right
                return new ChangeLaneCommand(1);
            } else if(myCar.speed>6&&LaneClean(middleDec)){ // try decelerate
                return new DecelerateCommand();
            } else if(hasPowerUp(PowerUps.LIZARD)){ // last hope, use lizard
                return new LizardCommand();
            } else if(Tankable(middleAcc)&&myCar.damage<2){ // try preserve speed
                return new AccelerateCommand();
            } else if(!Tankable(middle)&&Tankable(left)&&Tankable(right)){ // try taking advantage
                return PowerGreed(left,right);
            } else if(!Tankable(middle)&&Tankable(right)){ // try taking less damage
                return new ChangeLaneCommand(1);
            } else if(!Tankable(middle)&&Tankable(left)){ // try taking less damage
                return new ChangeLaneCommand(0);
            }
        } else if (P!=null) { // has power up
            return UsePower(P);
        }
        //just floor the pedal
        return new AccelerateCommand();
    }

    /*
     * Returns map of blocks and the objects in the for the current lanes, returns the amount of blocks that can be
     * traversed at max speed.
     * Returns wall if lane is not valid
     * Treats CyberTrucks as walls
     */
    private List<Object> getBlocksInFront(int lane, int block,int speed) {
        List<Lane[]> map = gameState.lanes;
        List<Object> blocks = new ArrayList<>();
        int startBlock = map.get(0)[0].position.block;


        //Returns null if speed is 0
        if(speed<=0)return blocks;
        //Returns if lane is not valid
        if(lane<1||lane>4){
            blocks.add(Terrain.WALL);
            return blocks;
        }

        Lane[] laneList = map.get(lane - 1);
        for (int i = max(block - startBlock, 0); i <= block - startBlock + speed; i++) {
            if (laneList[i] == null || laneList[i].terrain == Terrain.FINISH) {
                break;
            }
            if(laneList[i].isOccupiedByCyberTruck)blocks.add(Terrain.WALL);
            blocks.add(laneList[i].terrain);

        }
        return blocks;
    }
    private boolean hasPowerUp(PowerUps powerUpToCheck) {
        for (PowerUps powerUp: myCar.powerups) {
            if (powerUp.equals(powerUpToCheck)) {
                return true;
            }
        }
        return false;
    }

    // Checks if lane is clear
    private boolean LaneClean(List<Object> LaneBlocks){
        if(LaneBlocks.contains(Terrain.WALL))return false;
        if(LaneBlocks.contains(Terrain.MUD))return false;
        if(LaneBlocks.contains(Terrain.OIL_SPILL))return false;
        return true;
    }

    // Checks if lane is tankable
    private boolean Tankable(List<Object> LaneBlocks){
        if(LaneBlocks.contains(Terrain.WALL))return false;
        return MudCount(LaneBlocks) < 2;
    }

    // Counts mud and oil in a lane
    private int MudCount(List<Object> LaneBlocks){
        int ret=0;
        for(Object L:LaneBlocks)if(L.equals(Terrain.MUD)||L.equals(Terrain.OIL_SPILL))ret++;
        return ret;
    }

    // Checks if crashing to opponent
    private boolean IsCrashing(){
        int dist = max(0,myCar.position.block-opponent.position.block);
        boolean same = myCar.position.lane==opponent.position.lane;
        return same&&(dist<=myCar.speed-opponent.speed);
    }

    // Greedy power up in left or right lane
    private Command PowerGreed(List<Object> left, List<Object> right){
        if(right.contains(Terrain.BOOST))return new ChangeLaneCommand(1);
        if(left.contains(Terrain.BOOST))return new ChangeLaneCommand(0);
        if(right.contains(Terrain.LIZARD))return new ChangeLaneCommand(1);
        if(left.contains(Terrain.LIZARD))return new ChangeLaneCommand(0);
        if(right.contains(Terrain.EMP))return new ChangeLaneCommand(1);
        if(left.contains(Terrain.EMP))return new ChangeLaneCommand(0);
        if(right.contains(Terrain.TWEET))return new ChangeLaneCommand(1);
        if(left.contains(Terrain.TWEET))return new ChangeLaneCommand(0);
        if(right.contains(Terrain.OIL_POWER))return new ChangeLaneCommand(1);
        if(left.contains(Terrain.OIL_POWER))return new ChangeLaneCommand(0);
        if(myCar.position.lane==1||myCar.position.lane==2)return new ChangeLaneCommand(1);
        return new ChangeLaneCommand(0);
    }

    // Emp, Tweet, and Oil logic
    private PowerUps TryPower(){
        if (myCar.position.block<opponent.position.block&&hasPowerUp(PowerUps.EMP)&&Math.abs(myCar.position.lane-opponent.position.lane)<=1){
            return PowerUps.EMP;
        }
        //Tweet Logic
        if ((hasPowerUp(PowerUps.TWEET) && myCar.speed>=6)&&(opponent.position.lane != myCar.position.lane || opponent.position.block < myCar.position.block || opponent.position.block + opponent.speed + 3 > myCar.position.block + myCar.speed)){
            return PowerUps.TWEET;
        }

        //Oil Logic
        //1. Max speed and has powerup
        if((myCar.speed==9||myCar.speed==15)&&hasPowerUp(PowerUps.OIL)){
            return PowerUps.OIL;
        }
        if(myCar.speed>=6&&hasPowerUp(PowerUps.OIL) && opponent.position.lane == myCar.position.lane && opponent.position.block + opponent.speed >= myCar.position.block - 1){
            return PowerUps.OIL;
        }
        return null;
    }

    // Use power up P
    private Command UsePower(PowerUps P){
        if(P.equals(PowerUps.EMP))
            return new EmpCommand();
        if(P.equals(PowerUps.TWEET) ) {
            return new TweetCommand(opponent.position.lane, opponent.position.block + opponent.speed + 3);
            /*int tar = opponent.position.block + 3;
            //just tweet to his side if gonna crash
            if (tar - myCar.position.block <= myCar.speed && tar >= myCar.position.block) {
                if (opponent.position.lane <= 2) {
                    return new TweetCommand(opponent.position.lane + 1, opponent.position.block + opponent.speed);
                }
                return new TweetCommand(opponent.position.lane - 1, opponent.position.block + opponent.speed);
            }*/
        }
        return new OilCommand();
    }

    // gets speed if accel
    private int NextSpeed(){
        int s=myCar.speed;
        if(s==5)return 6;
        if(s==15)return 15;
        int[] speeds= new int[]{0, 3, 6, 8, 9, 15};
        int ret=0;
        for(int i=0;i<4;i++)if(s==speeds[i])ret=speeds[i+1];
        if(s==9)ret=9;
        ret=java.lang.Math.min(speeds[5- myCar.damage],ret);
        return ret;
    }

    // gets speed if decel
    private int PrevSpeed(){
        int s=myCar.speed;
        if(s==5)return 3;
        if(s==0)return 0;
        int[] speeds= new int[]{0, 3, 6, 8, 9, 15};
        for(int i=1;i<6;i++)if(s==speeds[i])return speeds[i-1];
        return 0;
    }

}
