package bguspl.set.ex;
import bguspl.set.Env;

import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

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
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

	//new fields
    /**
	 * queue of key presses
	 */
	ConcurrentLinkedQueue<Integer> slotQueue;

	int queueCounter; 

	public volatile boolean isChecked;

	public final Object PlayerLock;

    public final Object aiPlayerLock;

	int second = 1000;

	/**
     * The class constructor.
     *
     * @param env    
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */

    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
	 	this.queueCounter = 0; 
		this.slotQueue = new ConcurrentLinkedQueue<Integer>();
        this.isChecked = false;
		this.PlayerLock = new Object();
		this.aiPlayerLock = new Object();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() { //?????
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) {
            createArtificialIntelligence();
            synchronized (aiPlayerLock) { 
                
            }
        }

        while (!terminate) {  
		} 
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() { //?????
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {

            
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            synchronized (aiPlayerLock) { 
                aiPlayerLock.notifyAll(); //wake up the player thread
            }
            while (!terminate) {
                
                IAkeyPressed();
                try {
                    synchronized (this) { wait(); }
                } catch (InterruptedException ignored) {}
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        
		if (!human) aiThread.interrupt();
		if (playerThread != null) playerThread.interrupt();
		terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if(table.slotToCard[slot] != null){ 
            boolean isDoubleClick = false;	

            //add slot to slotQueue
                //poll remove the first element from the queue, we add the element back to the queue ?????
                // but when we reach the double slot we stop the process so the order of element is different
                //i dont think its a problem bur should be checked

                //another thing we need to check that there is no access to the table somehow ????
            for(int i = 0; i < queueCounter; i++){
                int currSlot= slotQueue.poll(); 
                if(currSlot != slot)
                    slotQueue.add(currSlot);

                //if the key is pressed twice, remove the token from the table
                else{
                    isDoubleClick = true;
                    table.removeToken(id, slot);
                    queueCounter--;
                }
            }
            if (!isDoubleClick) {
                slotQueue.add(slot); //add the key press to the queue
                queueCounter++;
                table.placeToken(id, slot); //place the token on the table
            }
            if(queueCounter == env.config.featureSize && !terminate){
                table.addQueuePlayers(this);
                notifyAll(); 
                //if notifyAll wakes up another player thread that already pressed env.config.featureSize keys and is waiting for the dealer to check, put him back to sleep 
                while (!isChecked) {
                    try {
                        Thread.currentThread().wait();
                    } catch (InterruptedException e) {} 
                }
                isChecked = false;
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
		env.ui.setScore(id, ++score);
		long pointFreeze = env.config.pointFreezeMillis;
        env.ui.setFreeze(id, pointFreeze);
		score++;

		while(pointFreeze > 0){ 
			try {
				Thread.sleep(second); //cut the freeze time of point to seconds so the updateTimerDisplay function will update the time countdown currently
			} catch (InterruptedException e){}
			pointFreeze -= second;
			env.ui.setFreeze(id, pointFreeze);
		}
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {

		long penaltyFreeze = env.config.penaltyFreezeMillis;
        env.ui.setFreeze(id, penaltyFreeze);

		while(penaltyFreeze > 0){ 
			try {
				Thread.sleep(second); //same as point
			} catch (InterruptedException e){}
			penaltyFreeze -= second;
			env.ui.setFreeze(id, penaltyFreeze);
		}
    }

    public int score() {
        return score;
    }

	//new methods
	public int id() {
		return id;
	}

    public Thread getPlayerThread() {
        return playerThread;
    }

    public void IAkeyPressed(){
        Random rand = new Random();
        int rand_int1 = rand.nextInt(env.config.tableSize+1);
        keyPressed(rand_int1);
    }

}
