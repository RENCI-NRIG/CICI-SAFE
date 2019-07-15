package exoplex.sdx.slice.slicemock;

import com.google.inject.Inject;
import exoplex.sdx.slice.SliceManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.renci.ahab.libndl.Slice;

@Ignore
public class SliceManagerMockTest {

    @Test
    public void testSliceManagerSerialization(){
        SliceManagerMock sliceManagerMock = new SliceManagerMock("test", "~/.ssl/geni-yuanjuny.pem", "~/.ssl/geni-yuanjuny.pem",
            "https://geni.renci.org:11443/orca/xmlrpc", "~/.ssh/id_rsa");
        sliceManagerMock.createSlice();
        sliceManagerMock.addComputeNode("c0");
        sliceManagerMock.addComputeNode("c1");
        sliceManagerMock.addLink("link0", "c0", "c1", 10000000l);
        sliceManagerMock.writeToFile("test");
        SliceManagerMock sliceManagerMock1 = new SliceManagerMock("test", "~/.ssl/geni-yuanjuny" +
            ".pem", "~/.ssl/geni-yuanjuny.pem",
            "https://geni.renci.org:11443/orca/xmlrpc", "~/.ssh/id_rsa");
        sliceManagerMock1.loadFromFile("test");
        sliceManagerMock.printSliceInfo();
        sliceManagerMock1.printSliceInfo();
    }
}
