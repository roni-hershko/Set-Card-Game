package bguspl.set.ex;
import bguspl.set.Env;

import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingDeque;


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
	BlockingQueue<Integer> slotQueue;

	int queueCounter; 

	public volatile boolean isChecked;

	public final Object PlayerLock;

    public final Object aiPlayerLock;

	int second = 1000;

    boolean AICreated = false;
    
    Dealer dealer;

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
		this.slotQueue = new LinkedBlockingDeque<>(env.config.featureSize);
        this.isChecked = false;
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
        if (!human) {
            createArtificialIntelligence();
            synchronized (aiPlayerLock) { 
                if(!AICreated) 
                    try { 
                        aiPlayerLock.wait(); 
                    } catch (InterruptedException e) {Thread.currentThread().interrupt();}
                
            }
        }
        synchronized(aiPlayerLock) {  //for the dealer
            aiPlayerLock.notifyAll(); 
        }
        while (!terminate) { 
			env.logger.info("thread " + Thread.currentThread().getName() + "wip1");

            synchronized(this){
				env.logger.info("thread " + Thread.currentThread().getName() + "wip2");

               if(queueCounter != env.config.featureSize){
					
					env.logger.info("thread " + Thread.currentThread().getName() + "wip3");

                    try {
						env.logger.info("thread " + Thread.currentThread().getName() + "wip4");

                        notifyAll();
                        wait();
						env.logger.info("thread " + Thread.currentThread().getName() + "wip5");

                    } catch (InterruptedException e) {Thread.currentThread().interrupt();}
					
                }
				else{
					env.logger.info("thread " + Thread.currentThread().getName() + " before  synchronize on dealer.");
					table.addQueuePlayers(this);
				}
                synchronized(dealer){
					env.logger.info("thread " + Thread.currentThread().getName() + " after  synchronize on dealer.");

                    dealer.notifyAll();
					env.logger.info("thread " + Thread.currentThread().getName() + " before notify synchronize on dealer.");

                }
               // if (!isChecked) { //maybe syncronized
                try {
                    wait();
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); } 
                isChecked = false; 
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
			env.logger.info("creat AI step 1 ");

            AICreated = true;
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            synchronized (aiPlayerLock) { 
                aiPlayerLock.notifyAll(); //wake up the player thread
				env.logger.info("creat AI step 2 ");
            }
            while (!terminate) {
				env.logger.info("creat AI step 3 ");

                synchronized (this) {
                    if(!table.canPlaceTokens){
						env.logger.info("creat AI step 4 ");

                        synchronized (table.lock) {
                            if(!table.canPlaceTokens){ 
								env.logger.info("creat AI step 5 ");

                                try { 
                                    table.lock.wait(); 
                                } catch (InterruptedException e) {Thread.currentThread().interrupt();}
                                table.lock.notifyAll();
								env.logger.info("creat AI step 6 ");

                            }
                        }
                    }
                }
				env.logger.info("creat AI step 7 ");

                AIkeyPressed();
				env.logger.info("creat AI step 8 ");

                try {
                    synchronized (this) {
						env.logger.info("creat AI step 9 ");
						notifyAll();
						wait(); 
						env.logger.info("creat AI step 10 ");
						}
                } catch (InterruptedException ignored) {}
				env.logger.info("creat AI step 11 ");
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
        // try {
        //     playerThread.join();
        // } catch (InterruptedException ignored) {}
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
		env.logger.info("KP step 1"+ Thread.currentThread().getName());

        if(table.slotToCard[slot] != null && table.canPlaceTokens){
            boolean isDoubleClick = false;	
			env.logger.info("KP step 2"+ Thread.currentThread().getName());

            for(int i = 0; i < queueCounter; i++){
                int currSlot= slotQueue.poll(); 
                if(currSlot != slot)
                    slotQueue.add(currSlot);


                //if the key is pressed twice, remove the token from the table
                else{
					env.logger.info("KP step 3"+ Thread.currentThread().getName());
                    isDoubleClick = true;
                    table.removeToken(id, slot);
                    queueCounter--;
                }
            }
            if (!isDoubleClick) {
				env.logger.info("KP step 4"+ Thread.currentThread().getName());

                slotQueue.add(slot); //add the key press to the queue
                queueCounter++;
				env.logger.info("KP step 5" + Thread.currentThread().getName());

                table.placeToken(id, slot); //place the token on the table
				env.logger.info("KP step 6"+ Thread.currentThread().getName());

            }
			env.logger.info("KP step 7"+ Thread.currentThread().getName());
			synchronized(this){
				env.logger.info("KP step 8"+ Thread.currentThread().getName());
				if(queueCounter == env.config.featureSize && !terminate){
					env.logger.info("KP step 9"+ Thread.currentThread().getName());
					notifyAll();
				}
			}
        }

		env.logger.info("KP step 10"+ Thread.currentThread().getName());
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
				Thread.sleep(second); //cut the freeze time of point to seconds so the updateTimerDisplay function will update the time countdown currently
			} catch (InterruptedException e){Thread.currentThread().interrupt();}
			pointFreeze -= second;
			env.ui.setFreeze(id, pointFreeze);
		}
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
		env.logger.info("penalty");

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

    public void AIkeyPressed(){
		env.logger.info("AI key pressed step 1 ");

		int randomSlot = (int) (Math.random() * env.config.tableSize);
		env.logger.info("AI key pressed step 2 ");

        keyPressed(randomSlot);
		env.logger.info("AI key pressed step 3, random slot: " + randomSlot );

    }

    public boolean isHuman() {
        return human;
    }

}
