package com.msnos.proxy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

public class CliParams {

    public static class UUIDConverter implements IStringConverter<UUID> {
        @Override
        public UUID convert(String value) {
            return UUID.fromString(value);
        }
    }

    @Parameter
    private List<String> parameters = new ArrayList<String>();

    @Parameter(names = {"--uuid", "-u"}, description = "Specify the uuid of the cloud to be used", converter = UUIDConverter.class)
    private UUID uuid = new UUID(111, 222);

    @Parameter(names = {"--port", "-p"}, description = "The port to be used by the proxy (note: it will bind also port+1)")
    private int port = 9991;

    @Parameter(names = {"--help", "-h"}, description = "Displays this help screen and exits", help = true)
    private boolean help;

    @Parameter(names = {"--timeout", "-t"}, description = "Specify the timeout after which to disconnect idle connections, in seconds (default is 30)")
    private int idleTimeout = 30;

    @Parameter(names = {"--connect", "-c"}, description = "Specify the timeout for connecting to the remote server, in seconds (default is 10)")
    private int connectTimeout = 30;

    public CliParams(String[] args) {
        JCommander jc = new JCommander(this, args);

        if (this.help) {
            jc.usage();
            System.exit(1);
        }
    }

    public List<String> parameters() {
        return parameters;
    }

    public UUID uuid() {
        return uuid;
    }

    public int port() {
        return port;
    }

    public int idleTimeoutInSeconds() {
        return idleTimeout;
    }

    public int connectTimeoutInSeconds() {
        return connectTimeout;
    }
    
    public String usage() {
        // TODO Auto-generated method stub
        return null;
    }
}
