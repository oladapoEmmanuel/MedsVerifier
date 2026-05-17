
import java.util.Scanner;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class ProductData {
    private String manufacturerName;
    private String productName;
    private String gtin;
    private String batchNumber;
    private String timeStamp;

    public ProductData(String manufacturerID){
        //manufacturers registering product data
        this.manufacturerName = manufacturerID;
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter drug name: ");
        this.productName = scanner.nextLine();
        this.gtin = "TIN098736JU";
        System.out.println("Enter batch number: ");
        this.batchNumber = scanner.nextLine();
        this.timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));


    }

    // create and return a payload of the registered product data
    public String BuildProductData(){
        return "Manufacturer:" + manufacturerName
                + "|ProductName:" + productName
                + "|GTIN:" + gtin
                + "|BatchNumber:" + batchNumber
                + "|TimeStamp:" + timeStamp;
    }

    public String getManufacturerName() {return manufacturerName;}
    public String getProductName() {return productName;}
    public String getGtin() {return gtin;}
    public String getBatchNumber() {return batchNumber;}
    public String getTimeStamp() {return timeStamp;}

}
