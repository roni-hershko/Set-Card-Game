package bguspl.set.ex;

import bguspl.set.Env;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Queue;	// added
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.LinkedList;	// added

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;
 
    //new fields

    long lastUpdateForElapsed;

    int second = 1000;
    long dealerSleepTime=1000;
    int miliSec10 = 10;
	
		//compare and set= boolean cas = env.util.testSet(int[] cards);
		//atomic integer, atomic boolean
		//concurrent queue, concurrent hash map, concurrent linked queue, 

	public Dealer(Env env, Table table, Player[] players) {
	this.env = env;
	this.table = table;
	this.players = players;
	deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
	terminate = false;
    reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
    lastUpdateForElapsed = System.currentTimeMillis();
}

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
		for (Player player : players) {
			//player.dealerThread = Thread.currentThread(); //??????
            new Thread(player).start(); 
            synchronized(player.aiPlayerLock){
                if(!player.isHuman() && !player.AICreated){
                    try {
                        player.aiPlayerLock.wait();
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            }
		}
		updateTimerDisplay(true);	
		env.logger.info("thread " + Thread.currentThread().getName() + " step 1");

        while (!shouldFinish()) {
			env.logger.info("thread " + Thread.currentThread().getName() + " step 2");

            placeCardsOnTable();
			env.logger.info("thread " + Thread.currentThread().getName() + " step 3");

            timerLoop();
			env.logger.info("thread " + Thread.currentThread().getName() + " step ?");

            updateTimerDisplay(true);
			env.logger.info("thread " + Thread.currentThread().getName() + " step ??");

            removeAllCardsFromTable();
			env.logger.info("thread " + Thread.currentThread().getName() + " step ???");

        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
	private void timerLoop() {
		env.logger.info("thread " + Thread.currentThread().getName() + " step 4");

        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
			env.logger.info("thread " + Thread.currentThread().getName() + " step 5");

            sleepUntilWokenOrTimeout();
			env.logger.info("thread " + Thread.currentThread().getName() + " step 6");

            updateTimerDisplay(false);
			env.logger.info("thread " + Thread.currentThread().getName() + " step 7");

            removeCardsFromTable();
			env.logger.info("thread " + Thread.currentThread().getName() + " step 8");

            placeCardsOnTable();
			env.logger.info("thread " + Thread.currentThread().getName() + " step 9");

        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        freezeAllPlayers(env.config.endGamePauseMillies); //????? the players cant do terminate while they are frozen
		if(!terminate){
			for (Player player : players) {
                player.terminate();
                try {
                    player.getPlayerThread().join();
                } catch (InterruptedException e) { }
 	  	 }
		}
	    terminate = true;
        //Thread.currentThread().interrupt();
	}

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() { 
        freezeAllPlayers(env.config.tableDelayMillis); //????? not needed beause happen in table
        table.canPlaceTokens=false;
		//check if there is a valid set and remove the cards
		for (Player player : table.playersQueue) {
            synchronized(player) {
                if(checkSet(player.slotQueue)){ //check the set for the first player in the playerqueue
                    player.point();
                    table.removeQueuePlayers(player);
					
                    //remove the set cards
                    for (int j = 0; j < player.queueCounter; j++){
                        int slot = player.slotQueue.poll();
                        table.removeToken(player.id, slot); 

                        table.removeCard(slot);

						//remove the tokens of the cards that were removed from the table from the other players
                        for (Player playerSlot : players) {
                            if(playerSlot.slotQueue.contains(slot)){
								table.removeToken(playerSlot.id, slot);
                                //check if the player is in the queue of players
                                if(table.playersQueue.contains(playerSlot))
                                    table.removeQueuePlayers(playerSlot);
                                playerSlot.slotQueue.remove(slot);
                                playerSlot.queueCounter--;
                            }
                        }
                        updateTimerDisplay(true);
                    }
                    player.queueCounter = 0;
                }
                else player.penalty();
                player.isChecked = true;
                player.notifyAll();
            }
        
        table.canPlaceTokens = true;
        synchronized(table.lock){
            table.lock.notifyAll();
        }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        freezeAllPlayers(env.config.tableDelayMillis); //same as in removeCardsFromTable
		table.canPlaceTokens=false;
		shuffleDeck();
        //place cards on null slots- removed cards 
		for (int i = 0; i < env.config.tableSize; i++){
            if (table.slotToCard[i] == null){
                if (env.config.deckSize > 0){
                    int card = deck.remove(0);
                    table.placeCard(card, i);
                }
            }
        }

        //create lists for searching sets
        List<Integer>cardListTable=new LinkedList<Integer>();
        for(int i=0; i<table.slotToCard.length; i++){
             if(table.slotToCard[i] != null){
                cardListTable.add(table.slotToCard[i]);
             }
         }
        List<int[]> findSetsTable = env.util.findSets(cardListTable, env.config.featureSize);
        if(findSetsTable.size()==0){ /// no set on table
            if(deck.size() == 0){
                terminate();
            }

            removeAllCardsFromTable();

            //check sets in the deck + table
            List<Integer> deck=new LinkedList<Integer>();
            for(int i=0; i<env.config.deckSize; i++){
                   deck.add(deck.get(i));
            }
    
            List<int[]> findSetsDeck = env.util.findSets(deck, env.config.featureSize);
            if(findSetsDeck.size()==0){
                terminate();
            }
            placeCardsOnTable();
        }

        table.canPlaceTokens = true;
        synchronized(table.lock){
            table.lock.notifyAll();
        }
            // List<Integer> deckAndTable=new LinkedList<Integer>();
            // for(int i=0; i<table.slotToCard.length; i++){
            //     if(table.slotToCard[i] != null){
            //         deckAndTable.add(table.slotToCard[i]);
            //     }
            // }
            // for(int i=0; i<deck.size(); i++){
            //     deckAndTable.add(deck.get(i));
            // }

            // List<int[]> findSetsDeck = env.util.findSets(deckAndTable, env.config.featureSize);

            // if(findSetsDeck.size()==0){
            //     terminate = true;
            // }
            // else{
            //     shuffleDeck();
            //     placeCardsOnTable();
            // }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        if (table.playersQueue.size()==0) { //
            try {
                wait(dealerSleepTime);
            } catch (InterruptedException x) { Thread.currentThread().interrupt();}  
        }
    }
	//one secod o update the timer
    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        long timer;

        if(env.config.turnTimeoutMillis == 0){
            if (reset) {
                env.ui.setElapsed(0);
                lastUpdateForElapsed = System.currentTimeMillis();
            } 
            else {
                timer = System.currentTimeMillis() - lastUpdateForElapsed;
                env.ui.setElapsed(timer);
            }
        }

        else if(env.config.turnTimeoutMillis > 0) {
            dealerSleepTime=second;
            if(reset){
                timer = env.config.turnTimeoutMillis;
                if(timer <= env.config.turnTimeoutWarningMillis){
                    env.ui.setCountdown(timer, true);
                    dealerSleepTime = miliSec10;
                }
                else
                    env.ui.setCountdown(timer, false);
                    reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            }
            else{
                timer = reshuffleTime - System.currentTimeMillis();
                if(timer <= env.config.turnTimeoutWarningMillis){
                    env.ui.setCountdown(timer , true);
                    dealerSleepTime = miliSec10;
                }
                else
                    env.ui.setCountdown(timer , false);
            }
        }
    }		//every minute the dealer should reshuffle the table- collect all cards and put new cards


    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        freezeAllPlayers(env.config.tableDelayMillis); 
        table.canPlaceTokens=false;

        //empty the queue of players in the table
        for(int i = 0; i < players.length; i++){ 
            for( Player player : table.playersQueue){
                synchronized(player){
                    table.removeQueuePlayers(player);
                    player.notifyAll();
                }
            }
        }

        //remove all the tokens from the table
        for(int i = 0; i < players.length; i++){
            for (int j = 0; j < players[i].queueCounter; j++){
                int slot = players[i].slotQueue.poll();
                table.removeToken(players[i].id, slot);
            }
        }
        
        //remove all the cards from the table
        for (int i = 0; i < env.config.tableSize; i++){
			if (table.slotToCard[i] != null) { 
            	table.removeCard(i);
				deck.add(i); //add cards from table to deck
			}
        }
        
        table.canPlaceTokens = true;
        synchronized(table.lock){
            table.lock.notifyAll();
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {

        int winnerScore = 0;
        int winnerIndex = 0;
		int [] winners = new int[players.length]; 

		for (int i = 0; i < players.length; i++) {
			if (players[i].score() > winnerScore) {
				winnerScore = players[i].score();
			}
		}
        for (int i = 0; i < players.length; i++) {
            if (players[i].score() == winnerScore) {
                winners[winnerIndex] = players[i].id;
                winnerIndex++;
            }
        }
		env.ui.announceWinner(winners);
    }

	//new methods
	public boolean checkSet(Queue<Integer> playQueue ) {
		int[] cards = new int[playQueue.size()];
        for (int i = 0; i < playQueue.size(); i++) {
            cards[i] = table.slotToCard[playQueue.poll()];
        }
		return env.util.testSet(cards);
	}

    public void shuffleDeck() {
        for (int i = 0; i < env.config.deckSize; i++) {
            int randomIndex = (int) (Math.random() * env.config.deckSize);
            int temp = deck.get(i);
            deck.set(i, deck.get(randomIndex));
            deck.set(randomIndex, temp);
        }
    }

    public void freezeAllPlayers(long time) { //problem with static
        for (Player player : players) {
            env.ui.setFreeze(player.id(), time);
            try {
                player.getPlayerThread().sleep(time);
            } catch (InterruptedException e) {}
        }
    }
}

