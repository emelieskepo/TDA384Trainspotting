import TSim.*;
import java.util.concurrent.*;

public class Lab1 {

    private final Train t1;
    private final Train t2;

    public Lab1(int speed1, int speed2) {

        //creating our semaphores
        //0 permits: initially blocked, used to coordinate trains so none goes first
        //1 permit: free from start, one train can go immediately
        //fair = true --> threads acquire in the order they try (FirstInFirstOut)
        Semaphore topTop        = new Semaphore(0, true);
        Semaphore topBottom     = new Semaphore(1, true);
        Semaphore middleTop     = new Semaphore(1, true);
        Semaphore middleBottom  = new Semaphore(1, true);
        Semaphore bottomTop     = new Semaphore(0, true);
        Semaphore bottomBottom  = new Semaphore(1, true);
        Semaphore leftSide      = new Semaphore(1, true);
        Semaphore rightSide     = new Semaphore(1, true);
        Semaphore intersection  = new Semaphore(1, true);

        t1 = new Train(1, speed1, Direction.DOWN,
                topTop, topBottom, middleTop, middleBottom,
                bottomTop, bottomBottom, leftSide, rightSide, intersection);

        t2 = new Train(2, speed2, Direction.UP,
                topTop, topBottom, middleTop, middleBottom,
                bottomTop, bottomBottom, leftSide, rightSide, intersection);

        t1.start();
        t2.start();
    }

    public enum Direction {
        UP, DOWN;
        public static Direction flip(Direction d) {
            //Flips the direction when arriving at a station
            if (d == UP) {
                return DOWN;
            } else {
                return UP;
            }
        }
    }

    //Each train is its own thread
    private class Train extends Thread {
        private final int id;
        private int speed;
        private Direction dir;
        private final TSimInterface tsi = TSimInterface.getInstance();

        // alla semaforer
        private final Semaphore topTop, topBottom, middleTop, middleBottom;
        private final Semaphore bottomTop, bottomBottom, leftSide, rightSide, intersection;

        Train(int id, int speed, Direction dir,
              Semaphore topTop, Semaphore topBottom, Semaphore middleTop, Semaphore middleBottom,
              Semaphore bottomTop, Semaphore bottomBottom, Semaphore leftSide,
              Semaphore rightSide, Semaphore intersection) {

            this.id = id;
            this.speed = speed;
            this.dir = dir;
            this.topTop = topTop;
            this.topBottom = topBottom;
            this.middleTop = middleTop;
            this.middleBottom = middleBottom;
            this.bottomTop = bottomTop;
            this.bottomBottom = bottomBottom;
            this.leftSide = leftSide;
            this.rightSide = rightSide;
            this.intersection = intersection;

            try {
                tsi.setSpeed(id, speed);
            } catch (CommandException e) {
                e.printStackTrace(); // Print the error to the console
                System.exit(1); // Stop the program immediately
            }
        }

        //Returns true if a sensor at coordinates (x, y) has just been triggered
        private boolean active(SensorEvent e, int x, int y) {
            return e.getXpos() == x && e.getYpos() == y &&
                    e.getStatus() == SensorEvent.ACTIVE;
        }

        //Stops the train
        private void stopTrain() throws CommandException {
            tsi.setSpeed(id, 0);
        }

        //Starts the train
        private void goTrain() throws CommandException {
            tsi.setSpeed(id, speed);
        }

        //Switches the track at the given coordinates (x, y)
        private void switchTrack(int x, int y, int direction) throws CommandException {
            tsi.setSwitch(x, y, direction);
        }

