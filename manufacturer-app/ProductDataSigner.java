
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;

public class ProductDataSigner {

    public static String signProductData(String productData, PrivateKey privateKey) throws Exception{
        Signature productSigner = Signature.getInstance("SHA256withECDSA", "BC");
        productSigner.initSign(privateKey);
        productSigner.update(productData.getBytes());
        byte[] signatureByte = productSigner.sign();
        System.out.println("Product Data Successfully Signed");
        return Base64.getEncoder().encodeToString(signatureByte);

    }
}
