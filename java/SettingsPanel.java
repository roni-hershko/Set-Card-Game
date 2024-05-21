import java.awt.*;
import java.util.Arrays;
import javax.swing.*;

public class SettingsPanel implements Runnable {

    private JFrame frame;
    private JPanel panel;
    private JLabel labelHumanPlayers;
    private JTextField textHumanPlayers;
    private JLabel labelBots;
    private JTextField textBots;
    private JLabel labelMode;
    private JTextField textMode;
    private JLabel labelPenaltyTime;
    private JTextField textPenaltyTime;
    private JLabel labelPointTime;
    private JTextField textPointTime;
    private JLabel labelPlayersNames;
    private JTextField textPlayersNames;
    private JButton buttonSave;
    private JButton buttonClear;

    Config config;
    Object settingsKey;

    public SettingsPanel (Config config, Object settingsKey) {
        this.config = config;
        this.settingsKey = settingsKey;
    }

    public void saveButtonPressed(String[] settings) {
        boolean humansChanged = false;
        try { //setting amount of human players
            int humanPlayers = Integer.parseInt(settings[0]);
            if (humanPlayers >= 0 && humanPlayers <= 2) {
                config.humanPlayers = humanPlayers;
                humansChanged=true;
            }
        } catch (NumberFormatException ignored) {
        }

        try {//setting amount of bot players
            int computerPlayers = Integer.parseInt(settings[1]);
            if (computerPlayers >= 0 && humansChanged && computerPlayers + config.humanPlayers <= 4) {
                config.computerPlayers = computerPlayers;
            }
        } catch (NumberFormatException ignored) {
        } //updating config properties revolving the new amount of players
        config.players = config.humanPlayers + config.computerPlayers;
        if (!settings[5].isBlank()) {
            String[] namesLong = settings[5].split(",");
            String[] names;
            if (namesLong.length>2) {
                names = new String[2];
                names[0] = namesLong[0];
                names[1] = namesLong[1];
            }
            else names = namesLong;
            config.playerNames = new String[config.players];
            Arrays.setAll(config.playerNames, i -> i < names.length ? names[i].trim() : "Player " + (i + 1));
        }
        config.playerKeys = new int[config.players][config.rows * config.columns];
        for (int i = 0; i < config.players; i++) {
            String defaultCodes = "";
            if (i < 2) defaultCodes = config.playerKeysDefaults[i];
            String playerKeysString = "";
            if (i==0) playerKeysString = "81,87,69,82,65,83,68,70,90,88,67,86";
            if (i==1) playerKeysString = "85,73,79,80,74,75,76,59,77,44,46,47";
            if (playerKeysString.length() > 0) {
                String[] codes = playerKeysString.split(",");
                for (int j = 0; j < Math.min(codes.length, config.tableSize); ++j) // parse the key codes string
                    config.playerKeys[i][j] = Integer.parseInt(codes[j]);
            }
        }

        try {//setting game mode
            int turnTimeoutMillis = Integer.parseInt(settings[2]);
            if (turnTimeoutMillis <= 0) config.turnTimeoutMillis = turnTimeoutMillis;
            else config.turnTimeoutMillis = 60000;
        } catch (NumberFormatException ignored) {
        }
        try {//setting freeze penalty time
            int penaltyFreezeMillis = Integer.parseInt(settings[3]);
            if (penaltyFreezeMillis >= 0) config.penaltyFreezeMillis = penaltyFreezeMillis * 1000;
        } catch (NumberFormatException ignored) {
        }
        try {//setting point penalty time
            int pointFreezeMillis = Integer.parseInt(settings[4]);
            if (pointFreezeMillis >= 0) config.pointFreezeMillis = pointFreezeMillis * 1000;
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public void run() {
        // Create the frame
        frame = new JFrame("C_All_Rights_Reserved - Avinoam David");
        frame.setSize(700, 600);
//        ImageIcon logo = new ImageIcon("setCardsGame.png"));
//        frame.setIconImage(logo.getImage());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Create the panel and set its layout
        panel = new JPanel();
        panel.setLayout(new GridLayout(7,2));

        // Create the label and text field for the human players section
        labelHumanPlayers = new JLabel("Amount of players (from 0 - 2) :");
        textHumanPlayers = new JTextField(20);

        // Create the label and text field for the human players names section
        labelPlayersNames = new JLabel("Player/s names (Names Divided by ', ') :");
        textPlayersNames = new JTextField(20);

        // Create the label and text field for the bots section
        labelBots = new JLabel("Amount of Bots (maximum of 4 players in TOTAL) :");
        textBots = new JTextField(20);

        // Create the label and text field for the game mode section
        labelMode = new JLabel("Game Mode ( -1 / 0 / 1 ) :");
        textMode = new JTextField(20);

        // Create the label and text field for the penalty freeze time section
        labelPenaltyTime = new JLabel("Penalty Freeze Time (in Seconds) :");
        textPenaltyTime = new JTextField(20);

        // Create the label and text field for the point freeze time section
        labelPointTime = new JLabel("Point Freeze Time (in Seconds) :");
        textPointTime = new JTextField(20);

        // Create the save and clear buttons
        buttonSave = new JButton("Save");
        buttonClear = new JButton("Clear");
        buttonClear.setBackground(Color.RED);




        // Add the components to the panel:




        //human Players section:
        GridBagConstraints c0 = new GridBagConstraints();
        c0.gridx = 0;
        c0.gridy = 0;
        //    c0.weightx = 1.0;
        c0.anchor = GridBagConstraints.WEST;
        panel.add(labelHumanPlayers, c0);
        panel.add(textHumanPlayers, c0);

        //human Players section:
        GridBagConstraints c0x = new GridBagConstraints();
        c0x.gridx = 0;
        c0x.gridy = 1;
        //    c0.weightx = 1.0;
        c0x.anchor = GridBagConstraints.WEST;
        panel.add(labelPlayersNames, c0x);
        panel.add(textPlayersNames, c0x);

        //bot Players section:
        GridBagConstraints c1 = new GridBagConstraints();
        c1.gridx = 0;
        c1.gridy = 2;
        //      c1.weightx = 1.0;
        c1.anchor = GridBagConstraints.WEST;
        panel.add(labelBots, c1);
        panel.add(textBots, c1);

        //game mode section:
        GridBagConstraints c2 = new GridBagConstraints();
        c2.gridx = 0;
        c2.gridy = 3;
        //      c2.weightx = 1.0;
        c2.anchor = GridBagConstraints.WEST;
        panel.add(labelMode, c2);
        panel.add(textMode, c2);

        //penalty freeze time section:
        GridBagConstraints c3 = new GridBagConstraints();
        c3.gridx = 0;
        c3.gridy = 4;
//        c3.weightx = 1.0;
        c3.anchor = GridBagConstraints.WEST;
        panel.add(labelPenaltyTime, c3);
        panel.add(textPenaltyTime, c3);

        //penalty freeze time section:
        GridBagConstraints c4 = new GridBagConstraints();
        c4.gridx = 0;
        c4.gridy = 5;
        //       c4.weightx = 1.0;
        c4.anchor = GridBagConstraints.WEST;
        panel.add(labelPointTime, c4);
        panel.add(textPointTime, c4);

        //save/clear Button:
        GridBagConstraints c5 = new GridBagConstraints();
        c5.gridx = 0;
        c5.gridy = 6;
        panel.add(buttonSave, c5);
        panel.add(buttonClear, c5);


        // Add the panel to the frame
        frame.add(panel);

        // Show the frame
        frame.setVisible(true);

        //change settings in config file
        buttonSave.addActionListener(e -> {
            String[] settings = new String[6];
            settings[0] = textHumanPlayers.getText();
            settings[1] = textBots.getText();
            settings[2] = textMode.getText();
            settings[3] = textPenaltyTime.getText();
            settings[4] = textPointTime.getText();
            settings[5] = textPlayersNames.getText();
            frame.dispose();
            saveButtonPressed(settings);
            synchronized (settingsKey) {settingsKey.notifyAll();}
        });

        buttonClear.addActionListener(e -> {
            textHumanPlayers.setText("");
            textPlayersNames.setText("");
            textBots.setText("");
            textMode.setText("");
            textPenaltyTime.setText("");
            textPointTime.setText("");
        });
    }
}
