package uk.co.fastchat.agm;

import com.omertron.thetvdbapi.model.Episode;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Richard on 25/12/2014.
 */
public class Utils {

    private static Pattern numberFindPattern = Pattern.compile("([0-9]+)");

    public static String readFile(String file, String csName)
            throws IOException {
        Charset cs = Charset.forName(csName);
        return readFile(file, cs);
    }

    public static String readFile(String file, Charset cs)
            throws IOException {
        // No real need to close the BufferedReader/InputStreamReader
        // as they're only wrapping the stream
        FileInputStream stream = new FileInputStream(file);
        try {
            Reader reader = new BufferedReader(new InputStreamReader(stream, cs));
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer, 0, buffer.length)) > 0) {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        } finally {
            // Potential issue here: if this throws an IOException,
            // it will mask any others. Normally I'd use a utility
            // method which would log exceptions and swallow them
            stream.close();
        }
    }

    public static boolean downloadFile(String url, File saveFile){

        return downloadFile(url, saveFile.getAbsolutePath());
    }

    public static boolean downloadFile(String url, String savePath){

        try {
            URL website = new URL(url);
            ReadableByteChannel rbc = Channels.newChannel(website.openStream());
            FileOutputStream fos = new FileOutputStream(savePath);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

            return true;
        }
        catch (MalformedURLException urlEx){
            System.err.println("URL appears to be malformed: " + urlEx.getMessage());
        }
        catch (FileNotFoundException fileEx){
            System.err.println("File not found: " + fileEx.getMessage());
        }
        catch (IOException ioEx){
            System.err.println("IO Exception: " + ioEx.getMessage());
        }

        return false;
    }

    public static Map<Episode, Float> sortByComparator(Map<Episode, Float> unsortMap, final boolean order)
    {

        List<Map.Entry<Episode, Float>> list = new LinkedList<Map.Entry<Episode, Float>>(unsortMap.entrySet());

        // Sorting the list based on values
        Collections.sort(list, new Comparator<Map.Entry<Episode, Float>>() {
            public int compare(Map.Entry<Episode, Float> o1, Map.Entry<Episode, Float> o2) {
                if (order) {
                    return o1.getValue().compareTo(o2.getValue());
                } else {
                    return o2.getValue().compareTo(o1.getValue());

                }
            }
        });

        // Maintaining insertion order with the help of LinkedList
        Map<Episode, Float> sortedMap = new LinkedHashMap<Episode, Float>();
        for (Map.Entry<Episode, Float> entry : list)
        {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        return sortedMap;
    }

    public static String replaceNumbersWithWords(String inString){

        NumberToWords.AbstractProcessor processor = new NumberToWords.DefaultProcessor();

        Matcher numbersFound = numberFindPattern.matcher(inString);
        StringBuffer outString = new StringBuffer();

        while(numbersFound.find()) {
            numbersFound.appendReplacement(outString, processor.getName(numbersFound.group(1)));
        }
        numbersFound.appendTail(outString);

        return outString.toString();
    }

    // From: http://stackoverflow.com/a/4746734/1622031
    public static boolean isChildOf(File maybeChild, File possibleParent)
    {
        try {
            final File parent = possibleParent.getCanonicalFile();
            if (!parent.exists() || !parent.isDirectory()) {
                // this cannot possibly be the parent
                return false;
            }

            File child = maybeChild.getCanonicalFile();
            while (child != null) {
                if (child.equals(parent)) {
                    return true;
                }
                child = child.getParentFile();
            }
        } catch (IOException e) {
            // There was an error
            return false;
        }
        // No match found and we've hit the root directory
        return false;
    }

    public static String getRelativePath(File child, File parent){

        try {
            // If child isn't a child of parent return null;
            if (!isChildOf(child, parent)) {
                return null;
            } else {
                return child.getCanonicalPath().substring(parent.getCanonicalPath().length() + 1);
            }
        } catch (IOException e){
            e.printStackTrace();
            return null;
        }
    }

    public static boolean buildDirectoryTree(File bottomLeafDirectory) {

        if (Files.isDirectory(bottomLeafDirectory.toPath())) {
            return true;
        }

        ArrayList<File> leafDirs = new ArrayList<File>();
        File leafDir = bottomLeafDirectory;

        while(!Files.isDirectory(leafDir.toPath())){
            leafDirs.add(leafDir);
            leafDir = leafDir.getParentFile();
        }

        Collections.reverse(leafDirs);

        for (File leaf : leafDirs){
            try {
                Files.createDirectories(leaf.toPath());
            } catch (IOException ioe){
                ioe.printStackTrace();
                return false;
            }
        }

        return true;
    }
}
