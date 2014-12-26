package co.uk.fastchat.agm;

import com.omertron.thetvdbapi.model.Episode;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

        return true;
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

    private static String hashFile(File file, String algorithm) throws HashGenerationException{

        return hashFile(file, algorithm, -1);
    }

    private static String hashFile(File file, String algorithm, int bytesToRead) throws HashGenerationException {
        try {
            FileInputStream inputStream = new FileInputStream(file);
            MessageDigest digest = MessageDigest.getInstance(algorithm);

            byte[] bytesBuffer = new byte[1024];
            int bytesRead = -1;
            int totalBytesRead = 0;

            while ((bytesRead = inputStream.read(bytesBuffer)) != -1) {
                digest.update(bytesBuffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                if(bytesToRead > 0 && totalBytesRead >= bytesToRead){
                    break;
                }
            }

            byte[] hashedBytes = digest.digest();

            return convertByteArrayToHexString(hashedBytes);
        } catch (NoSuchAlgorithmException exp) {
            throw new HashGenerationException(
                    "Could not generate hash from file", exp);
        } catch (IOException ex) {
            throw new HashGenerationException(
                    "Could not generate hash from file", ex);
        }
    }

    public static String generateMD5(File file) throws HashGenerationException {

        return hashFile(file, "MD5", 4096);
    }

    public static String generateSHA1(File file) throws HashGenerationException {
        return hashFile(file, "SHA-1");
    }

    public static String generateSHA256(File file) throws HashGenerationException {
        return hashFile(file, "SHA-256");
    }

    private static String convertByteArrayToHexString(byte[] arrayBytes) {
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < arrayBytes.length; i++) {
            stringBuffer.append(Integer.toString((arrayBytes[i] & 0xff) + 0x100, 16)
                    .substring(1));
        }
        return stringBuffer.toString();
    }
}
