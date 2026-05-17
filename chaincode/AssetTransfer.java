package org.hyperledger.fabric.samples.assettransfer;

import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

@Contract(name = "basic")
@Default
public final class AssetTransfer implements ContractInterface {

    // Register manufacturer
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String registerManufacturer(final Context ctx, final String manufacturerID) {
        String existingMfr = ctx.getStub().getStringState("MFR_" + manufacturerID);
        if (existingMfr != null && !existingMfr.isEmpty()) {
            throw new RuntimeException("Manufacturer already registered: " + manufacturerID);
        }
        ctx.getStub().putStringState("MFR_" + manufacturerID, "AUTHORISED");
        System.out.println("NAFDAC: Manufacturer registered: " + manufacturerID);
        return "Manufacturer registered successfully: " + manufacturerID;
    }

    // Revoke manufacturer
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String revokeManufacturer(final Context ctx, final String manufacturerID) {
        String existingMfr = ctx.getStub().getStringState("MFR_" + manufacturerID);
        if (existingMfr == null || existingMfr.isEmpty()) {
            throw new RuntimeException("Manufacturer not found: " + manufacturerID);
        }
        if ("REVOKED".equals(existingMfr)) {
            throw new RuntimeException("Manufacturer already revoked: " + manufacturerID);
        }
        ctx.getStub().putStringState("MFR_" + manufacturerID, "REVOKED");
        System.out.println("NAFDAC: Manufacturer REVOKED: " + manufacturerID);
        return "Manufacturer revoked: " + manufacturerID;
    }

    // Get manufacturer status
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String getManufacturerStatus(final Context ctx, final String manufacturerID) {
        String status = ctx.getStub().getStringState("MFR_" + manufacturerID);
        if (status == null || status.isEmpty()) { return "NOT_REGISTERED"; }
        return status;
    }

    // Register manufacturer public key
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void registerKey(final Context ctx, final String manufacturerID, final String publicKey) {
        String status = ctx.getStub().getStringState("MFR_" + manufacturerID);
        if (status == null || status.isEmpty()) {
            throw new RuntimeException("Manufacturer not registered with NAFDAC: " + manufacturerID);
        }
        if ("REVOKED".equals(status)) {
            throw new RuntimeException("Manufacturer is REVOKED by NAFDAC: " + manufacturerID);
        }
        ctx.getStub().putStringState(manufacturerID, publicKey);
        System.out.println("Public key registered for: " + manufacturerID);
    }

    // Get manufacturer public key
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String getKey(final Context ctx, final String manufacturerID) {
        String govStatus = ctx.getStub().getStringState("MFR_" + manufacturerID);
        if ("REVOKED".equals(govStatus)) { return "REVOKED"; }
        String publicKey = ctx.getStub().getStringState(manufacturerID);
        if (publicKey == null || publicKey.isEmpty()) {
            return "Manufacturer not found: " + manufacturerID;
        }
        return publicKey;
    }

    // Get all manufacturer public keys
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String getAllKeys(final Context ctx) {
        StringBuilder result = new StringBuilder("[");
        QueryResultsIterator<KeyValue> iterator = ctx.getStub().getStateByRange("", "");
        boolean first = true;
        for (KeyValue kv : iterator) {
            String key = kv.getKey();
            if (key.startsWith("MFR_")) continue;
            String govStatus = ctx.getStub().getStringState("MFR_" + key);
            if ("REVOKED".equals(govStatus)) continue;
            if (!first) { result.append(","); }
            result.append("{\"manufacturerID\":\"").append(key)
                  .append("\",\"publicKey\":\"").append(kv.getStringValue()).append("\"}");
            first = false;
        }
        try { iterator.close(); } catch (Exception e) { /* ignore */ }
        result.append("]");
        return result.toString();
    }
}