        @Override
        public void run() {
            try {
                while (true) { //Continuously listen for sensor events
                    SensorEvent e = tsi.getSensor(id); //Each SensorEvent tells which sensor the train just passed

                    //topTop
                    //DOWN: stop, lock right side, switch right, go, release topTop
                    //UP: release right side when leaving
                    if (active(e,14,7) && dir == Direction.DOWN) {
                        stopTrain();
                        //Stops the train briefly to acquire semaphores and switch the track,
                        //preventing collisions or blocking other trains
                        rightSide.acquire();
                        switchTrack(17,7,TSimInterface.SWITCH_RIGHT);
                        goTrain();
                        topTop.release();
                    } else if (active(e,14,7) && dir == Direction.UP) {
                        rightSide.release();
                    }

                    //intersection
                    //Lock intersection when entering, release when leaving
                    if (((active(e,6,6) || active(e,9,5)) && dir==Direction.DOWN) ||
                            ((active(e,10,8) || active(e,11,7)) && dir==Direction.UP)) {
                        stopTrain();
                        intersection.acquire();
                        goTrain();
                    } else if (((active(e,6,6) || active(e,9,5)) && dir==Direction.UP) ||
                            ((active(e,10,8) || active(e,11,7)) && dir==Direction.DOWN)) {
                        intersection.release();
                    }

                    //topBottom
                    //DOWN: lock right side, switch left, release topBottom
                    //UP: release right side
                    if (active(e,15,8) && dir==Direction.DOWN) {
                        stopTrain();
                        rightSide.acquire();
                        goTrain();
                        switchTrack(17,7,TSimInterface.SWITCH_LEFT);
                        topBottom.release();
                    } else if (active(e,15,8) && dir==Direction.UP) {
                        rightSide.release();
                    }

                    //rightSide
                    //Choose switch based on which semaphore is free
                    if (active(e,18,7) && dir==Direction.UP) {
                        if (topTop.tryAcquire()) switchTrack(17,7,TSimInterface.SWITCH_RIGHT);
                        else { topBottom.acquire(); switchTrack(17,7,TSimInterface.SWITCH_LEFT); }
                    }

                    if (active(e,16,9) && dir==Direction.DOWN) {
                        if (middleTop.tryAcquire()) switchTrack(15,9,TSimInterface.SWITCH_RIGHT);
                        else { middleBottom.acquire(); switchTrack(15,9,TSimInterface.SWITCH_LEFT); }
                    }

                    //bottomTop
                    //UP: lock left side, release bottomTop, switch left
                    //DOWN: release left side
                    if (active(e,6,11) && dir==Direction.UP) {
                        stopTrain();
                        leftSide.acquire();
                        goTrain();
                        bottomTop.release();
                        switchTrack(3,11,TSimInterface.SWITCH_LEFT);
                    } else if (active(e,6,11) && dir==Direction.DOWN) {
                        leftSide.release();
                    }

                    //bottomBottom
                    //UP: lock left side, release bottomBottom, switch right
                    //DOWN: release left side
                    if (active(e,4,13) && dir==Direction.UP) {
                        stopTrain();
                        leftSide.acquire();
                        goTrain();
                        bottomBottom.release();
                        switchTrack(3,11,TSimInterface.SWITCH_RIGHT);
                    } else if (active(e,4,13) && dir==Direction.DOWN) {
                        leftSide.release();
                    }

                    //leftSide
                    //Choose switch depending on which semaphore is free
                    if (active(e,2,11) && dir==Direction.DOWN) {
                        if (bottomTop.tryAcquire()) switchTrack(3,11,TSimInterface.SWITCH_LEFT);
                        else { bottomBottom.acquire(); switchTrack(3,11,TSimInterface.SWITCH_RIGHT); }
                    }

                    if (active(e,3,9) && dir==Direction.UP) {
                        if (middleTop.tryAcquire()) switchTrack(4,9,TSimInterface.SWITCH_LEFT);
                        else { middleBottom.acquire(); switchTrack(4,9,TSimInterface.SWITCH_RIGHT); }
                    }

                    //middleTop
                    //Lock left/right side when entering, release when leaving, switch track
                    if (active(e,7,9) && dir==Direction.DOWN) {
                        stopTrain();
                        leftSide.acquire();
                        goTrain();
                        switchTrack(4,9,TSimInterface.SWITCH_LEFT);
                        middleTop.release();
                    } else if (active(e,7,9) && dir==Direction.UP) {
                        leftSide.release();
                    }

                    if (active(e,12,9) && dir==Direction.UP) {
                        stopTrain();
                        rightSide.acquire();
                        goTrain();
                        switchTrack(15,9,TSimInterface.SWITCH_RIGHT);
                        middleTop.release();
                    } else if (active(e,12,9) && dir==Direction.DOWN) {
                        rightSide.release();
                    }

                    //middleBottom
                    //Lock/release sides and switch track depending on direction
                    if (active(e,13,10) && dir==Direction.UP) {
                        stopTrain(); rightSide.acquire(); goTrain();
                    } else if (active(e,6,10) && dir==Direction.DOWN) {
                        stopTrain(); leftSide.acquire(); goTrain();
                    }

                    if (active(e,13,10) && dir==Direction.UP) {
                        switchTrack(15,9,TSimInterface.SWITCH_LEFT);
                        middleBottom.release();
                    } else if (active(e,13,10) && dir==Direction.DOWN) {
                        rightSide.release();
                    }

                    if (active(e,6,10) && dir==Direction.DOWN) {
                        switchTrack(4,9,TSimInterface.SWITCH_RIGHT);
                        middleBottom.release();
                    } else if (active(e,6,10) && dir==Direction.UP) {
                        leftSide.release();
                    }


                    //Train stations: Stop, wait, reverse speed and direction
                    if (active(e,15,13) || active(e,15,11) ||
                            active(e,15,3)  || active(e,15,5)) {
                        stopTrain();
                        sleep(1000 + (20 * Math.abs(speed))); //Wait 1-2 seconds at each station
                        speed = -speed;
                        goTrain();
                        dir = Direction.flip(dir);
                    }
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage()); //Print the exception message to the console
            }
        }
    }
}