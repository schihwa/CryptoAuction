
import java.io.Serializable;
import java.security.PublicKey;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    int userID;
    String email;
    PublicKey publicKey;
    String challenge; // Package-private field for accessibility by Server

    public User(int userID, String email, PublicKey publicKey) {
        this.userID = userID;
        this.email = email;
        this.publicKey = publicKey;
        this.challenge = null; // Initialized as null for initial state
    }
}
