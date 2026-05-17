
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

public class AuthenticityVerifier {

    public static boolean verifyProduct(String productData,
                                        String signedProductData,
                                        PublicKey publicKey) throws Exception{

        byte[] signatureByte = Base64.getDecoder().decode(signedProductData);
        Signature verifySignature = Signature.getInstance("SHA256withECDSA", "BC");
        verifySignature.initVerify(publicKey);
        verifySignature.update(productData.getBytes());
        boolean isProductAuthentic = verifySignature.verify(signatureByte);
        System.out.println("Verification Completed Successfully");
        return isProductAuthentic;

    }
}
