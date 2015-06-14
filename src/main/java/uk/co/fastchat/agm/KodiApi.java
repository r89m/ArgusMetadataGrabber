package uk.co.fastchat.agm;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.utils.URIBuilder;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Richard on 27/12/2014.
 */
public class KodiApi {

    private String host = null;
    private Integer port = null;
    private String username = null;
    private String password = null;

    public KodiApi(String host){

        this(host, null);
    }

    public KodiApi(String host, Integer port){

        this.host = host;
        this.port = port;
    }

    public void setCredentials(String username, String password){

        this.username = username;
        this.password = password;
    }

    public String refreshLibrary(String refreshFolderPath){

        Map<String, String> params = new HashMap<String, String>();
        params.put("directory", refreshFolderPath);

        return makeRequest("VideoLibrary.Scan", params);
    }

    private String makeRequest(String method, Map<String, String> params){

        URL url = buildUrl(method, params);

        if(url != null) {
            try {
                URLConnection con = url.openConnection();

                if(username != null && password != null){
                    // Authorization
                    String userpass = username + ":" + password;
                    String basicAuth = "Basic " + new String(new Base64().encode(userpass.getBytes()));
                    con.setRequestProperty("Authorization", basicAuth);
                }

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                return response.toString();
            } catch (IOException ioe) {
                System.out.println("Request failed: " + ioe.getMessage());
                return "";
            }
        } else {
            System.out.println("URL is invalid");
            return "";
        }
    }

    private URL buildUrl(String method, Map<String, String> params){

        JSONObject RPCRequest = new JSONObject();
        RPCRequest.put("jsonrpc", "2.0");
        RPCRequest.put("id", 1);
        RPCRequest.put("method", method);
        RPCRequest.put("params", params);

        URIBuilder apiUri = new URIBuilder();
        apiUri.setScheme("http");
        apiUri.setHost(host);
        apiUri.setPort(port);
        apiUri.setUserInfo(username, password);
        apiUri.setPath("/jsonrpc");
        apiUri.setParameter("request", RPCRequest.toString());

        try {
            return apiUri.build().toURL();
        } catch (URISyntaxException e){
            e.printStackTrace();
            return null;
        } catch (MalformedURLException me){
            me.printStackTrace();
            return null;
        }
    }
}