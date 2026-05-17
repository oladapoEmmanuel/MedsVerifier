import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import java.util.Scanner;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class Main {
    private static final String GATEWAY_URL = "http://172.25.87.44:8080";

    public static void main(String[] args) throws Exception {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        // request manufacturer details
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter Manufacturer ID (must be registered on blockchain): ");
        String manufacturerID = scanner.nextLine().trim();

        // check if manufacturer is authorised on blockchain
        System.out.println("\nChecking manufacturer authorisation on blockchain...");
        String mfrStatus = getManufacturerStatus(manufacturerID);

        if (!mfrStatus.equals("AUTHORISED")) {
            System.out.println("ERROR: Manufacturer '" + manufacturerID + "' is not registered on the blockchain.");
            System.out.println("Please contact NAFDAC to register your organisation before adding products.");
            System.out.println("Exiting.");
            return;
        }

        System.out.println("Manufacturer authorised: " + mfrStatus);

        // request drug details
        ProductData productData = new ProductData(manufacturerID);
        String productDataString = productData.BuildProductData();

//        // generate key pair
//        KeyPair keyPair = CryptoKeyManager.generateKeyPair();
//        String publicKeyStr = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
//
//        // register public key on blockchain
//        System.out.println("\nRegistering public key on blockchain...");
//        String registerResponse = registerKey(manufacturerID, publicKeyStr);
//        if (registerResponse.startsWith("ERROR")) {
//            System.out.println("Failed to register key: " + registerResponse);
//            return;
//        }
//        System.out.println("Public key registered successfully.");


        // load existing private key from file
        if (!new java.io.File("private_key.pem").exists()) {
            System.out.println("ERROR: private_key.pem not found. Run SetupApp first.");
            return;
        }
        PrivateKey privateKey = CryptoKeyManager.loadPrivateKey();

        // sign the drug record
        //String signature = ProductDataSigner.signProductData(productDataString, keyPair.getPrivate());

        String signature = ProductDataSigner.signProductData(productDataString, privateKey);

        // generate QR code
        String qrData = productDataString + "|SIG:" + signature;
        String fileName = productData.getProductName() + productData.getBatchNumber() + ".png";
        QRCodeGenerator.GenerateQrCode(qrData, fileName);
        System.out.println("\nQR code saved: " + fileName);

        // verify drugs
        //boolean isValid = AuthenticityVerifier.verifyProduct(productDataString, signature, keyPair.getPublic());
        //System.out.println("Local verification: " + (isValid ? "VALID" : "INVALID"));
    }

    private static String getManufacturerStatus(String manufacturerID) throws Exception {
        URL url = new URL(GATEWAY_URL + "/getManufacturerStatus?manufacturerID=" + URLEncoder.encode(manufacturerID, StandardCharsets.UTF_8));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) response.append(line);
        reader.close();
        return response.toString();
    }

//    private static String registerKey(String manufacturerID, String publicKey) throws Exception {
//        URL url = new URL(GATEWAY_URL + "/registerKey");
//        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//        conn.setRequestMethod("POST");
//        conn.setDoOutput(true);
//        conn.setConnectTimeout(10000);
//        conn.setReadTimeout(30000);
//        String params = "manufacturerID=" + URLEncoder.encode(manufacturerID, StandardCharsets.UTF_8)
//                + "&publicKey=" + URLEncoder.encode(publicKey, StandardCharsets.UTF_8);
//        conn.getOutputStream().write(params.getBytes(StandardCharsets.UTF_8));
//        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
//        StringBuilder response = new StringBuilder();
//        String line;
//        while ((line = reader.readLine()) != null) response.append(line);
//        reader.close();
//        return response.toString();
//    }
}