import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.Naming;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

public class ClientTest {

    private static Auction server;
    private PrivateKey clientPrivateKey;
    private PublicKey clientPublicKey;
    @SuppressWarnings("unused")
    private PublicKey serverPublicKey;
    private int userId;
    private String token;

    public ClientTest() throws Exception {
        generateClientKeyPair();
        loadServerPublicKey();
        connectToServer();
        registerUser();
    }

    private void generateClientKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        this.clientPrivateKey = keyPair.getPrivate();
        this.clientPublicKey = keyPair.getPublic();
    }

    private void loadServerPublicKey() throws Exception {
        byte[] keyBytes = Files.readAllBytes(Paths.get("../keys/server_public.key"));
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        this.serverPublicKey = KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private void connectToServer() throws Exception {
        server = (Auction) Naming.lookup("rmi://localhost:1099/Auction");
    }

    private void registerUser() {
        try {
            userId = server.register("test@example.com", clientPublicKey);
            System.out.println(userId > 0 ? "✔️ Registered successfully." : "❌ Registration failed.");
        } catch (Exception e) {
            System.err.println("Registration error: " + e.getMessage());
        }
    }

    private void authenticate() {
        try {
            ChallengeInfo challenge = server.challenge(userId, "test-challenge");
            if (challenge == null) return;
            byte[] signature = SecurityUtils.sign(challenge.serverChallenge, clientPrivateKey);
            TokenInfo tokenInfo = server.authenticate(userId, signature);
            token = tokenInfo != null ? tokenInfo.token : null;
        } catch (Exception e) {
            System.err.println("Authentication error: " + e.getMessage());
        }
    }

    private void validateSingleUseToken() {
        System.out.println("---- Test: Single-Use Token ----");
        authenticate();
        try {
            System.out.println(server.listItems(userId, token) != null ? "✔️ Token valid on first use." : "❌ First use failed.");
            System.out.println(server.listItems(userId, token) == null ? "✔️ Token invalid on second use." : "❌ Second use allowed.");
        } catch (Exception e) {
            System.err.println("Single-Use Token error: " + e.getMessage());
        }
    }

    private void validateTokenExpiry() {
        System.out.println("---- Test: Token Expiry and New Token Generation ----");
        authenticate();
        try {
            // Wait for the token to expire
            Thread.sleep(10000);
            
            // Check if the expired token is denied
            System.out.println(server.listItems(userId, token) == null ? "✔️ Expired token denied." : "❌ Expired token granted access.");
            
            // Attempt to authenticate again and generate a new token
            authenticate();
            System.out.println(server.listItems(userId, token) != null ? "✔️ New token valid after expiry." : "❌ New token denied after expiry.");
        } catch (Exception e) {
            System.err.println("Token Expiry error: " + e.getMessage());
        }
    }

    private void validateAccessControl() {
        System.out.println("---- Test: Access Control ----");
        authenticate();
        try {
            System.out.println(server.listItems(userId, token) != null ? "✔️ Valid token allowed." : "❌ Valid token denied.");
            System.out.println(server.listItems(userId, "invalid-token") == null ? "✔️ Invalid token denied." : "❌ Invalid token allowed.");
            System.out.println(server.listItems(userId, null) == null ? "✔️ No token denied." : "❌ No token allowed.");
        } catch (Exception e) {
            System.err.println("Access Control error: " + e.getMessage());
        }
    }

    public void runTests() {
        validateSingleUseToken();
        validateTokenExpiry(); // Now includes testing new token generation after expiry
        validateAccessControl();
    }

    public static void main(String[] args) {
        try {
            ClientTest clientTest = new ClientTest();
            clientTest.runTests();
        } catch (Exception e) {
            System.err.println("Initialization Error: " + e.getMessage());
        }
    }
}
