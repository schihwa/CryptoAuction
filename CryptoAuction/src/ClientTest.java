import java.rmi.Naming;
import java.rmi.RemoteException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClientTest {

    private UtilityClass.Auction server;
    private PrivateKey privateKey;
    private PublicKey publicKey;

    public ClientTest() throws Exception {
        // Generate RSA key pair for client
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();

        // Locate the RMI server
        server = (UtilityClass.Auction) Naming.lookup("rmi://localhost:1099/Auction");
    }

    public void runTests() throws Exception {
        try {
            int userId = testRegisterUser("testuser@example.com");
            UtilityClass.TokenInfo tokenInfo = testAuthenticate(userId);

            int auctionItemId = testCreateAuction(userId, "Laptop", "High-end gaming laptop", 1000, tokenInfo.getToken());
            testListAuctionItems(userId, tokenInfo.getToken());
            testPlaceBid(userId, auctionItemId, 1200, tokenInfo.getToken());
            testCloseAuction(userId, auctionItemId, tokenInfo.getToken());

            testTokenExpiration(userId, tokenInfo.getToken());
            testUnauthorizedAccess(userId, auctionItemId);

            testMultipleClients();

        } catch (Exception e) {
            System.err.println("FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private int testRegisterUser(String email) throws RemoteException {
        int userId = server.register(email, publicKey);
        System.out.println("Registered user with ID: " + userId);
        if (userId <= 0) throw new AssertionError("FAILED: User ID should be positive.");
        return userId;
    }

    private int testCreateAuction(int userId, String itemName, String description, int reservePrice, String token) throws RemoteException {
        UtilityClass.AuctionSaleItem item = new UtilityClass.AuctionSaleItem(itemName, description, reservePrice);
        int itemId = server.newAuction(userId, item, token);
        System.out.println("Created auction with ID: " + itemId);
        if (itemId <= 0) throw new AssertionError("FAILED: Auction item ID should be positive.");
        return itemId;
    }

    private void testListAuctionItems(int userId, String token) throws RemoteException {
        UtilityClass.AuctionItem[] items = server.listItems(userId, token);
        if (items == null || items.length == 0) throw new AssertionError("FAILED: No auction items found.");
        System.out.println("Listed auction items:");
        for (UtilityClass.AuctionItem item : items) {
            System.out.println(" - " + item.getName() + ": " + item.getHighestBid());
        }
    }

    private void testPlaceBid(int userId, int itemId, int price, String token) throws RemoteException {
        boolean result = server.bid(userId, itemId, price, token);
        if (!result) throw new AssertionError("FAILED: Valid bid should be accepted.");
        System.out.println("Placed bid of " + price + " on item ID " + itemId);
    }

    private void testCloseAuction(int userId, int itemId, String token) throws RemoteException {
        UtilityClass.AuctionResult result = server.closeAuction(userId, itemId, token);
        if (result == null) throw new AssertionError("FAILED: Auction result should not be null.");
        System.out.println("Closed auction with winning price: " + result.getWinningPrice());
    }

    private UtilityClass.TokenInfo testAuthenticate(int userId) throws Exception {
        String clientChallenge = "test-challenge";
        UtilityClass.ChallengeInfo challengeInfo = server.challenge(userId, clientChallenge);
        byte[] signature = UtilityClass.SecurityUtils.sign(challengeInfo.getServerChallenge(), privateKey);
        UtilityClass.TokenInfo tokenInfo = server.authenticate(userId, signature);
        if (tokenInfo.getToken() == null || tokenInfo.getToken().isEmpty()) throw new AssertionError("FAILED: Token should be valid.");
        System.out.println("Authenticated with token: " + tokenInfo.getToken());
        return tokenInfo;
    }

    private void testTokenExpiration(int userId, String token) throws InterruptedException, RemoteException {
        System.out.println("Waiting for token to expire...");
        Thread.sleep(UtilityClass.TokenManager.getTokenExpiryDuration() + 1000); // Wait beyond expiration
        try {
            server.listItems(userId, token);
            throw new AssertionError("FAILED: Expired token should be rejected.");
        } catch (RemoteException e) {
            System.out.println("Expired token rejected as expected.");
        }
    }

    private void testUnauthorizedAccess(int userId, int itemId) throws RemoteException {
        String fakeToken = "invalid-token";
        try {
            server.getSpec(userId, itemId, fakeToken);
            throw new AssertionError("FAILED: Unauthorized access should not be permitted.");
        } catch (RemoteException e) {
            System.out.println("Unauthorized access rejected as expected.");
        }
    }

    private void testMultipleClients() throws Exception {
        List<ClientTest> clients = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            clients.add(new ClientTest());
        }

        for (ClientTest client : clients) {
            int userId = client.testRegisterUser("user" + UUID.randomUUID() + "@example.com");
            UtilityClass.TokenInfo tokenInfo = client.testAuthenticate(userId);
            int itemId = client.testCreateAuction(userId, "Item" + userId, "Description" + userId, 500, tokenInfo.getToken());
            client.testPlaceBid(userId, itemId, 600, tokenInfo.getToken());
            client.testCloseAuction(userId, itemId, tokenInfo.getToken());
        }

        System.out.println("Multi-client test passed.");
    }

    public static void main(String[] args) {
        try {
            ClientTest clientTest = new ClientTest();
            clientTest.runTests();
        } catch (Exception e) {
            System.err.println("FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
