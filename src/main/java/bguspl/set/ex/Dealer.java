package bguspl.set.ex;

import bguspl.set.Env;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Queue;	// added
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

    long countdown=0;
	//new fields
    	/**
	 * queue of players
	 */

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
    //GP: added
    countdown= System.currentTimeMillis();
	//playersQueue = new LinkedList<Player>();
}

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {

		//check if the counter of the keypress queue is 3 
		//check if the key presses in the keypress queue represent a set 
		// if set is found remove the cards from the table-----------------timeloop
		// discard the 3 cards from, the table and add 3 new cards from the deck using placeCardsOnTable ------------timeloop-- removeCardsFromTable+placeCardsOnTable	
		//if there are not enough cards in the deck put all the deck 
		//give the succesful player one point -----------where is the action of updating the score?
		//else if set is not found, penalty -------------check the time 
		//start the next round------------------- time loop
		//updateTimerDisplay------------------- time loop
		//check if there is a set on the table
		//if not reshuffle the table

		//every minute the dealer should reshuffle the table- collect all cards and put new cards
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
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
		checkSet(table.getQueuePlayers());
		removeCardsFromTable();
		placeCardsOnTable();
	}
}

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
		for (Player player : players) {
			player.terminate();
 	   }
	    terminate = true;

		//the game will countinue until the there are no more sets in the table or on deck!!!
		//the player with the most point will win 
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
    private void removeCardsFromTable() { //synchronized?
		//need to verify that no player do anything while removing the cards

		for (Player player : players) {
			if(player.queueCounter == 3){ //how to check 
                if(checkSet(player.keyPresses)){
                    player.point();
                    for (int j = 0; j < player.queueCounter; j++){
                        int slot =player.keyPresses.poll();
                        table.removeToken(player.id, slot);
                        table.removeCard(slot);
                    }
                }
                else player.penalty();
            }
        }
        
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
		//need to verify that no player do anything while removing the cards
        //relevent also for put 3 cards and also to fill all the table
        for (int i = 0; i < env.config.tableSize; i++){
            if (table.slotToCard[i] == null){
                if (deck.size() > 0){
                    int card = deck.remove(0);
                    table.placeCard(card, i);
                }
            }
        }
        notifyAll();
        List<Integer>cardList=new LinkedList<Integer>();
        for(int i=0; i<table.slotToCard.length; i++){
            if(table.slotToCard[i] != null){
                cardList.add(table.slotToCard[i]);
            }
        }
        List<int[]> findSetsTable = env.util.findSets(cardList, 3);
        List<int[]> findSetsDeck = env.util.findSets(deck, 3);

        if(findSetsTable.size()==0){
            if(findSetsDeck.size()==0){
                terminate = true;
            }
            else{
                removeAllCardsFromTable();
                placeCardsOnTable();
            }
        }
        

        if(findSetsDeck.size()==0)
            terminate = true;
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            synchronized (this) { //?
                wait(System.currentTimeMillis()-countdown ==env.config.turnTimeoutMillis) //second); //neeed to check 
            }
        } catch (InterruptedException ignored) {}
    }
	//one secod o update the timer
    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
		//need to verify that no player do anything while removing the cards
        for(int i = 0; i < players.length; i++){
            try {
                players[i].getPlayerThread().wait();
            } catch (InterruptedException e) {}
        }
        for(int i = 0; i < players.length; i++){
            for (int j = 0; j < players[i].queueCounter; j++){
                int slot =players[i].keyPresses.poll();
                table.removeToken(players[i].id, slot);
            }
        }
        for (int i = 0; i < env.config.tableSize; i++){
            table.removeCard(i);
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
		int winnerScore = 0;
		int [] winners = new int[players.length]; 
		for (int i = 0; i < players.length; i++) {
			if (players[i].score() > winnerScore) {
				winnerScore = players[i].score();
				winners[0] = players[i].id();
			}
			else if (players[i].score() == winnerScore) {
				winners[1] = players[i].id();
			}
		}
		env.ui.announceWinner(winners);
		terminate = true;
    }


	//new methods

	public void addPlayer(Player player) {
		playersQueue.add(player);
	}

	public boolean checkSet(Queue<Integer> playQueue ) {
		int[] cards = new int[keyPresses.size()];
        for (int i = 0; i < keyPresses.size(); i++) {
            cards[i] = table.slotToCard[keyPresses.poll()];
        }
		return env.util.testSet(cards);
	}
}
