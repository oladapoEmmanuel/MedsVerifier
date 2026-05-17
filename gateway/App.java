import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.Hash;
import org.hyperledger.fabric.client.Network;
import org.hyperledger.fabric.client.identity.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class App {

    private static final String MSP_ID = "Org1MSP";
    private static final String CHANNEL_NAME = "pharmachannel";
    private static final String CHAINCODE_NAME = "pharmacc";
    private static final String PEER_ENDPOINT = "peer0.org1.example.com:7051";
    private static final String OVERRIDE_AUTH = "peer0.org1.example.com";
    private static final Path CRYPTO_PATH = Paths.get(
        "../../test-network/organizations/peerOrganizations/org1.example.com");
    private static final Path CERT_DIR = CRYPTO_PATH.resolve(
        "users/User1@org1.example.com/msp/signcerts");
    private static final Path KEY_DIR = CRYPTO_PATH.resolve(
        "users/User1@org1.example.com/msp/keystore");
    private static final Path TLS_CERT_PATH = CRYPTO_PATH.resolve(
        "peers/peer0.org1.example.com/tls/ca.crt");
    private static Gateway gateway;
    private static Contract contract;

    public static void main(String[] args) throws Exception {
        System.out.println("Connecting to Fabric...");
        ManagedChannel channel = newGrpcConnection();
        gateway = Gateway.newInstance()
            .identity(newIdentity())
            .signer(newSigner())
            .hash(Hash.SHA256)
            .connection(channel)
            .evaluateOptions(o -> o.withDeadlineAfter(30, TimeUnit.SECONDS))
            .endorseOptions(o -> o.withDeadlineAfter(30, TimeUnit.SECONDS))
            .submitOptions(o -> o.withDeadlineAfter(30, TimeUnit.SECONDS))
            .commitStatusOptions(o -> o.withDeadlineAfter(2, TimeUnit.MINUTES))
            .connect();
        Network network = gateway.getNetwork(CHANNEL_NAME);
        contract = network.getContract(CHAINCODE_NAME);
        System.out.println("Connected to Fabric successfully.");
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8080), 0);
        server.createContext("/getKey", new GetKeyHandler());
        server.createContext("/registerKey", new RegisterKeyHandler());
        server.createContext("/registerManufacturer", new RegisterManufacturerHandler());
        server.createContext("/revokeManufacturer", new RevokeManufacturerHandler());
        server.createContext("/getManufacturerStatus", new GetManufacturerStatusHandler());
        server.createContext("/getAllKeys", new GetAllKeysHandler());
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();
        System.out.println("Server running at http://localhost:8080");
    }

    private static ManagedChannel newGrpcConnection() throws IOException {
        var credentials = TlsChannelCredentials.newBuilder()
            .trustManager(TLS_CERT_PATH.toFile()).build();
        return Grpc.newChannelBuilder(PEER_ENDPOINT, credentials)
            .overrideAuthority(OVERRIDE_AUTH).build();
    }

    private static Identity newIdentity() throws IOException, CertificateException {
        try (var r = Files.newBufferedReader(getFirstFilePath(CERT_DIR))) {
            return new X509Identity(MSP_ID, Identities.readX509Certificate(r));
        }
    }

    private static Signer newSigner() throws IOException, InvalidKeyException {
        try (var r = Files.newBufferedReader(getFirstFilePath(KEY_DIR))) {
            return Signers.newPrivateKeySigner(Identities.readPrivateKey(r));
        }
    }

    private static Path getFirstFilePath(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return files.findFirst().orElseThrow();
        }
    }

    private static void sendResponse(HttpExchange e, String response) throws IOException {
        e.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        byte[] rb = response.getBytes(StandardCharsets.UTF_8);
        e.sendResponseHeaders(200, rb.length);
        e.getResponseBody().write(rb);
        e.getResponseBody().close();
    }

    static class GetKeyHandler implements HttpHandler {
        public void handle(HttpExchange e) throws IOException {
            String query = e.getRequestURI().getQuery();
            String mID = "";
            if (query != null && query.startsWith("manufacturerID="))
                mID = query.substring("manufacturerID=".length());
            String response;
            try {
                byte[] result = contract.evaluateTransaction("getKey", mID);
                response = new String(result, StandardCharsets.UTF_8);
            } catch (Exception ex) {
                response = "ERROR: " + ex.getMessage();
            }
            sendResponse(e, response);
        }
    }

    static class RegisterKeyHandler implements HttpHandler {
        public void handle(HttpExchange e) throws IOException {
            if (!e.getRequestMethod().equals("POST")) {
                e.sendResponseHeaders(405, 0); return;
            }
            String body = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String mID = "", pubKey = "";
            for (String part : body.split("&")) {
                if (part.startsWith("manufacturerID="))
                    mID = java.net.URLDecoder.decode(part.substring(15), StandardCharsets.UTF_8);
                if (part.startsWith("publicKey="))
                    pubKey = java.net.URLDecoder.decode(part.substring(10), StandardCharsets.UTF_8);
            }
            String response;
            try {
                contract.submitTransaction("registerKey", mID, pubKey);
                response = "Key registered for: " + mID;
            } catch (Exception ex) {
                response = "ERROR: " + ex.getMessage();
            }
            sendResponse(e, response);
        }
    }

    static class RegisterManufacturerHandler implements HttpHandler {
        public void handle(HttpExchange e) throws IOException {
            if (!e.getRequestMethod().equals("POST")) {
                e.sendResponseHeaders(405, 0); return;
            }
            String body = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String mID = "";
            for (String part : body.split("&")) {
                if (part.startsWith("manufacturerID="))
                    mID = java.net.URLDecoder.decode(part.substring(15), StandardCharsets.UTF_8);
            }
            String response;
            try {
                byte[] result = contract.submitTransaction("registerManufacturer", mID);
                response = new String(result, StandardCharsets.UTF_8);
            } catch (Exception ex) {
                response = "ERROR: " + ex.getMessage();
            }
            sendResponse(e, response);
        }
    }

    static class RevokeManufacturerHandler implements HttpHandler {
        public void handle(HttpExchange e) throws IOException {
            if (!e.getRequestMethod().equals("POST")) {
                e.sendResponseHeaders(405, 0); return;
            }
            String body = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String mID = "";
            for (String part : body.split("&")) {
                if (part.startsWith("manufacturerID="))
                    mID = java.net.URLDecoder.decode(part.substring(15), StandardCharsets.UTF_8);
            }
            String response;
            try {
                byte[] result = contract.submitTransaction("revokeManufacturer", mID);
                response = new String(result, StandardCharsets.UTF_8);
            } catch (Exception ex) {
                response = "ERROR: " + ex.getMessage();
            }
            sendResponse(e, response);
        }
    }

    static class GetManufacturerStatusHandler implements HttpHandler {
        public void handle(HttpExchange e) throws IOException {
            String query = e.getRequestURI().getQuery();
            String mID = "";
            if (query != null && query.startsWith("manufacturerID="))
                mID = query.substring("manufacturerID=".length());
            String response;
            try {
                byte[] result = contract.evaluateTransaction("getManufacturerStatus", mID);
                response = new String(result, StandardCharsets.UTF_8);
            } catch (Exception ex) {
                response = "ERROR: " + ex.getMessage();
            }
            sendResponse(e, response);
        }
    }

    static class GetAllKeysHandler implements HttpHandler {
        public void handle(HttpExchange e) throws IOException {
            String response;
            try {
                byte[] result = contract.evaluateTransaction("getAllKeys");
                response = new String(result, StandardCharsets.UTF_8);
                System.out.println("getAllKeys() returned " + response.length() + " bytes");
            } catch (Exception ex) {
                response = "ERROR: " + ex.getMessage();
            }
            sendResponse(e, response);
        }
    }
}
