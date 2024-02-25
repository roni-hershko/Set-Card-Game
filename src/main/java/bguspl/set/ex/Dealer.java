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
			env.logger.info("thread " + Thread.currentThread().getName() + " run step 1.");

            placeCardsOnTable();

			env.logger.info("thread " + Thread.currentThread().getName() + " run step 2.");

            timerLoop();

			env.logger.info("thread " + Thread.currentThread().getName() + " run step 3.");

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
			env.logger.info("thread " + Thread.currentThread().getName() + " timer loop 1.");

			sleepUntilWokenOrTimeout();

			env.logger.info("thread " + Thread.currentThread().getName() + " timer loop 2.");

            updateTimerDisplay(false);

			env.logger.info("thread " + Thread.currentThread().getName() + " timer loop 3.");

            removeCardsFromTable();

			env.logger.info("thread " + Thread.currentThread().getName() + " timer loop 4.");

            placeCardsOnTable();

			env.logger.info("thread " + Thread.currentThread().getName() + " timer loop 5.");

        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        freezeAllPlayers(env.config.endGamePauseMillies); 
		env.logger.info("thread " + Thread.currentThread().getName() + " terminate 1.");

		if(!terminate){

			env.logger.info("thread " + Thread.currentThread().getName() + " terminate 2.");

			for (int i = players.length -1; i >= 0; i--) {
				env.logger.info("thread " + Thread.currentThread().getName() + " terminate 3.");

                players[i].terminate();
            //     try { //mabye not needed
            //         players[i].getPlayerThread().join(); //mabye not needed
            //     } catch (InterruptedException e) { } //mabye not needed
 	  		}
			   env.logger.info("thread " + Thread.currentThread().getName() + " terminate 4.");

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

			env.logger.info("thread " + Thread.currentThread().getName() + " rm 1.");

		//check if there is a valid set and remove the cards
		for (Player player : table.playersQueue) {
			table.canPlaceTokens=false;
			//start of check set ad
            synchronized(player) {
				env.logger.info("thread " + Thread.currentThread().getName() + " rm 2.");

				table.removeQueuePlayers(player); 

				env.logger.info("thread " + Thread.currentThread().getName() + " rm 3."); 
 

				//check the set
                 if(checkSet(player.slotQueue)){ 
					env.logger.info("thread " + Thread.currentThread().getName() + " rm 4.");

					player.isSetFound = true;
				}
				env.logger.info("thread " + Thread.currentThread().getName() + " rm 5.");

				//remove the set cards from the player data
				for (int j = 0; j <env.config.featureSize; j++){
					env.logger.info("thread " + Thread.currentThread().getName() + " rm 6."+ "slotQueue.size " + player.slotQueue.size() + " the player is: "+ player.id);

					//updateTimerDisplay(false); //remember to remove
					int slot; //added by rh

					synchronized(player.slotQueue){ //added by rh 
						env.logger.info("thread " + Thread.currentThread().getName() + " rm 7!!!.");

						slot = player.slotQueue.poll();
					}
					env.logger.info("thread " + Thread.currentThread().getName() + " rm 8 !!!!.");

					table.removeToken(player.id, slot); 
					env.logger.info("thread " + Thread.currentThread().getName() + " rm 8.5 !!!!.");


					//updateTimerDisplay(false); //remember to remove

					env.logger.info("thread " + Thread.currentThread().getName() + " rm 9.");

					if(player.isSetFound){
						env.logger.info("thread " + Thread.currentThread().getName() + " rm 10.");

						table.removeCard(slot);

						//remove the tokens of the cards that were removed from the table from the other players
						for (Player playerSlot : players) { //start remove all tokens ad
							env.logger.info("thread " + Thread.currentThread().getName() + " rm 11.");

							//updateTimerDisplay(false); //remember to remove

							if(player.id != playerSlot.id && playerSlot.slotQueue.contains(slot)){
								table.removeToken(playerSlot.id, slot);
								env.logger.info("thread " + Thread.currentThread().getName() + " rm 12.");

								//check if the player. is in the queue of players
								synchronized(playerSlot.slotQueue){
									env.logger.info("thread " + Thread.currentThread().getName() + " rm 13.");

									if(playerSlot.slotQueue.contains(slot)){
										env.logger.info("thread " + Thread.currentThread().getName() + " rm 14.");

										playerSlot.slotQueue.remove(slot); 
									//playerSlot.queueCounter--;
									}
								}
								env.logger.info("thread " + Thread.currentThread().getName() + " rm 15.");

								if(table.playersQueue.contains(playerSlot)){ //???}
									table.removeQueuePlayers(playerSlot); //??
								}
							}
						}
					}
				}
				env.logger.info("thread " + Thread.currentThread().getName() + " rm 17.");

				table.canPlaceTokens = true;
				synchronized(table.lock){
					env.logger.info("thread " + Thread.currentThread().getName() + " rm 18.");

					table.lock.notifyAll();
					env.logger.info("thread " + Thread.currentThread().getName() + "rm 19 .");
					
				}   
				env.logger.info("thread " + Thread.currentThread().getName() + " rm 20.");

				//player.queueCounter = 0;

				if(player.isSetFound){
					env.logger.info("thread " + Thread.currentThread().getName() + " rm 21.");

					//updateTimerDisplay(true); //remember to remove
				}
				else {
					//updateTimerDisplay(false); //remember to remove
				}
				env.logger.info("thread " + Thread.currentThread().getName() + " rm 22.");

				player.notifyAll();
				env.logger.info("thread " + Thread.currentThread().getName() + " rm 23.");

			}
			//updateTimerDisplay(false); //remember to remove

		}
	}

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        freezeAllPlayers(env.config.tableDelayMillis); 
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
		env.logger.info("thread " + Thread.currentThread().getName() + " step 1.");
		if(needReset)
			updateTimerDisplay(true); 
		else 
			updateTimerDisplay(false); 


        //create lists for searching sets on table
        List<Integer>cardListTable=new LinkedList<Integer>();
        for(int i=0; i<table.slotToCard.length; i++){

			env.logger.info("thread " + Thread.currentThread().getName() + " step 2.");

             if(table.slotToCard[i] != null){
                cardListTable.add(table.slotToCard[i]);
             }
         }
		 env.logger.info("thread " + Thread.currentThread().getName() + " step 3.");

		 //updateTimerDisplay(false); //remember to remove

		//check if there is a valid set on the table
        List<int[]> findSetsTable = env.util.findSets(cardListTable, env.config.featureSize);

		env.logger.info("thread " + Thread.currentThread().getName() + " step 4.");

        if(findSetsTable.size()==0){ // no set on table

            if(deck.size() == 0){ //and no cards in deck
				table.canPlaceTokens = true;
				synchronized(table.lock){
					table.lock.notifyAll(); 
				}
                terminate();
            }

		env.logger.info("thread " + Thread.currentThread().getName() + " step 5.");

			//updateTimerDisplay(false); //remember to remove
			//new cards on table
            removeAllCardsFromTable();

			env.logger.info("thread " + Thread.currentThread().getName() + " step 6.");

            //create lists for searching sets on deck + table
            List<Integer> deckForFindSets = new LinkedList<Integer>();
            for(int i = 0; i < deck.size(); i++){
				deckForFindSets.add(deck.get(i));
            }

			env.logger.info("thread " + Thread.currentThread().getName() + " step 7.");

			//updateTimerDisplay(false); //remember to remove

			//check if there are sets on deck + table
            List<int[]> findSetsDeck = env.util.findSets(deck, env.config.featureSize);
			env.logger.info("thread " + Thread.currentThread().getName() + " step 8.");

            if(findSetsDeck.size()==0){ //no sets on deck + table
				table.canPlaceTokens = true;
				synchronized(table.lock){
					table.lock.notifyAll(); 
				}
                terminate();
            }
			env.logger.info("thread " + Thread.currentThread().getName() + " step 9.");

			//updateTimerDisplay(false);
            placeCardsOnTable();
        }
		env.logger.info("thread " + Thread.currentThread().getName() + " step 10.");

        table.canPlaceTokens = true;
        synchronized(table.lock){
			env.logger.info("thread " + Thread.currentThread().getName() + " step 11.");

            table.lock.notifyAll();
			env.logger.info("thread " + Thread.currentThread().getName() + " step 12.");

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
        // long timer;

        // if(env.config.turnTimeoutMillis == 0){
        //     if (reset) {
        //         env.ui.setElapsed(0);
        //         lastUpdateForElapsed = System.currentTimeMillis();
        //     } 
        //     else {
        //         timer = System.currentTimeMillis() - lastUpdateForElapsed;
        //         env.ui.setElapsed(timer);
        //     }
        // }

        // else if(env.config.turnTimeoutMillis > 0) {
        //     dealerSleepTime=second;
        //     if(reset){
        //         timer = env.config.turnTimeoutMillis;
        //         if(timer <= env.config.turnTimeoutWarningMillis){
        //             env.ui.setCountdown(timer, true);
        //             dealerSleepTime = miliSec10;
        //         }
        //         else
        //             env.ui.setCountdown(timer, false);
        //             reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        //     }
        //     else{
        //         timer = reshuffleTime - System.currentTimeMillis();
        //         if(timer <= env.config.turnTimeoutWarningMillis){
        //             env.ui.setCountdown(timer , true);
        //             dealerSleepTime = miliSec10;
        //         }
        //         else
        //             env.ui.setCountdown(timer , false);
        //     }
        // }

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
			if(timeLeft <0){
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

		env.logger.info("remove all step 1 " + Thread.currentThread().getName() + " rm all 1.");
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
		env.logger.info("before removing token thread " + Thread.currentThread().getName() + " rm all 2.");
        //remove all the tokens from the table
        for(int i = 0; i < env.config.players; i++){
			synchronized(players[i].slotQueue){
            	for (int j = 0; j < players[i].slotQueue.size(); j++){
					env.logger.info("thread " + Thread.currentThread().getName() + " rm all 3" + players[i].slotQueue.size());
					int slot = players[i].slotQueue.poll();
					env.logger.info("thread " + Thread.currentThread().getName() + " rm all 4" + "slot to remove: " +slot);

					table.removeToken(players[i].id, slot);
					j--;
					env.logger.info("thread " + Thread.currentThread().getName() + " rm all 5" + "token removed: " +slot);
				}
            }
        }
		env.logger.info("after removing token thread " + Thread.currentThread().getName() + " rm 6.");

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

