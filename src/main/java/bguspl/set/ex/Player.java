package bguspl.set.ex;

import bguspl.set.Env;

import java.util.LinkedList;
import java.util.Queue;



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
		
	Queue<Integer> keyPresses;

	int queueCounter; 
	

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
		this.keyPresses = new LinkedList<Integer>();

    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
			playerThread.start();        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");

		//Once the player places his third token on the table, he must notify the dealer and wait until the dealer checks if it is a legal set or not. 
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
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
        
		if (aiThread != null) aiThread.interrupt();
		if (playerThread != null) playerThread.interrupt();
		terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
		boolean isDoubleClick = false;	
		for(int i = 0; i < queueCounter; i++){
			int pull= keyPresses.poll();
			if(pull != slot)
				keyPresses.add(pull);
			else{
				isDoubleClick = true;
				table.removeToken(id, slot);
				queueCounter--;
			}
		}
		if (!isDoubleClick) {
			keyPresses.add(slot); //add the key press to the queue
			queueCounter++;
			table.placeToken(id, slot); //place the token on the table
		}
		try {
			Thread.currentThread().wait();
		} catch (InterruptedException e) {} //wait for dealer to check if winner or penalty
		//need to ask the dealer if the cards in the queue are a set
		//if they are a set, then point, else penalty
		
	}
    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
		env.ui.setScore(id, ++score);
		env.ui.setFreeze(id, env.config.pointFreezeMillis);
		score++;

		try {
			Thread.sleep(env.config.pointFreezeMillis);
		} catch (InterruptedException e){}


        int ignored = table.countCards(); // this part is just for demonstration in the unit tests

    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        env.ui.setFreeze(id, env.config.penaltyFreezeMillis);
		try {
			Thread.sleep(env.config.penaltyFreezeMillis);
		} catch (InterruptedException e){}

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
}
