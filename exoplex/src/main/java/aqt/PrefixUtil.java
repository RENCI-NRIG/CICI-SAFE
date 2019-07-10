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
        return new Range(subnetInfo.asInteger(subnetInfo.getLowAddress()) - 1,
            subnetInfo.getAddressCount() + 2);
    }
}
