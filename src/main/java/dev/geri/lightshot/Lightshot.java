package dev.geri.lightshot;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.Compression;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Lightshot extends ListenerAdapter {

    private static final int maxGenerationAttemptLimit = 15;
    private static final int redirectFollowAttemptLimit = 5;

    private static final Logger logger = LoggerFactory.getLogger(Lightshot.class);
    private static final Pattern linkPattern = Pattern.compile("<meta name=\"twitter:image:src\" ?content=\"(?<link>.+?)\" ?/>");
    private static final String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/93.0.4577.63 Safari/537.36 Edg/93.0.961.38";
    private static final ArrayList<String> blacklistedLinks = new ArrayList<>(List.of(
            "https://i.imgur.com/removed.png"
    ));

    private static void print(String message) {
        logger.info(message);
    }

    private static void error(String message) {
        logger.error(message);
    }

    public static void main(String[] args) {
        if (args.length == 0 || args[0].length() == 0) {
            error("Invalid token provided, please start it with: java -jar <jar> <token>");
            System.exit(1);
        }

        JDABuilder builder = JDABuilder.createDefault(args[0]);
        builder.disableCache(CacheFlag.VOICE_STATE);
        builder.enableIntents(GatewayIntent.GUILD_MEMBERS);
        builder.setBulkDeleteSplittingEnabled(false);
        builder.setCompression(Compression.NONE);
        builder.setActivity(Activity.watching("lightshot..."));
        builder.addEventListeners(new Lightshot());

        try {
            builder.build();
        } catch (LoginException exception) {
            error("There was an error starting the bot: " + exception.getMessage());
        }
    }

    @Override
    public void onReady(ReadyEvent e) {
        CommandData command = new CommandData("generate", "Get random pic because yes ok.");
        for (Guild guild : e.getJDA().getGuilds()) {
            guild.updateCommands().addCommands(command).queue(success -> {
                print("Added commands to " + guild.getName());
            }, throwable -> error("There was an error updating commands for " + guild.getName() + ": " + throwable.getMessage()));
        }
    }

    @Override
    public void onSlashCommand(SlashCommandEvent e) {
        if (e.getName().length() == 0) return;
        if (e.getGuild() == null) return;
        this.handleInteraction(e);
    }

    @Override
    public void onButtonClick(ButtonClickEvent e) {
        if (e.getButton() == null || e.getButton().isDisabled() || e.getButton().getId() == null || e.getGuild() == null) return;
        if (e.getMessage().getAuthor() != e.getJDA().getSelfUser()) return;
        if (!e.getButton().getId().startsWith("retry")) return;
        this.handleInteraction(e);
    }

    /**
     * Handle a SlashCommand or button click interaction by creating or editing a message
     *
     * @param i The interaction to handle
     */
    private void handleInteraction(Interaction i) {

        User user = i.getUser();
        if (i instanceof ButtonClickEvent e) {
            String buttonId = e.getButton() != null ? e.getButton().getId() : null;
            if (buttonId == null) {
                replyInteraction(i, null);
                return;
            }

            String[] buttonComponents = buttonId.split(":");
            if (buttonComponents.length < 2) {
                replyInteraction(i, "Invalid button format!");
                return;
            }

            long givenUserId;
            try {
                givenUserId = Long.parseLong(buttonComponents[1]);
            } catch (NumberFormatException exception) {
                replyInteraction(i, "Invalid button!");
                return;
            }

            if (givenUserId != user.getIdLong()) {
                replyInteraction(i, "This is not your image, get your own using `/generate`!");
                return;
            }

            // Acknowledge interaction
            e.deferEdit().queue();

        } else if (i instanceof SlashCommandEvent e) { // Create new message
            e.reply("Loading images..").setEphemeral(false).queue(null, throwable -> {});
        } else {
            return;
        }

        HashMap<String, String> data = getImage();
        String link = data.get("link");
        String attempts = data.getOrDefault("attempts", "?");
        if (link == null) {
            replyInteraction(i, "No valid images found in " + attempts + " retries!");
            return;
        }

        print("Sending link: " + link + " to " + user.getAsTag());
        Button button = Button.success("retry:" + user.getIdLong(), "New image!").withEmoji(Emoji.fromUnicode("♻️"));

        if (i instanceof ButtonClickEvent e) {
            e.getMessage().editMessage(link).setActionRow(button).queue(null, throwable -> {});
        } else if (i instanceof SlashCommandEvent e) { // Create new message
            e.getTextChannel().sendMessage(link).setActionRow(button).queue(null, throwable -> {});
        }

    }

    /**
     * Reply to an interaction
     *
     * @param interaction The interaction to reply to
     * @param msg         An option message to send back to the user (error-only1)
     */
    private static void replyInteraction(Interaction interaction, String msg) {
        String formattedMessage = "❌ There was an error getting a new image";
        if (msg != null) formattedMessage += ": " + msg;

        User user = interaction.getUser();
        Button button = Button.success("retry:" + user.getIdLong(), "Try again!").withEmoji(Emoji.fromUnicode("♻️"));

        if (interaction instanceof SlashCommandEvent event) {
            event.reply(formattedMessage)
                    .addActionRow(button)
                    .queue(null, Throwable::printStackTrace);
        } else if (interaction instanceof ButtonClickEvent event) {
            Message message = event.getMessage();
            message.editMessage(formattedMessage)
                    .setActionRow(button)
                    .queue(null, Throwable::printStackTrace);
        }
    }

    /**
     * Get a new image from Lightshot
     *
     * @return A valid image or null if no valid image was found in the set amount of attempts
     */
    private HashMap<String, String> getImage(int... attempts) {
        int attempt = attempts.length > 0 ? attempts[0] : 0;
        if (attempt > maxGenerationAttemptLimit) return new HashMap<>() {{
            this.put("link", null);
            this.put("attempts", String.valueOf(attempt));
        }};

        StringBuilder letters = new StringBuilder();
        String characters = "abcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < 6; i++) {
            letters.append(characters.charAt(new Random().nextInt(characters.length())));
        }

        String url = "https://prnt.sc/" + letters;
        String data = requestData(url);

        if (data == null) {
            print("URL returned invalid data, skipping " + url);
            return getImage(attempt + 1);
        }

        Matcher matcher = linkPattern.matcher(data);
        if (!matcher.find()) {
            print("No valid metadata found, skipping " + url);
            return getImage(attempt + 1);
        }

        String imageLink = matcher.group("link");
        String finalLink = getFinalURL(imageLink);
        if (blacklistedLinks.contains(imageLink) || blacklistedLinks.contains(finalLink)) {
            print("Blacklisted URL found, skipping " + url);
            return getImage(attempt + 1);
        }

        String image = requestData(finalLink);
        if (image == null) {
            print("Final image's data is invalid, skipping " + url);
            return getImage(attempt + 1);
        }

        return new HashMap<>() {{
            this.put("link", url);
            this.put("attempts", String.valueOf(attempt));
        }};
    }

    /**
     * Get data from a specific endpoint
     *
     * @param endPoint The endpoint to get data from
     * @return The read data or null if there was an issue reading the data
     */
    private String requestData(String endPoint) {
        try {
            URL url = new URL(endPoint);

            URLConnection urlConnection = url.openConnection();
            urlConnection.setRequestProperty("User-Agent", userAgent);

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            String input;
            while ((input = bufferedReader.readLine()) != null) sb.append(input);
            return sb.toString();
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * Follow URL redirects to find the final URL
     *
     * @param url The URL to check
     * @return The final URL or the same URL in case there was an issue
     */
    public String getFinalURL(String url, int... attempts) {
        int attempt = attempts.length > 0 ? attempts[0] : 0;
        if (attempt > redirectFollowAttemptLimit) {
            error("Too many redirect attempts for " + url);
            return url;
        }

        try {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setInstanceFollowRedirects(false);
            con.connect();
            con.getInputStream();

            if (con.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM || con.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
                String redirectUrl = con.getHeaderField("Location");
                return getFinalURL(redirectUrl, attempt + 1);
            }
        } catch (IOException exception) {
            return url;
        }

        return url;
    }

}