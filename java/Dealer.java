import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {


    private final Env env; /*** The game environment object.*/

    /*** Game entities.*/
    private final Table table;private final Player[] players;

    /*** The list of card ids that are left in the dealer's deck.*/
    private final List<Integer> deck;
    Thread dealerThread;

    private final List<Integer> emptySlots;

    /** True iff game should be terminated due to an external event.*/
    private volatile boolean terminate;
    int sleepTime;

    /**The time when the dealer needs to reshuffle the deck due to turn timeout.*/
    private long reshuffleTime = Long.MAX_VALUE;
    private long timerDisplay = 60000;
    private final  int second = 1000;
    private final int mili10 = 10;
    private long lastUpdate;


    public Dealer(Env env, Table table, Player[] players) {
        sleepTime = second;
        this.env = env;
        this.table = table;
        this.players = players;
        this.terminate = false;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        emptySlots = new ArrayList();
        for (int i = 0; i < env.config.tableSize; i++) {
            emptySlots.add((Integer) i);
        }
        //init timer settings
        if (env.config.turnTimeoutMillis > 0)
            reshuffleTime = env.config.turnTimeoutMillis + System.currentTimeMillis(); //minute
        else
            lastUpdate = System.currentTimeMillis();

    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        dealerThread = Thread.currentThread();
        //start players' threads
        for (Player player : players) {
            player.dealerThread = Thread.currentThread();
            new Thread(player).start();
            synchronized (player.myKeyObj) { //waiting for player to initialize
                if (!player.myKey) {
                    try {
                        player.myKeyObj.wait();
                    } catch (InterruptedException x) { Thread.currentThread().interrupt();}
                }
            }
        }
        updateTimerDisplay(true); //resets the countdown / timer
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop(); //functions that take action within a minute
            updateTimerDisplay(true);
            removeAllCardsFromTable();
        }
        announceWinners();
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            checkSet(); //checks for a valid set and give instructions according to the result
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        if(!terminate){
//            if (Thread.currentThread()!=dealerThread)dealerThread.interrupt();
            for (int i = env.config.players - 1; i >= 0; i--) {
                players[i].terminate();
            }
            terminate = true;
//            try {
//                dealerThread.join();
//            } catch (InterruptedException ignored) {}
        }

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
     * Checks if any cards should be removed from the table and returns them to the deck.
     */
    private void removeCardsFromTable(Integer[] slotsToRemove) {
        table.freeze = true; //so no player can place a token
        for (int currSlot : slotsToRemove) {
            removeAllTokens(currSlot);
            table.removeCard(currSlot); //removes card
            emptySlots.add(currSlot); //adds the empty slot back to the market
        }
        table.freeze = false;
        synchronized (table.freezeObj){
            table.freezeObj.notifyAll();
        } //releases the key and creates a chain respond
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {

        table.freeze = true;

        Collections.shuffle(emptySlots);
        Collections.shuffle(deck);

        //places the cards, updates relevant fields
        placeCardOnEmptySlots();

        //turns the array into a list for findSets() use
        List<Integer> GridList = slotsToList();

        //checks if no sets remained and reshuffle\ terminate the game accordingly
        noSetsAction(GridList);

        table.freeze = false;
        synchronized (table.freezeObj){
            table.freezeObj.notifyAll();

        } //releases the key and creates a chain respond

    }

    private void checkSet() {
        while (!table.playersToCheckSet.isEmpty()) {
            Player playerToCheck = players[table.playersToCheckSet.remove()]; //removes the player from the queue
            synchronized (playerToCheck) {
                if (playerToCheck.myTokens.size() == env.config.featureSize) {

                    //creates an array of slots and array of cards
                    Integer[] setSlots = createSlotsArray(playerToCheck.id);
                    int[] setCards = createCardsArray(playerToCheck.id, setSlots);
                    boolean isSet = env.util.testSet(setCards);

                    //punish or reward
                    if (isSet) {
                        playerToCheck.score++;
                        removeCardsFromTable(setSlots);
                        updateTimerDisplay(true);
                        playerToCheck.myFreezeState = 1;
                    }
                    else {
                        playerToCheck.myFreezeState = 0;
                    }

                } else { //the player had less than 3 cards in his set
                    playerToCheck.myFreezeState = -1;
                }
                playerToCheck.notifyAll();

            }
        }
    }


    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private synchronized void sleepUntilWokenOrTimeout() {
        if (table.playersToCheckSet.size()==0) {
            try {
                wait(sleepTime);
            } catch (InterruptedException x) { Thread.currentThread().interrupt();
            }
        }

    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        //UP TIMER
        if (env.config.turnTimeoutMillis == 0) {
            if (reset) {
                env.ui.setElapsed(0);
                lastUpdate = System.currentTimeMillis();
            } else {
                long timerDisplay = (System.currentTimeMillis() - lastUpdate);
                env.ui.setElapsed(timerDisplay);
            }
        }

        //COUNTDOWN
        else if (env.config.turnTimeoutMillis > 0) {
            sleepTime = second;
            if (reset){
                timerDisplay = env.config.turnTimeoutMillis;
                if (timerDisplay<=env.config.turnTimeoutWarningMillis) {
                    env.ui.setCountdown(timerDisplay, true);
                    sleepTime = mili10;
                }
                else env.ui.setCountdown(timerDisplay, false);
                //the reshuffle time represents the time for next reshuffle
                reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            }
            else {
                timerDisplay = reshuffleTime - System.currentTimeMillis();
                if (timerDisplay<=env.config.turnTimeoutWarningMillis) {
                    env.ui.setCountdown(timerDisplay, true);
                    sleepTime = mili10;
                }
                else env.ui.setCountdown(timerDisplay, false);
            }
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     * @post: all slots are empty
     * @post: freeze == false
     */
    public void removeAllCardsFromTable() {
        table.freeze = true;
        for (Integer x : table.playersToCheckSet) {
            synchronized (players[x]) {
                table.playersToCheckSet.remove(x);
                players[x].notifyAll();
            }

        }
        for (int currSlot = 0; currSlot < env.config.tableSize; currSlot++) {
            if (table.slotToCard[currSlot] != null) {
                Integer cardToRemove = table.slotToCard[currSlot];
                removeAllTokens(currSlot); //removes tokens for all players
                table.removeCard(currSlot); //removes card
                deck.add(cardToRemove); //adds the card back to the deck
                emptySlots.add(currSlot); //adds the empty slot back to the market
            }
        }
        table.freeze = false;
        synchronized (table.freezeObj){
            table.freezeObj.notifyAll();} //releases the key and creates a chain respond
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int maxScore = 0;
        //finds max score
        LinkedList<Integer> winners = new LinkedList<>();
        for (int i = 0; i < env.config.players; i++) {
            if (players[i].getScore() > maxScore) maxScore = players[i].getScore();
        }
        //finds player with max score
        for (int i = 0; i < env.config.players; i++) {
            if (players[i].getScore() == maxScore) {
                winners.add(players[i].id);
            }
        }

        //turns the list into an array
        int[] winArray = new int[winners.size()];
        for (int i = 0; i < winArray.length; i++) {
            winArray[i] = winners.removeFirst();
        }
        env.ui.announceWinner(winArray);

    }

    /***Assit Functions*************************************************/
    private List<Integer> slotsToList (){
        List<Integer> GridList = new ArrayList<Integer>();
        if(env.config.turnTimeoutMillis <= 0 ||  deck.isEmpty()){
            for (int i=0;i< env.config.tableSize; i++){
                if (table.slotToCard[i] != null)
                    GridList.add(table.slotToCard[i]);
            }
        }
        return  GridList;
    }

    private void noSetsAction (List<Integer> GridList) {
        boolean cond1 = env.config.turnTimeoutMillis <= 0 && env.util.findSets(GridList, 1).size() == 0; //for timeState <=
        boolean cond2 = env.config.turnTimeoutMillis > 0 && deck.isEmpty() && env.util.findSets(GridList, 1).size() == 0; //for timeState >0 & deck is empty

        if (cond1 || cond2) { //there are no sets on the table
            if (!deck.isEmpty()) { //cond1 and the deck is not empty
                removeAllCardsFromTable();
                placeCardsOnTable();
            } else { //cond1 and deck is empty \ cond2
                table.freeze = false;
                synchronized (table.freezeObj){

                    table.freezeObj.notifyAll();}
                terminate();
            }
        }
    }

    private Integer[] createSlotsArray (int playerID){
        Integer[] setSlots = new Integer[env.config.featureSize];
        //translate the arrayList to array of slots and array of cards
        for (int i = 0; i < setSlots.length; i++) {
            setSlots[i] = (players[playerID].myTokens).get(i);
        }
        return  setSlots;
    }

    private int[] createCardsArray (int playerID ,Integer[] setSlots){
        int[] setCards = new int[env.config.featureSize];
        //translate the arrayList to array of slots and array of cards
        for (int i = 0; i < setCards.length; i++) {
            setCards[i] = table.slotToCard[setSlots[i]];
        }
        return setCards;
    }

    private void removeAllTokens(int currSlot){
        for (int i = 0; i < env.config.players; i++) { //removes tokens for all players
            table.removeToken(i, currSlot);
            synchronized (players[i].myTokens) { //updates the player's tokens list

                if (players[i].myTokens.contains((Integer) currSlot))
                    players[i].myTokens.remove((Integer) currSlot);
            }

        }
    }

    /**
     * @post all slots are filled
     * @post emptySlots list is empty
     */
    public void placeCardOnEmptySlots(){
        int size = emptySlots.size();
        for (int i = size - 1; i >= 0 & !deck.isEmpty(); i--) {
            int currSlot = emptySlots.get(i);
            int currCard = deck.remove(0); //remove card from deck
            table.placeCard(currCard, currSlot); //place it in the grid
            emptySlots.remove(i);
        }
    }

    public int getEmptySlotsSize(){
        return emptySlots.size();
    }


    /************************************************************************/
}
