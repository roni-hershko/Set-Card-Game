# **<u>Set Cards Game By Roni Hershko</u>**
![Set Cards Game Logo](./resources/LOGO.jpg)
## About Me
As for the time this project was built, I'm a 2nd year Computer Science student in Ben Gurion University. To this date i have knowledge at networks, apps, big data, designing algorithms and more, and I am fluent in C++, Java/#C, Python, Node.js and SQL.  

This Game was chosen by me as it was the game my family used to play every friday night at our house after dinner, and so I held it close to my heart.  

## About the Project
* The project was made in Java and in order to demonstrate my knowledge in *multi-threading* and designing algorithms. It was also made with small usage of *Swift* dir to make the game visible - though the front-end **was not the primary objective** of the project.
* You will find in the code usage of Java's concurrency tools and data structures such as - 'Synchronized' command, readers-writes locks, Semaphore, concurent queues and lists, etc.

Hope you Enjoy!

## About The Game
* To learn about the original game rules please consider [*this instruction video link*](https://www.youtube.com/watch?v=NzXDfSFQ1c0).  
* The project presents a similar version of the original Set Game with minor changes and added features - made to emphasize my multi-threading skills. some of them are:
    * Play against a fellow friend and some "AI powered" bots who infinitely selects 3 random cards from the table. (**Up to 2 human players and a total of 4 players**)
    * Add a desired freeze time to penalize a player that chose a false set, as-well as a small freeze to one who chose a correct set.
    * Choose a Game Mode:  
     enter a positive number to play with a Countdown timer that reshuffles the cards on the table whenever it goes off without a single set found!  
     Or set "0" / negative number to have a normal timer or not show time at all (respectively).  
* A simple program run will automatically load a pop up settings window which will allow editing and setting **SOME OF THE ADDED GAME FEATURES**.  
To review the code or edit more settings, feel free to enter the *config.properties* class where you will find more cool features supported by the game!
* To init the game with editted properties (and not from the Settings panel), simply find and hide / un-hide the command line:  
"*makeSettings(config);*" found in the *main* function at the *Main* class as shown here:

```java
Util util = new UtilImpl(config);

        /*The Following line should be unHidden IFF you wish to run the game with
          the limited "Settings" window instead of manually changing any desires
          settings via "config.properties" file, inside "resources" folder!!
         */

        //makeSettings(config);

        Player[] players = new Player[config.players];
```
### Feel Free To Contact Me !!




