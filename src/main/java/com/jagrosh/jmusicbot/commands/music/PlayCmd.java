/*
 * Copyright 2016 John Grosh <john.a.grosh@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.commands.music;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.Command;
import com.jagrosh.jmusicbot.commands.ResultHandler;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.specification.Track;
import com.wrapper.spotify.requests.authorization.client_credentials.ClientCredentialsRequest;
import com.wrapper.spotify.requests.data.tracks.GetTrackRequest;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.io.IOException;
import java.net.URLEncoder;

public class PlayCMD extends Command {
    private final Bot bot;
    private final String loadingEmoji;
    private final SpotifyApi spotifyApi;
    private String accessToken;

    public PlayCMD(Bot bot) {
        this.bot = bot;
        this.loadingEmoji = bot.getConfig().getLoading();
        this.name = "play";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.help = "plays the provided song";
        this.arguments = "<title|URL|subcommand>";
        this.guildOnly = false;
        this.beListening = true;
        this.bePlaying = false;
        this.children = new Command[]{new PlaylistCmd(bot)};

        // Initialize Spotify API with credentials from config.txt
        this.spotifyApi = new SpotifyApi.Builder()
                .setClientId(bot.getConfig().getSpotifyId())
                .setClientSecret(bot.getConfig().getSpotifySecret())
                .build();
    }

    @Override
    protected void execute(CommandEvent event) {
        String args = event.getArgs().startsWith("<") && event.getArgs().endsWith(">")
                ? event.getArgs().substring(1, event.getArgs().length() - 1)
                : event.getArgs().isEmpty() 
                    ? (event.getMessage().getAttachments().isEmpty() ? "" : event.getMessage().getAttachments().get(0).getUrl()) 
                    : event.getArgs();

        // Handle Spotify track URLs
        if (args.contains("open.spotify.com/track/")) {
            try {
                // Extract the track ID from the Spotify URL
                String trackId = args.substring(args.lastIndexOf("/") + 1).split("\\?")[0];

                // Fetch access token if not already fetched or expired
                if (accessToken == null) {
                    fetchAccessToken();
                }

                // Update Spotify API with the access token
                spotifyApi.setAccessToken(accessToken);

                // Create and execute the GetTrackRequest
                GetTrackRequest getTrackRequest = spotifyApi.getTrack(trackId).build();
                Track track = getTrackRequest.execute();

                String trackName = track.getName();
                String artistName = track.getArtists()[0].getName();

                // Construct YouTube search query using track name and artist
                String query = URLEncoder.encode(trackName + " " + artistName, "UTF-8");
                String youtubeSearch = "https://www.youtube.com/results?search_query=" + query;

                // Replace args with the YouTube search URL
                args = youtubeSearch;

                // Inform the user about the redirection
                event.reply("🎶 Redirected **" + trackName + "** by **" + artistName + "** to YouTube search.");
            } catch (SpotifyWebApiException e) {
                event.reply("🚫 Spotify API error: " + e.getMessage());
                e.printStackTrace();
                return;
            } catch (IOException e) {
                event.reply("🚫 Network error while accessing Spotify API.");
                e.printStackTrace();
                return;
            } catch (Exception e) {
                event.reply("🚫 Error processing Spotify URL.");
                e.printStackTrace();
                return;
            }
        }

        // Proceed to load the (possibly modified) args
        if (args.isEmpty()) {
            event.reply("🚫 No song specified.");
            return;
        }

        event.reply(loadingEmoji + " Loading... `[" + args + "]`", m ->
                bot.getPlayerManager().loadItemOrdered(event.getGuild(), args, new ResultHandler(m, event, false))
        );
    }

    /**
     * Fetches the access token using Client Credentials Flow.
     */
    private void fetchAccessToken() throws IOException, SpotifyWebApiException {
        ClientCredentialsRequest clientCredentialsRequest = spotifyApi.clientCredentials().build();
        com.wrapper.spotify.model_objects.credentials.ClientCredentials clientCredentials = clientCredentialsRequest.execute();
        accessToken = clientCredentials.getAccessToken();
    }
}

