import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Server extends UnicastRemoteObject implements Auction {

    private final AtomicInteger userCounter = new AtomicInteger(1);
    private final AtomicInteger auctionCounter = new AtomicInteger(1);
    private final Map<Integer, User> users = new ConcurrentHashMap<>();
    private final Map<Integer, AuctionItem> auctionItems = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> auctionOwners = new ConcurrentHashMap<>();
    private final TokenManager tokenManager = new TokenManager();
    private final PrivateKey serverPrivateKey;
    @SuppressWarnings("unused")
    private final PublicKey serverPublicKey;

    public Server(PrivateKey privateKey, PublicKey publicKey) throws RemoteException {
        this.serverPrivateKey = privateKey;
        this.serverPublicKey = publicKey;
    }

    @Override
    public int register(String email, PublicKey pkey) {
        int userID = userCounter.getAndIncrement();
        users.put(userID, new User(userID, email, pkey));
        return userID;
    }

    @Override
    public ChallengeInfo challenge(int userID, String clientChallenge) {
        User user = users.get(userID);
        if (user == null) {
            return null; // Return null if user not found
        }
        try {
            // Sign the client’s challenge using the server’s private key
            byte[] response = SecurityUtils.sign(clientChallenge, serverPrivateKey);

            // Generate a new server challenge to send back to the client
            user.challenge = UUID.randomUUID().toString();

            // Prepare the ChallengeInfo with the signed response and server’s own challenge
            ChallengeInfo challengeInfo = new ChallengeInfo();
            challengeInfo.response = response;
            challengeInfo.serverChallenge = user.challenge;
            return challengeInfo;
        } catch (Exception e) {
            return null; // Return null if challenge generation fails
        }
    }

    @Override
    public TokenInfo authenticate(int userID, byte[] signature) {
        User user = users.get(userID);
        if (user == null) {
            return null; // Return null if user not found
        }
        try {
            boolean isValid = SecurityUtils.verify(user.challenge, signature, user.publicKey);
            if (isValid) {
                return tokenManager.generateToken(userID); // Return token if signature is valid
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // Return null if signature verification fails or an exception occurs
    }

    @Override
    public AuctionItem getSpec(int userID, int itemID, String token) {
        if (tokenManager.validate(userID, token)) {
            return auctionItems.get(itemID);
        }
        return null; // Deny access if token is invalid or expired
    }

    @Override
    public int newAuction(int userID, AuctionSaleItem item, String token) {
        if (!tokenManager.validate(userID, token)) {
            return -1; // Deny if token is invalid
        }

        int itemID = auctionCounter.getAndIncrement();
        AuctionItem auctionItem = new AuctionItem();
        auctionItem.itemID = itemID;
        auctionItem.name = item.name;
        auctionItem.description = item.description;
        auctionItem.highestBid = 0;

        auctionItems.put(itemID, auctionItem);
        auctionOwners.put(itemID, userID);
        return itemID;
    }

    @Override
    public AuctionItem[] listItems(int userID, String token) {
        if (tokenManager.validate(userID, token)) {
            return auctionItems.values().toArray(new AuctionItem[0]);
        } else {
            return null; // Deny access if token is invalid or expired
        }
    }

    @Override
    public AuctionResult closeAuction(int userID, int itemID, String token) {
        if (tokenManager.validate(userID, token) && userID == auctionOwners.get(itemID)) {
            AuctionItem item = auctionItems.remove(itemID);

            if (item != null && item.highestBid > 0) {
                AuctionResult auctionResult = new AuctionResult();
                auctionResult.winningEmail = users.get(userID).email;
                auctionResult.winningPrice = item.highestBid;
                return auctionResult;
            }
        }
        return null; // Deny access if token is invalid or user is unauthorized
    }

    @Override
    public boolean bid(int userID, int itemID, int price, String token) {
        if (tokenManager.validate(userID, token)) {
            AuctionItem item = auctionItems.get(itemID);
            if (item != null) {
                synchronized (item) {
                    if (price > item.highestBid) {
                        item.highestBid = price;
                        return true;
                    }
                }
            }
        }
        return false; // Deny access if token is invalid or bid is not higher
    }

    public static void main(String[] args) {
        try {
            String publicKeyPath = "../keys/server_public.key";
            String privateKeyPath = "../privateKeys/server_private.key";

            if (!Files.exists(Paths.get(publicKeyPath)) || !Files.exists(Paths.get(privateKeyPath))) {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                Files.write(Paths.get(publicKeyPath), keyGen.genKeyPair().getPublic().getEncoded());
                Files.write(Paths.get(privateKeyPath), keyGen.genKeyPair().getPrivate().getEncoded());
            }

            PrivateKey privateKey = SecurityUtils.loadPrivateKey(privateKeyPath);
            PublicKey publicKey = SecurityUtils.loadPublicKey(publicKeyPath);

            Registry registry = LocateRegistry.createRegistry(1099);
            registry.rebind("Auction", new Server(privateKey, publicKey));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
