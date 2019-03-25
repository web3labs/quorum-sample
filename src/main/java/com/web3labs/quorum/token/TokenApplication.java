package com.web3labs.quorum.token;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.protocol.admin.Admin;
import org.web3j.protocol.admin.methods.response.NewAccountIdentifier;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.quorum.Node;
import org.web3j.quorum.Quorum;
import org.web3j.quorum.tx.ClientTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.exceptions.ContractCallException;
import org.web3j.tx.gas.DefaultGasProvider;

import com.web3labs.token.Token;

/**
 * Demonstration Quorum token application.
 */
public class TokenApplication {

    private static final Logger log = LoggerFactory.getLogger(TokenApplication.class);

    public static void main(String[] args) throws Exception {
        // FIXME: Add node URL and transaction node keys here
        Node nodeA = createAndUnlockAccount("nodeA", "http://<node-url>", "<transaction node key>");
        Node nodeB = createAndUnlockAccount("nodeB", "http://<node-url>", "<transaction node key>");
        Node nodeC = createAndUnlockAccount("nodeC", "http://<node-url>", "<transaction node key>");
        Node nodeZ = createAndUnlockAccount("nodeZ", "http://<node-url>", "<transaction node key>");

        new TokenApplication().run(nodeA, nodeB, nodeC, nodeZ);
    }

    public static Node createAndUnlockAccount(
            String name, String url, String publicKey) throws Exception {

        Admin admin = Admin.build(new HttpService(url));
        String password = createPassword(16);
        NewAccountIdentifier accountId = admin.personalNewAccount(password).send();

        log.info("{} account: {} created with password: {}",
                name, accountId.getAccountId(), password);

        // Unlock account for maximum duration
        admin.personalUnlockAccount(accountId.getAccountId(), password, BigInteger.ZERO).send();
        log.info("{} account {} unlocked", name, accountId.getAccountId());

        return new Node(accountId.getAccountId(), Collections.singletonList(publicKey), url);
    }

    public void run(Node nodeA, Node nodeB, Node nodeC, Node nodeZ) {

        try {
            String tokenName = "Quorum Token";
            String tokenSymbol = "QT";

            // Create token that is private to nodes A, B, C
            Token token = createToken(tokenName, tokenSymbol, 8, nodeA, nodeB, nodeC);

            log.info(
                    "{} ({}) created at contract address {}, by account {}\n",
                    tokenName, tokenSymbol, token.getContractAddress(), nodeA.getAddress());

            // Allocate tokens to nodes B, C and Z
            transferToken(token, nodeB.getAddress(), 100_000);
            transferToken(token, nodeC.getAddress(), 100_000);
            transferToken(token, nodeZ.getAddress(), 50_000);

            logBalances(token, nodeA, nodeB, nodeC, nodeZ);

            // Although Node Z has been allocated tokens, it cannot see this as it is not privy to
            // the underlying smart contract - it wasn't included as a participant
            try {
                log.info("Getting token balances from nodeZ ({})", nodeZ.getUrl());
                getBalanceByNode(token.getContractAddress(), nodeZ);
                throw new Exception("It should not be possible for nodeZ to see it's balance");
            } catch (ContractCallException e) {
                log.info("Exception: NodeZ unable to view its balance as not included in " +
                        "token contract creation\n");
            }

            // Mint additional tokens
            long mintQty = 500_000;
            log.info("Increasing available supply by {}", mintQty);
            increaseTokenSupply(token, mintQty, nodeA.getAddress());

            logBalances(token, nodeA, nodeB, nodeC, nodeZ);

            // Burn tokens
            long burnQty = 499_999;
            log.info("Decreasing available supply by {}", burnQty);
            decreaseTokenSupply(token, burnQty);

            logBalances(token, nodeA, nodeB, nodeC, nodeZ);

        } catch (Exception e) {
            log.error("Error performing operation", e);
        }
    }

    private void logBalances(
            Token token, Node nodeA, Node nodeB, Node nodeC, Node nodeZ) throws Exception {
        log.info("Getting token balances from nodeA ({})", nodeA.getUrl());
        log.info("NodeA balance: {}", token.balanceOf(nodeA.getAddress()).send().longValue());
        log.info("NodeB balance: {}", token.balanceOf(nodeB.getAddress()).send().longValue());
        log.info("NodeC balance: {}", token.balanceOf(nodeC.getAddress()).send().longValue());
        log.info("NodeZ balance: {}\n", token.balanceOf(nodeZ.getAddress()).send().longValue());
    }

    private void logSupply(Token token) throws Exception {
        log.info("Available supply: {}\n", token.totalSupply().send().longValue());
    }

    public Token createToken(
            String tokenName, String symbol, long decimals,
            Node creatorNode, Node... participantNodes) throws Exception {

        Quorum quorum = Quorum.build(new HttpService(creatorNode.getUrl()));

        ClientTransactionManager transactionManager = createTransactionManager(
                quorum, creatorNode, participantNodes);

        return Token.deploy(quorum, transactionManager, new DefaultGasProvider(),
                BigInteger.valueOf(1_000_000), tokenName, symbol, BigInteger.valueOf(decimals))
                .send();
    }

    public TransactionReceipt transferToken(
            Token token, String destinationAddress, long value) throws Exception {

        return token.transfer(
                destinationAddress, BigInteger.valueOf(value))
                .send();
    }

    public TransactionReceipt increaseTokenSupply(
            Token token, long quantity, String destinationAddress) throws Exception {

        return token.mint(
                destinationAddress, BigInteger.valueOf(quantity))
                .send();
    }

    public TransactionReceipt decreaseTokenSupply(
            Token token, long quantity) throws Exception {
        return token.burn(
                BigInteger.valueOf(quantity))
                .send();
    }

    public long getBalance(Token token, String address) throws Exception {
        return token.balanceOf(address).send().longValue();
    }

    public long getBalanceByNode(String contractAddress, Node node) throws Exception {
        Quorum quorum = Quorum.build(new HttpService(node.getUrl()));

        TransactionManager transactionManager = createTransactionManager(quorum, node);
        Token token = Token.load(
                contractAddress, quorum, transactionManager, new DefaultGasProvider());
        return token.balanceOf(node.getAddress()).send().longValue();
    }

    private static ClientTransactionManager createTransactionManager(
            Quorum quorum, Node creatorNode, Node... participantNodes) {

        List<String> publicKeys = Arrays.stream(participantNodes)
                .flatMap(n -> n.getPublicKeys().stream())
                .collect(Collectors.toList());

        return new ClientTransactionManager(
                        quorum,
                        creatorNode.getAddress(),
                        creatorNode.getPublicKeys().get(0),
                        publicKeys);
    }

    // Simple account password generator
    private static final String CHARS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!+<>[]%,(){}.&@^?*$-";
    private static SecureRandom RND = new SecureRandom();

    private static String createPassword(int length){
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(RND.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
