import com.workshare.msnos.core.Cloud;
import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.RestApi;

import java.util.UUID;

public class Main {

    private static int port;

    public static void main(String[] args) throws Exception {
        port = 8881;
        Microservice microservice = new Microservice("Proxy");
        Cloud nimbus = new Cloud(new UUID(111, 222));

        microservice.join(nimbus);

        RestApi restApi = new RestApi("proxy", "test", port);
        microservice.publish(restApi);

        Proxy proxy = new Proxy(microservice);
        proxy.start(port);
    }
}