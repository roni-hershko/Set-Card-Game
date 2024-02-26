package bguspl.set.ex;

import bguspl.set.Env;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
 


	public Dealer(Env env, Table table, Player[] players) {

		this.env = env;
		this.table = table;
		this.players = players;
		deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
		terminate = false;
		reshuffleTime = env.config.turnTimeoutMillis + System.currentTimeMillis(); 
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
		if(!terminate){
			for (int i = players.length -1; i >= 0; i--) {

                players[i].terminate();
                try { 
                    players[i].getPlayerThread().join(); 
                } catch (InterruptedException e) { } 
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

		//check if there is a valid set and remove the cards
		for (Player player : table.playersQueue) {
			table.canPlaceTokens=false;
			table.removeQueuePlayers(player);  

			//check the set
			if(checkSet(player.slotQueue)){ 
				player.isSetFound = true;
			}

			//remove the tokens from the table
			while(!player.slotQueue.isEmpty()){
				int slot; 
				synchronized(player.slotQueue){
					slot = player.slotQueue.poll();
				}
				table.removeToken(player.id, slot); 

				//remove the cards from table if set is found
				if(player.isSetFound){
					table.removeCard(slot);

					//remove the tokens of the cards that were removed from the table from the other players
					for (Player playerSlot : players) { 
						if(player.id != playerSlot.id && playerSlot.slotQueue.contains(slot)){
							table.removeToken(playerSlot.id, slot);

							//check if the player is in the queue of players
							synchronized(playerSlot.slotQueue){
								if(playerSlot.slotQueue.contains(slot)){
									playerSlot.slotQueue.remove(slot); 
								}
							}
							//if the player is in the queue of players, remove him
							if(table.playersQueue.contains(playerSlot)){ 
								table.removeQueuePlayers(playerSlot);
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

		table.canPlaceTokens=false;
		boolean needReset = false; //if there is set or turn timeout

		shuffleDeck();

        //place cards on empty slots on the table
		for (int i = 0; i < env.config.tableSize; i++){
            if (table.slotToCard[i] == null ){ //&& !deck.isEmpty()
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

        //create list for find sets on table
        List<Integer>cardsFromTable = new LinkedList<Integer>();
        for(int i=0; i<table.slotToCard.length; i++){
            if(table.slotToCard[i] != null){
            	cardsFromTable.add(table.slotToCard[i]);
            }
        }
		//check if there is a valid set on the table
        List<int[]> findSetsTable = env.util.findSets(cardsFromTable, env.config.featureSize);

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
				//create list for find sets on deck + table
				List<Integer> cardsFromTableAndDeck = new LinkedList<Integer>();
				for(int i = 0; i < deck.size(); i++){
					cardsFromTableAndDeck.add(deck.get(i));
				}
				//check if there are sets on deck + table
				List<int[]> findSetsDeck = env.util.findSets(cardsFromTableAndDeck, env.config.featureSize);
	
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
				wait(100);
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
			if(timeLeft <= env.config.turnTimeoutWarningMillis){
				warning = true;
			}
			else{
				warning = false;
			}
			env.ui.setCountdown(timeLeft, warning);
		}
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {

        table.canPlaceTokens=false;

		//remove all the tokens from the table
		for(int i = 0; i < env.config.players; i++){
			synchronized(players[i].slotQueue){
				while (!players[i].slotQueue.isEmpty()){
					int slot = players[i].slotQueue.poll();
					table.removeToken(players[i].id, slot);
				}
			}
		}
        //empty the queue of players
        for(int i = 0; i < players.length; i++){ 
            for( Player player : table.playersQueue){
                synchronized(player){
                    table.removeQueuePlayers(player);
                    player.notifyAll();
                }
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
        int numOfWinner = 0; 

		//Find the highest score
		for (int i = 0; i < players.length; i++) {
			if (players[i].score() > winnerScore) {
				winnerScore = players[i].score();
			}
		}
		//find the number of winners
        for (int i = 0; i < players.length; i++) {
            if (players[i].score() == winnerScore) {
                numOfWinner++;
            }
        }
		//create an array of the winners
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
	public boolean checkSet(Queue<Integer> playerSlotQueue) {
		int[] cards = new int[playerSlotQueue.size()];
        for (int i = 0; i < playerSlotQueue.size(); i++) {
			int slot = playerSlotQueue.poll();
            cards[i] = table.slotToCard[slot];
			playerSlotQueue.add(slot);
        }
		return env.util.testSet(cards);
	}

    public void shuffleDeck() {
		Collections.shuffle(deck);
    }

    public void freezeAllPlayers(long time) { 
        for (Player player : players) {
            try {
                player.getPlayerThread().sleep(time);
            } catch (InterruptedException e) {}
        }
    }
}

