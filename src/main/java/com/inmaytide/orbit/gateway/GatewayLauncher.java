package com.inmaytide.orbit.gateway;

import org.lionsoul.ip2region.xdb.Searcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

@SpringBootApplication(scanBasePackages = {"com.inmaytide.orbit.commons", "com.inmaytide.orbit.gateway"})
public class GatewayLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayLauncher.class);

    public static void main(String[] args) {
        SpringApplication.run(GatewayLauncher.class, args);
    }

    @Bean("ipRegionSearcher")
    public Searcher searcher() throws IOException {
        try {
            String fileAddress = System.getProperty("user.dir") + "/ip2region.xdb";
            byte[] bytes = Searcher.loadContentFromFile(fileAddress);
            return Searcher.newWithBuffer(bytes);
        } catch (Exception e) {
            LOG.error("Failed to create content cached searcher, Cause by: \n", e);
            throw e;
        }
    }


}
