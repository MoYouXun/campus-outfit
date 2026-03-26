import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClients;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

public class TestUpload {
    public static void main(String[] args) throws IOException {
        // 创建一个空的PNG文件用于测试
        File testFile = new File("test.png");
        if (!testFile.exists()) {
            // 创建一个最小的PNG文件
            String pngHeader = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
            byte[] pngBytes = java.util.Base64.getDecoder().decode(pngHeader);
            java.nio.file.Files.write(testFile.toPath(), pngBytes);
            System.out.println("创建了测试图片文件: " + testFile.getAbsolutePath());
        }

        HttpClient httpclient = HttpClients.createDefault();
        HttpPost httppost = new HttpPost("http://localhost:8080/api/outfit/upload");

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addBinaryBody("files", testFile);
        httppost.setEntity(builder.build());

        System.out.println("正在发送请求...");
        HttpResponse response = httpclient.execute(httppost);
        System.out.println("响应状态: " + response.getStatusLine());

        // 读取响应内容
        try (InputStream inputStream = response.getEntity().getContent()) {
            Scanner scanner = new Scanner(inputStream, "UTF-8");
            String responseBody = scanner.useDelimiter("\\A").next();
            System.out.println("响应内容: " + responseBody);
        }

        // 清理
        testFile.delete();
    }
}