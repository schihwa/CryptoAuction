import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.*;
import java.util.*;

public class Client {

    private static Auction auction;
    private static int userID;
    private static PrivateKey privateKey;
    private static PublicKey publicKey;
    private static PublicKey serverPublicKey;
    private static String token;

    public static void main(String[] args) {
        try {
            // Load the server's public key
            serverPublicKey = SecurityUtils.loadPublicKey("../keys/server_public.key");

            // Connect to the RMI registry and look up the Auction service
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            auction = (Auction) registry.lookup("Auction");

            // Generate the client's RSA key pair
            generateClientKeyPair();

            // Register the user with the server
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter your email: ");
            String email = scanner.nextLine();
            userID = auction.register(email, publicKey);
            System.out.println("Registered with userID: " + userID);

            // Show the main menu
            showMenu(scanner);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Generates the client's RSA key pair.
     */
    private static void generateClientKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        privateKey = keyPair.getPrivate();
        publicKey = keyPair.getPublic();
    }

    /**
     * Authenticates the client with the server.
     */
    private static boolean authenticate() {
        try {
            String clientChallenge = UUID.randomUUID().toString();
            ChallengeInfo challengeInfo = auction.challenge(userID, clientChallenge);
            if (challengeInfo == null) return false;

            boolean serverVerified = SecurityUtils.verify(clientChallenge, challengeInfo.response, serverPublicKey);
            if (!serverVerified) return false;

            byte[] signature = SecurityUtils.sign(challengeInfo.serverChallenge, privateKey);
            TokenInfo tokenInfo = auction.authenticate(userID, signature);
            if (tokenInfo == null) return false;

            token = tokenInfo.token;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Executes an action after successful authentication.
     */
    private static void executeAuthenticated(Runnable action) {
        if (authenticate()) action.run();
        else System.out.println("Authentication failed.");
    }

    /**
     * Shows the main menu and handles user input.
     */
    private static void showMenu(Scanner scanner) {
        Map<Integer, Runnable> menuOptions = new HashMap<>();
        menuOptions.put(1, () -> listItems());
        menuOptions.put(2, () -> getItemDetails(scanner));
        menuOptions.put(3, () -> createAuction(scanner));
        menuOptions.put(4, () -> bidOnItem(scanner));
        menuOptions.put(5, () -> closeAuction(scanner));

        while (true) {
            System.out.println("\nMenu:");
            System.out.println("1. List items");
            System.out.println("2. Get item details");
            System.out.println("3. Create new auction");
            System.out.println("4. Bid on item");
            System.out.println("5. Close auction");
            System.out.println("6. Exit");
            System.out.print("Select an option: ");

            int choice = Integer.parseInt(scanner.nextLine());
            if (choice == 6) {
                System.out.println("Exiting.");
                return;
            }
            menuOptions.getOrDefault(choice, () -> System.out.println("Invalid choice.")).run();
        }
    }

    /**
     * Lists all auction items.
     */
    private static void listItems() {
        executeAuthenticated(() -> {
            try {
                AuctionItem[] items = auction.listItems(userID, token);
                if (items == null || items.length == 0) {
                    System.out.println("No items available.");
                    return;
                }
                System.out.println("Auction Items:");
                Arrays.stream(items).forEach(item ->
                        System.out.println("Item ID: " + item.itemID + ", Name: " + item.name + ", Highest Bid: " + item.highestBid));
            } catch (Exception e) {
                System.out.println("Error listing items.");
            }
        });
    }

    /**
     * Retrieves details of a specific auction item.
     */
    private static void getItemDetails(Scanner scanner) {
        System.out.print("Enter item ID: ");
        int itemID = Integer.parseInt(scanner.nextLine());

        executeAuthenticated(() -> {
            try {
                AuctionItem item = auction.getSpec(userID, itemID, token);
                if (item == null) {
                    System.out.println("Item not found or token invalid.");
                    return;
                }
                System.out.println("Item ID: " + item.itemID);
                System.out.println("Name: " + item.name);
                System.out.println("Description: " + item.description);
                System.out.println("Highest Bid: " + item.highestBid);
            } catch (Exception e) {
                System.out.println("Error retrieving item details.");
            }
        });
    }

    /**
     * Creates a new auction.
     */
    private static void createAuction(Scanner scanner) {
        // Collect data before authentication
        System.out.print("Enter item name: ");
        String name = scanner.nextLine();
        System.out.print("Enter item description: ");
        String description = scanner.nextLine();
        System.out.print("Enter reserve price: ");
        int reservePrice = Integer.parseInt(scanner.nextLine());

        AuctionSaleItem item = new AuctionSaleItem();
        item.name = name;
        item.description = description;
        item.reservePrice = reservePrice;

        executeAuthenticated(() -> {
            try {
                int itemID = auction.newAuction(userID, item, token);
                System.out.println(itemID != -1 ? "Auction created with item ID: " + itemID : "Failed to create auction.");
            } catch (Exception e) {
                System.out.println("Error creating auction.");
            }
        });
    }

    /**
     * Places a bid on an auction item.
     */
    private static void bidOnItem(Scanner scanner) {
        // Collect data before authentication
        System.out.print("Enter item ID: ");
        int itemID = Integer.parseInt(scanner.nextLine());
        System.out.print("Enter your bid price: ");
        int price = Integer.parseInt(scanner.nextLine());

        executeAuthenticated(() -> {
            try {
                boolean success = auction.bid(userID, itemID, price, token);
                System.out.println(success ? "Bid placed successfully." : "Failed to place bid.");
            } catch (Exception e) {
                System.out.println("Error placing bid.");
            }
        });
    }

    /**
     * Closes an auction item.
     */
    private static void closeAuction(Scanner scanner) {
        // Collect data before authentication
        System.out.print("Enter item ID to close: ");
        int itemID = Integer.parseInt(scanner.nextLine());

        executeAuthenticated(() -> {
            try {
                AuctionResult result = auction.closeAuction(userID, itemID, token);
                if (result == null) {
                    System.out.println("Failed to close auction.");
                    return;
                }
                System.out.println("Auction closed.");
                if (result.winningEmail != null) {
                    System.out.println("Winning Email: " + result.winningEmail);
                    System.out.println("Winning Price: " + result.winningPrice);
                } else {
                    System.out.println("No winning bid (reserve price not met).");
                }
            } catch (Exception e) {
                System.out.println("Error closing auction.");
            }
        });
    }
}