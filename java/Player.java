import java.util.ArrayList;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    volatile  Boolean myKey = false;
    volatile  Boolean aiKey = false;
    Object myKeyObj = new Object();
    Object aiKeyObj = new Object();

    Dealer dealer;
    Thread dealerThread;
    int myFreezeState;
    int second = 1000;
    volatile boolean readyForCheck = false;

    protected ArrayList<Integer> myTokens;

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True if the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    int score;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        myTokens = new ArrayList<>();
        myFreezeState=-1;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        playerThread = Thread.currentThread();
        if (!human) {
            createArtificialIntelligence();
            synchronized (aiKeyObj) { //waiting for AI to initialize
                if (!aiKey) {
                    try {
                        aiKeyObj.wait();
                    } catch (InterruptedException x) { Thread.currentThread().interrupt();}
                }
            }
        }
        myKey = true;
        synchronized (myKeyObj) { myKeyObj.notifyAll(); } //unpausing dealer
        while (!terminate) {
            synchronized (this) {
                try {
                    if (!readyForCheck) { //the player waits for three cards
                        notifyAll();
                        wait();
                    }
                } catch (InterruptedException exit) {Thread.currentThread().interrupt();}

                //adds the player to the concurrency queue
                table.playersToCheckSet.add(id);

                //wakes up the dealer and wait for him

                synchronized (dealer) {dealer.notifyAll();}
                try {
                    wait();
                } catch (InterruptedException x) {Thread.currentThread().interrupt();}
                pointOrPenalty();
                readyForCheck = false;
            }
        }
        Thread.interrupted();
        if (!human) {try {aiThread.join();} catch (InterruptedException ignored) {} }
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            aiKey = true;
            synchronized (aiKeyObj) {aiKeyObj.notifyAll();} //unpausing player
            while (!terminate) {
                synchronized (this) {
                    if (table.freeze){
                        synchronized (table.freezeObj){
                            if (table.freeze) {
                                try{
                                    table.freezeObj.wait();
                                } catch (InterruptedException x){Thread.currentThread().interrupt();}
                                table.freezeObj.notifyAll();
                            }
                        }
                    }
                    int chosenSlot = (int) (Math.random() * env.config.tableSize); //choose a random number
                    keyPressed(chosenSlot);
                    while (readyForCheck) { //the AI player waits until there are 3 cards
                        try {
                            notifyAll();
                            wait();
                        } catch (InterruptedException ignored) {Thread.currentThread().interrupt();}
                    }
                }
            }
            //synchronized (this) {notifyAll();}
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        readyForCheck = false;
        terminate = true;
        if (!human) {aiThread.interrupt();}
        playerThread.interrupt();
        try {
            playerThread.join();
        } catch (InterruptedException x) {
        }
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        synchronized (myTokens) {
            if (!table.freeze && myFreezeState == -1 && table.slotToCard[slot] != null) {
                boolean isDeletion = myTokens.contains(slot);
                if (isDeletion) { //removes token if requested
                    table.removeToken(id, slot);
                    myTokens.remove((Integer) slot);
                }
                if (!isDeletion && myTokens.size() < env.config.featureSize) { //adds a token if requested

                    myTokens.add(slot);
                    table.placeToken(id, slot); //TODO replace "feature size" in the next line:
                    synchronized (this) {
                        if (myTokens.size() == env.config.featureSize && !terminate) {// 3 cards on the queue
                            readyForCheck = true;
                            notifyAll();
                        }
                    }
                }
            }
        }
    }




    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        //increase score and present it
        env.ui.setScore(id, score);
        env.ui.setFreeze(this.id,env.config.pointFreezeMillis);
        long freezeEnd = env.config.pointFreezeMillis;
        //makes the thread sleep once a second and wakes him up to update the countdown
        while (freezeEnd>0) {
            try {
                Thread.sleep(second);
            } catch (InterruptedException ignored) {Thread.currentThread().interrupt();}
            freezeEnd -= second;
            env.ui.setFreeze(this.id, freezeEnd);
        }
    }

    /**
     * Penalize a player and perform other related actions.
     *
     */

    /**
     * @PRE myFreezeState = 1
     * @POST myFreezeState = 1
     * @POST @post score = @prescore
     * */
    public void penalty() {
        //sets the freeze countdown
        env.ui.setFreeze(this.id,env.config.penaltyFreezeMillis);
        long freezeEnd = env.config.penaltyFreezeMillis;
        //makes the thread sleep once a second and wakes him up to update the countdown
        while (freezeEnd>0) {
            try {
                Thread.sleep(second);
            } catch (InterruptedException ignored) {Thread.currentThread().interrupt();}
            freezeEnd -= second;
            env.ui.setFreeze(this.id, freezeEnd);
        }


    }

    /***
     * @post @post score == @pre score
     * @return
     */
    public int getScore() {
        return score;
    }

    /***Assist Functions*********************************************************/

    private void pointOrPenalty(){
        if (myFreezeState == 0) {
            penalty();
        } else if (myFreezeState == 1) {
            point();
        }
        myFreezeState = -1;
    }
    /***************************************************************************/
}
