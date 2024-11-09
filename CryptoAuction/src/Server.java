import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Server extends UnicastRemoteObject implements UtilityClass.Auction {

    private final AtomicInteger userCounter = new AtomicInteger(1);
    private final AtomicInteger auctionCounter = new AtomicInteger(1);
    private final Map<Integer, UtilityClass.User> users = new ConcurrentHashMap<>();
    private final Map<Integer, UtilityClass.AuctionItem> auctionItems = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> auctionOwners = new ConcurrentHashMap<>();
    private final UtilityClass.TokenManager tokenManager = new UtilityClass.TokenManager();
    private final PrivateKey serverPrivateKey;

    public Server(PrivateKey privateKey) throws RemoteException {
        this.serverPrivateKey = privateKey;
    }

    @Override
    public int register(String email, PublicKey pkey) {
        int userID = userCounter.getAndIncrement();
        users.put(userID, new UtilityClass.User(userID, email, pkey));
        return userID;
    }

    @Override
    public UtilityClass.ChallengeInfo challenge(int userID, String clientChallenge) throws RemoteException {
        UtilityClass.User user = Optional.ofNullable(users.get(userID))
            .orElseThrow(() -> new RemoteException("User not found for ID: " + userID));
        try {
            byte[] serverResponse = UtilityClass.SecurityUtils.sign(clientChallenge, serverPrivateKey);
            user.setChallenge(UUID.randomUUID().toString());
            return new UtilityClass.ChallengeInfo(serverResponse, user.getChallenge());
        } catch (Exception e) {
            throw new RemoteException("Error generating challenge response: " + e.getMessage(), e);
        }
    }

    @Override
    public UtilityClass.TokenInfo authenticate(int userID, byte[] signature) throws RemoteException {
        UtilityClass.User user = Optional.ofNullable(users.get(userID))
            .filter(u -> u.getChallenge() != null)
            .orElseThrow(() -> new RemoteException("Invalid authentication attempt for user ID: " + userID));
        try {
            if (!UtilityClass.SecurityUtils.verify(user.getChallenge(), signature, user.getPublicKey())) {
                throw new RemoteException("Authentication failed for user ID: " + userID);
            }
            user.setChallenge(null);
            return tokenManager.generateToken(userID);
        } catch (Exception e) {
            throw new RemoteException("Authentication error: " + e.getMessage(), e);
        }
    }

    @Override
    public UtilityClass.AuctionItem getSpec(int userID, int itemID, String token) throws RemoteException {
        tokenManager.validate(userID, token);
        return Optional.ofNullable(auctionItems.get(itemID))
            .orElseThrow(() -> new RemoteException("Item not found for ID: " + itemID));
    }

    @Override
    public int newAuction(int userID, UtilityClass.AuctionSaleItem item, String token) throws RemoteException {
        tokenManager.validate(userID, token);
        int itemID = auctionCounter.getAndIncrement();
        auctionItems.put(itemID, new UtilityClass.AuctionItem(itemID, item.getName(), item.getDescription()));
        auctionOwners.put(itemID, userID);
        return itemID;
    }

    @Override
    public UtilityClass.AuctionItem[] listItems(int userID, String token) throws RemoteException {
        tokenManager.validate(userID, token);
        return auctionItems.values().toArray(UtilityClass.AuctionItem[]::new);
    }

    @Override
    public UtilityClass.AuctionResult closeAuction(int userID, int itemID, String token) throws RemoteException {
        tokenManager.validate(userID, token);
        if (!Objects.equals(auctionOwners.get(itemID), userID)) {
            throw new RemoteException("Unauthorized action by user ID: " + userID + " for item ID: " + itemID);
        }
        return Optional.ofNullable(auctionItems.remove(itemID))
            .filter(item -> item.getHighestBid() > 0)
            .map(item -> new UtilityClass.AuctionResult(users.get(userID).getEmail(), item.getHighestBid()))
            .orElse(new UtilityClass.AuctionResult(null, 0));
    }

    @Override
    public boolean bid(int userID, int itemID, int price, String token) throws RemoteException {
        tokenManager.validate(userID, token);
        return Optional.ofNullable(auctionItems.get(itemID))
            .filter(item -> price > item.getHighestBid())
            .map(item -> synchronizedUpdate(item, price))
            .orElseThrow(() -> new RemoteException("Bid failed for item ID: " + itemID));
    }

    private boolean synchronizedUpdate(UtilityClass.AuctionItem item, int price) {
        synchronized (item) {
            if (price > item.getHighestBid()) {
                item.setHighestBid(price);
                return true;
            }
            return false;
        }
    }

    public static void main(String[] args) {
        try {
            // Use argument if provided, otherwise default to CryptoAuction/keys/server_private_key.pem
            String privateKeyPath = args.length > 0 ? args[0] : "CryptoAuction/keys/server_private_key.pem";
            
            PrivateKey privateKey = UtilityClass.SecurityUtils.loadPrivateKey(privateKeyPath);
            Server server = new Server(privateKey);
            
            // Start the RMI registry
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.rebind("Auction", server);
            
            System.out.println("Auction server is running...");
        } catch (Exception e) {
            System.err.println("Server initialization error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
}
