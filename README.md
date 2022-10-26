# Lightshot Scraper
A simple Java application to find random images from Lightshot (https://prnt.sc/) just for fun.

At the time of writing this, there have been over 4,888,738,259 images uploaded since 2010 to this site, and yet they still use incredibly short links that at this point can simply be guessed.

People tend to upload some funny/curious things to sites like this.

⚠ **HEADS UP**: While a bunch of these links are public knowledge and are indexed elsewhere, please make sure to respect others' privacy in case you find any sensitive data.

---

# Usage
The application is written using Java and is compiled to be used on Java 17. Make sure you have that installed, or recompile it with your desired version. (Java 8+)

To use the application, follow the steps below:
1. Download it from the 'Releases' tab: https://github.com/notgeri/lightshot/releases
2. Create a new application at https://discord.com/developers/applications
3. On the left side, click 'Bot' and click 'Add Bot'.
4. Once created, click 'Reset Token' and copy the fresh copied token. Note this down somewhere safe temporarily.
5. Click 'OAuth2' on the left side and click 'URL Generator'.
6. There, click `bot` and `applications.commands`. Make sure to give it permission to read the channel you intended to use it in.
7. Open the 'Generated URL' from below and let Discord guide you through inviting the bot to your desired server. 
8. Open a terminal in the directory where you downloaded the JAR to and start the application using: `java -jar Lightshot.jar <token>` and make sure to replace `<token>` with your token from step #3.
9. Done! If you are using a Linux machine, you can use utilities such as `screen` to run it in the background instead of having to keep the terminal up.

Once up and running, you can use the `/generate` command in channels that the bot has access to, to start generating images. 
Simply click the 'New image!' button below the new message to generate a new one each time.

By default, the bot will re-try in case the found image was broken/invalid. After 15 attempts, it will give up, however.
