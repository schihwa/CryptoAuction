import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Server extends UnicastRemoteObject implements Auction {

    private final AtomicInteger userCounter = new AtomicInteger(1);
    private final AtomicInteger auctionCounter = new AtomicInteger(1);

    private final Map<Integer, User> users = new ConcurrentHashMap<>();
    private final Map<Integer, AuctionItem> auctionItems = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> auctionOwners = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> highestBidders = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> reservePrices = new ConcurrentHashMap<>();

    private final TokenManager tokenManager = new TokenManager();

    private final PrivateKey serverPrivateKey;
    private final PublicKey serverPublicKey;

    /**
     * Constructs the server with the specified private and public keys.
     */
    public Server(PrivateKey privateKey, PublicKey publicKey) throws RemoteException {
        this.serverPrivateKey = privateKey;
        this.serverPublicKey = publicKey;
    }

    /**
     * Registers a new user with the server.
     */
    @Override
    public int register(String email, PublicKey pkey) {
        int userID = userCounter.getAndIncrement();
        users.put(userID, new User(userID, email, pkey));
        return userID;
    }

    /**
     * Performs the challenge process for authentication.
     */
    @Override
    public ChallengeInfo challenge(int userID, String clientChallenge) {
        User user = users.get(userID);
        if (user == null) return null;

        try {
            byte[] response = SecurityUtils.sign(clientChallenge, serverPrivateKey);
            user.challenge = UUID.randomUUID().toString();

            ChallengeInfo challengeInfo = new ChallengeInfo();
            challengeInfo.response = response;
            challengeInfo.serverChallenge = user.challenge;

            return challengeInfo;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Authenticates a user using their signature.
     */
    @Override
    public TokenInfo authenticate(int userID, byte[] signature) {
        User user = users.get(userID);
        if (user == null) return null;

        try {
            boolean isValid = SecurityUtils.verify(user.challenge, signature, user.publicKey);
            return isValid ? tokenManager.generateToken(userID) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Retrieves the specification of an auction item.
     */
    @Override
    public AuctionItem getSpec(int userID, int itemID, String token) {
        return tokenManager.validate(userID, token) ? auctionItems.get(itemID) : null;
    }

    /**
     * Creates a new auction item.
     */
    @Override
    public int newAuction(int userID, AuctionSaleItem item, String token) {
        if (!tokenManager.validate(userID, token)) return -1;

        int itemID = auctionCounter.getAndIncrement();

        // Create and store the auction item
        AuctionItem auctionItem = new AuctionItem();
        auctionItem.itemID = itemID;
        auctionItem.name = item.name;
        auctionItem.description = item.description;
        auctionItem.highestBid = 0;

        auctionItems.put(itemID, auctionItem);
        auctionOwners.put(itemID, userID);

        // Store the reserve price separately
        reservePrices.put(itemID, item.reservePrice);

        return itemID;
    }

    /**
     * Lists all auction items.
     */
    @Override
    public AuctionItem[] listItems(int userID, String token) {
        return tokenManager.validate(userID, token)
                ? auctionItems.values().toArray(new AuctionItem[0])
                : null;
    }

    /**
     * Closes an auction item.
     */
    @Override
    public AuctionResult closeAuction(int userID, int itemID, String token) {
        if (!tokenManager.validate(userID, token) || !Objects.equals(userID, auctionOwners.get(itemID)))
            return null;

        AuctionItem item = auctionItems.remove(itemID);
        auctionOwners.remove(itemID);
        Integer highestBidderID = highestBidders.remove(itemID);
        Integer reservePrice = reservePrices.remove(itemID);

        if (item == null) return null;

        AuctionResult auctionResult = new AuctionResult();

        // Check if the highest bid meets or exceeds the reserve price
        if (item.highestBid >= (reservePrice != null ? reservePrice : 0) && highestBidderID != null) {
            User winner = users.get(highestBidderID);
            auctionResult.winningEmail = winner.email;
            auctionResult.winningPrice = item.highestBid;
        } else {
            auctionResult.winningEmail = null;
            auctionResult.winningPrice = 0;
        }

        return auctionResult;
    }

    /**
     * Places a bid on an auction item.
     */
    @Override
    public boolean bid(int userID, int itemID, int price, String token) {
        if (!tokenManager.validate(userID, token) || Objects.equals(userID, auctionOwners.get(itemID)))
            return false;

        AuctionItem item = auctionItems.get(itemID);
        if (item == null) return false;

        synchronized (item) {
            if (price > item.highestBid) {
                item.highestBid = price;
                highestBidders.put(itemID, userID);
                return true;
            }
        }
        return false;
    }

    /**
     * Main method to start the server.
     */
    public static void main(String[] args) {
        try {
            String publicKeyPath = "../keys/server_public.key";
            String privateKeyPath = "../privateKeys/server_private.key";

            // Generate keys if they do not exist
            if (!Files.exists(Paths.get(publicKeyPath)) || !Files.exists(Paths.get(privateKeyPath))) {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                KeyPair keyPair = keyGen.generateKeyPair();
                Files.write(Paths.get(publicKeyPath), keyPair.getPublic().getEncoded());
                Files.write(Paths.get(privateKeyPath), keyPair.getPrivate().getEncoded());
            }

            // Load server keys
            PrivateKey privateKey = SecurityUtils.loadPrivateKey(privateKeyPath);
            PublicKey publicKey = SecurityUtils.loadPublicKey(publicKeyPath);

            // Start RMI registry and bind the server
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.rebind("Auction", new Server(privateKey, publicKey));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}