
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class TokenManager {
    private final Map<Integer, TokenInfo> tokens = new ConcurrentHashMap<>();
    static final long TOKEN_EXPIRY_DURATION = TimeUnit.SECONDS.toMillis(10);

    // Generate a new token for a user
    public TokenInfo generateToken(int userID) {
        TokenInfo tokenInfo = new TokenInfo();
        tokenInfo.token = UUID.randomUUID().toString();  // Generate a new unique UUID token
        tokenInfo.expiryTime = System.currentTimeMillis() + TOKEN_EXPIRY_DURATION;

        // Ensure any old token for this user is invalidated before storing the new token
        tokens.put(userID, tokenInfo);  // Store the new token for the user
        System.out.println("Token generated for userID " + userID + ": " + tokenInfo.token + ", expires at " + tokenInfo.expiryTime);
        return tokenInfo;
    }

    // Validate the token (check if it's expired, matches, and was valid for the user)
    public boolean validate(int userID, String token) {
        TokenInfo tokenInfo = tokens.get(userID);

        if (tokenInfo == null) {
            System.out.println("No token found for userID " + userID);
            return false;  // No token found
        }

        // Check if token is valid, matches, and hasn't expired
        if (Objects.equals(tokenInfo.token, token) && System.currentTimeMillis() < tokenInfo.expiryTime) {
            tokens.remove(userID); 
            System.out.println("Token validated and removed for userID " + userID);
            return true;  // Token is valid
        }

        // If token is invalid or expired, remove it
        System.out.println("Token validation failed for userID " + userID + ". Expired or incorrect token.");
        tokens.remove(userID);  // Remove expired or invalid token
        return false;  // Token is invalid
    }
}
