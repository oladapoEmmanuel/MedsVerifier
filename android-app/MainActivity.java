package com.medsverifier;

import android.os.Bundle;
import android.util.Base64;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    TextView viewResult;
    Button scanButton;

    ActivityResultLauncher<ScanOptions> scanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() == null) {
                    viewResult.setText("Scan cancelled.");
                } else {
                    verifyProductData(result.getContents());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Replace Android's stripped BouncyCastle with full version for ECDSA support
        Security.removeProvider("BC");
        Security.addProvider(new BouncyCastleProvider());

        viewResult = findViewById(R.id.viewResult);
        scanButton = findViewById(R.id.btnScan);

        scanButton.setOnClickListener(v -> {
            ScanOptions options = new ScanOptions();
            options.setPrompt("Scan Medicine QR Code");
            options.setBeepEnabled(true);
            options.setOrientationLocked(false);
            scanLauncher.launch(options);
        });

        // Sync all manufacturer public keys from blockchain when app starts
        syncKeysFromBlockchain();
    }
    private void verifyProductData(String qrContent) {

        // Extract manufacturer name and signature from QR code
        String manufacturerName = retrieveProductField(qrContent, "Manufacturer");
        String signature = retrieveProductField(qrContent, "SIG");

        // Product data is everything before the signature
        String productData = qrContent;
        int sigIndex = qrContent.lastIndexOf("|SIG:");
        if (sigIndex != -1) {
            productData = qrContent.substring(0, sigIndex);
        }

        final String finalProductData = productData;
        final String finalSignature = signature;
        final String finalManufacturerName = manufacturerName;

        new Thread(() -> {

            String publicKeyStr = null;
            boolean isOffline = false;

            try {
                // Try to get public key from blockchain
                String encodedName = java.net.URLEncoder.encode(finalManufacturerName, "UTF-8");
                URL url = new URL("http://10.245.168.46:8080/getKey?manufacturerID=" + encodedName);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                publicKeyStr = response.toString();

                // Save the key to local cache for offline use later
                if (publicKeyStr != null && !publicKeyStr.contains("not found")) {
                    getSharedPreferences("pharma_keys", MODE_PRIVATE)
                            .edit().putString(finalManufacturerName, publicKeyStr).apply();
                }

            } catch (Exception e) {
                // No network — try local cache instead
                isOffline = true;
                publicKeyStr = getSharedPreferences("pharma_keys", MODE_PRIVATE)
                        .getString(finalManufacturerName, null);
            }

            final String finalPublicKey = publicKeyStr;
            final boolean finalIsOffline = isOffline;

            runOnUiThread(() -> {

                // manufacturer not found online or in cache
                if (finalPublicKey == null || finalPublicKey.contains("not found")) {
                    viewResult.setText("Manufacturer not recognised!");
                    return;
                }

                // manufacturer has been revoked by NAFDAC
                if (finalPublicKey.equals("REVOKED")) {
                    viewResult.setText("WARNING: This manufacturer has been REVOKED by NAFDAC.\n\nDo NOT consume this medicine.\nContact NAFDAC or your pharmacy.");
                    return;
                }

                // Offline and key was never cached before
                if (finalIsOffline && finalPublicKey == null) {
                    viewResult.setText("No internet connection. Please connect to verify this medicine.");
                    return;
                }

                try {
                    boolean isValid = verifySignature(finalProductData, finalSignature, finalPublicKey);

                    if (isValid) {
                        String resultMessage = "Product is Valid! Authentic Medication";
                        if (finalIsOffline) {
                            resultMessage = resultMessage + "\n(Verified offline)";
                        }
                        resultMessage = resultMessage + "\n\n " + getProductDetails(finalProductData);
                        viewResult.setText(resultMessage);
                    } else {
                        viewResult.setText("Invalid QR Code! This medication may be fake.");
                    }

                } catch (Exception e) {
                    viewResult.setText("Verification error: " + e.getMessage());
                }
            });

        }).start();
    }
    private boolean verifySignature(String productData, String signatureStr, String publicKeyStr) throws Exception {
        byte[] publicKeyBytes = Base64.decode(publicKeyStr, Base64.DEFAULT);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("ECDSA", "BC");
        PublicKey publicKey = keyFactory.generatePublic(keySpec);

        byte[] signatureBytes = Base64.decode(signatureStr, Base64.DEFAULT);
        Signature sig = Signature.getInstance("SHA256withECDSA", "BC");
        sig.initVerify(publicKey);
        sig.update(productData.getBytes());
        return sig.verify(signatureBytes);
    }
    private String retrieveProductField(String record, String fieldName) {
        String[] fields = record.split("\\|");
        for (String field : fields) {
            if (field.startsWith(fieldName + ":")) {
                return field.substring(fieldName.length() + 1);
            }
        }
        return "";
    }
    private String getProductDetails(String productData) {
        StringBuilder details = new StringBuilder();
        String[] fields = productData.split("\\|");
        for (String field : fields) {
            if (!field.startsWith("SIG:")) {
                details.append(field).append("\n");
            }
        }
        return details.toString();
    }
    private void syncKeysFromBlockchain() {
        // Fetch all manufacturer public keys and store locally for offline verification
        new Thread(() -> {
            try {
                URL url = new URL("http://10.245.168.46:8080/getAllKeys");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                String json = response.toString();

                if (json.startsWith("[") && json.length() > 2) {
                    android.content.SharedPreferences.Editor editor =
                            getSharedPreferences("pharma_keys", MODE_PRIVATE).edit();

                    // Parse the JSON array manually
                    String stripped = json.substring(1, json.length() - 1);
                    String[] entries = stripped.split("\\},\\{");

                    for (String entry : entries) {
                        entry = entry.replace("{", "").replace("}", "");
                        String manufacturerID = "";
                        String publicKey = "";
                        for (String part : entry.split(",\"")) {
                            part = part.replace("\"", "");
                            if (part.startsWith("manufacturerID:")) {
                                manufacturerID = part.substring("manufacturerID:".length());
                            }
                            if (part.startsWith("publicKey:")) {
                                publicKey = part.substring("publicKey:".length());
                            }
                        }
                        if (!manufacturerID.isEmpty() && !publicKey.isEmpty()) {
                            editor.putString(manufacturerID, publicKey);
                        }
                    }
                    editor.apply();
                    runOnUiThread(() ->
                            android.widget.Toast.makeText(this, "Keys synced from blockchain",
                                    android.widget.Toast.LENGTH_SHORT).show());
                }

            } catch (Exception e) {
                runOnUiThread(() ->
                        android.widget.Toast.makeText(this, "Sync failed: " + e.getMessage(),
                                android.widget.Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
