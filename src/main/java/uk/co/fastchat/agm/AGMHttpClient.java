package uk.co.fastchat.agm;

import org.yamj.api.common.http.DefaultPoolingHttpClient;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by Richard on 12/06/2015.
 *
 */
public class AGMHttpClient extends DefaultPoolingHttpClient {

    @Override
    public String requestContent(String url, Charset charset) throws IOException {

        // Handle local paths as well as remote ones
        if(url.substring(0, 5).equalsIgnoreCase("file:")){
            try {
                Path localXML = Paths.get(new URL(url).toURI());
                byte[] encoded = Files.readAllBytes(localXML);
                return new String(encoded, charset);
            } catch (URISyntaxException e){
                e.printStackTrace();
                return null;
            }
        } else {
            return super.requestContent(url, charset);
        }
    }
}
