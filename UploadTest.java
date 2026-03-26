import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class UploadTest {
    public static void main(String[] args) throws IOException {
        // 图片文件路径
        String imagePath = "e:/Profession/specialty practice2/campus-outfit-project/frontend/7C1E6E1CD7EBCCF64D244BC254B9D098.jpg";
        File imageFile = new File(imagePath);
        
        if (!imageFile.exists()) {
            System.out.println("图片文件不存在: " + imagePath);
            return;
        }
        
        System.out.println("使用图片: " + imageFile.getName() + " (大小: " + imageFile.length() + " bytes)");
        
        // API URL
        String apiUrl = "http://localhost:8080/api/outfit/upload";
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        // 设置请求方法和头信息
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setDoInput(true);
        
        String boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW";
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        
        // 构建请求体
        OutputStream os = conn.getOutputStream();
        
        // 开始表单数据
        os.write(("--" + boundary + "\r\n").getBytes());
        os.write("Content-Disposition: form-data; name=\"files\"; filename=\"test.jpg\"\r\n".getBytes());
        os.write("Content-Type: image/jpeg\r\n\r\n".getBytes());
        
        // 写入图片数据
        FileInputStream fis = new FileInputStream(imageFile);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            os.write(buffer, 0, bytesRead);
        }
        fis.close();
        
        // 结束表单数据
        os.write("\r\n".getBytes());
        os.write(("--" + boundary + "--\r\n").getBytes());
        os.flush();
        os.close();
        
        // 获取响应
        int responseCode = conn.getResponseCode();
        System.out.println("\n响应状态码: " + responseCode);
        
        // 读取响应内容
        BufferedReader br = new BufferedReader(new InputStreamReader(
                responseCode == 200 ? conn.getInputStream() : conn.getErrorStream()));
        String line;
        StringBuffer response = new StringBuffer();
        
        while ((line = br.readLine()) != null) {
            response.append(line);
        }
        br.close();
        
        // 打印响应
        System.out.println("响应内容: " + response.toString());
        
        conn.disconnect();
    }
}