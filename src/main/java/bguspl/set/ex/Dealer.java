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

	private volatile boolean setFound;

	private volatile boolean changeTime;


	public Dealer(Env env, Table table, Player[] players) {

		this.env = env;
		this.table = table;
		this.players = players;
		deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
		terminate = false;
		this.setFound = false;
		this.changeTime = false;
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
            updateTimerDisplay(true);
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
        freezeAllPlayers(env.config.endGamePauseMillies); 
		if(!terminate){
			for (int i = players.length -1; i >= 0; i--) {
                players[i].terminate();
                try { //mabye not needed
                    players[i].getPlayerThread().join(); //mabye not needed
                } catch (InterruptedException e) { } //mabye not needed
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

        freezeAllPlayers(env.config.tableDelayMillis);

        table.canPlaceTokens=false;

		//check if there is a valid set and remove the cards
		for (Player player : table.playersQueue) {
            synchronized(player) {
				table.removeQueuePlayers(player);  
				//check the set
                if(checkSet(player.slotQueue)){ 
					setFound = true;
                    player.point();  

				}
				if(!setFound){
					player.penalty();

				}
					
				//remove the set cards from the player data
				for (int j = 0; j < player.queueCounter; j++){
					int slot = player.slotQueue.poll();
					table.removeToken(player.id, slot); 


					if(setFound){
						table.removeCard(slot);

						//remove the tokens of the cards that were removed from the table from the other players
						for (Player playerSlot : players) {
							
							if(player.id != playerSlot.id && playerSlot.slotQueue.contains(slot)){
								table.removeToken(playerSlot.id, slot);
								//check if the player. is in the queue of players
								if(table.playersQueue.contains(playerSlot))
									table.removeQueuePlayers(playerSlot);
								playerSlot.slotQueue.remove(slot); 
								playerSlot.queueCounter--;
							}
						}
					}
				}
				player.isChecked = true;
				player.notifyAll();
				// if(!player.isHuman())
				// 	player.aiPlayerLock.notifyAll();
				player.queueCounter = 0;
			}

			//player.isChecked = true;
			if(setFound){
        		changeTime = true;
				setFound=false;
			}
        }

		if(changeTime)
			updateTimerDisplay(true);

		changeTime = false;
		table.canPlaceTokens = true;

		synchronized(table.lock){
			table.lock.notifyAll();
		}   
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        freezeAllPlayers(env.config.tableDelayMillis); 
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
                terminate();
            }

			//new cards on table
            removeAllCardsFromTable();

            //create lists for searching sets on deck + table
            List<Integer> deck = new LinkedList<Integer>();
            for(int i=0; i<env.config.deckSize; i++){
                   deck.add(deck.get(i));
            }

			//check if there are sets on deck + table
            List<int[]> findSetsDeck = env.util.findSets(deck, env.config.featureSize);
            if(findSetsDeck.size()==0){ //no sets on deck + table
				table.canPlaceTokens = true;
				synchronized(table.lock){
					table.lock.notifyAll(); 
				}
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
    private synchronized void sleepUntilWokenOrTimeout() {
		if (table.playersQueue.size()==0) { 
			try {
				wait(dealerSleepTime);
			} catch (InterruptedException x) { Thread.currentThread().interrupt();}  
		}
    }

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
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        freezeAllPlayers(env.config.tableDelayMillis); 
        table.canPlaceTokens=false;
		env.logger.info("REMOVE ALL 1 ");

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
	public boolean checkSet(Queue<Integer> playerQueue ) {
		int[] cards = new int[playerQueue.size()];
        for (int i = 0; i < playerQueue.size(); i++) {
			int slot = playerQueue.poll();
            cards[i] = table.slotToCard[slot];
			playerQueue.add(slot);
        }
		return env.util.testSet(cards);
	}

    public void shuffleDeck() {
        // for (int i = 0; i < env.config.deckSize; i++) {
        //     int randomIndex = (int) (Math.random() * env.config.deckSize);
        //     int temp = deck.get(i);
        //     deck.set(i, deck.get(randomIndex));
        //     deck.set(randomIndex, temp);
        // }
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

