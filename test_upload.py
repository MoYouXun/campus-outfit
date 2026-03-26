import requests
import base64
import json

# 创建一个最小的PNG文件
def create_test_png():
    # 最小的PNG文件（1x1像素，黑色）
    png_data = base64.b64decode(
        b'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=='
    )
    with open('test.png', 'wb') as f:
        f.write(png_data)
    print("Created test.png file")

# 测试上传功能
def test_upload():
    create_test_png()
    
    url = 'http://localhost:8080/api/outfit/upload'
    files = {'files': open('test.png', 'rb')}
    
    try:
        print("Sending request to", url)
        response = requests.post(url, files=files)
        print(f"Response status code: {response.status_code}")
        print(f"Response headers: {dict(response.headers)}")
        
        try:
            json_response = response.json()
            print(f"Response JSON: {json.dumps(json_response, indent=2, ensure_ascii=False)}")
        except json.JSONDecodeError:
            print(f"Response content: {response.text}")
            
    except Exception as e:
        print(f"Error occurred: {e}")

if __name__ == "__main__":
    test_upload()