package uk.co.fastchat.agm;


import com.omertron.thetvdbapi.TheTVDBApi;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.nio.file.Files;

public class ArgusMediaGrabber {

    private static ArgusMediaGrabber instance = null;

    private static TheTVDBApi apiInstance;

    public final static String TVDB_ID_FILE = "tvdb.id";
    public final static String TVDB_DATA_FILE = "tvdb.data.xml";
    public final static String TVDB_IGNORE_FOLDER = "IGNORE";
    public final static String TVDB_ERROR_READING_ID = "ERROR";
    public final static String TVDB_SERIES_NOT_FOUND = "NOT_FOUND";

    //TODO: Create ini file (using ini4j)
    //      + this will store an API key (can be overridden from command line)
    //      + output directory
    //      + more...?

    public static void main(String[] args) {

        // Create an instance of the metadata grabber
        ArgusMediaGrabber grabber = getInstance();
        // Register it's command line arguments
        CmdLineParser parser = new CmdLineParser(grabber);

        try{
            // Parse the command line arguments - if that worked, start the grabber
            parser.parseArgument(args);
            grabber.start();
        } catch (CmdLineException e){
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
        }
    }

    public static TheTVDBApi getAPIInstance(){

        if(apiInstance == null){
            apiInstance = new TheTVDBApi(getInstance().apiKey, new AGMHttpClient());
        }

        return apiInstance;
    }

    public static String getApiUrl(){

        return getAPIInstance().getXmlMirror(null) + getInstance().apiKey + "/";
    }

    public static ArgusMediaGrabber getInstance(){

        if(instance == null){
            instance = new ArgusMediaGrabber();
        }

        return instance;
    }


    // Command line arguments
    @Option(name="--api-key", usage="Your TVDB API Key", required=true)
    public String apiKey = "";
    @Option(name="--target", usage="The file or folder you would like to work on", required=true)
    public File targetFile = null;
    @Option(name="--source-folder", usage="The folder in which all original recordings are stored")
    public File baseFolder = new File("D:\\TV Recordings");
    @Option(name="--output-folder", usage="The folder into which all links should be created. This will be the folder that Kodi is looking at")
    public File outputFolder = new File("D:\\TV Recordings for Kodi");
    @Option(name="--refresh", usage="Force all TVDB data within the target folder to be refreshed")
    public boolean refreshData = false;
    @Option(name="--max-age", usage="Maximum age (in days) for TVDB data - automatically refresh it if it is older than this")
    public int maxAgeDays = 30;
    @Option(name="--clean", usage="Cleanup the target directory")
    public boolean cleanDirectory = false;
    // Not yet used
    //@Option(name="--config", usage="Define a path to a config.ini file")
    //public File iniPath = new File("");

    @Option(name="--kodi-host", usage="Kodi API host")
    public String kodiHost = "localhost";
    @Option(name="--kodi-port", usage="Kodi API port")
    public int kodiPort = 8080;
    @Option(name="--kodi-username", usage="Kodi API username")
    public String kodiUsername = "kodi";
    @Option(name="--kodi-password", usage="Kodi API password")
    public String kodiPassword = "kodi";


    public void start(){

        // Check that the given target exists
        if(!Files.exists(targetFile.toPath())){
            System.err.println("The target file doesn't exist");
            System.exit(1);
            return;
        }

        // Check that the given target is within either the source or output directories
        if(! (Utils.isChildOf(targetFile, baseFolder) || Utils.isChildOf(targetFile, outputFolder))){
            System.err.println("The target file must reside within either the source folder or output folder");
            System.exit(1);
            return;
        }

        ArgusObject target = new ArgusObject(targetFile);

        if(cleanDirectory){
            target.cleanFolder();
        } else {
            if(refreshData){
                target.reloadSeasonData();
            }
            target.process();
        }

        // Refresh the Kodi database
        KodiApi kodiApi = new KodiApi(kodiHost, kodiPort);
        kodiApi.setCredentials(kodiUsername, kodiPassword);
        kodiApi.refreshLibrary(target.getOutputDirectory().toString());
        System.out.println("Kodi Video Library Scan Initiated for: " + target.getOutputDirectory().toString());
        System.out.println("Processing complete");
    }
}
