package bguspl.set.ex;
import bguspl.set.Env;

import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

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
	BlockingQueue<Integer> slotQueue;

	public final Object PlayerLock;

    public final Object aiPlayerLock;

    volatile boolean AICreated = false;

	volatile boolean PlayerCreated = false;

	volatile boolean isSetFound = false;
	
	volatile boolean isReadyToCheck = false;

    Dealer dealer;

	

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
		this.slotQueue = new LinkedBlockingQueue<>(env.config.featureSize);
		this.PlayerLock = new Object();
		this.aiPlayerLock = new Object();
        this.dealer = dealer;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() { 
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) { createArtificialIntelligence();
            synchronized (aiPlayerLock) { 
                if(!AICreated) {
                    try { 
                        aiPlayerLock.wait(); 
                    } catch (InterruptedException e) {Thread.currentThread().interrupt();}
				}
            }
        }
		PlayerCreated= true;
		synchronized(PlayerLock){
			PlayerLock.notifyAll();
		}
		
        while (!terminate) { 
            synchronized(this){
                try {
                    if(!isReadyToCheck){					
                        notifyAll();
                        wait();
                    }
                } catch (InterruptedException e) {Thread.currentThread().interrupt();}

                table.addQueuePlayers(this);    
		
			    synchronized(dealer){
					dealer.notifyAll();
				}
				try {
					wait();
				} catch (InterruptedException e) { Thread.currentThread().interrupt(); } 

				if(isSetFound){
					point();  
				}	
				else{
					penalty();
				}
				isSetFound = false;
				isReadyToCheck = false;
			}
		}
		Thread.interrupted();
		if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
		env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
	}
	
    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() { 
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
			AICreated = true;
			
			//wake up the player thread
            synchronized (aiPlayerLock) { 
                aiPlayerLock.notifyAll(); 
            }
            while (!terminate) {
                synchronized (this) {
                    if(!table.canPlaceTokens){
                        synchronized (table.lock) {
                            if(!table.canPlaceTokens){ 
                                try { 
                                    table.lock.wait(); 
                                } catch (InterruptedException e) {Thread.currentThread().interrupt();}
                                table.lock.notifyAll();
                            }
                        }
                    }
					keyPressed(RandomSlot());
					while (isReadyToCheck) {
						try {
							notifyAll();
							wait(); 
						} catch (InterruptedException ignored) {}
					}
                }
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
		terminate = true;

		if (!human) 
			aiThread.interrupt();
		if (playerThread != null) 
			playerThread.interrupt();
        try {
             playerThread.join();
         } catch (InterruptedException ignored) {}
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {

		synchronized(slotQueue){

			if(table.slotToCard[slot] != null  && table.canPlaceTokens){
				boolean isDoubleClick = false;	

				for(int i = 0; i < slotQueue.size(); i++){
					int currSlot= slotQueue.poll(); 
					if(currSlot != slot){
						slotQueue.add(currSlot);
					}

					//if the key is pressed twice, remove the token from the table
					else{
						isDoubleClick = true;
						table.removeToken(id, slot);
					}
				}
				if (!isDoubleClick && slotQueue.size() < env.config.featureSize) {
					slotQueue.add(slot); //add the key press to the queue
					table.placeToken(id, slot); //place the token on the table
				}
			}
			synchronized(this){
				if(slotQueue.size() == env.config.featureSize && !terminate){
					isReadyToCheck= true;
					notifyAll();
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
		score++;
		env.ui.setScore(id, score);
		long pointFreeze = env.config.pointFreezeMillis;
        env.ui.setFreeze(id, pointFreeze);

		while(pointFreeze > 0){ 
			try {
				Thread.sleep(1000); //cut the freeze time of point to seconds so the updateTimerDisplay function will update the time countdown correctly
			} catch (InterruptedException e){Thread.currentThread().interrupt();}
			pointFreeze = pointFreeze - 1000;
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
				Thread.sleep(1000); //same as point
			} catch (InterruptedException e){}
			penaltyFreeze = penaltyFreeze - 1000;
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

	public boolean isHuman() {
		return human;
	}

	public int RandomSlot() {
		return (int) (Math.random() * env.config.tableSize);
	}
}