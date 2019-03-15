package com.web3labs.quorum.token;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

/**
 * Simple integration test to demonstrate Token contract.
 */
public class TokenApplicationIT {

    private static final Logger log = LoggerFactory.getLogger(TokenApplicationIT.class);

    @Test
    public void testNodeConnections() throws Exception {
        testConnection("<node-url>");
        // duplicate for multiple nodes ...
    }

    private void testConnection(String nodeUrl) throws Exception {
        Web3j web3j = Web3j.build(new HttpService(nodeUrl));
        log.info(web3j.web3ClientVersion().send().getWeb3ClientVersion());
    }

    @Test
    public void testLifeCycle() throws Exception {
        TokenApplication.main(new String[]{ });
    }
}
