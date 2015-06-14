package uk.co.fastchat.agm;

import com.omertron.thetvdbapi.TheTVDBApi;
import com.omertron.thetvdbapi.model.Episode;
import com.omertron.thetvdbapi.model.Series;
import com.omertron.thetvdbapi.tools.TvdbParser;
import uk.ac.shef.wit.simmetrics.similaritymetrics.Levenshtein;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Richard on 30/12/2014.
 */
public class ArgusObject {

    private static Pattern episodeNamePattern = Pattern.compile(".*\\((.*)\\).*");
    private static String EPISODE_FILENAME_PATTERN = "S%02dE%02d - %s.ts";

    private String tvdbId = null;
    private File target = null;
    private ArrayList<Episode> episodes;

    private File sourceFile = null;
    private File outputFile = null;

    public ArgusObject(File target){

        this.target = target;
    }

    public File getSourceFile(){

        if(sourceFile == null) {
            if(Utils.isChildOf(target, ArgusMediaGrabber.getInstance().baseFolder)){
                sourceFile = target;
            } else {
                String relativePath = Utils.getRelativePath(target, ArgusMediaGrabber.getInstance().outputFolder);
                if(relativePath != null){
                    sourceFile = new File(ArgusMediaGrabber.getInstance().baseFolder, relativePath);
                } else {
                    sourceFile = null;
                }
            }
        }

        return sourceFile;
    }

    public File getOutputFile(){

        if(outputFile == null) {
            if(Utils.isChildOf(target, ArgusMediaGrabber.getInstance().outputFolder)){
                outputFile = target;
            } else {
                String relativePath = Utils.getRelativePath(target, ArgusMediaGrabber.getInstance().baseFolder);
                if(relativePath != null){
                    outputFile =  new File(ArgusMediaGrabber.getInstance().outputFolder, relativePath);
                } else {
                    outputFile = null;
                }
            }
        }

        return outputFile;
    }

    public File getSourceDirectory(){

        File dir = getSourceFile();
        if(dir.isDirectory()){
            return dir;
        } else {
            return dir.getParentFile();
        }
    }

    public File getOutputDirectory(){

        File dir = getOutputFile();
        if(dir.isDirectory()){
            return dir;
        } else {
            return dir.getParentFile();
        }
    }

    public File getTVDBIdFile(){

        return new File(getOutputDirectory(), ArgusMediaGrabber.TVDB_ID_FILE);
    }

    public File getTVDBDataFile(){

        return new File(getOutputDirectory(), ArgusMediaGrabber.TVDB_DATA_FILE);
    }

    public String getTVDBId(){

        // Get the TVDB Id from the file, if it exists. If not, search for it on the TVDB
        File tvdbIdFile = getTVDBIdFile();

        if(tvdbIdFile.exists()){
            try{
                tvdbId = Utils.readFile(tvdbIdFile.getPath(), StandardCharsets.UTF_8);
            }
            catch (IOException exp){
                exp.printStackTrace();
                tvdbId = ArgusMediaGrabber.TVDB_ERROR_READING_ID;
            }
        }

        // If the TVDB file doesn't exist, try to search for the show
        else {
            tvdbId = searchForShowId();
        }

        return tvdbId;
    }

    private String searchForShowId(){

        // Search for the series
        List<Series> results = ArgusMediaGrabber.getAPIInstance().searchSeries(getSourceDirectory().getName(), null);

        if(results != null && !results.isEmpty()){
            tvdbId = results.get(0).getId();
        } else {
            tvdbId = ArgusMediaGrabber.TVDB_SERIES_NOT_FOUND;
        }

        // Save the TVDB Id to the baseFolder
        try{
            File tvdbIdFile = getTVDBIdFile();
            if(!tvdbIdFile.exists()){
                tvdbIdFile.createNewFile();
            }
            System.out.println("TVDB ID for '" + getOutputDirectory().getName() + "': " + tvdbId);
            BufferedWriter out = new BufferedWriter(new FileWriter(tvdbIdFile.getAbsoluteFile()));
            out.write(tvdbId);
            out.close();
        }
        catch (IOException exp){
            System.err.println("TVDB Id could not be written in folder: " + exp.getMessage());
        }
        return tvdbId;
    }

    private List<Episode> getEpisodeList() {

        // Build the path to the episodes data file
        File episodesDataFile = getTVDBDataFile();

        long maxAgeMillis = (ArgusMediaGrabber.getInstance().maxAgeDays * 1000L * 60 * 60 * 24);

        // Update the series xml file if it is too old or doesn't exist
        if (!episodesDataFile.exists() ||
                (System.currentTimeMillis() - episodesDataFile.lastModified()) > maxAgeMillis) {
            reloadSeasonData();
        }

        return TvdbParser.getAllEpisodes(episodesDataFile.toURI().toString(), -1, ArgusMediaGrabber.getAPIInstance().getBannerMirror(""));
    }


