import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.util.Base64;
import java.util.Scanner;

public class SetupApp {

    private static final String GATEWAY_URL = "http://172.25.87.44:8080";
    private static final String PRIVATE_KEY_FILE = "private_key.pem";

    public static void main(String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        Scanner scanner = new Scanner(System.in);

        // return a warning if key file already exists
        if (Files.exists(Paths.get(PRIVATE_KEY_FILE))) {
            System.out.println("WARNING: private_key.pem already exists.");
            System.out.println("Generating a new key will make all existing QR codes invalid.");
            System.out.print("Type YES to continue: ");
            if (!scanner.nextLine().trim().equals("YES")) {
                System.out.println("Aborted. Key Already Exist!.");
                return;
            }
        }

        System.out.print("Enter Manufacturer ID (must match what NAFDAC registered): ");
        String manufacturerID = scanner.nextLine().trim();

        // NAFDAC authorisation validation
        System.out.println("Checking NAFDAC authorisation...");
        String status = httpGet(GATEWAY_URL + "/getManufacturerStatus?manufacturerID=" + manufacturerID);
        System.out.println("Status: " + status);

        if (!status.equals("AUTHORISED")) {
            System.out.println("ERROR: Manufacturer is not AUTHORISED. Contact NAFDAC.");
            return;
        }

        // Generate key pair
        KeyPair keyPair = CryptoKeyManager.generateKeyPair();

        // Save private key to file
        String publicKeyStr = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        CryptoKeyManager.savePrivateKey(keyPair.getPrivate());
        System.out.println("Private key saved to: " + PRIVATE_KEY_FILE);

        // Register public key on blockchain
        System.out.println("Registering public key on blockchain...");
        String params = "manufacturerID=" + URLEncoder.encode(manufacturerID, StandardCharsets.UTF_8)
                + "&publicKey=" + URLEncoder.encode(publicKeyStr, StandardCharsets.UTF_8);
        String response = httpPost(GATEWAY_URL + "/registerKey", params);

        if (response.startsWith("ERROR")) {
            System.out.println("Failed to register key: " + response);
            System.out.println("Private key file exists but public key is NOT on blockchain.");
        } else {
            System.out.println(response);
            System.out.println("Setup complete. You can now run Main.java to create drug batches.");
        }
    }

    private static String httpPost(String urlStr, String params) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(30000);
        conn.getOutputStream().write(params.getBytes(StandardCharsets.UTF_8));
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String responseLine;
        while ((responseLine = reader.readLine()) != null) response.append(responseLine);
        reader.close();
        return response.toString();
    }

    private static String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String responseLine;
        while ((responseLine = reader.readLine()) != null) response.append(responseLine);
        reader.close();
        return response.toString();
    }
}
