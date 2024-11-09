import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * UtilityClass serves as a container for shared interfaces and classes 
 * used in the distributed auction system.
 */
public class UtilityClass {

    /**
     * Auction interface defines the RMI methods for auction operations.
     */
    public interface Auction extends Remote {
        int register(String email, PublicKey pkey) throws RemoteException;
        ChallengeInfo challenge(int userID, String clientChallenge) throws RemoteException;
        TokenInfo authenticate(int userID, byte[] signature) throws RemoteException;
        AuctionItem getSpec(int userID, int itemID, String token) throws RemoteException;
        int newAuction(int userID, AuctionSaleItem item, String token) throws RemoteException;
        AuctionItem[] listItems(int userID, String token) throws RemoteException;
        AuctionResult closeAuction(int userID, int itemID, String token) throws RemoteException;
        boolean bid(int userID, int itemID, int price, String token) throws RemoteException;
    }

    /**
     * Utility for signing, verifying, and loading private keys.
     */
    public static class SecurityUtils {
        private static final String SIGNING_ALGORITHM = "SHA256withRSA";

        public static byte[] sign(String data, PrivateKey privateKey) throws Exception {
            Signature signature = Signature.getInstance(SIGNING_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(data.getBytes());
            return signature.sign();
        }

        public static boolean verify(String data, byte[] signatureBytes, PublicKey publicKey) throws Exception {
            Signature signature = Signature.getInstance(SIGNING_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(data.getBytes());
            return signature.verify(signatureBytes);
        }

        public static PrivateKey loadPrivateKey(String filePath) throws Exception {
            String key = new String(Files.readAllBytes(Paths.get(filePath)))
                    .replaceAll("-----\\w+ PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(key);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(spec);
        }
    }

    /**
     * Manages user tokens and enforces expiration.
     */
    public static class TokenManager {
        private final Map<Integer, TokenInfo> tokens = new ConcurrentHashMap<>();
        private static final long TOKEN_EXPIRY_DURATION = TimeUnit.SECONDS.toMillis(10);

        public boolean validate(int userID, String token) throws RemoteException {
            TokenInfo tokenInfo = tokens.get(userID);
            if (tokenInfo == null || !Objects.equals(tokenInfo.getToken(), token) || tokenInfo.isExpired()) {
                tokens.remove(userID);
                throw new RemoteException("Token validation failed: Invalid or expired token for user ID: " + userID);
            }
            return true;
        }

        public TokenInfo generateToken(int userID) {
            String token = UUID.randomUUID().toString();
            TokenInfo tokenInfo = new TokenInfo(token, System.currentTimeMillis() + TOKEN_EXPIRY_DURATION);
            tokens.put(userID, tokenInfo);
            return tokenInfo;
        }

        public static long getTokenExpiryDuration() {
            return TOKEN_EXPIRY_DURATION;
        }
    }

    /**
     * ChallengeInfo class holds the server response and challenge for authentication.
     */
    public static class ChallengeInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private final byte[] response; 
        private final String serverChallenge;

        public ChallengeInfo(byte[] response, String serverChallenge) {
            this.response = response;
            this.serverChallenge = serverChallenge;
        }

        public byte[] getResponse() { return response; }
        public String getServerChallenge() { return serverChallenge; }
    }

    /**
     * TokenInfo class holds the token and its expiry time for secure access control.
     */
    public static class TokenInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String token;
        private final long expiryTime;

        public TokenInfo(String token, long expiryTime) {
            this.token = token;
            this.expiryTime = expiryTime;
        }

        public String getToken() { return token; }
        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    /**
     * Represents a registered user in the auction system.
     */
    public static class User implements Serializable {
        private static final long serialVersionUID = 1L;
        private final int userID;
        private final String email;
        private final PublicKey publicKey;
        private String challenge;

        public User(int userID, String email, PublicKey publicKey) {
            this.userID = userID;
            this.email = email;
            this.publicKey = publicKey;
        }

        public int getUserID() { return userID; }
        public String getEmail() { return email; }
        public PublicKey getPublicKey() { return publicKey; }
        public String getChallenge() { return challenge; }
        public void setChallenge(String challenge) { this.challenge = challenge; }
    }

    /**
     * Represents an item listed for auction.
     */
    public static class AuctionItem implements Serializable {
        private static final long serialVersionUID = 1L;
        private final int itemID;
        private final String name;
        private final String description;
        private int highestBid;

        public AuctionItem(int itemID, String name, String description) {
            this.itemID = itemID;
            this.name = name;
            this.description = description;
            this.highestBid = 0;
        }

        public int getItemID() { return itemID; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public int getHighestBid() { return highestBid; }
        public void setHighestBid(int highestBid) { this.highestBid = highestBid; }
    }

    /**
     * Represents the details of an item to be put up for auction.
     */
    public static class AuctionSaleItem implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String name;
        private final String description;
        private final int reservePrice;

        public AuctionSaleItem(String name, String description, int reservePrice) {
            this.name = name;
            this.description = description;
            this.reservePrice = reservePrice;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public int getReservePrice() { return reservePrice; }
    }

    /**
     * Represents the result of a closed auction, including the winner and final bid price.
     */
    public static class AuctionResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String winningEmail;
        private final int winningPrice;

        public AuctionResult(String winningEmail, int winningPrice) {
            this.winningEmail = winningEmail;
            this.winningPrice = winningPrice;
        }

        public String getWinningEmail() { return winningEmail; }
        public int getWinningPrice() { return winningPrice; }
    }
}
