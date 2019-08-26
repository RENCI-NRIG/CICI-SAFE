package exoplex.sdx.advertise;

import exoplex.sdx.safe.SafeManager;

public class SafeManagerMock extends SafeManager {
    public SafeManagerMock(){
        super("", "", "", true);
    }

    @Override
    public boolean verifyCompliantPath(String srcPid, String srcIP, String destIP, String
        policyToken, String routeToken, String path) {
        return true;
    }
}
