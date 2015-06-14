

import org.junit.Test;

import uk.co.fastchat.agm.Utils;

import javax.rmi.CORBA.Util;
import java.io.File;

import static org.junit.Assert.assertEquals;

/**
 * Created by Richard on 10/06/2015.
 */

public class UtilsTest {



    @Test
    public void shouldReturnRelativePath() throws Exception {

        assertEquals("Work", Utils.getRelativePath(new File("D:\\Dropbox\\Work"), new File("D:\\Dropbox")));
        assertEquals("COALAS", Utils.getRelativePath(new File("D:\\GitHub\\COALAS"), new File("D:\\GitHub\\")));
        assertEquals(null, Utils.getRelativePath(new File("D:\\GitHubb\\COALAS"), new File("D:\\GitHub")));
    }
}