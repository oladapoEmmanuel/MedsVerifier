
import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

public class QRCodeGenerator {

    public static void GenerateQrCode(String qrData, String fileName) throws Exception{
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(qrData, BarcodeFormat.QR_CODE, 400, 400);
        Path filePath = FileSystems.getDefault().getPath(fileName);
        MatrixToImageWriter.writeToPath(bitMatrix, "PNG", filePath);
        System.out.println("QR Code generated and saved: " + fileName);

    }
}
