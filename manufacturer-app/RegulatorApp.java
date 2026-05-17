import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class RegulatorApp {
    private static final String GATEWAY_URL = "http://172.25.87.44:8080";

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        System.out.println("NAFDAC Regulator Application");
        System.out.println("1. Register a manufacturer");
        System.out.println("2. Revoke a manufacturer");
        System.out.println("3. Check manufacturer status");
        System.out.print("Enter choice: ");
        String choice = scanner.nextLine().trim();
        System.out.print("Enter Manufacturer ID: ");
        String manufacturerID = scanner.nextLine().trim();
        if (choice.equals("1")) {
            String params = "manufacturerID=" + URLEncoder.encode(manufacturerID, StandardCharsets.UTF_8);
            String response = httpPost(GATEWAY_URL+"/registerManufacturer", params);
            if (response.startsWith("ERROR")) {
                System.out.println("Registration failed: " + response);
            } else {
                System.out.println(response);
            }
        } else if (choice.equals("2")) {
            String status = httpGet(GATEWAY_URL+"/getManufacturerStatus?manufacturerID=" + manufacturerID);
            if (status.equals("NOT_REGISTERED")) {
                System.out.println("manufacturer not registered.");
            } else if (status.equals("REVOKED")) {
                System.out.println("Manufacturer is already revoked.");
            } else {
                String params = "manufacturerID=" + URLEncoder.encode(manufacturerID, StandardCharsets.UTF_8);
                String response = httpPost(GATEWAY_URL+"/revokeManufacturer", params);
                if (response.startsWith("ERROR")) {
                    System.out.println("Revocation failed: " + response);
                } else {
                    System.out.println(response);
                }
            }
        } else if (choice.equals("3")) {
            String status = httpGet(GATEWAY_URL+"/getManufacturerStatus?manufacturerID=" + manufacturerID);
            System.out.println("Manufacturer: " + manufacturerID);
            System.out.println("Status: " + status);
        } else {
            System.out.println("Invalid choice.");
        }

    }

    private static String httpPost(String urlStr, String params) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(30000);
        conn.getOutputStream().write(params.getBytes(StandardCharsets.UTF_8));
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();

        String responseLine;
        while((responseLine = reader.readLine()) != null) {
            response.append(responseLine);
        }

        reader.close();
        return response.toString();
    }

    private static String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();

        String responseLine;
        while((responseLine = reader.readLine()) != null) {
            response.append(responseLine);
        }

        reader.close();
        return response.toString();
    }
}
