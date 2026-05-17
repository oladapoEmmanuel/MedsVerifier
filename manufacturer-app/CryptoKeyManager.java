
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.*;
import java.util.Base64;

public class CryptoKeyManager {

    public static KeyPair generateKeyPair() throws Exception {

        Security.addProvider(new BouncyCastleProvider());
        KeyPairGenerator genKeyPair = KeyPairGenerator.getInstance("ECDSA", "BC");
        genKeyPair.initialize(256);
        KeyPair keyPair = genKeyPair.generateKeyPair();
        System.out.println("Key generated successfully");
        return keyPair;

    }

    public static void savePrivateKey(PrivateKey privateKey) throws Exception {
        String encoded = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        java.nio.file.Files.writeString(java.nio.file.Paths.get("private_key.pem"), encoded);
    }

    public static PrivateKey loadPrivateKey() throws Exception {
        String encoded = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get("private_key.pem"))).trim();
        byte[] keyBytes = Base64.getDecoder().decode(encoded);
        java.security.spec.PKCS8EncodedKeySpec keySpec =
                new java.security.spec.PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("ECDSA", "BC");
        return keyFactory.generatePrivate(keySpec);
    }

}