    public boolean reloadSeasonData(){

        System.out.println("Refresing data from the TVDB for: " + getOutputDirectory().getName());

        StringBuilder allSeriesEpsBuilder = new StringBuilder();
        allSeriesEpsBuilder.append(TheTVDBApi.getXmlMirror(null));
        allSeriesEpsBuilder.append(ArgusMediaGrabber.getInstance().apiKey);
        allSeriesEpsBuilder.append("/series/");
        allSeriesEpsBuilder.append(getTVDBId());
        allSeriesEpsBuilder.append("/all/en.xml");

        return Utils.downloadFile(allSeriesEpsBuilder.toString(), getTVDBDataFile());
    }

    public void process(){


        // Ensure output folder exists
        Utils.buildDirectoryTree(getOutputDirectory());

        if(Files.isDirectory(target.toPath())){
            processFolder();
        } else {
            processEpisode(getEpisodeList());
        }

    }

    public void processFolder() {

        // If the given folder is the source folder, iterate over all sub folders and process them
        if(ArgusMediaGrabber.getInstance().baseFolder.getAbsolutePath().equals(target.getAbsolutePath())){
            File[] shows = target.listFiles();
            if(shows != null){
                for(File show : shows){
                    if(show.isDirectory()){
                        new ArgusObject(show).processFolder();
                    }
                }
            }
        } else {
            // Ensure output folder exists
            //Utils.buildDirectoryTree(getOutputDirectory());

            // Process the folder
            String tvdbId = getTVDBId();

            // If the target folder has a valid TVDB Id associated with it
            if (!(tvdbId.equals(ArgusMediaGrabber.TVDB_IGNORE_FOLDER) || tvdbId.equals(ArgusMediaGrabber.TVDB_SERIES_NOT_FOUND))) {

                List<Episode> episodes = getEpisodeList();

                File[] directoryListing = getSourceDirectory().listFiles();
                if (directoryListing != null) {
                    for (File child : directoryListing) {
                        String ext = child.getPath().substring(child.getPath().lastIndexOf('.') + 1);

                        // Process subdirectories
                        if (child.isDirectory()) {
                            ArgusObject childFolder = new ArgusObject(child);
                            childFolder.processFolder();
                        }
                        // Detect recordings from Argus
                        else if ("ts".equalsIgnoreCase(ext)) {
                            // Record the filename
                            new ArgusObject(child).processEpisode(episodes);
                        }
                    }
                }
            }
        }
    }

    private void processEpisode(List<Episode> episodeList){

        Matcher videoEpisodeNameMatcher = episodeNamePattern.matcher(getSourceFile().getName());
        String videoEpisodeName = null;

        if(videoEpisodeNameMatcher.matches()) {
            videoEpisodeName = videoEpisodeNameMatcher.group(1);
            int nameDashPosition = videoEpisodeName.lastIndexOf(" - ");
            if(nameDashPosition > -1) {
                videoEpisodeName = videoEpisodeName.substring(0, nameDashPosition);
            }
        }

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

        File episodeLink;

        if(bestMatchedEpisode != null) {
            System.out.print("Best matched episode name for:         " + getSourceFile().getName() + " is:   ");
            System.out.println(bestMatchedEpisode.getEpisodeName());
            String episodeFilename = String.format(EPISODE_FILENAME_PATTERN, bestMatchedEpisode.getSeasonNumber(), bestMatchedEpisode.getEpisodeNumber(), bestMatchedEpisode.getEpisodeName());
            episodeLink = new File(getOutputDirectory(), episodeFilename);
        } else {
            System.out.println("Best match episode name not found for: " + getSourceFile().getName());
            episodeLink = new File(getOutputDirectory(), getSourceFile().getName());
        }

        try {

            File episodeComSkipFile = new File(getSourceFile().getAbsolutePath().replace(".ts", ".txt"));
            File episodeComSkipLink = new File(episodeLink.getAbsolutePath().replace(".ts", ".txt"));
            // Remove and recreate the link
            episodeLink.delete();
            episodeComSkipLink.delete();
            Files.createLink(episodeLink.toPath(), getSourceFile().toPath());
            System.out.println("        Video Link Created");
            // If the ComSkip file exists, create a link to it
            if(episodeComSkipFile.exists()) {
                Files.createLink(episodeComSkipLink.toPath(), episodeComSkipFile.toPath());
                System.out.println("        ComSkip Link Created");
            }
        }
        catch (IOException ex){
            ex.printStackTrace();
            System.err.println("Symbolic link could not be created");
            System.exit(1);
        }

    }

    public void cleanFolder(){

        System.out.println("Clean Directory: " + getOutputDirectory().getName());
        System.out.println("    Deleting all links...");

        File[] directoryContents = getOutputDirectory().listFiles();

        if(directoryContents != null){
            for(File child : directoryContents){
                if(Files.isDirectory(child.toPath())){
                    new ArgusObject(child).cleanFolder();
                }
                if(child.getName().substring(Math.max(child.getName().length() - 3, 0)).equals(".ts")) {
                    try {
                        Files.delete(child.toPath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        System.out.println("    Recreating links");
        processFolder();
    }
}
