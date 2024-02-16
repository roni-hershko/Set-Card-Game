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
	

public Dealer(Env env, Table table, Player[] players) {
	this.env = env;
	this.table = table;
	this.players = players;
	deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
	terminate = false;
    reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
    lastUpdateForElapsed = System.currentTimeMillis();
    //GP: added
	
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


		//compare and set= boolean cas = env.util.testSet(int[] cards);
		//atomic integer, atomic boolean
		//concurrent queue, concurrent hash map, concurrent linked queue, 

		//every minute the dealer should reshuffle the table- collect all cards and put new cards
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
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
        freezeAllPlayers(env.config.tableDelayMillis);
		for (Player player : players) {
			if(player.queueCounter == 3){ //how to check 
                if(checkSet(player.slotQueue)){
                    player.point();
                    for (int j = 0; j < player.queueCounter; j++){
                        int slot =player.slotQueue.poll();
                        table.removeToken(player.id, slot); 
                        for (Player playerSlot : players) {
                            if(playerSlot.slotQueue.contains(slot)){
                                playerSlot.slotQueue.remove(slot);
                                playerSlot.queueCounter--;
                            }
                        }
                        table.removeCard(slot);// check if other players have placed token while the dealer was checking the set
                        updateTimerDisplay(true);
                    }
                    player.queueCounter = 0;
                }
                else player.penalty();
                player.isChecked = true;
            }
        }
        
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
		//need to verify that no player do anything while removing the cards
        //relevent also for put 3 cards and also to fill all the table
        //place cards on the table
        freezeAllPlayers(env.config.tableDelayMillis);
        for (int i = 0; i < env.config.tableSize; i++){
            if (table.slotToCard[i] == null){
                if (deck.size() > 0){
                    int card = deck.remove(0);
                    table.placeCard(card, i);
                }
            }
        }
        notifyAll();
        //create lists for searching sets
        List<Integer>cardList=new LinkedList<Integer>();
        for(int i=0; i<table.slotToCard.length; i++){
            if(table.slotToCard[i] != null){
                cardList.add(table.slotToCard[i]);
            }
        }
        List<int[]> findSetsTable = env.util.findSets(cardList, 3);
        if(findSetsTable.size()==0){ /// no set on table
            List<Integer> deckAndTable=new LinkedList<Integer>();
            for(int i=0; i<table.slotToCard.length; i++){
                if(table.slotToCard[i] != null){
                    deckAndTable.add(table.slotToCard[i]);
                }
            }
            for(int i=0; i<deck.size(); i++){
                deckAndTable.add(deck.get(i));
            }

            List<int[]> findSetsDeck = env.util.findSets(deckAndTable, 3);

            if(findSetsDeck.size()==0){
                terminate = true;
            }
            else{
                shuffleDeck();
                placeCardsOnTable();
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        if (table.playersQueue.size()==0) {
            try {
                wait(second);
            } catch (InterruptedException x) { Thread.currentThread().interrupt();}
        }
    }
	//one secod o update the timer
    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if(env.config.turnTimeoutMillis == 0){
            if (reset) {
                env.ui.setElapsed(0);
                lastUpdateForElapsed = System.currentTimeMillis();
            } 
            else {
                env.ui.setElapsed(System.currentTimeMillis() - lastUpdateForElapsed);
            }
        }

        if(env.config.turnTimeoutMillis > 0) {
            if(reset){
                reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
                env.ui.setCountdown(env.config.turnTimeoutMillis, false);
            }
            else{
                if(reshuffleTime -System.currentTimeMillis() <= env.config.turnTimeoutWarningMillis)
                    env.ui.setCountdown(reshuffleTime -System.currentTimeMillis() , true);
                else
                    env.ui.setCountdown(reshuffleTime -System.currentTimeMillis() , false);
            }
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        freezeAllPlayers(env.config.tableDelayMillis);
        
        for(int i = 0; i < players.length; i++){
            try {
                players[i].getPlayerThread().wait();
            } catch (InterruptedException e) {}
        }
        for(int i = 0; i < players.length; i++){
            for (int j = 0; j < players[i].queueCounter; j++){
                int slot =players[i].slotQueue.poll();
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
		terminate = true;
    }


	//new method

	public boolean checkSet(Queue<Integer> playQueue ) {
		int[] cards = new int[playQueue.size()];
        for (int i = 0; i < playQueue.size(); i++) {
            cards[i] = table.slotToCard[playQueue.poll()];
        }
		return env.util.testSet(cards);
	}


    public void shuffleDeck() {
        for(int i=0; i<table.slotToCard.length; i++){
            if(table.slotToCard[i] != null){
                deck.add(table.slotToCard[i]);
            }
        }
        removeAllCardsFromTable();
        //shuffle the deck
        for (int i = 0; i < env.config.deckSize; i++) {
            int randomIndex = (int) (Math.random() * deck.size());
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

