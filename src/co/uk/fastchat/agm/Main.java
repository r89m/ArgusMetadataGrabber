package co.uk.fastchat.agm;


import com.omertron.thetvdbapi.TheTVDBApi;
import com.omertron.thetvdbapi.model.Episode;
import com.omertron.thetvdbapi.model.Mirrors;
import com.omertron.thetvdbapi.model.Series;
import org.apache.commons.cli.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import uk.ac.shef.wit.simmetrics.similaritymetrics.Levenshtein;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static TheTVDBApi api;

    private static Options options = null;
    private final static String OPTION_API_KEY = "a";
    private final static String OPTION_FILENAME = "f";
    private final static String OPTION_FORCE_REFRESH = "r";
    private final static String OPTION_CLEAN_DIRECTORY = "c";
    private final static String OPTION_HELP = "h";

    private static String TV_RECORDINGS_ROOT_FOLDER = "D:\\TV Recordings";

    private static int CONFIG_REFRESH_DAYS = 30;
    private static String CONFIG_API_KEY = "";

    private static String TVDB_API_URL = null;
    private final static String TVDB_ID_FILE = "tvdb.id";
    private final static String TVDB_DATA_FILE = "tvdb.data.xml";
    private final static String TVDB_IGNORE_FOLDER = "IGNORE";
    private final static String TVDB_FOLDER_NOT_FOUND = "NOT_FOUND";

    private static Pattern episodeNamePattern = Pattern.compile(".*\\((.*)\\).*");
    private static Pattern episodeFilenamePattern = Pattern.compile("S[0-9]{2}E[0-9]{2}.*");
    private static String EPISODE_FILENAME_PATTERN = "S%02dE%02d - %s.ts";

    public static void main(String[] args) {

        // Create the parser
        CommandLineParser parser = new BasicParser();

        // Create the Parser Options
        options = new Options();
        options.addOption(OptionBuilder.withLongOpt("api key").withDescription("Your TheTVDB API key").hasArg().isRequired().create(OPTION_API_KEY));
        options.addOption(OPTION_FILENAME, "filename", true, "recording filename");
        options.addOption(OptionBuilder.withLongOpt("refresh").withDescription("force refresh of TVDB data if it is older than r days (defaults to 30)").hasArg().withType(Number.class).create(OPTION_FORCE_REFRESH));
        options.addOption(OptionBuilder.withLongOpt("clean").withDescription("clean the directory given by the argument -f. looks for any orphaned symbolic links").create(OPTION_CLEAN_DIRECTORY));
        options.addOption(OPTION_HELP, "help", false, "display this help message");

        try {
            // Parse the command line arguments
            CommandLine line = parser.parse(options, args);

            // Check whether or not the help file has been requested
            if(line.hasOption(OPTION_HELP)){
                displayHelpMessage();
            }

            // Check whether or not we want to clean
            if(line.hasOption(OPTION_CLEAN_DIRECTORY)){
                // If a folder name is given, clean it
                if(line.hasOption(OPTION_FILENAME)) {
                    cleanDirectory(line.getOptionValue(OPTION_FILENAME));
                }
                // Otherwise, clean the whole directory
                else {
                    cleanDirectory(TV_RECORDINGS_ROOT_FOLDER);
                }
                completeRun();
            }

            // Get our API key from the command line
            if(line.hasOption(OPTION_API_KEY)){
                CONFIG_API_KEY = line.getOptionValue(OPTION_API_KEY);
                api = new TheTVDBApi(CONFIG_API_KEY);
                TVDB_API_URL = new Mirrors(CONFIG_API_KEY).getMirror(Mirrors.TYPE_XML);
                TVDB_API_URL += "/api/";
            }

            // Check to see how old data can be
            if(line.hasOption(OPTION_FORCE_REFRESH)){
                CONFIG_REFRESH_DAYS = ((Number)line.getParsedOptionValue(OPTION_FORCE_REFRESH)).intValue();
            }

            // Check whether or not a filename has been supplied
            if(line.hasOption(OPTION_FILENAME)){
                processFile(line.getOptionValue(OPTION_FILENAME));
            }
            // If not, process all folders
            else{
                processAll();
            }
        }
        catch (ParseException exp){
            // Something went wrong
            System.err.println("Parsing failed. Reason: " + exp.getMessage());
            System.exit(1);
        }

        completeRun();
    }

    private static void completeRun(){

        System.out.println("Processing complete");
        System.exit(0);
    }

    private static void processFile(String filename) {

        processFile(new File(filename));
    }

    private static void processFile(File file){

        System.out.println("Processing file: " + file.getName());
        processFolder(file.getParentFile());
    }

    private static void processFolder(String folderPath) {

        processFolder(new File(folderPath));
    }

    private static void processFolder(File folder){

        System.out.println("Processing folder: " + folder.getAbsolutePath());

        if(TV_RECORDINGS_ROOT_FOLDER.equals(folder.getAbsolutePath())){
            File[] shows = folder.listFiles();
            if(shows != null){
                for(File show : shows){
                    if(show.isDirectory()){
                        processFolder(show);
                    }
                }
            }

        } else {

            String tvdbId = getTVDBId(folder);

            // If the folder has a valid TVDB Id associated with it
            if(!(tvdbId == TVDB_IGNORE_FOLDER || tvdbId == TVDB_FOLDER_NOT_FOUND)) {

                // Get all videos and symlinks in this folder
                HashMap<String, File> videos = new HashMap<String, File>();
                ArrayList<String> videoLinks = new ArrayList<String>();

                File[] directoryListing = folder.listFiles();

                if (directoryListing != null) {
                    for (File child : directoryListing) {
                        String ext = child.getPath().substring(child.getPath().lastIndexOf('.') + 1);

                        // Process subdirectories
                        if (child.isDirectory()) {
                            processFolder(child);
                        }
                        // Check whether or not the child is named like a link
                        else if (episodeFilenamePattern.matcher(child.getName()).matches()) {
                            // Record the hash of the file it points to
                            try{
                                videoLinks.add(Utils.generateMD5(child));
                            } catch (HashGenerationException ex){}
                        }
                        // Detect recordings from Argus
                        else if ("ts".equalsIgnoreCase(ext)) {
                            // Record the filename
                            try {
                                videos.put(Utils.generateMD5(child), child);
                            } catch (HashGenerationException ex){}
                        }
                    }
                }

                // Filter out all video files that have already been linked to
                for (String target : videoLinks) {
                    videos.remove(target);
                }

                // Update the series xml file if it is too old or doesn't exist
                File episodesDataFile = new File(folder, TVDB_DATA_FILE);

                if (!episodesDataFile.exists() || (System.currentTimeMillis() - episodesDataFile.lastModified()) > (CONFIG_REFRESH_DAYS * 1000 * 60 * 60 * 24)) {

                    StringBuilder allSeriesEpsBuilder = new StringBuilder();
                    allSeriesEpsBuilder.append(TVDB_API_URL);
                    allSeriesEpsBuilder.append(CONFIG_API_KEY);
                    allSeriesEpsBuilder.append("/series/");
                    allSeriesEpsBuilder.append(tvdbId);
                    allSeriesEpsBuilder.append("/all/en.xml");

                    Utils.downloadFile(allSeriesEpsBuilder.toString(), episodesDataFile);
                }

                // Get an array of all the shows episodes
                ArrayList<Episode> episodes = new ArrayList<Episode>();

                try {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    Document doc = db.parse(episodesDataFile);
                    NodeList eps = doc.getElementsByTagName("Episode");

                    for (int i = 0; i < eps.getLength(); i++) {
                        Node ep = eps.item(i);
                        Episode tvdbEp = new Episode();

                        NodeList props = ep.getChildNodes();

                        for (int j = 0; j < props.getLength(); j++) {
                            Node node = props.item(j);
                            String nodeName = node.getNodeName();
                            if ("EpisodeName".equals(nodeName)) {
                                tvdbEp.setEpisodeName(node.getTextContent());
                            } else if ("SeasonNumber".equals(nodeName)) {
                                try {
                                    tvdbEp.setSeasonNumber(Integer.parseInt(node.getTextContent()));
                                } catch (NumberFormatException ex) {
                                }
                            } else if ("EpisodeNumber".equals(nodeName)) {
                                try {
                                    tvdbEp.setEpisodeNumber(Integer.parseInt(node.getTextContent()));
                                } catch (NumberFormatException ex) {
                                }
                            }
                        }
                        episodes.add(tvdbEp);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println(e.getMessage());
                }

                // Iterate all the files that are left and search for the episode
                for (File video : videos.values()) {
                    processEpisode(episodes, video);
                }
            }
        }
    }

    private static void processEpisode(List<Episode> episodeList, File episodeVideo){

        Matcher videoEpisodeNameMatcher = episodeNamePattern.matcher(episodeVideo.getName());
        String videoEpisodeName = null;

        if(videoEpisodeNameMatcher.matches()) {
            System.out.println("Matched");
            videoEpisodeName = videoEpisodeNameMatcher.group(1);
            videoEpisodeName = videoEpisodeName.substring(0, videoEpisodeName.lastIndexOf(" - "));
        }

        System.out.println(videoEpisodeName);

        Levenshtein stringComparator = new Levenshtein();
        Map<Episode, Float> episodeNameScores = new HashMap<Episode, Float>();

        if(videoEpisodeName != null){
            for(Episode ep : episodeList){
                if(ep != null && ep.getEpisodeName() != null) {
                    Float episodeMaxScore;
                    Float episodeScore;

                    // Compare the raw episode name and filename
                    episodeMaxScore = stringComparator.getSimilarity(videoEpisodeName, ep.getEpisodeName());

                    // Convert all numbers in episode name and filename to strings
                    episodeScore = stringComparator.getSimilarity(Utils.replaceNumbersWithWords(videoEpisodeName), Utils.replaceNumbersWithWords(ep.getEpisodeName()));
                    episodeMaxScore = Math.max(episodeMaxScore, episodeScore);

                    episodeNameScores.put(ep, episodeMaxScore);
                }
            }
        }
        // Sort list of episode scores
        Map<Episode, Float> sortedScores = Utils.sortByComparator(episodeNameScores, false);

        Episode bestMatchedEpisode = null;
        for(Episode ep : sortedScores.keySet()){
            bestMatchedEpisode = ep;
            break;
        }

        if(bestMatchedEpisode != null) {
            System.out.println("Best matched episode name for: " + episodeVideo.getName());
            System.out.println(bestMatchedEpisode.getEpisodeName());
            String episodeFilename = String.format(EPISODE_FILENAME_PATTERN, bestMatchedEpisode.getSeasonNumber(), bestMatchedEpisode.getEpisodeNumber(), bestMatchedEpisode.getEpisodeName());
            File episodeLink = new File(episodeVideo.getParentFile(), episodeFilename);
            try {
                Files.createLink(episodeLink.toPath(), episodeVideo.toPath());
            }
            catch (IOException ex){
                System.err.println("Symbolic link could not be created");
                System.exit(1);
            }
        } else {
            System.out.println("Best match not found for: " + episodeVideo.getName());
        }

    }

    private static void processAll(){

        processFolder(TV_RECORDINGS_ROOT_FOLDER);

    }

    private static void cleanDirectory(String folderPath){

        cleanDirectory(new File(folderPath));
    }

    private static void cleanDirectory(File folderToClean){

        System.out.println("Clean Directory: " + folderToClean.getName());

        File[] directoryContents = folderToClean.listFiles();

        if(directoryContents != null){
            for(File child : directoryContents){
                System.out.println(child.toPath());
                try {
                    System.out.println(child.getCanonicalPath());
                } catch (IOException ex){}
                if(child.isDirectory()){
                    cleanDirectory(child);
                } else if(Files.isSymbolicLink(child.toPath())){
                    System.out.println("Checking: " + child.toPath());
                    try {
                        if (!Files.readSymbolicLink(child.toPath()).toFile().exists()) {
                            System.out.println("Link Orphaned. Deleting: " + child.getName());
                            child.delete();
                        }
                    } catch (IOException ex){}
                }
            }
        }
    }

    private static String getTVDBId(File folder){

        // Get the TVDB Id from the file, if it exists. If not, search for it on the TVDB
        File tvdbIdFile = new File(folder, TVDB_ID_FILE);
        String tvdbId;
        if(tvdbIdFile.exists()){
            try{
                tvdbId = Utils.readFile(tvdbIdFile.getPath(), StandardCharsets.UTF_8);
            }
            catch (IOException exp){
                tvdbId = TVDB_IGNORE_FOLDER;
            }
        }

        // If the TVDB file doesn't exist, try to search for the show
        else {
            tvdbId = searchForShowId(folder, folder.getName());
        }

        return tvdbId;
    }

    private static String searchForShowId(File folder, String seriesName){

        String tvdbId;
        // Search for the series
        List<Series> results = api.searchSeries(seriesName, null);

        if(results != null && !results.isEmpty()){
            tvdbId = results.get(0).getId();
        } else {
            tvdbId = TVDB_FOLDER_NOT_FOUND;
        }

        // Save the TVDB Id to the folder
        try{
            File tvdbIdFile = new File(folder, TVDB_ID_FILE);
            if(!tvdbIdFile.exists()){
                tvdbIdFile.createNewFile();
            }
            System.out.println("TVDB ID: " + tvdbId);
            BufferedWriter out = new BufferedWriter(new FileWriter(tvdbIdFile.getAbsoluteFile()));
            out.write(tvdbId);
            out.close();
        }
        catch (IOException exp){
            System.err.println("TVDB Id could not be written in folder: " + exp.getMessage());
        }
        return tvdbId;
    }



    private static void displayHelpMessage(){

        System.out.println("Argus Metadata Grabber (AGM) Help");
        System.out.println("=================================");
        System.out.println("agm.jar -[fh]");

        if(options != null){
            Collection<Option> opts = options.getOptions();
            for(Option opt : opts){
                System.out.println("    -" + opt.getOpt() + " " + opt.getLongOpt() + " - " + opt.getDescription());
            }
        }

        System.exit(0);
    }
}
