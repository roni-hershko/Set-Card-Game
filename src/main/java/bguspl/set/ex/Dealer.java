package bguspl.set.ex;

import bguspl.set.Env;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;
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

	public Dealer(Env env, Table table, Player[] players) {

		this.env = env;
		this.table = table;
		this.players = players;
		deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
		terminate = false;
		if (env.config.turnTimeoutMillis > 0)
			reshuffleTime = env.config.turnTimeoutMillis + System.currentTimeMillis(); //minute
		else
			lastUpdateForElapsed = System.currentTimeMillis();
	}

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
		env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
		for (Player player : players) {
            new Thread(player).start(); 
				synchronized(player.PlayerLock){
				if(!player.PlayerCreated){
                    try {
						player.PlayerLock.wait();
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            }
		}
		updateTimerDisplay(true);	

        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
		env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
	private void timerLoop() {

        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
			sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        //freezeAllPlayers(env.config.endGamePauseMillies); 
		if(!terminate){
			for (int i = players.length -1; i >= 0; i--) {

                players[i].terminate();
                try { //mabye not needed
                    players[i].getPlayerThread().join(); //mabye not needed
                } catch (InterruptedException e) { } //mabye not needed
 	  		}
		}
	    terminate = true;
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

        //freezeAllPlayers(env.config.tableDelayMillis);

		//check if there is a valid set and remove the cards
		for (Player player : table.playersQueue) {
			table.canPlaceTokens=false;
			//start of check set ad
			table.removeQueuePlayers(player);  

			//check the set
			if(checkSet(player.slotQueue)){ 
				player.isSetFound = true;
			}

			//remove the set cards from the player data
			//for (int j = 0; j <env.config.featureSize; j++){
			while(!player.slotQueue.isEmpty()){
				int slot; //added by rh
				synchronized(player.slotQueue){ //added by rh 
					slot = player.slotQueue.poll();
				}
				table.removeToken(player.id, slot); 

				if(player.isSetFound){
					table.removeCard(slot);

					//remove the tokens of the cards that were removed from the table from the other players
					for (Player playerSlot : players) { //start remove all tokens ad
						if(player.id != playerSlot.id && playerSlot.slotQueue.contains(slot)){
							table.removeToken(playerSlot.id, slot);

							//check if the player. is in the queue of players
							synchronized(playerSlot.slotQueue){
								if(playerSlot.slotQueue.contains(slot)){
									playerSlot.slotQueue.remove(slot); 
								}
							}
							
							if(table.playersQueue.contains(playerSlot)){ //???
								table.removeQueuePlayers(playerSlot); //??
								synchronized(playerSlot) {
									playerSlot.notifyAll();
								}
							}
						}
					}
				}
			}
				table.canPlaceTokens = true;
				synchronized(table.lock){
					table.lock.notifyAll();					
				}
				synchronized(player) {
					player.notifyAll();
				}
			}
		}

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        //freezeAllPlayers(env.config.tableDelayMillis); 
		table.canPlaceTokens=false;

		shuffleDeck();
		boolean needReset = false;
        //place cards on null slots- removed cards 
		for (int i = 0; i < env.config.tableSize; i++){
            if (table.slotToCard[i] == null){
				needReset = true;
                if (!deck.isEmpty()){
                    int card = deck.remove(0);
                    table.placeCard(card, i);
                }
            }
        }
		if(needReset){
			updateTimerDisplay(true); 
		}
		else {
			updateTimerDisplay(false); 
		}
		// if(deck.isEmpty()){
		// 	isDeckEmpty = true;
		// }

        //create lists for searching sets on table
        List<Integer>cardListTable=new LinkedList<Integer>();
        for(int i=0; i<table.slotToCard.length; i++){
             if(table.slotToCard[i] != null){
                cardListTable.add(table.slotToCard[i]);
             }
         }
		//check if there is a valid set on the table
        List<int[]> findSetsTable = env.util.findSets(cardListTable, env.config.featureSize);

        if(findSetsTable.size()==0){ // no set on table
            if(deck.size() == 0){ //and no cards in deck
				table.canPlaceTokens = true;
				synchronized(table.lock){
					table.lock.notifyAll(); 
				}
				removeAllCardsFromTable();
                terminate();
            }
			else{
				removeAllCardsFromTable();
				//create lists for searching sets on deck + table
				List<Integer> deckForFindSets = new LinkedList<Integer>();
				for(int i = 0; i < deck.size(); i++){
					deckForFindSets.add(deck.get(i));
				}
				//check if there are sets on deck + table
				List<int[]> findSetsDeck = env.util.findSets(deckForFindSets, env.config.featureSize);
	
				if(findSetsDeck.size()==0){ //no sets on deck + table
					table.canPlaceTokens = true;
					synchronized(table.lock){
						table.lock.notifyAll(); 
					}
					terminate();
				}
				else{
					placeCardsOnTable();
				}
			}
        }
        table.canPlaceTokens = true;
        synchronized(table.lock){
            table.lock.notifyAll();
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private synchronized void sleepUntilWokenOrTimeout() {
		if (table.playersQueue.size()==0) { 
			try {
				wait(100); //changet
			} catch (InterruptedException x) { Thread.currentThread().interrupt();}  
		}
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
		boolean warning= false;
		if(reset){
			if(env.config.turnTimeoutMillis <= env.config.turnTimeoutWarningMillis){
				warning = true;
			}
			reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
			env.ui.setCountdown(env.config.turnTimeoutMillis, warning);
		}
		else{
			long timeLeft = reshuffleTime - System.currentTimeMillis() + 50;
			if(timeLeft < 0){
				timeLeft = 0;
			}
			warning =timeLeft <= env.config.turnTimeoutWarningMillis;
			env.ui.setCountdown(timeLeft, warning);
		}
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
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
        for(int i = 0; i < env.config.players; i++){
			synchronized(players[i].slotQueue){
				while (!players[i].slotQueue.isEmpty()) {
            	//for (int j = 0; j < players[i].slotQueue.size(); j++){
					int slot = players[i].slotQueue.poll();
					table.removeToken(players[i].id, slot);
					// j--;
				}
            }
        }
		updateTimerDisplay(false);

        //remove all the cards from the table
        for (int i = 0; i < env.config.tableSize; i++){
			Integer card = table.slotToCard[i];
			if (table.slotToCard[i] != null) { 
            	table.removeCard(i);
				deck.add(card); //add cards from table to deck
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
        int numOfWinner = 0; 

		for (int i = 0; i < players.length; i++) {
			if (players[i].score() > winnerScore) {
				winnerScore = players[i].score();
			}
		}
        for (int i = 0; i < players.length; i++) {
            if (players[i].score() == winnerScore) {
                numOfWinner++;
            }
        }
		int [] winners = new int[numOfWinner];
		int index = 0;
		for (int i = 0; i < players.length; i++) {
            if (players[i].score() == winnerScore) {
				winners[index] = players[i].id;
				index++;
            }
		}
		env.ui.announceWinner(winners);
    }

	//new methods
	public boolean checkSet(Queue<Integer> playerSlotQueue ) {
		int[] cards = new int[playerSlotQueue.size()];
        for (int i = 0; i < playerSlotQueue.size(); i++) {
			int slot = playerSlotQueue.poll();
            cards[i] = table.slotToCard[slot];
			playerSlotQueue.add(slot);
        }
		return env.util.testSet(cards);
	}

	// public void checkSetResult(Player player) {
	// 	if(checkSet(player.slotQueue)){ 
	// 		setFound = true;
	// 		player.point();  
	// 	}
	// 	else{
	// 		setFound = false;
	// 		player.penalty();
	// 	}
	// }

    public void shuffleDeck() {
		Collections.shuffle(deck);
    }

    public void freezeAllPlayers(long time) { //problem with static
        for (Player player : players) {
            try {
                player.getPlayerThread().sleep(time);
            } catch (InterruptedException e) {}
        }
    }
}

