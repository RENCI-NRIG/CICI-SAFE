package exoplex.sdx.network;

import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class ReactiveFlowTest {

    /**
     * sudo mn --controller=remote,ip=127.0.0.1,port=6633 --topo linear,2
     * h1 ifconfig h1-eth0 10.0.1.2/24
     * h2 ifconfig h2-eth0 10.0.2.2/24
     * h1 ifconfig h1-eth0 10.0.1.2/24
     * h1 ip route add 10.0.2.0/24 via 10.0.1.1
     * h2 ip route add 10.0.1.0/24 via 10.0.2.1
     */
    @Test
    public void testReactiveFlow(){
        RoutingManager routingManager = new RoutingManager();
        String controller = "152.3.137.55:8080";
        String manageIP = "152.3.137.55";
        routingManager.newRouter("s1", "0000000000000001", manageIP);
        routingManager.newRouter("s2", "0000000000000002", manageIP);
        routingManager.newExternalLink("elink0", "10.0.1.1/24", "s1",
            "10.0.0.2", controller);
        routingManager.newExternalLink("elink1", "10.0.2.1/24", "s2",
            "10.0.1.2", controller);
        routingManager.newInternalLink("link0", "10.0.3.1/24", "s1",
            "10.0.3.2/24", "s2", controller, 10);
        routingManager.monitorOnAllRouter("10.0.2.0/24", SdnUtil.DEFAULT_ROUTE,
          controller);
    }
}
