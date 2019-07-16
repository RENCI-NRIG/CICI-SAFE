package aqt;

import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;

public class PrefixUtil {

    static public String normalizePrefix(String prefix){
        SubnetUtils subnetUtils = new SubnetUtils(prefix);
        SubnetInfo subnetInfo = subnetUtils.getInfo();
        return String.format("%s/%s", subnetInfo.getNetworkAddress(),
            subnetInfo.getCidrSignature().split("/")[1]);
    }

    static public Range prefixToRange(String prefix){
        SubnetUtils subnetUtils = new SubnetUtils(prefix);
        SubnetInfo subnetInfo = subnetUtils.getInfo();
        String lowAddr = subnetInfo.getLowAddress();
        int lowAddrInt = subnetInfo.asInteger(lowAddr);
        return new Range(addressToLong(subnetInfo.getLowAddress()) - 1,
            subnetInfo.getAddressCountLong() + 2);
    }

    static public Rectangle prefixPairToRectangle(String prefix1, String prefix2){
        Range range1 = prefixToRange(normalizePrefix(prefix1));
        Range range2 = prefixToRange(normalizePrefix(prefix2));
        return new Rectangle(range1, range2);
    }

    static private long addressToLong(String address){
        long result = 0;
        String[] parts = address.split("\\.");
        for(String s: parts){
            result = result * 256 + Long.valueOf(s);
        }
        return result;
    }
}
