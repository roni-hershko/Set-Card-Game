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


	//new fields

	/**
	 * queue of players
	 */
	private volatile Queue<Player> playersQueue;

	/**
	 * cards that should be removed from the table
	 */
	private List<Integer> cardsToRemove;


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
	playersQueue = new LinkedList<Player>();
	cardsToRemove = new LinkedList<Integer>(); 
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

		//the goal of the field card to remove is to save all 3 cards in the list and then remove them is one action
		for (int currSlot : cardsToRemove) {
			table.removeToken(currSlot); //need to add the player to the signature
            table.removeCard(currSlot); 
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
		//need to verify that no player do anything while removing the cards

        // TODO implement
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
    }

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

		for (int i = 0; i < env.config.tableSize; i++)
			table.removeCard(i);
		for(int i = 0; i < player; i++)
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

	public boolean checkSet(Queue<Integer> keyPresses ) {
		int[] cards = new int[keyPresses.size()];
		return env.util.testSet(cards);
	}
}
